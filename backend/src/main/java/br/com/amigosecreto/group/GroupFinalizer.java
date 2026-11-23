package br.com.amigosecreto.group;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finaliza sozinho os grupos sorteados cujo evento já aconteceu (FR-017, research R17). Roda na subida da
 * aplicação, recuperando o que venceu enquanto ela estava parada, e depois a cada minuto. Cada grupo é
 * finalizado na própria transação, com o mesmo lock das escritas: a falha de um não impede os outros.
 */
@Component
public class GroupFinalizer {

    static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(GroupFinalizer.class);

    private final SecretGroupRepository groups;
    private final GroupLock lock;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public GroupFinalizer(
            SecretGroupRepository groups, GroupLock lock, PlatformTransactionManager transactionManager, Clock clock) {
        this.groups = groups;
        this.lock = lock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 0)
    public void run() {
        finishDueGroups();
    }

    /**
     * Finaliza, em lotes de {@value #BATCH_SIZE}, todos os grupos vencidos. Um lote em que nada pôde ser
     * finalizado encerra a rodada, para não repetir sem fim um grupo que falha: ele volta no próximo ciclo.
     *
     * @return quantos grupos foram finalizados
     */
    public int finishDueGroups() {
        int finished = 0;
        Set<UUID> failed = new HashSet<>();
        List<UUID> batch;
        int finishedInBatch;
        do {
            batch = transactions.execute(status -> groups.findIdsDueForFinish(clock.instant(), BATCH_SIZE));
            finishedInBatch = 0;
            for (UUID groupId : batch) {
                if (!failed.contains(groupId) && finish(groupId, failed)) {
                    finishedInBatch++;
                }
            }
            finished += finishedInBatch;
        } while (batch.size() == BATCH_SIZE && finishedInBatch > 0);
        return finished;
    }

    private boolean finish(UUID groupId, Set<UUID> failed) {
        try {
            return Boolean.TRUE.equals(transactions.execute(status ->
                    groups.findByIdForUpdate(groupId).map(lock::finishIfDue).orElse(false)));
        } catch (RuntimeException e) {
            failed.add(groupId);
            log.warn("Falha ao finalizar o grupo {}", groupId, e);
            return false;
        }
    }
}
