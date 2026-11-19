package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Regras de data do evento em horário de Brasília, gravadas como instantes UTC (research R13, R14, R16). */
class EventScheduleTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DEC_20 = LocalDate.of(2026, 12, 20);

    private static EventSchedule at(String instant) {
        return new EventSchedule(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), BRASILIA);
    }

    private final EventSchedule schedule = at("2026-11-19T15:00:00Z");

    @Test
    void dateWithoutTimeStartsAtMidnightInBrasiliaAndFinalizesAtTheEndOfTheDay() {
        assertThat(schedule.startsAt(DEC_20, null)).isEqualTo(Instant.parse("2026-12-20T03:00:00Z"));
        assertThat(schedule.finalizesAt(DEC_20, null)).isEqualTo(Instant.parse("2026-12-21T03:00:00Z"));
    }

    @Test
    void dateWithTimeStartsAndFinalizesAtTheEventTime() {
        LocalTime seven = LocalTime.of(19, 0);

        assertThat(schedule.startsAt(DEC_20, seven)).isEqualTo(Instant.parse("2026-12-20T22:00:00Z"));
        assertThat(schedule.finalizesAt(DEC_20, seven)).isEqualTo(Instant.parse("2026-12-20T22:00:00Z"));
    }

    @Test
    void todayIsTheDateInBrasiliaNotInUtc() {
        // 01:30 UTC do dia 20 ainda é 22:30 do dia 19 em Brasília
        EventSchedule lateNight = at("2026-12-20T01:30:00Z");

        assertThat(lateNight.today()).isEqualTo(LocalDate.of(2026, 12, 19));
        assertThat(lateNight.isPast(LocalDate.of(2026, 12, 19))).isFalse();
        assertThat(lateNight.isPast(LocalDate.of(2026, 12, 18))).isTrue();
        assertThat(lateNight.isPast(DEC_20)).isFalse();
    }

    @Test
    void onlyDatesBeforeTodayArePast() {
        assertThat(schedule.today()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(schedule.isPast(LocalDate.of(2026, 11, 18))).isTrue();
        assertThat(schedule.isPast(LocalDate.of(2026, 11, 19))).isFalse();
        assertThat(schedule.isPast(LocalDate.of(2026, 11, 20))).isFalse();
    }

    @Test
    void timeIsPastWhenTodaysEventTimeHasAlreadyArrived() {
        // 15:00 UTC = 12:00 em Brasília
        LocalDate today = LocalDate.of(2026, 11, 19);

        assertThat(schedule.isTimePast(today, LocalTime.of(11, 59))).isTrue();
        assertThat(schedule.isTimePast(today, LocalTime.of(12, 0))).isTrue();
        assertThat(schedule.isTimePast(today, LocalTime.of(12, 1))).isFalse();
        assertThat(schedule.isTimePast(today.plusDays(1), LocalTime.of(8, 0))).isFalse();
    }

    @Test
    void eventHasHappenedFromTheEventTimeOrFromTheEndOfTheDay() {
        Instant withTime = schedule.finalizesAt(DEC_20, LocalTime.of(19, 0));
        Instant withoutTime = schedule.finalizesAt(DEC_20, null);

        assertThat(at("2026-12-20T21:59:59Z").hasHappened(withTime)).isFalse();
        assertThat(at("2026-12-20T22:00:00Z").hasHappened(withTime)).isTrue();

        // Sem horário, o dia inteiro ainda conta como "por vir"
        assertThat(at("2026-12-21T02:59:59Z").hasHappened(withoutTime)).isFalse();
        assertThat(at("2026-12-21T03:00:00Z").hasHappened(withoutTime)).isTrue();
    }

    @Test
    void convertsTheStoredInstantBackToDateAndTimeInBrasilia() {
        Instant withTime = schedule.startsAt(DEC_20, LocalTime.of(19, 0));
        Instant withoutTime = schedule.startsAt(DEC_20, null);

        assertThat(schedule.toLocalDate(withTime)).isEqualTo(DEC_20);
        assertThat(schedule.toLocalTime(withTime, true)).isEqualTo(LocalTime.of(19, 0));
        assertThat(schedule.toLocalDate(withoutTime)).isEqualTo(DEC_20);
        assertThat(schedule.toLocalTime(withoutTime, false)).isNull();
    }

    @Test
    void lateEveningEventKeepsItsDateInBrasilia() {
        // 23:30 em Brasília já é o dia seguinte em UTC
        Instant late = schedule.startsAt(DEC_20, LocalTime.of(23, 30));

        assertThat(late).isEqualTo(Instant.parse("2026-12-21T02:30:00Z"));
        assertThat(schedule.toLocalDate(late)).isEqualTo(DEC_20);
        assertThat(schedule.toLocalTime(late, true)).isEqualTo(LocalTime.of(23, 30));
    }
}
