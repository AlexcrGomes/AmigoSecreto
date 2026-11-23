package br.com.amigosecreto.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.common.RateLimitExceededException;
import br.com.amigosecreto.common.UnprocessableException;
import br.com.amigosecreto.draw.dto.DrawResult;
import br.com.amigosecreto.events.GroupEvents.GroupDrawnEvent;
import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.exitrequest.ExitRequestRepository;
import br.com.amigosecreto.group.EventDetails;
import br.com.amigosecreto.group.EventSchedule;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.SecretGroupRepository;
import br.com.amigosecreto.ratelimit.RateLimitPolicy;
import br.com.amigosecreto.ratelimit.RateLimiter;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrawServiceTest {

    /** 20/12/2026, 12:00 em Brasília. */
    private static final Instant NOW = Instant.parse("2026-12-20T15:00:00Z");

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private ExitRequestRepository exitRequests;

    @Mock
    private DrawAssignmentRepository assignments;

    @Mock
    private DerangementGenerator derangement;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private ApplicationEventPublisher events;

    private final AssignmentCipher cipher = new AssignmentCipher(new byte[32], new SecureRandom());
    private DrawService service;
    private SecretGroup group;

    @BeforeEach
    void setUp() {
        group = groupOn(LocalDate.of(2026, 12, 24), LocalTime.of(20, 0));
        when(members.existsById(any(GroupMemberId.class))).thenReturn(true);
        when(members.findUserIds(group.getId())).thenReturn(List.of(ANA, BRUNO, CARLA));
        when(members.findPendingNames(group.getId())).thenReturn(List.of());
        when(derangement.generate(anyList())).thenReturn(Map.of(ANA, BRUNO, BRUNO, CARLA, CARLA, ANA));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new DrawService(
                new GroupLock(groups, members, exitRequests, clock, events),
                members,
                assignments,
                derangement,
                cipher,
                rateLimiter,
                clock,
                new EventSchedule(clock, BRASILIA),
                events);
    }

    /** Grupo da Ana com o evento na data e hora informadas (gravadas uma semana antes de agora). */
    private SecretGroup groupOn(LocalDate date, LocalTime time) {
        Instant weekAgo = NOW.minusSeconds(7 * 24 * 3600);
        SecretGroup created = new SecretGroup("Natal", "K7PQM2XA", ANA, weekAgo);
        created.updateEventDetails(
                new EventDetails(null, date, time, "Casa da Ana"),
                new EventSchedule(Clock.fixed(weekAgo, ZoneOffset.UTC), BRASILIA));
        when(groups.findByIdForUpdate(created.getId())).thenReturn(Optional.of(created));
        return created;
    }

    private void assertBlocked(String type, String detail) {
        assertThatThrownBy(() -> service.draw(ANA, group.getId()))
                .isInstanceOfSatisfying(UnprocessableException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo(type);
                    assertThat(ex.getMessage()).isEqualTo(detail);
                });
        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
        verify(derangement, never()).generate(anyList());
        verify(assignments, never()).saveAll(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void drawEncryptsEveryPairMarksDrawnAndPublishesWithoutPairs() {
        DrawResult result = service.draw(ANA, group.getId());

        verify(groups).findByIdForUpdate(group.getId());
        verify(rateLimiter).consume(RateLimitPolicy.DRAW, group.getId().toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DrawAssignment>> saved = ArgumentCaptor.forClass(List.class);
        verify(assignments).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(3);
        for (DrawAssignment row : saved.getValue()) {
            UUID giver = row.getId().giverId();
            assertThat(row.getId().groupId()).isEqualTo(group.getId());
            assertThat(row.getCreatedAt()).isEqualTo(NOW);
            // cifrado com o AAD (grupo, quem tirou): decifra só com essa combinação
            UUID receiver = cipher.decrypt(row.getReceiverEnc(), group.getId(), giver);
            assertThat(receiver)
                    .isEqualTo(Map.of(ANA, BRUNO, BRUNO, CARLA, CARLA, ANA).get(giver));
            assertThat(row.toString()).doesNotContain(receiver.toString());
        }

        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
        assertThat(group.getDrawnAt()).isEqualTo(NOW);
        verify(events).publishEvent(new GroupDrawnEvent(group.getId(), 3));
        assertThat(result).isEqualTo(new DrawResult(group.getId(), GroupStatus.DRAWN, NOW));
    }

    @Test
    void nonMemberGetsNotFound() {
        when(members.existsById(new GroupMemberId(group.getId(), BRUNO))).thenReturn(false);

        assertThatThrownBy(() -> service.draw(BRUNO, group.getId())).isInstanceOf(NotFoundException.class);
        verify(assignments, never()).saveAll(any());
    }

    @Test
    void unknownGroupGetsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(groups.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.draw(ANA, unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void memberWhoIsNotTheOrganizerIsForbidden() {
        assertThatThrownBy(() -> service.draw(BRUNO, group.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Só o organizador pode realizar o sorteio.");
        verify(assignments, never()).saveAll(any());
    }

    @Test
    void fewerThanThreeIsUnprocessable() {
        when(members.findUserIds(group.getId())).thenReturn(List.of(ANA, BRUNO));

        assertBlocked("not-enough-participants", "São necessários pelo menos 3 participantes.");
    }

    @Test
    void oldGroupWithoutEventDetailsCannotBeDrawn() {
        group = new SecretGroup("Antigo", "K7PQM2XA", ANA, NOW.minusSeconds(3600));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.findUserIds(group.getId())).thenReturn(List.of(ANA, BRUNO, CARLA));
        when(members.findPendingNames(group.getId())).thenReturn(List.of());

        assertBlocked("event-details-required", "Informe a data e o local do evento para sortear.");
    }

    @Test
    void eventThatAlreadyHappenedCannotBeDrawn() {
        // hoje às 11:59 de Brasília; agora são 12:00
        group = groupOn(LocalDate.of(2026, 12, 20), LocalTime.of(11, 59));
        when(members.findUserIds(group.getId())).thenReturn(List.of(ANA, BRUNO, CARLA));
        when(members.findPendingNames(group.getId())).thenReturn(List.of());

        assertBlocked("event-date-passed", "A data do evento já passou. Atualize a data para sortear.");
    }

    @Test
    void eventLaterTodayCanStillBeDrawn() {
        group = groupOn(LocalDate.of(2026, 12, 20), LocalTime.of(12, 1));
        when(members.findUserIds(group.getId())).thenReturn(List.of(ANA, BRUNO, CARLA));
        when(members.findPendingNames(group.getId())).thenReturn(List.of());

        service.draw(ANA, group.getId());

        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
    }

    @Test
    void pendingParticipantsBlockTheDrawAndAreNamed() {
        when(members.findPendingNames(group.getId())).thenReturn(List.of("Bruno", "Carla"));

        assertBlocked("participants-pending", "Faltam 2 confirmar: Bruno, Carla.");
        assertThatThrownBy(() -> service.draw(ANA, group.getId()))
                .isInstanceOfSatisfying(
                        UnprocessableException.class,
                        ex -> assertThat(ex.getProperties()).containsEntry("pending", List.of("Bruno", "Carla")));
    }

    @Test
    void onePendingParticipantUsesTheSingular() {
        when(members.findPendingNames(group.getId())).thenReturn(List.of("Carla"));

        assertBlocked("participants-pending", "Falta 1 confirmar: Carla.");
    }

    @Test
    void reasonsOtherThanPendingCarryNoExtraProperties() {
        when(members.findUserIds(group.getId())).thenReturn(List.of(ANA, BRUNO));

        assertThatThrownBy(() -> service.draw(ANA, group.getId()))
                .isInstanceOfSatisfying(
                        UnprocessableException.class,
                        ex -> assertThat(ex.getProperties()).isEmpty());
    }

    @Test
    void alreadyDrawnIsConflict() {
        group.markDrawn(NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.draw(ANA, group.getId()))
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo("group-already-drawn"));
        verify(derangement, never()).generate(anyList());
        verify(assignments, never()).saveAll(any());
    }

    @Test
    void rateLimitedBeforeTouchingTheGroup() {
        doThrow(new RateLimitExceededException(60))
                .when(rateLimiter)
                .consume(RateLimitPolicy.DRAW, group.getId().toString());

        assertThatThrownBy(() -> service.draw(ANA, group.getId())).isInstanceOf(RateLimitExceededException.class);
        verify(groups, never()).findByIdForUpdate(any());
    }

    @Test
    void generatorFailurePropagatesAndPublishesNothing() {
        when(derangement.generate(anyList())).thenThrow(new IllegalStateException("sem derangement"));

        assertThatThrownBy(() -> service.draw(ANA, group.getId())).isInstanceOf(IllegalStateException.class);
        verify(assignments, never()).saveAll(any());
        verify(events, never()).publishEvent(any());
        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
    }

    @Test
    void saveFailurePropagatesAndPublishesNothing() {
        when(assignments.saveAll(anyList())).thenThrow(new DataAccessResourceFailureException("banco fora"));

        assertThatThrownBy(() -> service.draw(ANA, group.getId()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(events, never()).publishEvent(any());
    }

    // ---- grupo finalizado (US3) ----

    @Test
    void finishedGroupCannotBeDrawn() {
        group.markDrawn(NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);

        assertThatThrownBy(() -> service.draw(ANA, group.getId()))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-finished");
                    assertThat(ex.getMessage()).isEqualTo("Este grupo foi finalizado.");
                });
        verify(assignments, never()).saveAll(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void drawingADueGroupFinishesItInstead() {
        group.markDrawn(NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(group, "finalizesAt", NOW.minusSeconds(60));
        when(members.countByIdGroupId(group.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.draw(ANA, group.getId())).isInstanceOf(GroupFinishedException.class);

        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        verify(events).publishEvent(new GroupFinishedEvent(group.getId(), 3));
        verify(assignments, never()).saveAll(any());
    }

    // ---- sorteio desfeito (US4) ----

    @Test
    void discardDrawDeletesEveryPairWithoutReadingAnyAndReopensTheGroup() {
        group.markDrawn(NOW.minusSeconds(3600));

        service.discardDraw(group, NOW);

        verify(assignments).deleteByGroupId(group.getId());
        verify(assignments, never()).findByIdGroupIdAndIdGiverId(any(), any());
        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
        assertThat(group.getDrawnAt()).isNull();
        assertThat(group.getDrawUndoneAt()).isEqualTo(NOW);
        verify(events, never()).publishEvent(any());
    }
}
