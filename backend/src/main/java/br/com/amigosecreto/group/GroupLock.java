package br.com.amigosecreto.group;

import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import java.time.Clock;
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
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public GroupLock(
            SecretGroupRepository groups,
            GroupMemberRepository members,
            Clock clock,
            ApplicationEventPublisher events) {
        this.groups = groups;
        this.members = members;
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
     * Finaliza um grupo já travado cujo evento aconteceu e publica {@code group-finished} (entregue após o
     * commit). Log só com o ID do grupo.
     *
     * @return se o grupo acabou de ser finalizado
     */
    public boolean finishIfDue(SecretGroup group) {
        if (!group.finishIfDue(clock.instant())) {
            return false;
        }
        int count = (int) members.countByIdGroupId(group.getId());
        events.publishEvent(new GroupFinishedEvent(group.getId(), count));
        log.info("Grupo {} finalizado", group.getId());
        return true;
    }
}
