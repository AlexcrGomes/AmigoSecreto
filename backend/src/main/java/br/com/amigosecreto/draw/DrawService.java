package br.com.amigosecreto.draw;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.common.UnprocessableException;
import br.com.amigosecreto.draw.dto.DrawResult;
import br.com.amigosecreto.events.GroupEvents.GroupDrawnEvent;
import br.com.amigosecreto.group.DrawBlockedReason;
import br.com.amigosecreto.group.EventSchedule;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.ratelimit.RateLimitPolicy;
import br.com.amigosecreto.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sorteio (US3, FR-020 a FR-027; condições novas na 002, FR-013): único, atômico e no servidor. A linha do grupo fica travada
 * ({@code FOR UPDATE}) do início ao fim, então dois sorteios simultâneos nunca acontecem: o segundo encontra
 * o grupo já {@code DRAWN}. Qualquer falha desfaz tudo (rollback) e nenhum aviso é enviado. NUNCA logar
 * quem tirou quem.
 */
@Service
public class DrawService {

    private static final String GROUP_NOT_FOUND = "Grupo não encontrado.";

    private final GroupLock lock;
    private final GroupMemberRepository members;
    private final DrawAssignmentRepository assignments;
    private final DerangementGenerator derangement;
    private final AssignmentCipher cipher;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final EventSchedule schedule;
    private final ApplicationEventPublisher events;

    public DrawService(
            GroupLock lock,
            GroupMemberRepository members,
            DrawAssignmentRepository assignments,
            DerangementGenerator derangement,
            AssignmentCipher cipher,
            RateLimiter rateLimiter,
            Clock clock,
            EventSchedule schedule,
            ApplicationEventPublisher events) {
        this.lock = lock;
        this.members = members;
        this.assignments = assignments;
        this.derangement = derangement;
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.schedule = schedule;
        this.events = events;
    }

    @Transactional(noRollbackFor = GroupFinishedException.class)
    public DrawResult draw(UUID userId, UUID groupId) {
        rateLimiter.consume(RateLimitPolicy.DRAW, groupId.toString());

        SecretGroup group = lock.acquire(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        if (!members.existsById(new GroupMemberId(groupId, userId))) {
            throw new NotFoundException(GROUP_NOT_FOUND);
        }
        if (!group.isOrganizer(userId)) {
            throw new ForbiddenException("Só o organizador pode realizar o sorteio.");
        }
        if (group.getStatus() == GroupStatus.FINISHED) {
            throw new GroupFinishedException();
        }
        if (group.getStatus() != GroupStatus.OPEN) {
            throw new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado.");
        }
        // Condições da 002, na ordem de research R16; dentro do lock, então ninguém entra nem muda a data no meio
        List<UUID> participants = members.findUserIds(groupId);
        List<String> pending = members.findPendingNames(groupId);
        DrawBlockedReason blocked = group.drawBlockedReason(participants.size(), pending.size(), schedule);
        if (blocked == DrawBlockedReason.PARTICIPANTS_PENDING) {
            throw new UnprocessableException(
                    blocked.code(), DrawBlockedReason.pendingMessage(pending), Map.of("pending", pending));
        }
        if (blocked != null) {
            throw new UnprocessableException(blocked.code(), blocked.message());
        }

        Map<UUID, UUID> pairs = derangement.generate(participants);
        Instant now = clock.instant();
        List<DrawAssignment> rows = pairs.entrySet().stream()
                .map(pair -> new DrawAssignment(
                        groupId, pair.getKey(), cipher.encrypt(pair.getValue(), groupId, pair.getKey()), now))
                .toList();
        assignments.saveAll(rows);
        group.markDrawn(now);

        // Entregue aos participantes só depois do commit (SseEmitterRegistry, AFTER_COMMIT)
        events.publishEvent(new GroupDrawnEvent(groupId, participants.size()));
        return new DrawResult(groupId, GroupStatus.DRAWN, now);
    }
}
