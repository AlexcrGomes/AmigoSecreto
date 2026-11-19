package br.com.amigosecreto.group;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.group.dto.CreateGroupRequest;
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
    public static final int MIN_PARTICIPANTS_TO_DRAW = 3;
    static final int CODE_ATTEMPTS = 5;

    static final String GROUP_NOT_FOUND = "Grupo não encontrado.";
    static final String INVALID_CODE = "Código inválido.";

    private final SecretGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupCodeGenerator codes;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public GroupService(
            SecretGroupRepository groups,
            GroupMemberRepository members,
            GroupCodeGenerator codes,
            RateLimiter rateLimiter,
            Clock clock,
            ApplicationEventPublisher events) {
        this.groups = groups;
        this.members = members;
        this.codes = codes;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.events = events;
    }

    /** O criador vira organizador e participante (FR-008, FR-009). */
    @Transactional
    public GroupDetail create(UUID userId, CreateGroupRequest request) {
        SecretGroup group = groups.save(new SecretGroup(request.name(), uniqueCode(), userId, clock.instant()));
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
                        view.organizerId().equals(userId)))
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

    private GroupDetail detail(SecretGroup group, UUID userId) {
        List<Participant> participants = members.findParticipants(group.getId()).stream()
                .map(view -> new Participant(view.userId(), view.name(), group.isOrganizer(view.userId())))
                .toList();
        boolean organizer = group.isOrganizer(userId);
        boolean canDraw =
                organizer && group.getStatus() == GroupStatus.OPEN && participants.size() >= MIN_PARTICIPANTS_TO_DRAW;
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
                canDraw);
    }
}
