package br.com.amigosecreto.group;

import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.exitrequest.ExitRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Trava a linha do grupo para uma escrita (research R7, R15) e, antes de qualquer regra, finaliza o grupo
 * se o evento já aconteceu (research R17). Assim, entre dois ciclos do {@link GroupFinalizer}, ninguém
 * altera um grupo cujo evento passou. Deve ser chamado dentro da transação da escrita.
 */
@Component
public class GroupLock {

    private static final Logger log = LoggerFactory.getLogger(GroupLock.class);

    private final SecretGroupRepository groups;
    private final GroupMemberRepository members;
    private final ExitRequestRepository exitRequests;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public GroupLock(
            SecretGroupRepository groups,
            GroupMemberRepository members,
            ExitRequestRepository exitRequests,
            Clock clock,
            ApplicationEventPublisher events) {
        this.groups = groups;
        this.members = members;
        this.exitRequests = exitRequests;
        this.clock = clock;
        this.events = events;
    }

    /** {@code SELECT … FOR UPDATE} e finalização, se for a hora. Vazio se o grupo não existe. */
    public Optional<SecretGroup> acquire(UUID groupId) {
        Optional<SecretGroup> group = groups.findByIdForUpdate(groupId);
        group.ifPresent(this::finishIfDue);
        return group;
    }

    /**
     * Finaliza um grupo já travado cujo evento aconteceu, cancela com aviso os pedidos de saída pendentes
     * (FR-033) e publica {@code group-finished} (entregue após o commit). Log só com o ID do grupo.
     *
     * @return se o grupo acabou de ser finalizado
     */
    public boolean finishIfDue(SecretGroup group) {
        Instant now = clock.instant();
        if (!group.finishIfDue(now)) {
            return false;
        }
        exitRequests.cancelAllPending(group.getId(), now);
        int count = (int) members.countByIdGroupId(group.getId());
        events.publishEvent(new GroupFinishedEvent(group.getId(), count));
        log.info("Grupo {} finalizado", group.getId());
        return true;
    }
}
