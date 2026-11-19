package br.com.amigosecreto.group;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.events.GroupEvents.GroupUpdatedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.group.dto.CreateGroupRequest;
import br.com.amigosecreto.group.dto.EventDetailsRequest;
import br.com.amigosecreto.group.dto.EventDetailsResponse;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.GroupSummary;
import br.com.amigosecreto.group.dto.JoinGroupRequest;
import br.com.amigosecreto.group.dto.Participant;
import br.com.amigosecreto.ratelimit.RateLimitPolicy;
import br.com.amigosecreto.ratelimit.RateLimiter;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Grupos e participantes (US2). Todo ID de usuário vem da sessão, via {@code CurrentUser}. */
@Service
public class GroupService {

    public static final int MAX_PARTICIPANTS = 100;
    static final int CODE_ATTEMPTS = 5;

    static final String GROUP_NOT_FOUND = "Grupo não encontrado.";
    static final String INVALID_CODE = "Código inválido.";

    private final SecretGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupCodeGenerator codes;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final EventSchedule schedule;
    private final ApplicationEventPublisher events;

    public GroupService(
            SecretGroupRepository groups,
            GroupMemberRepository members,
            GroupCodeGenerator codes,
            RateLimiter rateLimiter,
            Clock clock,
            EventSchedule schedule,
            ApplicationEventPublisher events) {
        this.groups = groups;
        this.members = members;
        this.codes = codes;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.schedule = schedule;
        this.events = events;
    }

    /**
     * O criador vira organizador e participante já confirmado (FR-008, FR-009 da 001). O grupo nasce com os
     * detalhes do evento (FR-001 a FR-003 da 002): data no passado não cria nada.
     */
    @Transactional
    public GroupDetail create(UUID userId, CreateGroupRequest request) {
        SecretGroup newGroup = new SecretGroup(request.name(), uniqueCode(), userId, clock.instant());
        newGroup.updateEventDetails(request.eventDetails(), schedule);
        SecretGroup group = groups.save(newGroup);
        members.save(GroupMember.organizer(group.getId(), userId, clock.instant()));
        events.publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 1));
        return detail(group, userId);
    }

    /**
     * Entra pelo código (FR-011 a FR-013). Idempotente para quem já é membro. A linha do grupo fica travada
     * ({@code FOR UPDATE}) para que entradas simultâneas e o sorteio não ultrapassem o limite nem entrem
     * depois do sorteio.
     */
    @Transactional
    public GroupDetail join(UUID userId, JoinGroupRequest request) {
        String limitKey = userId.toString();
        rateLimiter.ensureNotExhausted(RateLimitPolicy.JOIN_INVALID_CODE, limitKey);

        Optional<UUID> groupId = GroupCodeGenerator.normalize(request.code()).flatMap(groups::findIdByCode);
        if (groupId.isEmpty()) {
            rateLimiter.consume(RateLimitPolicy.JOIN_INVALID_CODE, limitKey);
            throw new NotFoundException(INVALID_CODE);
        }

        SecretGroup group =
                groups.findByIdForUpdate(groupId.get()).orElseThrow(() -> new NotFoundException(INVALID_CODE));
        if (members.existsById(new GroupMemberId(group.getId(), userId))) {
            return detail(group, userId);
        }
        if (group.getStatus() != GroupStatus.OPEN) {
            throw new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado.");
        }
        long count = members.countByIdGroupId(group.getId());
        if (count >= MAX_PARTICIPANTS) {
            throw new ConflictException("group-full", "Este grupo está cheio.");
        }

        members.save(new GroupMember(group.getId(), userId, clock.instant()));
        events.publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, (int) count + 1));
        return detail(group, userId);
    }

    /**
     * O organizador altera descrição, data, horário e local (FR-005, FR-006). Trava a linha do grupo, como
     * entrar, confirmar e sortear. Não membro recebe 404, como em qualquer rota do grupo. Antes do sorteio,
     * mudar data, horário ou local volta todos, menos o organizador, a pendente (FR-015); depois do sorteio,
     * nada muda nas confirmações (FR-016).
     */
    @Transactional
    public GroupDetail updateEventDetails(UUID userId, UUID groupId, EventDetailsRequest request) {
        SecretGroup group = groups.findByIdForUpdate(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        requireMember(userId, groupId);
        if (!group.isOrganizer(userId)) {
            throw new ForbiddenException("Só o organizador pode alterar os detalhes do evento.");
        }
        boolean scheduleChanged = group.updateEventDetails(request.toEventDetails(), schedule);
        boolean confirmationsReset = scheduleChanged && group.getStatus() == GroupStatus.OPEN;
        if (confirmationsReset) {
            members.resetConfirmations(groupId, group.getOrganizerId());
        }
        int count = (int) members.countByIdGroupId(groupId);
        events.publishEvent(new GroupUpdatedEvent(groupId, group.getStatus(), count, confirmationsReset));
        return detail(group, userId);
    }

    /** Detalhes para um membro. Grupo inexistente e não-membro respondem igual (FR-017). */
    @Transactional(readOnly = true)
    public GroupDetail getDetail(UUID userId, UUID groupId) {
        SecretGroup group = groups.findById(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        requireMember(userId, groupId);
        return detail(group, userId);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> listMine(UUID userId) {
        return members.findSummaries(userId).stream()
                .map(view -> new GroupSummary(
                        view.id(),
                        view.name(),
                        view.status(),
                        view.participantCount().intValue(),
                        view.organizerId().equals(userId),
                        view.eventStartsAt() == null ? null : schedule.toLocalDate(view.eventStartsAt()),
                        view.eventStartsAt() == null
                                ? null
                                : EventDetailsResponse.formatTime(
                                        schedule.toLocalTime(view.eventStartsAt(), view.eventHasTime()))))
                .toList();
    }

    /** 404 se o usuário não é membro do grupo (o mesmo de um grupo inexistente). */
    @Transactional(readOnly = true)
    public void requireMember(UUID userId, UUID groupId) {
        if (!members.existsById(new GroupMemberId(groupId, userId))) {
            throw new NotFoundException(GROUP_NOT_FOUND);
        }
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = codes.generate();
            if (!groups.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de grupo único");
    }

    /** O grupo visto por um membro (usado também pela confirmação). */
    GroupDetail detail(SecretGroup group, UUID userId) {
        List<ParticipantView> views = members.findParticipants(group.getId());
        List<Participant> participants = views.stream()
                .map(view ->
                        new Participant(view.userId(), view.name(), group.isOrganizer(view.userId()), view.confirmed()))
                .toList();
        int confirmedCount =
                (int) views.stream().filter(ParticipantView::confirmed).count();
        boolean meConfirmed = views.stream().anyMatch(view -> view.userId().equals(userId) && view.confirmed());
        boolean open = group.getStatus() == GroupStatus.OPEN;
        DrawBlockedReason blocked = open
                ? group.drawBlockedReason(participants.size(), participants.size() - confirmedCount, schedule)
                : null;
        boolean organizer = group.isOrganizer(userId);
        boolean canDraw = organizer && open && blocked == null;
        EventDetailsResponse eventDetails =
                group.eventDetails(schedule).map(EventDetailsResponse::from).orElse(null);
        return new GroupDetail(
                group.getId(),
                group.getName(),
                GroupCodeGenerator.format(group.getCode()),
                group.getStatus(),
                group.getDrawnAt(),
                participants,
                participants.size(),
                MAX_PARTICIPANTS,
                organizer,
                canDraw,
                eventDetails,
                eventDetails == null,
                group.getDetailsVersion(),
                confirmedCount,
                meConfirmed ? ConfirmationStatus.CONFIRMED : ConfirmationStatus.PENDING,
                blocked == null ? null : blocked.code());
    }
}
