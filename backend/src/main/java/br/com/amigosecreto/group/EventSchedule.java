package br.com.amigosecreto.group;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Converte a data e o horário do evento, informados em horário de Brasília, para os instantes UTC gravados
 * no banco, e responde às perguntas de calendário das regras (research R13, R14, R16). É a única classe que
 * conhece o fuso do evento.
 *
 * <ul>
 *   <li>{@code startsAt}: data e horário; sem horário, a meia-noite do dia.
 *   <li>{@code finalizesAt}: o próprio horário do evento; sem horário, a meia-noite do dia seguinte (o dia
 *       inteiro ainda conta como "por vir").
 * </ul>
 */
@Component
public final class EventSchedule {

    private final Clock clock;
    private final ZoneId zone;

    public EventSchedule(Clock clock, ZoneId eventZone) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(eventZone, "eventZone");
    }

    /** A data de hoje no fuso do evento. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Início do evento; {@code time} nulo = meia-noite do dia. */
    public Instant startsAt(LocalDate date, LocalTime time) {
        Objects.requireNonNull(date, "date");
        return date.atTime(time == null ? LocalTime.MIDNIGHT : time)
                .atZone(zone)
                .toInstant();
    }

    /** Quando o grupo deve finalizar: o horário do evento ou, sem horário, o fim do dia. */
    public Instant finalizesAt(LocalDate date, LocalTime time) {
        Objects.requireNonNull(date, "date");
        return time == null ? date.plusDays(1).atStartOfDay(zone).toInstant() : startsAt(date, time);
    }

    /** A data é anterior a hoje. */
    public boolean isPast(LocalDate date) {
        return date.isBefore(today());
    }

    /** O evento já aconteceu: agora ≥ momento de finalizar. */
    public boolean hasHappened(Instant finalizesAt) {
        return !clock.instant().isBefore(finalizesAt);
    }

    /** A data e o horário informados já chegaram (o minuto atual conta como passado). */
    public boolean isTimePast(LocalDate date, LocalTime time) {
        return !startsAt(date, Objects.requireNonNull(time, "time")).isAfter(clock.instant());
    }

    /** A data do evento, no fuso do evento, a partir do instante gravado. */
    public LocalDate toLocalDate(Instant startsAt) {
        return startsAt.atZone(zone).toLocalDate();
    }

    /** O horário do evento, ou {@code null} quando não foi informado. */
    public LocalTime toLocalTime(Instant startsAt, boolean hasTime) {
        return hasTime ? startsAt.atZone(zone).toLocalTime() : null;
    }
}
