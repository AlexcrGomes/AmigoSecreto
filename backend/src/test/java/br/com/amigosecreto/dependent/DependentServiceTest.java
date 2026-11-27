package br.com.amigosecreto.dependent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.dependent.dto.DependentRequest;
import br.com.amigosecreto.dependent.dto.MyDependent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.exitrequest.ExitRequestRepository;
import br.com.amigosecreto.group.DependentAccounts;
import br.com.amigosecreto.group.EventDetails;
import br.com.amigosecreto.group.EventSchedule;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupService;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.SecretGroupRepository;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
class DependentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final UUID ORGANIZER = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUCAS = UUID.randomUUID();

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private ExitRequestRepository exitRequests;

    @Mock
    private AppUserRepository users;

    @Mock
    private DependentAccounts accounts;

    @Mock
    private GroupService groupService;

    @Mock
    private ApplicationEventPublisher events;

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private SecretGroup group;
    private GroupMember ana;
    private GroupMember lucas;
    private DependentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        group = new SecretGroup("Natal", "K7PQM2XA", ORGANIZER, NOW);
        group.updateEventDetails(
                new EventDetails(null, LocalDate.of(2026, 12, 19), LocalTime.of(19, 0), "Casa"),
                new EventSchedule(clock, BRASILIA));
        ana = new GroupMember(group.getId(), ANA, NOW);
        ana.confirm(NOW);
        lucas = GroupMember.dependentOf(ana, LUCAS, NOW);
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.findById(new GroupMemberId(group.getId(), ANA))).thenReturn(Optional.of(ana));
        when(members.findDependent(LUCAS, group.getId(), ANA)).thenReturn(Optional.of(lucas));
        when(members.countByIdGroupId(group.getId())).thenReturn(4L);
        when(members.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupService.myDependent(any(), any(), any()))
                .thenAnswer(inv -> new MyDependent(
                        ((GroupMember) inv.getArgument(1)).getId().userId(),
                        inv.getArgument(2),
                        ((GroupMember) inv.getArgument(1)).isConfirmed(),
                        MyWishlist.of(List.of(), true),
                        null));
        service = new DependentService(
                new GroupLock(groups, members, exitRequests, clock, events),
                members,
                users,
                new DependentLookup(members),
                accounts,
                exitRequests,
                groupService,
                clock,
                events);
    }

    private static void assertConflict(Runnable action, String type) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo(type));
    }

    // ---- adicionar ----

    @Test
    void addCreatesADependentWithTheGuardiansConfirmation() {
        MyDependent added = service.add(ANA, group.getId(), new DependentRequest("  Lucas "));

        ArgumentCaptor<AppUser> user = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().isDependent()).isTrue();
        assertThat(user.getValue().getName()).isEqualTo("Lucas");
        ArgumentCaptor<GroupMember> member = ArgumentCaptor.forClass(GroupMember.class);
        verify(members).save(member.capture());
        assertThat(member.getValue().getGuardianId()).isEqualTo(ANA);
        assertThat(member.getValue().getId().userId()).isEqualTo(user.getValue().getId());
        assertThat(member.getValue().getConfirmedAt()).isEqualTo(NOW);
        assertThat(added.name()).isEqualTo("Lucas");
        assertThat(added.confirmed()).isTrue();
        verify(events).publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 5));
    }

    @Test
    void theNameMustHaveTwoToEightyCharactersAfterTrimming() {
        assertThat(validator.validate(new DependentRequest("Lu"))).isEmpty();
        assertThat(validator.validate(new DependentRequest("a".repeat(80)))).isEmpty();
        assertThat(validator.validate(new DependentRequest(" L "))).isNotEmpty();
        assertThat(validator.validate(new DependentRequest("   "))).isNotEmpty();
        assertThat(validator.validate(new DependentRequest(null))).isNotEmpty();
        assertThat(validator.validate(new DependentRequest("a".repeat(81)))).isNotEmpty();
    }

    @Test
    void cannotAddAfterTheDrawOrOnceFinished() {
        group.markDrawn(NOW);
        assertConflict(() -> service.add(ANA, group.getId(), new DependentRequest("Lucas")), "group-already-drawn");

        SecretGroup finished = new SecretGroup("Antigo", "K7PQM2XB", ORGANIZER, NOW);
        finished.updateEventDetails(
                new EventDetails(null, LocalDate.of(2026, 11, 26), null, "Casa"),
                new EventSchedule(Clock.fixed(NOW, ZoneOffset.UTC), BRASILIA));
        finished.markDrawn(NOW);
        assertThat(finished.finishIfDue(Instant.parse("2026-11-28T12:00:00Z"))).isTrue();
        when(groups.findByIdForUpdate(finished.getId())).thenReturn(Optional.of(finished));
        when(members.findById(new GroupMemberId(finished.getId(), ANA)))
                .thenReturn(Optional.of(new GroupMember(finished.getId(), ANA, NOW)));
        assertThatThrownBy(() -> service.add(ANA, finished.getId(), new DependentRequest("Lucas")))
                .isInstanceOf(GroupFinishedException.class);
        verify(users, never()).save(any());
    }

    @Test
    void aFullGroupCountsTheDependents() {
        when(members.countByIdGroupId(group.getId())).thenReturn(100L);

        assertConflict(() -> service.add(ANA, group.getId(), new DependentRequest("Lucas")), "group-full");
        verify(users, never()).save(any());
    }

    @Test
    void atMostFiveDependentsPerGuardian() {
        when(members.countDependents(group.getId(), ANA)).thenReturn(5L);

        assertThatThrownBy(() -> service.add(ANA, group.getId(), new DependentRequest("Lucas")))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("dependents-limit");
                    assertThat(ex.getMessage()).isEqualTo("Você pode adicionar até 5 dependentes.");
                });
        verify(users, never()).save(any());
    }

    @Test
    void aNonMemberGetsNotFound() {
        UUID outsider = UUID.randomUUID();

        assertThatThrownBy(() -> service.add(outsider, group.getId(), new DependentRequest("Lucas")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.add(ANA, UUID.randomUUID(), new DependentRequest("Lucas")))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- renomear e remover ----

    @Test
    void renameChangesTheNameAndSignals() {
        AppUser lucasUser = AppUser.dependent("Lucas", NOW);
        when(users.findById(LUCAS)).thenReturn(Optional.of(lucasUser));

        MyDependent renamed = service.rename(ANA, group.getId(), LUCAS, new DependentRequest("Lucas M."));

        assertThat(lucasUser.getName()).isEqualTo("Lucas M.");
        assertThat(renamed.name()).isEqualTo("Lucas M.");
        verify(events).publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 4));
    }

    @Test
    void removeDeletesTheParticipationAndTheDependent() {
        service.remove(ANA, group.getId(), LUCAS);

        verify(members).delete(lucas);
        verify(accounts).deleteUnreferenced(List.of(LUCAS));
        verify(events).publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, 4));
    }

    @Test
    void someoneElsesDependentLooksLikeItDoesNotExist() {
        UUID bruno = UUID.randomUUID();
        when(members.findById(new GroupMemberId(group.getId(), bruno)))
                .thenReturn(Optional.of(new GroupMember(group.getId(), bruno, NOW)));

        Throwable rename =
                catchThrowable(() -> service.rename(bruno, group.getId(), LUCAS, new DependentRequest("Outro")));
        Throwable remove = catchThrowable(() -> service.remove(bruno, group.getId(), LUCAS));
        Throwable unknown = catchThrowable(() -> service.remove(ANA, group.getId(), UUID.randomUUID()));

        assertThat(List.of(rename, remove, unknown))
                .allSatisfy(error -> assertThat(error)
                        .isInstanceOf(NotFoundException.class)
                        .hasMessage("Dependente não encontrado."));
        verify(members, never()).delete(any(GroupMember.class));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void renameAndRemoveOnlyBeforeTheDraw() {
        group.markDrawn(NOW);

        assertConflict(
                () -> service.rename(ANA, group.getId(), LUCAS, new DependentRequest("Outro")), "group-already-drawn");
        assertConflict(() -> service.remove(ANA, group.getId(), LUCAS), "group-already-drawn");
        verify(members, never()).delete(any(GroupMember.class));
        verify(accounts, never()).deleteUnreferenced(eq(List.of(LUCAS)));
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            return ex;
        }
        throw new AssertionError("era esperada uma exceção");
    }

    // ---- US3: não readicionar depois de uma saída aprovada de dependente (Clarifications Q3) ----

    @Test
    void afterAnApprovedDependentExitThatGuardianCannotAddMore() {
        when(exitRequests.existsApprovedDependentExit(group.getId(), ANA)).thenReturn(true);

        assertThatThrownBy(() -> service.add(ANA, group.getId(), new DependentRequest("Bia")))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("dependents-locked");
                    assertThat(ex.getMessage())
                            .isEqualTo(
                                    "Depois da saída de um dependente seu, não dá para adicionar outros neste grupo.");
                });
        verify(users, never()).save(any());
    }
}
