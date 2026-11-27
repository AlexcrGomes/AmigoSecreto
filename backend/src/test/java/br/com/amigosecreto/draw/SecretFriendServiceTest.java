package br.com.amigosecreto.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.dependent.dto.DependentSecretFriend;
import br.com.amigosecreto.draw.dto.SecretFriendResponse;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.SecretGroupRepository;
import br.com.amigosecreto.wishlist.WishItem;
import br.com.amigosecreto.wishlist.WishItemRepository;
import br.com.amigosecreto.wishlist.dto.FriendWishItem;
import java.security.SecureRandom;
import java.time.Instant;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecretFriendServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-20T15:00:00Z");
    private static final UUID ANA = UUID.randomUUID();

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private DrawAssignmentRepository assignments;

    @Mock
    private AppUserRepository users;

    @Mock
    private WishItemRepository wishItems;

    private final AssignmentCipher cipher = new AssignmentCipher(new byte[32], new SecureRandom());
    private SecretFriendService service;
    private SecretGroup group;
    private AppUser carla;

    @BeforeEach
    void setUp() {
        group = new SecretGroup("Natal", "K7PQM2XA", ANA, NOW);
        carla = new AppUser("Carla", "carla@x.com", "h", NOW);
        when(groups.findById(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(new GroupMemberId(group.getId(), ANA))).thenReturn(true);
        when(users.findById(carla.getId())).thenReturn(Optional.of(carla));
        service = new SecretFriendService(groups, members, assignments, users, cipher, wishItems);
    }

    @Test
    void returnsOnlyTheNameOfWhoTheSessionUserDrew() {
        group.markDrawn(NOW);
        byte[] encrypted = cipher.encrypt(carla.getId(), group.getId(), ANA);
        when(assignments.findByIdGroupIdAndIdGiverId(group.getId(), ANA))
                .thenReturn(Optional.of(new DrawAssignment(group.getId(), ANA, encrypted, NOW)));

        SecretFriendResponse response = service.getMine(ANA, group.getId());

        assertThat(response).isEqualTo(new SecretFriendResponse("Carla"));
        verify(assignments).findByIdGroupIdAndIdGiverId(group.getId(), ANA);
        verifyNoMoreInteractions(assignments);
    }

    @Test
    void nonMemberGetsNotFound() {
        UUID stranger = UUID.randomUUID();
        group.markDrawn(NOW);

        assertThatThrownBy(() -> service.getMine(stranger, group.getId())).isInstanceOf(NotFoundException.class);
        verify(assignments, never()).findByIdGroupIdAndIdGiverId(any(), any());
    }

    @Test
    void unknownGroupGetsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(groups.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(ANA, unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void openGroupIsConflict() {
        assertThatThrownBy(() -> service.getMine(ANA, group.getId()))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("group-not-drawn");
                    assertThat(ex.getMessage()).isEqualTo("O sorteio deste grupo ainda não foi realizado.");
                });
    }

    @Test
    void missingPairIsAnInternalInconsistency() {
        group.markDrawn(NOW);
        when(assignments.findByIdGroupIdAndIdGiverId(group.getId(), ANA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(ANA, group.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(ANA.toString());
    }

    @Test
    void finishedGroupStillRevealsOnlyMyOwnFriend() {
        group.markDrawn(NOW);
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);
        byte[] encrypted = cipher.encrypt(carla.getId(), group.getId(), ANA);
        when(assignments.findByIdGroupIdAndIdGiverId(group.getId(), ANA))
                .thenReturn(Optional.of(new DrawAssignment(group.getId(), ANA, encrypted, NOW)));

        assertThat(service.getMine(ANA, group.getId())).isEqualTo(new SecretFriendResponse("Carla"));
        verify(assignments).findByIdGroupIdAndIdGiverId(group.getId(), ANA);
        verifyNoMoreInteractions(assignments);
    }

    // ---- lista de desejos de quem o usuário tirou (US2 da 003, research R27) ----

    /** Sorteado em NOW: Ana tirou a Carla. */
    private void anaDrewCarla() {
        group.markDrawn(NOW);
        byte[] encrypted = cipher.encrypt(carla.getId(), group.getId(), ANA);
        when(assignments.findByIdGroupIdAndIdGiverId(group.getId(), ANA))
                .thenReturn(Optional.of(new DrawAssignment(group.getId(), ANA, encrypted, NOW)));
    }

    private void carlasListChangedAt(Instant when, WishItem... items) {
        GroupMember carlaMember = new GroupMember(group.getId(), carla.getId(), NOW.minusSeconds(86_400));
        if (when != null) {
            carlaMember.touchWishlist(when);
        }
        when(members.findById(new GroupMemberId(group.getId(), carla.getId()))).thenReturn(Optional.of(carlaMember));
        when(wishItems.findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(group.getId(), carla.getId()))
                .thenReturn(List.of(items));
    }

    @Test
    void bringsTheWishlistOfWhoTheSessionUserDrewWithoutAnyId() {
        anaDrewCarla();
        carlasListChangedAt(
                NOW.minusSeconds(3600),
                new WishItem(group.getId(), carla.getId(), "Livro", "https://loja.com/livro", NOW.minusSeconds(7200)),
                new WishItem(group.getId(), carla.getId(), "Caneca", null, NOW.minusSeconds(7000)));

        SecretFriendResponse response = service.getMine(ANA, group.getId());

        assertThat(response.name()).isEqualTo("Carla");
        assertThat(response.wishlist())
                .containsExactly(
                        new FriendWishItem("Livro", "https://loja.com/livro"), new FriendWishItem("Caneca", null));
        // Mudou antes do sorteio: nada de "Atualizada em"
        assertThat(response.wishlistUpdatedAt()).isNull();
        // Só a lista de quem a Ana tirou, achada pelo par dela — nunca a da própria Ana
        verify(wishItems).findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(group.getId(), carla.getId());
        verify(wishItems, never()).findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(group.getId(), ANA);
    }

    @Test
    void aChangeAfterTheDrawIsReported() {
        anaDrewCarla();
        Instant changed = NOW.plusSeconds(3600);
        carlasListChangedAt(changed, new WishItem(group.getId(), carla.getId(), "Livro", null, NOW));

        assertThat(service.getMine(ANA, group.getId()).wishlistUpdatedAt()).isEqualTo(changed);
    }

    @Test
    void anEmptyListIsAnEmptyArray() {
        anaDrewCarla();
        carlasListChangedAt(null);

        SecretFriendResponse response = service.getMine(ANA, group.getId());

        assertThat(response.wishlist()).isEmpty();
        assertThat(response.wishlistUpdatedAt()).isNull();
    }

    @Test
    void aFinishedGroupStillBringsTheList() {
        anaDrewCarla();
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);
        carlasListChangedAt(null, new WishItem(group.getId(), carla.getId(), "Livro", null, NOW));

        assertThat(service.getMine(ANA, group.getId()).wishlist()).containsExactly(new FriendWishItem("Livro", null));
    }

    @Test
    void beforeTheDrawNoListIsEverRead() {
        assertThatThrownBy(() -> service.getMine(ANA, group.getId())).isInstanceOf(ConflictException.class);

        verifyNoInteractions(wishItems);
    }

    // ---- feature 004: resultados dos dependentes do usuário da sessão (US2, research R38) ----

    /** Lucas e Bia são dependentes da Ana: Lucas tirou a Carla e Bia tirou a própria Ana. */
    private List<AppUser> anaWithTwoDependents() {
        group.markDrawn(NOW);
        AppUser lucas = AppUser.dependent("Lucas", NOW);
        AppUser bia = AppUser.dependent("Bia", NOW);
        AppUser ana = new AppUser("Ana", "ana@x.com", "h", NOW);
        ReflectionTestUtils.setField(ana, "id", ANA);
        when(users.findById(lucas.getId())).thenReturn(Optional.of(lucas));
        when(users.findById(bia.getId())).thenReturn(Optional.of(bia));
        when(users.findById(ANA)).thenReturn(Optional.of(ana));
        GroupMember anaMember = new GroupMember(group.getId(), ANA, NOW);
        when(members.findDependents(group.getId(), ANA))
                .thenReturn(List.of(
                        GroupMember.dependentOf(anaMember, lucas.getId(), NOW),
                        GroupMember.dependentOf(anaMember, bia.getId(), NOW)));
        when(assignments.findByIdGroupIdAndIdGiverId(group.getId(), lucas.getId()))
                .thenReturn(Optional.of(new DrawAssignment(
                        group.getId(),
                        lucas.getId(),
                        cipher.encrypt(carla.getId(), group.getId(), lucas.getId()),
                        NOW)));
        when(assignments.findByIdGroupIdAndIdGiverId(group.getId(), bia.getId()))
                .thenReturn(Optional.of(new DrawAssignment(
                        group.getId(), bia.getId(), cipher.encrypt(ANA, group.getId(), bia.getId()), NOW)));
        return List.of(lucas, bia);
    }

    @Test
    void theGuardianGetsEachDependentsResultInTheOrderTheyWereAdded() {
        List<AppUser> dependents = anaWithTwoDependents();
        carlasListChangedAt(NOW.plusSeconds(60), new WishItem(group.getId(), carla.getId(), "Livro", null, NOW));

        List<DependentSecretFriend> results = service.getMyDependents(ANA, group.getId());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).dependentId()).isEqualTo(dependents.get(0).getId());
        assertThat(results.get(0).dependentName()).isEqualTo("Lucas");
        assertThat(results.get(0).secretFriend().name()).isEqualTo("Carla");
        assertThat(results.get(0).secretFriend().wishlist())
                .extracting("description")
                .containsExactly("Livro");
        assertThat(results.get(0).secretFriend().wishlistUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(results.get(1).dependentName()).isEqualTo("Bia");
        assertThat(results.get(1).secretFriend().name()).isEqualTo("Ana");
        // O id de quem foi tirado nunca sai
        assertThat(results.toString()).doesNotContain(carla.getId().toString());
    }

    @Test
    void withoutDependentsTheListIsEmptyAndNothingIsDecrypted() {
        group.markDrawn(NOW);

        assertThat(service.getMyDependents(ANA, group.getId())).isEmpty();
        verify(assignments, never()).findByIdGroupIdAndIdGiverId(any(), any());
    }

    @Test
    void dependentsResultsFollowTheSameRulesAsMine() {
        assertThatThrownBy(() -> service.getMyDependents(ANA, group.getId()))
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo("group-not-drawn"));
        anaWithTwoDependents();
        assertThatThrownBy(() -> service.getMyDependents(UUID.randomUUID(), group.getId()))
                .isInstanceOf(NotFoundException.class);
        verify(assignments, never()).findByIdGroupIdAndIdGiverId(any(), any());

        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);
        assertThat(service.getMyDependents(ANA, group.getId())).hasSize(2);
    }

    @Test
    void onlyTheSessionUsersDependentsAreLookedUp() {
        group.markDrawn(NOW);

        service.getMyDependents(ANA, group.getId());

        verify(members).findDependents(group.getId(), ANA);
    }
}
