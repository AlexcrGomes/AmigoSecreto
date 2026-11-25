package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.UnprocessableException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SecretGroupTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    /** Hoje em Brasília é 18/11/2026, 09:00. */
    private static final EventSchedule SCHEDULE = scheduleAt(NOW);

    private static final LocalDate DEC_19 = LocalDate.of(2026, 12, 19);
    private static final EventDetails PARTY =
            new EventDetails("Traga um prato", DEC_19, LocalTime.of(19, 0), "Casa da Ana");

    private static EventSchedule scheduleAt(Instant instant) {
        return new EventSchedule(Clock.fixed(instant, ZoneOffset.UTC), BRASILIA);
    }

    private static SecretGroup groupWith(EventDetails details) {
        SecretGroup group = new SecretGroup("Natal", "K7PQM2XA", UUID.randomUUID(), NOW);
        group.updateEventDetails(details, SCHEDULE);
        return group;
    }

    // ---- detalhes do evento (US1) ----

    @Test
    void firstEventDetailsFillEveryFieldWithoutBumpingTheVersion() {
        SecretGroup group = groupWith(PARTY);

        assertThat(group.eventDetails(SCHEDULE)).contains(PARTY);
        assertThat(group.getDescription()).isEqualTo("Traga um prato");
        assertThat(group.getLocation()).isEqualTo("Casa da Ana");
        assertThat(group.getEventHasTime()).isTrue();
        assertThat(group.getEventStartsAt()).isEqualTo(Instant.parse("2026-12-19T22:00:00Z"));
        assertThat(group.getFinalizesAt()).isEqualTo(Instant.parse("2026-12-19T22:00:00Z"));
        assertThat(group.getDetailsVersion()).isEqualTo(1);
    }

    @Test
    void oldGroupGetsEveryFieldAtOnce() {
        SecretGroup old = new SecretGroup("Antigo", "K7PQM2XA", UUID.randomUUID(), NOW);
        assertThat(old.eventDetails(SCHEDULE)).isEmpty();

        boolean changed = old.updateEventDetails(new EventDetails(null, DEC_19, null, "Escritório"), SCHEDULE);

        assertThat(changed).isTrue();
        assertThat(old.getDescription()).isNull();
        assertThat(old.getEventHasTime()).isFalse();
        assertThat(old.getEventStartsAt()).isEqualTo(Instant.parse("2026-12-19T03:00:00Z"));
        assertThat(old.getFinalizesAt()).isEqualTo(Instant.parse("2026-12-20T03:00:00Z"));
    }

    @Test
    void changingDateTimeOrLocationBumpsTheVersionAndRecalculates() {
        SecretGroup group = groupWith(PARTY);

        assertThat(group.updateEventDetails(
                        new EventDetails("Traga um prato", DEC_19.plusDays(1), LocalTime.of(19, 0), "Casa da Ana"),
                        SCHEDULE))
                .isTrue();
        assertThat(group.getDetailsVersion()).isEqualTo(2);
        assertThat(group.getEventStartsAt()).isEqualTo(Instant.parse("2026-12-20T22:00:00Z"));

        assertThat(group.updateEventDetails(
                        new EventDetails("Traga um prato", DEC_19.plusDays(1), null, "Casa da Ana"), SCHEDULE))
                .isTrue();
        assertThat(group.getDetailsVersion()).isEqualTo(3);
        assertThat(group.getEventHasTime()).isFalse();
        assertThat(group.getFinalizesAt()).isEqualTo(Instant.parse("2026-12-21T03:00:00Z"));

        assertThat(group.updateEventDetails(
                        new EventDetails("Traga um prato", DEC_19.plusDays(1), null, "Salão"), SCHEDULE))
                .isTrue();
        assertThat(group.getDetailsVersion()).isEqualTo(4);
        assertThat(group.getLocation()).isEqualTo("Salão");
    }

    @Test
    void changingOnlyTheDescriptionKeepsTheVersion() {
        SecretGroup group = groupWith(PARTY);

        boolean changed = group.updateEventDetails(
                new EventDetails("  Amigo da onça  ", DEC_19, LocalTime.of(19, 0), "Casa da Ana"), SCHEDULE);

        assertThat(changed).isFalse();
        assertThat(group.getDetailsVersion()).isEqualTo(1);
        assertThat(group.getDescription()).isEqualTo("Amigo da onça");

        group.updateEventDetails(new EventDetails("   ", DEC_19, LocalTime.of(19, 0), "Casa da Ana"), SCHEDULE);
        assertThat(group.getDescription()).isNull();
        assertThat(group.getDetailsVersion()).isEqualTo(1);
    }

    @Test
    void dateBeforeTodayOrTimeAlreadyPassedTodayIsRejected() {
        SecretGroup group = groupWith(PARTY);
        LocalDate today = LocalDate.of(2026, 11, 18);

        assertThatThrownBy(() -> group.updateEventDetails(
                        new EventDetails(null, today.minusDays(1), null, "Casa da Ana"), SCHEDULE))
                .isInstanceOf(UnprocessableException.class)
                .hasFieldOrPropertyWithValue("type", "event-date-in-past")
                .hasMessage("A data do evento precisa ser hoje ou no futuro.");
        // 09:00 em Brasília: 08:59 já passou, 09:01 não
        assertThatThrownBy(() -> group.updateEventDetails(
                        new EventDetails(null, today, LocalTime.of(8, 59), "Casa da Ana"), SCHEDULE))
                .isInstanceOf(UnprocessableException.class)
                .hasFieldOrPropertyWithValue("type", "event-time-in-past")
                .hasMessage("Esse horário já passou.");
        assertThat(group.getDetailsVersion()).isEqualTo(1);
        assertThat(group.eventDetails(SCHEDULE)).contains(PARTY);

        group.updateEventDetails(new EventDetails(null, today, LocalTime.of(9, 1), "Casa da Ana"), SCHEDULE);
        group.updateEventDetails(new EventDetails(null, today, null, "Casa da Ana"), SCHEDULE);
        assertThat(group.getDetailsVersion()).isEqualTo(3);
    }

    @Test
    void dateIsOnlyValidatedWhenDateOrTimeChange() {
        SecretGroup group = groupWith(PARTY);
        EventSchedule afterTheParty = scheduleAt(Instant.parse("2026-12-25T12:00:00Z"));

        group.updateEventDetails(new EventDetails("Foi ótimo", DEC_19, LocalTime.of(19, 0), "Salão"), afterTheParty);

        assertThat(group.getLocation()).isEqualTo("Salão");
        assertThat(group.getDetailsVersion()).isEqualTo(2);
        assertThatThrownBy(() -> group.updateEventDetails(
                        new EventDetails(null, DEC_19, LocalTime.of(20, 0), "Salão"), afterTheParty))
                .isInstanceOf(UnprocessableException.class);
    }

    @Test
    void eventDetailsCanBeEditedAfterTheDrawButNotOnceFinished() {
        SecretGroup group = groupWith(PARTY);
        group.markDrawn(NOW);

        group.updateEventDetails(new EventDetails(null, DEC_19, LocalTime.of(19, 0), "Salão"), SCHEDULE);
        assertThat(group.getLocation()).isEqualTo("Salão");

        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);
        assertThatThrownBy(() -> group.updateEventDetails(PARTY, SCHEDULE))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("type", "group-finished")
                .hasMessage("Este grupo foi finalizado.");
        assertThat(group.getLocation()).isEqualTo("Salão");
    }

    // ---- condições do sorteio (US2, research R16) ----

    @Test
    void drawBlockedReasonFollowsTheOrderOfResearchR16() {
        SecretGroup old = new SecretGroup("Antigo", "K7PQM2XA", UUID.randomUUID(), NOW);
        assertThat(old.drawBlockedReason(2, 1, SCHEDULE)).isEqualTo(DrawBlockedReason.EVENT_DETAILS_REQUIRED);

        SecretGroup group = groupWith(PARTY);
        EventSchedule afterTheParty = scheduleAt(Instant.parse("2026-12-25T12:00:00Z"));
        assertThat(group.drawBlockedReason(2, 1, afterTheParty)).isEqualTo(DrawBlockedReason.EVENT_DATE_PASSED);
        assertThat(group.drawBlockedReason(2, 1, SCHEDULE)).isEqualTo(DrawBlockedReason.NOT_ENOUGH_PARTICIPANTS);
        assertThat(group.drawBlockedReason(3, 1, SCHEDULE)).isEqualTo(DrawBlockedReason.PARTICIPANTS_PENDING);
        assertThat(group.drawBlockedReason(3, 0, SCHEDULE)).isNull();
    }

    @Test
    void eventDatePassedStartsAtTheEventTime() {
        SecretGroup group = groupWith(PARTY); // 19/12 às 19:00 de Brasília = 22:00 UTC

        assertThat(group.drawBlockedReason(3, 0, scheduleAt(Instant.parse("2026-12-19T21:59:00Z"))))
                .isNull();
        assertThat(group.drawBlockedReason(3, 0, scheduleAt(Instant.parse("2026-12-19T22:00:00Z"))))
                .isEqualTo(DrawBlockedReason.EVENT_DATE_PASSED);
    }

    @Test
    void withoutTimeTheWholeEventDayStillAllowsTheDraw() {
        SecretGroup group = groupWith(new EventDetails(null, DEC_19, null, "Casa da Ana"));

        // 23:59 de 19/12 em Brasília = 02:59 UTC de 20/12
        assertThat(group.drawBlockedReason(3, 0, scheduleAt(Instant.parse("2026-12-20T02:59:00Z"))))
                .isNull();
        assertThat(group.drawBlockedReason(3, 0, scheduleAt(Instant.parse("2026-12-20T03:00:00Z"))))
                .isEqualTo(DrawBlockedReason.EVENT_DATE_PASSED);
    }

    @Test
    void drawBlockedReasonsHaveTheContractCodes() {
        assertThat(DrawBlockedReason.EVENT_DETAILS_REQUIRED.code()).isEqualTo("event-details-required");
        assertThat(DrawBlockedReason.EVENT_DATE_PASSED.code()).isEqualTo("event-date-passed");
        assertThat(DrawBlockedReason.NOT_ENOUGH_PARTICIPANTS.code()).isEqualTo("not-enough-participants");
        assertThat(DrawBlockedReason.PARTICIPANTS_PENDING.code()).isEqualTo("participants-pending");
        assertThat(DrawBlockedReason.NOT_ENOUGH_PARTICIPANTS.message())
                .isEqualTo("São necessários pelo menos 3 participantes.");
        assertThat(DrawBlockedReason.pendingMessage(List.of("Carla"))).isEqualTo("Falta 1 confirmar: Carla.");
        assertThat(DrawBlockedReason.pendingMessage(List.of("Bruno", "Carla")))
                .isEqualTo("Faltam 2 confirmar: Bruno, Carla.");
    }

    @Test
    void confirmIsIdempotent() {
        GroupMember guest = new GroupMember(UUID.randomUUID(), UUID.randomUUID(), NOW);

        assertThat(guest.confirm(NOW.plusSeconds(60))).isTrue();
        assertThat(guest.getConfirmedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(guest.confirm(NOW.plusSeconds(120))).isFalse();
        assertThat(guest.getConfirmedAt()).isEqualTo(NOW.plusSeconds(60));
    }

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

    // ---- finalização (US3, research R17) ----

    /** 19/12/2026 às 19:00 em Brasília. */
    private static final Instant PARTY_STARTS = Instant.parse("2026-12-19T22:00:00Z");

    @Test
    void drawnGroupFinishesWhenTheEventStarts() {
        SecretGroup group = groupWith(PARTY);
        group.markDrawn(NOW);

        assertThat(group.finishIfDue(PARTY_STARTS.minusSeconds(1))).isFalse();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
        assertThat(group.getFinishedAt()).isNull();

        assertThat(group.finishIfDue(PARTY_STARTS)).isTrue();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        assertThat(group.getFinishedAt()).isEqualTo(PARTY_STARTS);
    }

    @Test
    void withoutTimeTheGroupFinishesAtTheEndOfTheDay() {
        SecretGroup group = groupWith(new EventDetails(null, DEC_19, null, "Casa da Ana"));
        group.markDrawn(NOW);
        Instant midnight = Instant.parse("2026-12-20T03:00:00Z"); // 20/12, 00:00 em Brasília

        assertThat(group.finishIfDue(PARTY_STARTS)).isFalse();
        assertThat(group.finishIfDue(midnight)).isTrue();
        assertThat(group.getFinishedAt()).isEqualTo(midnight);
    }

    @Test
    void openGroupIsNeverFinishedEvenAfterTheEvent() {
        SecretGroup group = groupWith(PARTY);

        assertThat(group.finishIfDue(PARTY_STARTS.plusSeconds(86_400))).isFalse();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
        assertThat(group.getFinishedAt()).isNull();
    }

    @Test
    void finishingAgainKeepsTheFirstMoment() {
        SecretGroup group = groupWith(PARTY);
        group.markDrawn(NOW);
        group.finishIfDue(PARTY_STARTS);

        assertThat(group.finishIfDue(PARTY_STARTS.plusSeconds(3600))).isFalse();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        assertThat(group.getFinishedAt()).isEqualTo(PARTY_STARTS);
    }

    @Test
    void oldDrawnGroupWithoutAnEventDateIsNeverFinished() {
        SecretGroup group = new SecretGroup("Antigo", "K7PQM2XA", UUID.randomUUID(), NOW);
        group.markDrawn(NOW);

        assertThat(group.finishIfDue(NOW.plusSeconds(365L * 86_400))).isFalse();
        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
    }

    // ---- sorteio desfeito (US4, research R18) ----

    @Test
    void undoingTheDrawReopensTheGroupUntilTheNextDraw() {
        SecretGroup group = groupWith(PARTY);
        group.markDrawn(NOW);
        Instant approved = NOW.plusSeconds(3600);

        group.undoDraw(approved);

        assertThat(group.getStatus()).isEqualTo(GroupStatus.OPEN);
        assertThat(group.getDrawnAt()).isNull();
        assertThat(group.getDrawUndoneAt()).isEqualTo(approved);

        Instant redrawn = approved.plusSeconds(60);
        group.markDrawn(redrawn);
        assertThat(group.getStatus()).isEqualTo(GroupStatus.DRAWN);
        assertThat(group.getDrawnAt()).isEqualTo(redrawn);
        assertThat(group.getDrawUndoneAt()).isNull();
    }

    @Test
    void onlyADrawnGroupCanHaveTheDrawUndone() {
        SecretGroup group = groupWith(PARTY);
        assertThatIllegalStateException().isThrownBy(() -> group.undoDraw(NOW));

        group.markDrawn(NOW);
        group.finishIfDue(PARTY_STARTS);
        assertThatIllegalStateException().isThrownBy(() -> group.undoDraw(NOW));
        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
    }

    // ---- lista de desejos (feature 003) ----

    @Test
    void memberRemembersWhenTheWishlistLastChanged() {
        GroupMember member = new GroupMember(UUID.randomUUID(), UUID.randomUUID(), NOW);
        assertThat(member.getWishlistUpdatedAt()).isNull();

        member.touchWishlist(NOW.plusSeconds(60));
        member.touchWishlist(NOW.plusSeconds(120));

        assertThat(member.getWishlistUpdatedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void newGroupHasNoGiftValue() {
        assertThat(groupWith(PARTY).getGiftValue()).isNull();
    }
}
