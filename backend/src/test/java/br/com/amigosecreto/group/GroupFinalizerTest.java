package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Tarefa que finaliza os grupos sorteados cujo evento já aconteceu (US3 da 002, research R17). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupFinalizerTest {

    /** 20/12/2026, 00:00 em Brasília: fim do dia 19/12. */
    private static final Instant NOW = Instant.parse("2026-12-20T03:00:00Z");

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private PlatformTransactionManager transactionManager;

    private GroupFinalizer finalizer;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        finalizer =
                new GroupFinalizer(groups, new GroupLock(groups, members, clock, events), transactionManager, clock);
    }

    /** Grupo sorteado com o evento em 19/12/2026, sem horário: vence agora. */
    private SecretGroup dueGroup() {
        Instant monthAgo = NOW.minusSeconds(30L * 86_400);
        SecretGroup group = new SecretGroup("Natal", "K7PQM2XA", UUID.randomUUID(), monthAgo);
        group.updateEventDetails(
                new EventDetails(null, LocalDate.of(2026, 12, 19), null, "Casa da Ana"),
                new EventSchedule(Clock.fixed(monthAgo, ZoneOffset.UTC), BRASILIA));
        group.markDrawn(monthAgo);
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.countByIdGroupId(group.getId())).thenReturn(4L);
        return group;
    }

    private void givenDue(UUID... ids) {
        when(groups.findIdsDueForFinish(NOW, GroupFinalizer.BATCH_SIZE)).thenReturn(List.of(ids));
    }

    @Test
    void finishesEachDueGroupInItsOwnTransactionAndSignalsIt() {
        SecretGroup natal = dueGroup();
        SecretGroup firma = dueGroup();
        givenDue(natal.getId(), firma.getId());

        int finished = finalizer.finishDueGroups();

        assertThat(finished).isEqualTo(2);
        assertThat(natal.getStatus()).isEqualTo(GroupStatus.FINISHED);
        assertThat(natal.getFinishedAt()).isEqualTo(NOW);
        assertThat(firma.getStatus()).isEqualTo(GroupStatus.FINISHED);
        verify(groups).findByIdForUpdate(natal.getId());
        verify(groups).findByIdForUpdate(firma.getId());
        verify(events).publishEvent(new GroupFinishedEvent(natal.getId(), 4));
        verify(events).publishEvent(new GroupFinishedEvent(firma.getId(), 4));
        // Uma transação para buscar os vencidos e uma para cada grupo
        verify(transactionManager, times(3)).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void aFailingGroupDoesNotStopTheOthers() {
        SecretGroup natal = dueGroup();
        SecretGroup firma = dueGroup();
        UUID broken = UUID.randomUUID();
        when(groups.findByIdForUpdate(broken)).thenThrow(new IllegalStateException("falha no banco"));
        givenDue(natal.getId(), broken, firma.getId());

        int finished = finalizer.finishDueGroups();

        assertThat(finished).isEqualTo(2);
        assertThat(natal.getStatus()).isEqualTo(GroupStatus.FINISHED);
        assertThat(firma.getStatus()).isEqualTo(GroupStatus.FINISHED);
        verify(transactionManager).rollback(any());
        verify(events).publishEvent(new GroupFinishedEvent(natal.getId(), 4));
        verify(events).publishEvent(new GroupFinishedEvent(firma.getId(), 4));
    }

    @Test
    void groupThatIsNoLongerDueIsLeftAlone() {
        SecretGroup reopened = dueGroup();
        // Entre a busca e o lock, uma saída aprovada desfez o sorteio (US4): o grupo voltou a OPEN
        ReflectionTestUtils.setField(reopened, "status", GroupStatus.OPEN);
        givenDue(reopened.getId());

        assertThat(finalizer.finishDueGroups()).isZero();
        assertThat(reopened.getStatus()).isEqualTo(GroupStatus.OPEN);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void nothingDueDoesNothing() {
        givenDue();

        assertThat(finalizer.finishDueGroups()).isZero();
        verify(groups, never()).findByIdForUpdate(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void fullBatchesAreFollowedByTheNextOne() {
        List<UUID> first = new ArrayList<>();
        for (int i = 0; i < GroupFinalizer.BATCH_SIZE; i++) {
            first.add(dueGroup().getId());
        }
        SecretGroup last = dueGroup();
        when(groups.findIdsDueForFinish(NOW, GroupFinalizer.BATCH_SIZE))
                .thenReturn(first)
                .thenReturn(List.of(last.getId()));

        assertThat(finalizer.finishDueGroups()).isEqualTo(GroupFinalizer.BATCH_SIZE + 1);
        verify(groups, times(2)).findIdsDueForFinish(NOW, GroupFinalizer.BATCH_SIZE);
        assertThat(last.getStatus()).isEqualTo(GroupStatus.FINISHED);
    }

    @Test
    void aBatchWhereEverythingFailsIsNotRetriedInALoop() {
        List<UUID> broken = new ArrayList<>();
        for (int i = 0; i < GroupFinalizer.BATCH_SIZE; i++) {
            UUID id = UUID.randomUUID();
            when(groups.findByIdForUpdate(id)).thenThrow(new IllegalStateException("falha no banco"));
            broken.add(id);
        }
        when(groups.findIdsDueForFinish(NOW, GroupFinalizer.BATCH_SIZE)).thenReturn(broken);

        assertThat(finalizer.finishDueGroups()).isZero();
        verify(groups, times(1)).findIdsDueForFinish(NOW, GroupFinalizer.BATCH_SIZE);
    }
}
