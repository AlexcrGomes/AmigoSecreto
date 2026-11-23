package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.events.GroupEvents.GroupUpdatedEvent;
import br.com.amigosecreto.group.dto.ConfirmationRequest;
import br.com.amigosecreto.group.dto.GroupDetail;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final LocalDate DEC_19 = LocalDate.of(2026, 12, 19);

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private GroupService groupService;

    @Mock
    private ApplicationEventPublisher events;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EventSchedule schedule = new EventSchedule(clock, ZoneId.of("America/Sao_Paulo"));
    private final GroupDetail detail = Mockito.mock(GroupDetail.class);

    private GroupConfirmationService service;
    private SecretGroup group;
    private GroupMember bruno;

    @BeforeEach
    void setUp() {
        group = new SecretGroup("Natal", "K7PQM2XA", ANA, NOW);
        group.updateEventDetails(new EventDetails(null, DEC_19, LocalTime.of(19, 0), "Casa da Ana"), schedule);
        bruno = new GroupMember(group.getId(), BRUNO, NOW);
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.findById(new GroupMemberId(group.getId(), BRUNO))).thenReturn(Optional.of(bruno));
        when(members.countByIdGroupId(group.getId())).thenReturn(3L);
        when(groupService.detail(group, BRUNO)).thenReturn(detail);
        service = new GroupConfirmationService(
                new GroupLock(groups, members, clock, events), members, groupService, clock, events);
    }

    @Test
    void confirmsTheSessionUserForTheCurrentVersionUnderLock() {
        GroupDetail result = service.confirm(BRUNO, group.getId(), new ConfirmationRequest(1));

        verify(groups).findByIdForUpdate(group.getId());
        assertThat(bruno.isConfirmed()).isTrue();
        assertThat(bruno.getConfirmedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.OPEN, 3, false));
        assertThat(result).isSameAs(detail);
    }

    @Test
    void anOldVersionIsRejectedSoTheNewDetailsAreSeenFirst() {
        group.updateEventDetails(new EventDetails(null, DEC_19, LocalTime.of(19, 0), "Salão"), schedule);

        assertThatThrownBy(() -> service.confirm(BRUNO, group.getId(), new ConfirmationRequest(1)))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("details-changed");
                    assertThat(ex.getMessage()).isEqualTo("Os detalhes do evento mudaram. Confira e confirme de novo.");
                });
        assertThat(bruno.isConfirmed()).isFalse();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void confirmingAgainChangesNothing() {
        bruno.confirm(NOW.minusSeconds(60));

        GroupDetail result = service.confirm(BRUNO, group.getId(), new ConfirmationRequest(1));

        assertThat(bruno.getConfirmedAt()).isEqualTo(NOW.minusSeconds(60));
        verify(events, never()).publishEvent(any());
        assertThat(result).isSameAs(detail);
    }

    @Test
    void drawnGroupHasNothingToConfirm() {
        group.markDrawn(NOW);

        assertThatThrownBy(() -> service.confirm(BRUNO, group.getId(), new ConfirmationRequest(1)))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-already-drawn");
                    assertThat(ex.getMessage()).isEqualTo("O sorteio deste grupo já foi realizado.");
                });
        assertThat(bruno.isConfirmed()).isFalse();
    }

    @Test
    void finishedGroupHasNothingToConfirm() {
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);

        assertThatThrownBy(() -> service.confirm(BRUNO, group.getId(), new ConfirmationRequest(1)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("type", "group-finished");
        assertThat(bruno.isConfirmed()).isFalse();
    }

    @Test
    void nonMemberOrMissingGroupGetsNotFound() {
        UUID outsider = UUID.randomUUID();
        when(members.findById(new GroupMemberId(group.getId(), outsider))).thenReturn(Optional.empty());
        UUID missing = UUID.randomUUID();
        when(groups.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(outsider, group.getId(), new ConfirmationRequest(1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Grupo não encontrado.");
        assertThatThrownBy(() -> service.confirm(BRUNO, missing, new ConfirmationRequest(1)))
                .isInstanceOf(NotFoundException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void confirmingInADueGroupFinishesItAndIsRefused() {
        group.markDrawn(NOW);
        ReflectionTestUtils.setField(group, "finalizesAt", NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.confirm(BRUNO, group.getId(), new ConfirmationRequest(1)))
                .isInstanceOfSatisfying(GroupFinishedException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-finished");
                    assertThat(ex.getMessage()).isEqualTo("Este grupo foi finalizado.");
                });

        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        assertThat(bruno.isConfirmed()).isFalse();
        verify(events).publishEvent(new GroupFinishedEvent(group.getId(), 3));
    }
}
