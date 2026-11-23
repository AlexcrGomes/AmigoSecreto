package br.com.amigosecreto.exitrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.draw.DrawService;
import br.com.amigosecreto.events.GroupEvents.DrawUndoneEvent;
import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.events.GroupEvents.GroupUpdatedEvent;
import br.com.amigosecreto.events.GroupEvents.MembershipRevokedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.events.GroupEvents.RevocationReason;
import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import br.com.amigosecreto.group.EventDetails;
import br.com.amigosecreto.group.EventSchedule;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupService;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.SecretGroupRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Pedido de saída depois do sorteio (US4 da 002, FR-025 a FR-033, research R18). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExitRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant WEEK_AGO = NOW.minusSeconds(7 * 24 * 3600);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
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
    private GroupExitBanRepository exitBans;

    @Mock
    private AppUserRepository users;

    @Mock
    private DrawService drawService;

    @Mock
    private GroupService groupService;

    @Mock
    private ApplicationEventPublisher events;

    private final GroupDetail detail = mock(GroupDetail.class);
    private SecretGroup group;
    private ExitRequestService service;

    @BeforeEach
    void setUp() {
        group = new SecretGroup("Natal", "K7PQM2XA", ANA, WEEK_AGO);
        group.updateEventDetails(
                new EventDetails(null, LocalDate.of(2026, 12, 19), LocalTime.of(19, 0), "Casa da Ana"),
                new EventSchedule(Clock.fixed(WEEK_AGO, ZoneOffset.UTC), BRASILIA));
        group.markDrawn(WEEK_AGO);
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(any(GroupMemberId.class))).thenReturn(true);
        when(members.countByIdGroupId(group.getId())).thenReturn(4L);
        when(exitRequests.save(any(ExitRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        AppUser bruno = mock(AppUser.class);
        when(bruno.getName()).thenReturn("Bruno");
        when(users.findById(BRUNO)).thenReturn(Optional.of(bruno));
        when(groupService.detail(group, ANA)).thenReturn(detail);
        // O DrawService de verdade apaga os pares e reabre o grupo; aqui só reabre
        doAnswer(inv -> {
                    group.undoDraw(NOW);
                    return null;
                })
                .when(drawService)
                .discardDraw(group, NOW);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ExitRequestService(
                new GroupLock(groups, members, exitRequests, clock, events),
                members,
                exitRequests,
                exitBans,
                users,
                drawService,
                groupService,
                clock,
                events);
    }

    /** Pedido pendente de alguém, feito há uma hora. */
    private ExitRequest pendingOf(UUID userId) {
        ExitRequest request = new ExitRequest(group.getId(), userId, NOW.minusSeconds(3600));
        when(exitRequests.findById(request.getId())).thenReturn(Optional.of(request));
        when(exitRequests.findByGroupIdAndUserIdAndStatus(group.getId(), userId, ExitRequestStatus.PENDING))
                .thenReturn(Optional.of(request));
        return request;
    }

    private static void assertConflict(Runnable action, String type) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo(type));
    }

    // ---- pedir ----

    @Test
    void requestingCreatesAPendingRequestAndSignalsTheOrganizer() {
        ExitRequestResponse response = service.request(BRUNO, group.getId());

        ArgumentCaptor<ExitRequest> saved = ArgumentCaptor.forClass(ExitRequest.class);
        verify(exitRequests).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ExitRequestStatus.PENDING);
        assertThat(saved.getValue().getUserId()).isEqualTo(BRUNO);
        assertThat(saved.getValue().getRequestedAt()).isEqualTo(NOW);
        assertThat(response).isEqualTo(new ExitRequestResponse(saved.getValue().getId(), BRUNO, "Bruno", NOW));
        verify(groups).findByIdForUpdate(group.getId());
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.DRAWN, 4, false));
        // FR-027: o sorteio continua valendo
        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
        verify(drawService, never()).discardDraw(any(), any());
    }

    @Test
    void theOrganizerCannotAskToLeave() {
        assertThatThrownBy(() -> service.request(ANA, group.getId()))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("organizer-cannot-leave");
                    assertThat(ex.getMessage()).isEqualTo("O organizador não pode sair do grupo.");
                });
        verify(exitRequests, never()).save(any());
    }

    @Test
    void beforeTheDrawThePersonLeavesDirectly() {
        SecretGroup open = new SecretGroup("Aberto", "K7PQM2XA", ANA, WEEK_AGO);
        when(groups.findByIdForUpdate(open.getId())).thenReturn(Optional.of(open));

        assertConflict(() -> service.request(BRUNO, open.getId()), "group-not-drawn");
        verify(exitRequests, never()).save(any());
    }

    @Test
    void onlyOnePendingRequestPerPerson() {
        pendingOf(BRUNO);

        assertThatThrownBy(() -> service.request(BRUNO, group.getId()))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("exit-request-already-pending");
                    assertThat(ex.getMessage()).isEqualTo("Você já tem um pedido de saída aguardando o organizador.");
                });
        verify(exitRequests, never()).save(any());
    }

    @Test
    void afterARejectionThePersonCanAskAgain() {
        ExitRequest rejected = new ExitRequest(group.getId(), BRUNO, NOW.minusSeconds(7200));
        rejected.reject(NOW.minusSeconds(3600));
        // O recusado não conta como pendente
        when(exitRequests.findByGroupIdAndUserIdAndStatus(group.getId(), BRUNO, ExitRequestStatus.PENDING))
                .thenReturn(Optional.empty());

        service.request(BRUNO, group.getId());

        verify(exitRequests).save(any(ExitRequest.class));
    }

    @Test
    void nonMembersAndUnknownGroupsGetNotFound() {
        when(members.existsById(new GroupMemberId(group.getId(), CARLA))).thenReturn(false);
        UUID unknown = UUID.randomUUID();
        when(groups.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(CARLA, group.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.request(BRUNO, unknown)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(CARLA, group.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aFinishedGroupRefusesEveryAction() {
        ExitRequest request = pendingOf(BRUNO);
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);

        assertConflict(() -> service.request(BRUNO, group.getId()), "group-finished");
        assertConflict(() -> service.cancel(BRUNO, group.getId()), "group-finished");
        assertConflict(() -> service.reject(ANA, group.getId(), request.getId()), "group-finished");
        assertConflict(() -> service.approve(ANA, group.getId(), request.getId()), "group-finished");
        assertThat(request.isPending()).isTrue();
        verify(events, never()).publishEvent(any());
    }

    // ---- cancelar ----

    @Test
    void theRequesterCancelsWithoutANoticeForThemselves() {
        ExitRequest request = pendingOf(BRUNO);

        service.cancel(BRUNO, group.getId());

        assertThat(request.getStatus()).isEqualTo(ExitRequestStatus.CANCELLED);
        assertThat(request.getDecidedAt()).isEqualTo(NOW);
        assertThat(request.getDecisionSeenAt()).isEqualTo(NOW);
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.DRAWN, 4, false));
    }

    @Test
    void cancellingWithoutAPendingRequestIsNotFound() {
        assertThatThrownBy(() -> service.cancel(BRUNO, group.getId())).isInstanceOf(NotFoundException.class);
        verify(events, never()).publishEvent(any());
    }

    // ---- recusar ----

    @Test
    void theOrganizerRejectsNothingChangesAndTheRequesterIsNotified() {
        ExitRequest request = pendingOf(BRUNO);

        GroupDetail result = service.reject(ANA, group.getId(), request.getId());

        assertThat(result).isSameAs(detail);
        assertThat(request.getStatus()).isEqualTo(ExitRequestStatus.REJECTED);
        assertThat(request.getDecidedAt()).isEqualTo(NOW);
        assertThat(request.getDecisionSeenAt()).isNull();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
        verify(members, never()).deleteById(any());
        verify(drawService, never()).discardDraw(any(), any());
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.DRAWN, 4, false));
    }

    @Test
    void onlyTheOrganizerDecides() {
        ExitRequest request = pendingOf(BRUNO);

        assertThatThrownBy(() -> service.reject(BRUNO, group.getId(), request.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.approve(CARLA, group.getId(), request.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(request.isPending()).isTrue();
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        ExitRequest request = pendingOf(BRUNO);
        request.reject(NOW.minusSeconds(60));

        assertConflict(() -> service.reject(ANA, group.getId(), request.getId()), "exit-request-not-pending");
        assertConflict(() -> service.approve(ANA, group.getId(), request.getId()), "exit-request-not-pending");
        verify(drawService, never()).discardDraw(any(), any());
    }

    @Test
    void aRequestFromAnotherGroupIsNotFound() {
        ExitRequest elsewhere = new ExitRequest(UUID.randomUUID(), BRUNO, NOW);
        when(exitRequests.findById(elsewhere.getId())).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.approve(ANA, group.getId(), elsewhere.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.reject(ANA, group.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- aprovar ----

    @Test
    void approvingUndoesTheDrawInTheOrderOfResearchR18() {
        ExitRequest request = pendingOf(BRUNO);
        when(members.countByIdGroupId(group.getId())).thenReturn(3L);
        when(exitRequests.cancelAllPending(group.getId(), NOW)).thenAnswer(inv -> {
            // O pedido aprovado já mudou antes de cancelar os demais
            assertThat(request.getStatus()).isEqualTo(ExitRequestStatus.APPROVED);
            return 1;
        });
        InOrder order = inOrder(drawService, members, exitBans, exitRequests, events);

        GroupDetail result = service.approve(ANA, group.getId(), request.getId());

        order.verify(drawService).discardDraw(group, NOW);
        order.verify(members).deleteById(new GroupMemberId(group.getId(), BRUNO));
        order.verify(exitBans)
                .save(argThat(ban -> ban.getId().equals(new GroupExitBanId(group.getId(), BRUNO))
                        && ban.getCreatedAt().equals(NOW)));
        order.verify(exitRequests).cancelAllPending(group.getId(), NOW);
        order.verify(events)
                .publishEvent(new MembershipRevokedEvent(group.getId(), BRUNO, RevocationReason.EXIT_APPROVED));
        order.verify(events).publishEvent(new DrawUndoneEvent(group.getId(), 3));
        order.verify(events).publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 3));

        assertThat(result).isSameAs(detail);
        assertThat(request.getStatus()).isEqualTo(ExitRequestStatus.APPROVED);
        assertThat(request.getDecidedAt()).isEqualTo(NOW);
        assertThat(request.getDecisionSeenAt()).isNull();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
        assertThat(group.getDrawUndoneAt()).isEqualTo(NOW);
    }

    @Test
    void anyWriteOnADueGroupFinishesItAndCancelsThePendingRequests() {
        ExitRequest request = pendingOf(BRUNO);
        ReflectionTestUtils.setField(group, "finalizesAt", NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.approve(ANA, group.getId(), request.getId()))
                .isInstanceOf(GroupFinishedException.class);

        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        // FR-033: o cancelamento automático vem do GroupLock, na mesma transação
        verify(exitRequests).cancelAllPending(group.getId(), NOW);
        verify(events).publishEvent(new GroupFinishedEvent(group.getId(), 4));
        verify(drawService, never()).discardDraw(any(), any());
        verify(members, never()).deleteById(any());
    }
}
