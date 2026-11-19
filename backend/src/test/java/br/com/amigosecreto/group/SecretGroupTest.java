package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecretGroupTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    @Test
    void newGroupIsOpenWithTrimmedName() {
        UUID organizer = UUID.randomUUID();
        SecretGroup group = new SecretGroup("  Natal 2026 ", "K7PQM2XA", organizer, NOW);

        assertThat(group.getName()).isEqualTo("Natal 2026");
        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
        assertThat(group.getDrawnAt()).isNull();
        assertThat(group.isOrganizer(organizer)).isTrue();
        assertThat(group.isOrganizer(UUID.randomUUID())).isFalse();
        assertThat(group.isNew()).isTrue();
        assertThat(group.getCode()).isEqualTo("K7PQM2XA");
    }

    @Test
    void markDrawnOnlyLeavesOpen() {
        SecretGroup group = new SecretGroup("Natal", "K7PQM2XA", UUID.randomUUID(), NOW);

        group.markDrawn(NOW.plusSeconds(60));

        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
        assertThat(group.getDrawnAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatIllegalStateException().isThrownBy(() -> group.markDrawn(NOW.plusSeconds(120)));
        assertThat(group.getDrawnAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void newGroupHasNoEventDetailsUntilTheyAreInformed() {
        SecretGroup group = new SecretGroup("Natal", "K7PQM2XA", UUID.randomUUID(), NOW);
        EventSchedule schedule = new EventSchedule(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Sao_Paulo"));

        assertThat(group.eventDetails(schedule)).isEmpty();
        assertThat(group.getDetailsVersion()).isEqualTo(1);
        assertThat(group.getLocation()).isNull();
        assertThat(group.getEventStartsAt()).isNull();
        assertThat(group.getFinalizesAt()).isNull();
        assertThat(group.getFinishedAt()).isNull();
        assertThat(group.getDrawUndoneAt()).isNull();
    }

    @Test
    void organizerIsBornConfirmedAndOthersStartPending() {
        UUID groupId = UUID.randomUUID();

        GroupMember organizer = GroupMember.organizer(groupId, UUID.randomUUID(), NOW);
        GroupMember guest = new GroupMember(groupId, UUID.randomUUID(), NOW.plusSeconds(60));

        assertThat(organizer.isConfirmed()).isTrue();
        assertThat(organizer.getConfirmedAt())
                .isEqualTo(organizer.getJoinedAt())
                .isEqualTo(NOW);
        assertThat(guest.isConfirmed()).isFalse();
        assertThat(guest.getConfirmedAt()).isNull();
    }

    @Test
    void memberStartsNewAndBecomesManaged() {
        GroupMember member = new GroupMember(UUID.randomUUID(), UUID.randomUUID(), NOW);

        assertThat(member.isNew()).isTrue();
        member.markNotNew();
        assertThat(member.isNew()).isFalse();

        SecretGroup group = new SecretGroup("Natal", "K7PQM2XA", UUID.randomUUID(), NOW);
        group.markNotNew();
        assertThat(group.isNew()).isFalse();
    }
}
