package br.com.amigosecreto.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.draw.dto.SecretFriendResponse;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.SecretGroupRepository;
import java.security.SecureRandom;
import java.time.Instant;
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
        service = new SecretFriendService(groups, members, assignments, users, cipher);
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
}
