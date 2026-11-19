package br.com.amigosecreto.group;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.events.GroupEvents.GroupUpdatedEvent;
import br.com.amigosecreto.group.dto.ConfirmationRequest;
import br.com.amigosecreto.group.dto.GroupDetail;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmação de participação (US2 da 002, FR-008 a FR-010, FR-035). Sempre o usuário da sessão: ninguém
 * confirma por outra pessoa.
 */
@Service
public class GroupConfirmationService {

    private final SecretGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupService groupService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public GroupConfirmationService(
            SecretGroupRepository groups,
            GroupMemberRepository members,
            GroupService groupService,
            Clock clock,
            ApplicationEventPublisher events) {
        this.groups = groups;
        this.members = members;
        this.groupService = groupService;
        this.clock = clock;
        this.events = events;
    }

    /**
     * Confirma quem está na sessão. Trava a linha do grupo, a mesma da alteração de detalhes e do sorteio:
     * ou a confirmação vem antes de uma mudança de data ou local (e é desfeita por ela), ou vem depois e a
     * versão vista já é antiga (FR-035). Quem já confirmou recebe o grupo sem nenhuma mudança (FR-010).
     */
    @Transactional
    public GroupDetail confirm(UUID userId, UUID groupId, ConfirmationRequest request) {
        SecretGroup group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException(GroupService.GROUP_NOT_FOUND));
        GroupMember member = members.findById(new GroupMemberId(groupId, userId))
                .orElseThrow(() -> new NotFoundException(GroupService.GROUP_NOT_FOUND));
        if (group.getStatus() == GroupStatus.FINISHED) {
            throw new ConflictException("group-finished", "Este grupo foi finalizado.");
        }
        if (group.getStatus() != GroupStatus.OPEN) {
            throw new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado.");
        }
        if (member.isConfirmed()) {
            return groupService.detail(group, userId);
        }
        if (request.detailsVersion() != group.getDetailsVersion()) {
            throw new ConflictException(
                    "details-changed", "Os detalhes do evento mudaram. Confira e confirme de novo.");
        }

        member.confirm(clock.instant());
        int count = (int) members.countByIdGroupId(groupId);
        // Entregue aos membros só depois do commit (SseEmitterRegistry, AFTER_COMMIT)
        events.publishEvent(new GroupUpdatedEvent(groupId, GroupStatus.OPEN, count, false));
        return groupService.detail(group, userId);
    }
}
