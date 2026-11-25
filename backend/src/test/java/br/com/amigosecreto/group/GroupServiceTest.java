package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.common.RateLimitExceededException;
import br.com.amigosecreto.common.UnprocessableException;
import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.events.GroupEvents.GroupUpdatedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.exitrequest.ExitRequest;
import br.com.amigosecreto.exitrequest.ExitRequestRepository;
import br.com.amigosecreto.exitrequest.ExitRequestStatus;
import br.com.amigosecreto.exitrequest.GroupExitBanId;
import br.com.amigosecreto.exitrequest.GroupExitBanRepository;
import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import br.com.amigosecreto.group.dto.CreateGroupRequest;
import br.com.amigosecreto.group.dto.EventDetailsRequest;
import br.com.amigosecreto.group.dto.EventDetailsResponse;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.GroupSummary;
import br.com.amigosecreto.group.dto.JoinGroupRequest;
import br.com.amigosecreto.ratelimit.RateLimitPolicy;
import br.com.amigosecreto.ratelimit.RateLimiter;
import br.com.amigosecreto.wishlist.WishItem;
import br.com.amigosecreto.wishlist.WishItemRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final LocalDate DEC_19 = LocalDate.of(2026, 12, 19);

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private ExitRequestRepository exitRequests;

    @Mock
    private GroupExitBanRepository exitBans;

    @Mock
    private WishItemRepository wishItems;

    @Mock
    private GroupCodeGenerator codes;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private ApplicationEventPublisher events;

    private GroupService service;

    @BeforeEach
    void setUp() {
        when(codes.generate()).thenReturn("K7PQM2XA");
        when(groups.save(any(SecretGroup.class))).thenAnswer(inv -> inv.getArgument(0));
        when(members.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new GroupService(
                groups,
                members,
                codes,
                rateLimiter,
                clock,
                new EventSchedule(clock, ZoneId.of("America/Sao_Paulo")),
                new GroupLock(groups, members, exitRequests, clock, events),
                exitBans,
                exitRequests,
                wishItems,
                events);
    }

    /** Pedido de criação com detalhes válidos: 19/12/2026 às 19:00 (hoje é 18/11/2026 em Brasília). */
    private static CreateGroupRequest createRequest(String name) {
        return new CreateGroupRequest(name, null, DEC_19, "19:00", "Casa da Ana");
    }

    /** Grupo aberto com o evento em 19/12/2026 às 19:00. */
    private SecretGroup openGroup(UUID organizer) {
        SecretGroup group = new SecretGroup("Natal 2026", "K7PQM2XA", organizer, NOW);
        group.updateEventDetails(new EventDetails(null, DEC_19, LocalTime.of(19, 0), "Casa da Ana"), schedule());
        return group;
    }

    /** Participantes todos confirmados. */
    private void givenParticipants(SecretGroup group, UUID... userIds) {
        givenParticipants(group, List.of(), userIds);
    }

    /** Participantes confirmados, menos os de {@code pending}. */
    private void givenParticipants(SecretGroup group, List<UUID> pending, UUID... userIds) {
        List<ParticipantView> views = new ArrayList<>();
        for (UUID id : userIds) {
            views.add(new ParticipantView(
                    id, "Pessoa " + id.toString().substring(0, 4), pending.contains(id) ? null : NOW));
        }
        when(members.findParticipants(group.getId())).thenReturn(views);
        when(members.countByIdGroupId(group.getId())).thenReturn((long) userIds.length);
    }

    // ---- create ----

    @Test
    void createSavesAnOpenGroupWithTheCreatorAsMember() {
        when(members.findParticipants(any())).thenReturn(List.of(new ParticipantView(ANA, "Ana", NOW)));

        GroupDetail detail = service.create(ANA, createRequest("Natal 2026"));

        ArgumentCaptor<SecretGroup> group = ArgumentCaptor.forClass(SecretGroup.class);
        verify(groups).save(group.capture());
        assertThat(group.getValue().getStatus()).isEqualTo(GroupStatus.OPEN);
        assertThat(group.getValue().getOrganizerId()).isEqualTo(ANA);
        assertThat(group.getValue().getCreatedAt()).isEqualTo(NOW);

        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(members).save(member.capture());
        assertThat(member.getValue().getId())
                .isEqualTo(new GroupMemberId(group.getValue().getId(), ANA));
        assertThat(member.getValue().getJoinedAt()).isEqualTo(NOW);

        assertThat(detail.code()).isEqualTo("K7PQ-M2XA");
        assertThat(detail.organizer()).isTrue();
        assertThat(detail.maxParticipants()).isEqualTo(100);
        verify(events)
                .publishEvent(new ParticipantsChangedEvent(group.getValue().getId(), GroupStatus.OPEN, 1));
    }

    @Test
    void codeCollisionGeneratesAnotherUpToFiveAttempts() {
        when(codes.generate()).thenReturn("AAAAAAAA", "BBBBBBBB", "CCCCCCCC");
        when(groups.existsByCode("AAAAAAAA")).thenReturn(true);
        when(groups.existsByCode("BBBBBBBB")).thenReturn(true);
        when(members.findParticipants(any())).thenReturn(List.of(new ParticipantView(ANA, "Ana", NOW)));

        GroupDetail detail = service.create(ANA, createRequest("Natal"));

        assertThat(detail.code()).isEqualTo("CCCC-CCCC");
        verify(codes, times(3)).generate();
    }

    @Test
    void givesUpAfterFiveCollisions() {
        when(groups.existsByCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(ANA, createRequest("Natal"))).isInstanceOf(IllegalStateException.class);
        verify(codes, times(5)).generate();
        verify(groups, never()).save(any());
    }

    @Test
    void createSavesTheEventDetailsAndTheOrganizerAlreadyConfirmed() {
        when(members.findParticipants(any())).thenReturn(List.of(new ParticipantView(ANA, "Ana", NOW)));

        GroupDetail detail = service.create(
                ANA, new CreateGroupRequest("Natal", "Traga um prato\nE um sorriso", DEC_19, null, "Casa da Ana"));

        ArgumentCaptor<SecretGroup> group = ArgumentCaptor.forClass(SecretGroup.class);
        verify(groups).save(group.capture());
        assertThat(group.getValue().getLocation()).isEqualTo("Casa da Ana");
        assertThat(group.getValue().getEventStartsAt()).isEqualTo(Instant.parse("2026-12-19T03:00:00Z"));
        assertThat(group.getValue().getFinalizesAt()).isEqualTo(Instant.parse("2026-12-20T03:00:00Z"));

        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(members).save(member.capture());
        assertThat(member.getValue().isConfirmed()).isTrue();

        assertThat(detail.eventDetails())
                .isEqualTo(new EventDetailsResponse("Traga um prato\nE um sorriso", DEC_19, null, "Casa da Ana"));
        assertThat(detail.eventDetailsMissing()).isFalse();
        assertThat(detail.detailsVersion()).isEqualTo(1);
    }

    @Test
    void createRejectsAPastDateOrATimeThatAlreadyPassedToday() {
        LocalDate today = LocalDate.of(2026, 11, 18); // 09:00 em Brasília

        assertThatThrownBy(() ->
                        service.create(ANA, new CreateGroupRequest("Natal", null, today.minusDays(1), null, "Casa")))
                .isInstanceOf(UnprocessableException.class)
                .hasFieldOrPropertyWithValue("type", "event-date-in-past");
        assertThatThrownBy(() -> service.create(ANA, new CreateGroupRequest("Natal", null, today, "08:00", "Casa")))
                .isInstanceOf(UnprocessableException.class)
                .hasFieldOrPropertyWithValue("type", "event-time-in-past");
        verify(groups, never()).save(any());
        verify(members, never()).save(any());
    }

    // ---- updateEventDetails ----

    private SecretGroup lockedGroupWithDetails(UUID organizer, UUID... others) {
        SecretGroup group = openGroup(organizer);
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(new GroupMemberId(group.getId(), organizer))).thenReturn(true);
        for (UUID other : others) {
            when(members.existsById(new GroupMemberId(group.getId(), other))).thenReturn(true);
        }
        UUID[] everyone = new UUID[others.length + 1];
        everyone[0] = organizer;
        System.arraycopy(others, 0, everyone, 1, others.length);
        givenParticipants(group, everyone);
        return group;
    }

    private static EventSchedule schedule() {
        return new EventSchedule(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void organizerUpdatesTheEventDetailsUnderLockAndEveryoneIsNotified() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);

        GroupDetail detail = service.updateEventDetails(
                ANA, group.getId(), new EventDetailsRequest("Amigo da onça", DEC_19, "20:00", "Salão"));

        verify(groups).findByIdForUpdate(group.getId());
        assertThat(detail.eventDetails())
                .isEqualTo(new EventDetailsResponse("Amigo da onça", DEC_19, "20:00", "Salão"));
        assertThat(detail.detailsVersion()).isEqualTo(2);
        verify(members).resetConfirmations(group.getId(), ANA);
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.OPEN, 2, true));
    }

    @Test
    void changingOnlyTheDescriptionKeepsTheConfirmations() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);

        service.updateEventDetails(
                ANA, group.getId(), new EventDetailsRequest("Amigo da onça", DEC_19, "19:00", "Casa da Ana"));

        verify(members, never()).resetConfirmations(any(), any());
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.OPEN, 2, false));
    }

    @Test
    void changingTheDetailsAfterTheDrawNeverResetsTheConfirmations() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);
        group.markDrawn(NOW);

        service.updateEventDetails(ANA, group.getId(), new EventDetailsRequest(null, DEC_19, "20:00", "Salão"));

        verify(members, never()).resetConfirmations(any(), any());
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.DRAWN, 2, false));
    }

    @Test
    void onlyTheOrganizerUpdatesTheEventDetails() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);

        assertThatThrownBy(() -> service.updateEventDetails(
                        BRUNO, group.getId(), new EventDetailsRequest(null, DEC_19, null, "Salão")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Só o organizador pode alterar os detalhes do evento.");
        assertThat(group.getLocation()).isEqualTo("Casa da Ana");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void nonMemberOrMissingGroupGetsNotFound() {
        SecretGroup group = lockedGroupWithDetails(ANA);
        UUID missing = UUID.randomUUID();
        EventDetailsRequest request = new EventDetailsRequest(null, DEC_19, null, "Salão");

        assertThatThrownBy(() -> service.updateEventDetails(BRUNO, group.getId(), request))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateEventDetails(ANA, missing, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void finishedGroupDetailsCannotChange() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);

        assertThatThrownBy(() -> service.updateEventDetails(
                        ANA, group.getId(), new EventDetailsRequest(null, DEC_19, null, "Salão")))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("type", "group-finished")
                .hasMessage("Este grupo foi finalizado.");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void oldGroupWithoutDetailsIsFlaggedForTheOrganizer() {
        SecretGroup old = new SecretGroup("Antigo", "K7PQM2XA", ANA, NOW);
        when(groups.findById(old.getId())).thenReturn(Optional.of(old));
        when(members.existsById(new GroupMemberId(old.getId(), ANA))).thenReturn(true);
        givenParticipants(old, ANA);

        GroupDetail detail = service.getDetail(ANA, old.getId());

        assertThat(detail.eventDetails()).isNull();
        assertThat(detail.eventDetailsMissing()).isTrue();
        assertThat(detail.drawBlockedReason()).isEqualTo("event-details-required");
        assertThat(detail.canDraw()).isFalse();
    }

    // ---- join ----

    @Test
    void joinWithValidCodeAddsTheMemberUnderLock() {
        SecretGroup group = openGroup(ANA);
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        givenParticipants(group, ANA, UUID.randomUUID());

        GroupDetail detail = service.join(BRUNO, new JoinGroupRequest(" k7pq-m2xa "));

        verify(groups).findByIdForUpdate(group.getId());
        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(members).save(member.capture());
        assertThat(member.getValue().getId()).isEqualTo(new GroupMemberId(group.getId(), BRUNO));
        assertThat(member.getValue().isConfirmed()).isFalse();
        assertThat(detail.organizer()).isFalse();
        verify(events).publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 3));
    }

    @Test
    void joinIsIdempotentForExistingMembers() {
        SecretGroup group = openGroup(ANA);
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(new GroupMemberId(group.getId(), BRUNO))).thenReturn(true);
        givenParticipants(group, ANA, BRUNO);

        GroupDetail detail = service.join(BRUNO, new JoinGroupRequest("K7PQM2XA"));

        assertThat(detail.participantCount()).isEqualTo(2);
        verify(members, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void existingMemberOfADrawnGroupStillGetsTheGroup() {
        SecretGroup group = openGroup(ANA);
        group.markDrawn(NOW);
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(new GroupMemberId(group.getId(), BRUNO))).thenReturn(true);
        givenParticipants(group, ANA, BRUNO, UUID.randomUUID());

        assertThat(service.join(BRUNO, new JoinGroupRequest("K7PQM2XA")).status())
                .isEqualTo(GroupStatus.DRAWN);
    }

    @Test
    void unknownCodeIsNotFoundAndCountsAsFailedAttempt() {
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("K7PQ-M2XA")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Código inválido.");
        verify(rateLimiter).consume(RateLimitPolicy.JOIN_INVALID_CODE, BRUNO.toString());
    }

    @Test
    void malformedCodeIsNotFoundWithoutQueryingTheDatabase() {
        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("abc")))
                .isInstanceOf(NotFoundException.class);
        verify(groups, never()).findIdByCode(anyString());
        verify(rateLimiter).consume(RateLimitPolicy.JOIN_INVALID_CODE, BRUNO.toString());
    }

    @Test
    void blockedUserIsRejectedBeforeLookingUpTheCode() {
        doThrow(new RateLimitExceededException(600))
                .when(rateLimiter)
                .ensureNotExhausted(RateLimitPolicy.JOIN_INVALID_CODE, BRUNO.toString());

        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("K7PQM2XA")))
                .isInstanceOf(RateLimitExceededException.class);
        verify(groups, never()).findIdByCode(anyString());
    }

    @Test
    void joiningADrawnGroupIsConflict() {
        SecretGroup group = openGroup(ANA);
        group.markDrawn(NOW);
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("K7PQM2XA")))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-already-drawn");
                    assertThat(ex.getMessage()).isEqualTo("O sorteio deste grupo já foi realizado.");
                });
        verify(members, never()).save(any());
    }

    @Test
    void joiningAFullGroupIsConflict() {
        SecretGroup group = openGroup(ANA);
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.countByIdGroupId(group.getId())).thenReturn(100L);

        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("K7PQM2XA")))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-full");
                    assertThat(ex.getMessage()).isEqualTo("Este grupo está cheio.");
                });
        verify(members, never()).save(any());
    }

    // ---- getDetail ----

    @Test
    void nonMemberGetsNotFound() {
        SecretGroup group = openGroup(ANA);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.getDetail(BRUNO, group.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Grupo não encontrado.");
    }

    @Test
    void unknownGroupGetsTheSameNotFound() {
        UUID id = UUID.randomUUID();
        when(groups.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(BRUNO, id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Grupo não encontrado.");
    }

    @Test
    void detailListsParticipantsInOrderWithTheOrganizerFlag() {
        SecretGroup group = openGroup(ANA);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(new GroupMemberId(group.getId(), BRUNO))).thenReturn(true);
        when(members.findParticipants(group.getId()))
                .thenReturn(List.of(new ParticipantView(ANA, "Ana", NOW), new ParticipantView(BRUNO, "Bruno", null)));

        GroupDetail detail = service.getDetail(BRUNO, group.getId());

        assertThat(detail.participants())
                .extracting("name", "organizer")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ana", true),
                        org.assertj.core.groups.Tuple.tuple("Bruno", false));
        assertThat(detail.participantCount()).isEqualTo(2);
        assertThat(detail.drawnAt()).isNull();
    }

    @Test
    void canDrawOnlyForTheOrganizerWithAnOpenGroupAndAtLeastThreeMembers() {
        SecretGroup group = openGroup(ANA);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(any())).thenReturn(true);

        givenParticipants(group, ANA, BRUNO);
        assertThat(service.getDetail(ANA, group.getId()).canDraw()).isFalse();

        givenParticipants(group, ANA, BRUNO, UUID.randomUUID());
        assertThat(service.getDetail(ANA, group.getId()).canDraw()).isTrue();
        assertThat(service.getDetail(BRUNO, group.getId()).canDraw()).isFalse();

        group.markDrawn(NOW);
        assertThat(service.getDetail(ANA, group.getId()).canDraw()).isFalse();
        assertThat(service.getDetail(ANA, group.getId()).drawBlockedReason()).isNull();
    }

    @Test
    void detailShowsWhoConfirmedAndBlocksTheDrawWhileSomeoneIsPending() {
        SecretGroup group = openGroup(ANA);
        UUID carla = UUID.randomUUID();
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(any())).thenReturn(true);
        givenParticipants(group, List.of(BRUNO), ANA, BRUNO, carla);

        GroupDetail forAna = service.getDetail(ANA, group.getId());
        GroupDetail forBruno = service.getDetail(BRUNO, group.getId());

        assertThat(forAna.participants()).extracting("confirmed").containsExactly(true, false, true);
        assertThat(forAna.confirmedCount()).isEqualTo(2);
        assertThat(forAna.myConfirmation()).isEqualTo(ConfirmationStatus.CONFIRMED);
        assertThat(forBruno.myConfirmation()).isEqualTo(ConfirmationStatus.PENDING);
        assertThat(forAna.drawBlockedReason()).isEqualTo("participants-pending");
        assertThat(forAna.canDraw()).isFalse();

        givenParticipants(group, ANA, BRUNO, carla);
        GroupDetail allConfirmed = service.getDetail(ANA, group.getId());
        assertThat(allConfirmed.confirmedCount()).isEqualTo(3);
        assertThat(allConfirmed.drawBlockedReason()).isNull();
        assertThat(allConfirmed.canDraw()).isTrue();
    }

    @Test
    void requireMemberRejectsNonMembersWithNotFound() {
        UUID groupId = UUID.randomUUID();
        when(members.existsById(new GroupMemberId(groupId, BRUNO))).thenReturn(false);

        assertThatThrownBy(() -> service.requireMember(BRUNO, groupId)).isInstanceOf(NotFoundException.class);
    }

    // ---- listMine ----

    @Test
    void listMineBringsCountAndOrganizerFlag() {
        UUID g1 = UUID.randomUUID();
        UUID g2 = UUID.randomUUID();
        UUID g3 = UUID.randomUUID();
        when(members.findSummaries(BRUNO))
                .thenReturn(List.of(
                        new GroupSummaryView(
                                g1, "Natal", GroupStatus.OPEN, BRUNO, 4L, Instant.parse("2026-12-19T22:00:00Z"), true),
                        new GroupSummaryView(
                                g2, "Firma", GroupStatus.DRAWN, ANA, 12L, Instant.parse("2026-12-19T03:00:00Z"), false),
                        new GroupSummaryView(g3, "Antigo", GroupStatus.DRAWN, ANA, 3L, null, null)));

        List<GroupSummary> mine = service.listMine(BRUNO);

        assertThat(mine)
                .containsExactly(
                        new GroupSummary(g1, "Natal", GroupStatus.OPEN, 4, true, DEC_19, "19:00"),
                        new GroupSummary(g2, "Firma", GroupStatus.DRAWN, 12, false, DEC_19, null),
                        new GroupSummary(g3, "Antigo", GroupStatus.DRAWN, 3, false, null, null));
    }

    // ---- grupo finalizado (US3) ----

    /** Grupo sorteado cujo evento já aconteceu, mas que a tarefa agendada ainda não finalizou. */
    private SecretGroup drawnGroupDue(UUID organizer, UUID... others) {
        SecretGroup group = lockedGroupWithDetails(organizer, others);
        group.markDrawn(NOW);
        ReflectionTestUtils.setField(group, "finalizesAt", NOW.minusSeconds(60));
        return group;
    }

    @Test
    void theFirstWriteAfterTheEventFinishesTheGroupAndIsRefused() {
        SecretGroup group = drawnGroupDue(ANA, BRUNO);

        assertThatThrownBy(() -> service.updateEventDetails(
                        ANA, group.getId(), new EventDetailsRequest(null, DEC_19, null, "Salão")))
                .isInstanceOfSatisfying(GroupFinishedException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-finished");
                    assertThat(ex.getMessage()).isEqualTo("Este grupo foi finalizado.");
                });

        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        assertThat(group.getFinishedAt()).isEqualTo(NOW);
        assertThat(group.getLocation()).isEqualTo("Casa da Ana");
        verify(events).publishEvent(new GroupFinishedEvent(group.getId(), 2));
        verify(events, never()).publishEvent(any(GroupUpdatedEvent.class));
    }

    @Test
    void joiningAFinishedGroupIsRefused() {
        SecretGroup group = openGroup(ANA);
        group.markDrawn(NOW);
        group.finishIfDue(Instant.parse("2026-12-20T00:00:00Z"));
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("K7PQM2XA")))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-finished");
                    assertThat(ex.getMessage()).isEqualTo("Este grupo foi finalizado.");
                });
        verify(members, never()).save(any());
    }

    @Test
    void joiningADueGroupFinishesItFirst() {
        SecretGroup group = drawnGroupDue(ANA);
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));

        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("K7PQM2XA")))
                .isInstanceOf(GroupFinishedException.class);
        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        verify(events).publishEvent(new GroupFinishedEvent(group.getId(), 1));
        verify(members, never()).save(any());
    }

    @Test
    void openGroupWhoseEventHasPassedIsFlaggedButNotFinished() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO, UUID.randomUUID());
        ReflectionTestUtils.setField(group, "finalizesAt", NOW.minusSeconds(60));
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));

        GroupDetail detail = service.getDetail(ANA, group.getId());

        assertThat(detail.status()).isEqualTo(GroupStatus.OPEN);
        assertThat(detail.eventDatePassed()).isTrue();
        assertThat(detail.drawBlockedReason()).isEqualTo("event-date-passed");
        assertThat(detail.canDraw()).isFalse();
        assertThat(detail.finishedAt()).isNull();
    }

    @Test
    void eventDatePassedIsOnlyForOpenGroups() {
        SecretGroup upcoming = lockedGroupWithDetails(ANA, BRUNO);
        when(groups.findById(upcoming.getId())).thenReturn(Optional.of(upcoming));
        assertThat(service.getDetail(ANA, upcoming.getId()).eventDatePassed()).isFalse();

        SecretGroup drawn = drawnGroupDue(ANA, BRUNO);
        when(groups.findById(drawn.getId())).thenReturn(Optional.of(drawn));
        assertThat(service.getDetail(ANA, drawn.getId()).eventDatePassed()).isFalse();

        SecretGroup old = new SecretGroup("Antigo", "K7PQM2XA", ANA, NOW);
        when(groups.findById(old.getId())).thenReturn(Optional.of(old));
        when(members.existsById(new GroupMemberId(old.getId(), ANA))).thenReturn(true);
        givenParticipants(old, ANA);
        assertThat(service.getDetail(ANA, old.getId()).eventDatePassed()).isFalse();
    }

    @Test
    void finishedGroupDetailBringsWhenItWasFinishedAndNothingToDo() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO, UUID.randomUUID());
        group.markDrawn(NOW);
        Instant finishedAt = Instant.parse("2026-12-19T22:00:00Z");
        group.finishIfDue(finishedAt);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));

        GroupDetail detail = service.getDetail(ANA, group.getId());

        assertThat(detail.status()).isEqualTo(GroupStatus.FINISHED);
        assertThat(detail.finishedAt()).isEqualTo(finishedAt);
        assertThat(detail.eventDatePassed()).isFalse();
        assertThat(detail.canDraw()).isFalse();
        assertThat(detail.drawBlockedReason()).isNull();
        // A leitura não muda o estado (research R17)
        verify(events, never()).publishEvent(any());
    }

    // ---- saída depois do sorteio (US4) ----

    @Test
    void whoLeftAfterTheDrawCannotJoinAgain() {
        SecretGroup group = openGroup(ANA);
        when(groups.findIdByCode("K7PQM2XA")).thenReturn(Optional.of(group.getId()));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(exitBans.existsById(new GroupExitBanId(group.getId(), BRUNO))).thenReturn(true);

        assertThatThrownBy(() -> service.join(BRUNO, new JoinGroupRequest("K7PQM2XA")))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("rejoin-not-allowed");
                    assertThat(ex.getMessage())
                            .isEqualTo("Você saiu deste grupo depois do sorteio e não pode entrar de novo.");
                });
        verify(members, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void organizerSeesThePendingExitRequestsAndEachPersonOnlyTheirOwn() {
        UUID carla = UUID.randomUUID();
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO, carla);
        group.markDrawn(NOW);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));
        ExitRequest brunos = new ExitRequest(group.getId(), BRUNO, NOW);
        ExitRequestResponse asListed = new ExitRequestResponse(brunos.getId(), BRUNO, "Bruno", NOW);
        when(exitRequests.findPending(group.getId())).thenReturn(List.of(asListed));
        when(exitRequests.findByGroupIdAndUserIdAndStatus(group.getId(), BRUNO, ExitRequestStatus.PENDING))
                .thenReturn(Optional.of(brunos));
        when(members.existsById(new GroupMemberId(group.getId(), carla))).thenReturn(true);

        GroupDetail forAna = service.getDetail(ANA, group.getId());
        assertThat(forAna.pendingExitRequests()).containsExactly(asListed);
        assertThat(forAna.myExitRequest()).isNull();

        GroupDetail forBruno = service.getDetail(BRUNO, group.getId());
        assertThat(forBruno.pendingExitRequests()).isEmpty();
        assertThat(forBruno.myExitRequest())
                .isEqualTo(new ExitRequestResponse(
                        brunos.getId(), BRUNO, "Pessoa " + BRUNO.toString().substring(0, 4), NOW));

        GroupDetail forCarla = service.getDetail(carla, group.getId());
        assertThat(forCarla.pendingExitRequests()).isEmpty();
        assertThat(forCarla.myExitRequest()).isNull();
    }

    @Test
    void drawUndoneIsFlaggedUntilTheNextDraw() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO, UUID.randomUUID());
        group.markDrawn(NOW);
        group.undoDraw(NOW.plusSeconds(60));
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));

        GroupDetail undone = service.getDetail(ANA, group.getId());
        assertThat(undone.drawUndone()).isTrue();
        assertThat(undone.status()).isEqualTo(GroupStatus.OPEN);
        assertThat(undone.myExitRequest()).isNull();
        assertThat(undone.pendingExitRequests()).isEmpty();

        group.markDrawn(NOW.plusSeconds(120));
        assertThat(service.getDetail(ANA, group.getId()).drawUndone()).isFalse();
    }

    @Test
    void exitRequestsAreOnlyLookedUpForDrawnGroups() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));

        service.getDetail(ANA, group.getId());

        verify(exitRequests, never()).findPending(any());
        verify(exitRequests, never()).findByGroupIdAndUserIdAndStatus(any(), any(), any());
    }

    // ---- lista de desejos (US1 da 003) ----

    @Test
    void theDetailBringsOnlyTheSessionUsersOwnWishlist() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));
        WishItem livro = new WishItem(group.getId(), BRUNO, "Livro", "https://loja.com/livro", NOW);
        WishItem caneca = new WishItem(group.getId(), BRUNO, "Caneca", null, NOW.plusSeconds(1));
        when(wishItems.findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(group.getId(), BRUNO))
                .thenReturn(List.of(livro, caneca));

        GroupDetail forBruno = service.getDetail(BRUNO, group.getId());
        assertThat(forBruno.myWishlist().items())
                .extracting("description", "url")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Livro", "https://loja.com/livro"),
                        org.assertj.core.groups.Tuple.tuple("Caneca", null));
        assertThat(forBruno.myWishlist().maxItems()).isEqualTo(5);
        assertThat(forBruno.myWishlist().editable()).isTrue();

        // A organizadora não vê a lista do Bruno: só a própria (vazia)
        GroupDetail forAna = service.getDetail(ANA, group.getId());
        assertThat(forAna.myWishlist().items()).isEmpty();
        verify(wishItems).findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(group.getId(), ANA);
    }

    @Test
    void theWishlistIsReadOnlyOnceTheGroupIsFinished() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);
        group.markDrawn(NOW);
        group.finishIfDue(Instant.parse("2026-12-20T00:00:00Z"));
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));

        assertThat(service.getDetail(BRUNO, group.getId()).myWishlist().editable())
                .isFalse();
    }

    // ---- valor do presente (US3 da 003) ----

    @Test
    void theGroupIsCreatedWithTheGiftValue() {
        when(members.findParticipants(any())).thenReturn(List.of(new ParticipantView(ANA, "Ana", NOW)));

        GroupDetail detail = service.create(
                ANA, new CreateGroupRequest("Natal", null, DEC_19, "19:00", "Casa da Ana", BigDecimal.valueOf(80)));

        assertThat(detail.eventDetails().giftValue()).isEqualTo(80);
    }

    @Test
    void changingOnlyTheGiftValueKeepsTheConfirmationsAndTellsEveryone() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);

        GroupDetail detail = service.updateEventDetails(
                ANA,
                group.getId(),
                new EventDetailsRequest(null, DEC_19, "19:00", "Casa da Ana", BigDecimal.valueOf(100)));

        assertThat(detail.eventDetails().giftValue()).isEqualTo(100);
        assertThat(detail.detailsVersion()).isEqualTo(1);
        verify(members, never()).resetConfirmations(any(), any());
        verify(events).publishEvent(new GroupUpdatedEvent(group.getId(), GroupStatus.OPEN, 2, false));
    }

    @Test
    void afterTheDrawADifferentGiftValueIsRefusedAndNothingChanges() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);
        group.updateGiftValue(80);
        group.markDrawn(NOW);

        assertThatThrownBy(() -> service.updateEventDetails(
                        ANA,
                        group.getId(),
                        new EventDetailsRequest(null, DEC_19, "19:00", "Salão", BigDecimal.valueOf(50))))
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo("gift-value-locked"));
        assertThat(group.getLocation()).isEqualTo("Casa da Ana");
        verify(events, never()).publishEvent(any());

        // O mesmo valor passa: os demais detalhes continuam editáveis depois do sorteio
        GroupDetail detail = service.updateEventDetails(
                ANA, group.getId(), new EventDetailsRequest(null, DEC_19, "19:00", "Salão", BigDecimal.valueOf(80)));
        assertThat(detail.eventDetails().location()).isEqualTo("Salão");
        assertThat(detail.eventDetails().giftValue()).isEqualTo(80);
    }

    @Test
    void everyParticipantSeesTheGiftValue() {
        SecretGroup group = lockedGroupWithDetails(ANA, BRUNO);
        group.updateGiftValue(80);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));

        assertThat(service.getDetail(BRUNO, group.getId()).eventDetails().giftValue())
                .isEqualTo(80);
    }
}
