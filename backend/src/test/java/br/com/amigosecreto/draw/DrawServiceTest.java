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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrawServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-20T15:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

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
        group = new SecretGroup("Natal", "K7PQM2XA", ANA, NOW.minusSeconds(3600));
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.existsById(any(GroupMemberId.class))).thenReturn(true);
        when(members.findUserIds(group.getId())).thenReturn(List.of(ANA, BRUNO, CARLA));
        when(derangement.generate(anyList())).thenReturn(Map.of(ANA, BRUNO, BRUNO, CARLA, CARLA, ANA));
        service = new DrawService(
                groups,
                members,
                assignments,
                derangement,
                cipher,
                rateLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC),
                events);
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

        assertThatThrownBy(() -> service.draw(ANA, group.getId()))
                .isInstanceOfSatisfying(UnprocessableException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("not-enough-participants");
                    assertThat(ex.getMessage()).isEqualTo("São necessários pelo menos 3 participantes.");
                });
        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
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
}
