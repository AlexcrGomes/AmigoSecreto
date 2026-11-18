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
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.common.RateLimitExceededException;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.group.dto.CreateGroupRequest;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.GroupSummary;
import br.com.amigosecreto.group.dto.JoinGroupRequest;
import br.com.amigosecreto.ratelimit.RateLimitPolicy;
import br.com.amigosecreto.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

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
        service = new GroupService(groups, members, codes, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC), events);
    }

    private SecretGroup openGroup(UUID organizer) {
        return new SecretGroup("Natal 2026", "K7PQM2XA", organizer, NOW);
    }

    private void givenParticipants(SecretGroup group, UUID... userIds) {
        List<ParticipantView> views = new ArrayList<>();
        for (UUID id : userIds) {
            views.add(new ParticipantView(id, "Pessoa " + id.toString().substring(0, 4)));
        }
        when(members.findParticipants(group.getId())).thenReturn(views);
        when(members.countByIdGroupId(group.getId())).thenReturn((long) userIds.length);
    }

    // ---- create ----

    @Test
    void createSavesAnOpenGroupWithTheCreatorAsMember() {
        when(members.findParticipants(any())).thenReturn(List.of(new ParticipantView(ANA, "Ana")));

        GroupDetail detail = service.create(ANA, new CreateGroupRequest("Natal 2026"));

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
        when(members.findParticipants(any())).thenReturn(List.of(new ParticipantView(ANA, "Ana")));

        GroupDetail detail = service.create(ANA, new CreateGroupRequest("Natal"));

        assertThat(detail.code()).isEqualTo("CCCC-CCCC");
        verify(codes, times(3)).generate();
    }

    @Test
    void givesUpAfterFiveCollisions() {
        when(groups.existsByCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(ANA, new CreateGroupRequest("Natal")))
                .isInstanceOf(IllegalStateException.class);
        verify(codes, times(5)).generate();
        verify(groups, never()).save(any());
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
                .thenReturn(List.of(new ParticipantView(ANA, "Ana"), new ParticipantView(BRUNO, "Bruno")));

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
        when(members.findSummaries(BRUNO))
                .thenReturn(List.of(
                        new GroupSummaryView(g1, "Natal", GroupStatus.OPEN, BRUNO, 4L),
                        new GroupSummaryView(g2, "Firma", GroupStatus.DRAWN, ANA, 12L)));

        List<GroupSummary> mine = service.listMine(BRUNO);

        assertThat(mine)
                .containsExactly(
                        new GroupSummary(g1, "Natal", GroupStatus.OPEN, 4, true),
                        new GroupSummary(g2, "Firma", GroupStatus.DRAWN, 12, false));
    }
}
