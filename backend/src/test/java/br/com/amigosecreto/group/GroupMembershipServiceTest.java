package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.events.GroupEvents.MembershipRevokedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.events.GroupEvents.RevocationReason;
import br.com.amigosecreto.exitrequest.ExitRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupMembershipServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final UUID ANA = UUID.randomUUID(); // organizadora
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private ExitRequestRepository exitRequests;

    @Mock
    private DependentAccounts dependentAccounts;

    @Mock
    private ApplicationEventPublisher events;

    private GroupMembershipService service;
    private SecretGroup group;

    @BeforeEach
    void setUp() {
        group = new SecretGroup("Natal", "K7PQM2XA", ANA, NOW);
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(any(GroupMemberId.class))).thenReturn(true);
        when(members.countByIdGroupId(group.getId())).thenReturn(2L);
        service = new GroupMembershipService(
                members,
                new GroupLock(groups, members, exitRequests, Clock.fixed(NOW, ZoneOffset.UTC), events),
                dependentAccounts,
                events);
    }

    private GroupMemberId member(UUID userId) {
        return new GroupMemberId(group.getId(), userId);
    }

    // ---- leave ----

    @Test
    void participantLeavesUnderLockAndEveryoneIsSignaled() {
        service.leave(BRUNO, group.getId());

        verify(groups).findByIdForUpdate(group.getId());
        verify(members).deleteById(member(BRUNO));
        verify(events).publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 2));
        verify(events).publishEvent(new MembershipRevokedEvent(group.getId(), BRUNO, RevocationReason.LEFT));
    }

    @Test
    void organizerCannotLeave() {
        assertThatThrownBy(() -> service.leave(ANA, group.getId()))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("organizer-cannot-leave");
                    assertThat(ex.getMessage()).isEqualTo("O organizador não pode sair do grupo.");
                });
        verify(members, never()).deleteById(any());
    }

    @Test
    void leavingADrawnGroupIsConflict() {
        group.markDrawn(NOW);

        assertThatThrownBy(() -> service.leave(BRUNO, group.getId()))
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo("group-already-drawn"));
        verify(members, never()).deleteById(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void nonMemberLeavingGetsNotFound() {
        when(members.existsById(member(CARLA))).thenReturn(false);

        assertThatThrownBy(() -> service.leave(CARLA, group.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unknownGroupGetsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(groups.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(BRUNO, unknown)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.remove(ANA, unknown, BRUNO)).isInstanceOf(NotFoundException.class);
    }

    // ---- remove ----

    @Test
    void organizerRemovesAParticipantWhoIsNotified() {
        service.remove(ANA, group.getId(), CARLA);

        verify(groups).findByIdForUpdate(group.getId());
        verify(members).deleteById(member(CARLA));
        // A revogação vem antes: quem saiu é desconectado antes do sinal aos demais
        var order = org.mockito.Mockito.inOrder(events);
        order.verify(events).publishEvent(new MembershipRevokedEvent(group.getId(), CARLA, RevocationReason.REMOVED));
        order.verify(events).publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 2));
    }

    @Test
    void nonOrganizerCannotRemove() {
        assertThatThrownBy(() -> service.remove(BRUNO, group.getId(), CARLA))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Só o organizador pode remover participantes.");
        verify(members, never()).deleteById(any());
    }

    @Test
    void outsiderTryingToRemoveGetsNotFound() {
        UUID stranger = UUID.randomUUID();
        when(members.existsById(member(stranger))).thenReturn(false);

        assertThatThrownBy(() -> service.remove(stranger, group.getId(), CARLA)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void organizerCannotBeRemoved() {
        assertThatThrownBy(() -> service.remove(ANA, group.getId(), ANA))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("organizer-cannot-be-removed");
                    assertThat(ex.getMessage()).isEqualTo("O organizador não pode ser removido.");
                });
    }

    @Test
    void removingSomeoneWhoIsNotAMemberIsNotFound() {
        when(members.existsById(member(CARLA))).thenReturn(false);

        assertThatThrownBy(() -> service.remove(ANA, group.getId(), CARLA))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Participante não encontrado.");
    }

    @Test
    void removingFromADrawnGroupIsConflict() {
        group.markDrawn(NOW);

        assertThatThrownBy(() -> service.remove(ANA, group.getId(), CARLA))
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo("group-already-drawn"));
        verify(members, never()).deleteById(any());
    }

    // ---- grupo finalizado (US3) ----

    @Test
    void leavingOrRemovingInAFinishedGroupIsRefused() {
        group.markDrawn(NOW);
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);

        assertThatThrownBy(() -> service.leave(BRUNO, group.getId()))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-finished");
                    assertThat(ex.getMessage()).isEqualTo("Este grupo foi finalizado.");
                });
        assertThatThrownBy(() -> service.remove(ANA, group.getId(), BRUNO))
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo("group-finished"));
        verify(members, never()).deleteById(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void leavingADueGroupFinishesItAndIsRefused() {
        group.markDrawn(NOW);
        ReflectionTestUtils.setField(group, "finalizesAt", NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.leave(BRUNO, group.getId())).isInstanceOf(GroupFinishedException.class);

        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        verify(events).publishEvent(new GroupFinishedEvent(group.getId(), 2));
        verify(members, never()).deleteById(any());
    }

    // ---- feature 004: dependentes saem junto com o responsável (FR-016) ----

    private GroupMember dependentOf(UUID guardian, UUID dependentId) {
        return GroupMember.dependentOf(new GroupMember(group.getId(), guardian, NOW), dependentId, NOW);
    }

    @Test
    void aGuardianWhoLeavesTakesTheDependentsAlong() {
        GroupMember lucas = dependentOf(BRUNO, UUID.randomUUID());
        when(members.findDependents(group.getId(), BRUNO)).thenReturn(List.of(lucas));

        service.leave(BRUNO, group.getId());

        verify(members).deleteAll(List.of(lucas));
        verify(members).deleteById(member(BRUNO));
        verify(dependentAccounts).deleteUnreferenced(List.of(lucas.getId().userId()));
        verify(events).publishEvent(new MembershipRevokedEvent(group.getId(), BRUNO, RevocationReason.LEFT));
        verify(events, times(1)).publishEvent(any(ParticipantsChangedEvent.class));
    }

    @Test
    void theOrganizerRemovesADependentWithoutRevokingAnyConnection() {
        UUID lucasId = UUID.randomUUID();
        when(members.findById(member(lucasId))).thenReturn(Optional.of(dependentOf(BRUNO, lucasId)));

        service.remove(ANA, group.getId(), lucasId);

        verify(members).deleteById(member(lucasId));
        verify(dependentAccounts).deleteUnreferenced(List.of(lucasId));
        verify(events, never()).publishEvent(any(MembershipRevokedEvent.class));
        verify(events).publishEvent(any(ParticipantsChangedEvent.class));
    }

    @Test
    void removingAGuardianRemovesTheDependentsToo() {
        GroupMember lucas = dependentOf(CARLA, UUID.randomUUID());
        GroupMember bia = dependentOf(CARLA, UUID.randomUUID());
        when(members.findDependents(group.getId(), CARLA)).thenReturn(List.of(lucas, bia));

        service.remove(ANA, group.getId(), CARLA);

        verify(members).deleteAll(List.of(lucas, bia));
        verify(members).deleteById(member(CARLA));
        verify(dependentAccounts)
                .deleteUnreferenced(List.of(lucas.getId().userId(), bia.getId().userId()));
        verify(events).publishEvent(new MembershipRevokedEvent(group.getId(), CARLA, RevocationReason.REMOVED));
    }
}
