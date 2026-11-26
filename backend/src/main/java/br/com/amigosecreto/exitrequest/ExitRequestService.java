package br.com.amigosecreto.exitrequest;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.draw.DrawService;
import br.com.amigosecreto.events.GroupEvents.DrawUndoneEvent;
import br.com.amigosecreto.events.GroupEvents.GroupUpdatedEvent;
import br.com.amigosecreto.events.GroupEvents.MembershipRevokedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.events.GroupEvents.RevocationReason;
import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import br.com.amigosecreto.group.DependentAccounts;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupService;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.dto.GroupDetail;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedido de saída depois do sorteio (US4 da 002, FR-025 a FR-033, research R18). Toda operação trava o grupo
 * pelo {@link GroupLock} — o mesmo lock de entrar, confirmar e sortear — e o finaliza antes, se o evento já
 * aconteceu. Nada aqui lê, loga ou devolve pares do sorteio (FR-032).
 */
@Service
public class ExitRequestService {

    static final String GROUP_NOT_FOUND = "Grupo não encontrado.";
    static final String REQUEST_NOT_FOUND = "Pedido de saída não encontrado.";

    private final GroupLock lock;
    private final GroupMemberRepository members;
    private final ExitRequestRepository exitRequests;
    private final GroupExitBanRepository exitBans;
    private final AppUserRepository users;
    private final DrawService drawService;
    private final GroupService groupService;
    private final DependentAccounts dependentAccounts;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ExitRequestService(
            GroupLock lock,
            GroupMemberRepository members,
            ExitRequestRepository exitRequests,
            GroupExitBanRepository exitBans,
            AppUserRepository users,
            DrawService drawService,
            GroupService groupService,
            DependentAccounts dependentAccounts,
            Clock clock,
            ApplicationEventPublisher events) {
        this.lock = lock;
        this.members = members;
        this.exitRequests = exitRequests;
        this.exitBans = exitBans;
        this.users = users;
        this.drawService = drawService;
        this.groupService = groupService;
        this.dependentAccounts = dependentAccounts;
        this.clock = clock;
        this.events = events;
    }

    /**
     * O usuário da sessão pede para sair de um grupo sorteado (FR-025). O sorteio continua valendo enquanto o
     * pedido estiver pendente (FR-027). O organizador vê o pedido ao vivo ({@code group-updated}).
     */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public ExitRequestResponse request(UUID userId, UUID groupId) {
        SecretGroup group = lockedGroupOfMember(userId, groupId);
        if (group.isOrganizer(userId)) {
            throw new ConflictException("organizer-cannot-leave", "O organizador não pode sair do grupo.");
        }
        if (group.getStatus() != GroupStatus.DRAWN) {
            throw new ConflictException(
                    "group-not-drawn", "O sorteio ainda não foi feito: você pode sair do grupo diretamente.");
        }
        if (exitRequests
                .findByGroupIdAndUserIdAndStatus(groupId, userId, ExitRequestStatus.PENDING)
                .isPresent()) {
            throw new ConflictException(
                    "exit-request-already-pending", "Você já tem um pedido de saída aguardando o organizador.");
        }

        ExitRequest request = exitRequests.save(new ExitRequest(groupId, userId, clock.instant()));
        signalUpdate(group);
        String name = users.findById(userId).map(AppUser::getName).orElse("");
        return new ExitRequestResponse(request.getId(), userId, name, request.getRequestedAt());
    }

    /** O solicitante cancela o próprio pedido pendente (FR-025). Sem aviso: foi ele quem cancelou. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public void cancel(UUID userId, UUID groupId) {
        SecretGroup group = lockedGroupOfMember(userId, groupId);
        ExitRequest request = exitRequests
                .findByGroupIdAndUserIdAndStatus(groupId, userId, ExitRequestStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Você não tem pedido de saída pendente neste grupo."));
        request.cancel(clock.instant(), false);
        signalUpdate(group);
    }

    /** O organizador recusa: nada muda no grupo, e o solicitante é avisado (FR-026). */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public GroupDetail reject(UUID userId, UUID groupId, UUID requestId) {
        SecretGroup group = lockedGroupOfMember(userId, groupId);
        ExitRequest request = pendingRequestForOrganizer(group, userId, requestId);
        request.reject(clock.instant());
        signalUpdate(group);
        return groupService.detail(group, userId);
    }

    /**
     * O organizador aprova (FR-028, FR-030, research R18), de uma só vez e com o grupo travado: apaga TODOS os
     * pares, volta o grupo a {@code OPEN}, tira o solicitante do grupo, impede que ele volte, aprova o pedido
     * e cancela, com aviso, os demais pendentes. As confirmações de quem fica continuam valendo.
     */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public GroupDetail approve(UUID userId, UUID groupId, UUID requestId) {
        SecretGroup group = lockedGroupOfMember(userId, groupId);
        ExitRequest request = pendingRequestForOrganizer(group, userId, requestId);
        if (group.getStatus() != GroupStatus.DRAWN) {
            throw new ConflictException("group-not-drawn", "O sorteio deste grupo ainda não foi realizado.");
        }
        Instant now = clock.instant();
        UUID leaving = request.getUserId();
        // Os dependentes de quem sai saem junto (FR-016 da 004); o ban é só de quem saiu (research R37)
        List<GroupMember> dependents = members.findDependents(groupId, leaving);
        List<UUID> dependentIds =
                dependents.stream().map(dependent -> dependent.getId().userId()).toList();

        // Os pares primeiro: draw_assignment referencia a participação de quem sai
        drawService.discardDraw(group, now);
        members.deleteAll(dependents);
        members.deleteById(new GroupMemberId(groupId, leaving));
        members.flush();
        dependentAccounts.deleteUnreferenced(dependentIds);
        exitBans.save(new GroupExitBan(groupId, leaving, now));
        request.approve(now);
        exitRequests.cancelAllPending(groupId, now);

        int count = (int) members.countByIdGroupId(groupId);
        // A revogação vem primeiro (os listeners pós-commit rodam na ordem de publicação): quem saiu perde as
        // conexões antes do sinal aos demais
        events.publishEvent(new MembershipRevokedEvent(groupId, leaving, RevocationReason.EXIT_APPROVED));
        events.publishEvent(new DrawUndoneEvent(groupId, count));
        events.publishEvent(new ParticipantsChangedEvent(groupId, GroupStatus.OPEN, count));
        return groupService.detail(group, userId);
    }

    /** Grupo travado (e finalizado, se for a hora); 404 para quem não é membro, 409 se finalizado. */
    private SecretGroup lockedGroupOfMember(UUID userId, UUID groupId) {
        SecretGroup group = lock.acquire(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        if (!members.existsById(new GroupMemberId(groupId, userId))) {
            throw new NotFoundException(GROUP_NOT_FOUND);
        }
        if (group.getStatus() == GroupStatus.FINISHED) {
            throw new GroupFinishedException();
        }
        return group;
    }

    private ExitRequest pendingRequestForOrganizer(SecretGroup group, UUID userId, UUID requestId) {
        if (!group.isOrganizer(userId)) {
            throw new ForbiddenException("Só o organizador pode decidir os pedidos de saída.");
        }
        ExitRequest request = exitRequests
                .findById(requestId)
                .filter(found -> found.getGroupId().equals(group.getId()))
                .orElseThrow(() -> new NotFoundException(REQUEST_NOT_FOUND));
        if (!request.isPending()) {
            throw new ConflictException("exit-request-not-pending", "Este pedido de saída já foi decidido.");
        }
        return request;
    }

    /** Sinal sem dados (R21): o organizador vê o pedido e o solicitante vê a decisão ao vivo. */
    private void signalUpdate(SecretGroup group) {
        int count = (int) members.countByIdGroupId(group.getId());
        events.publishEvent(new GroupUpdatedEvent(group.getId(), group.getStatus(), count, false));
    }
}
