package br.com.amigosecreto.group.dto;

import br.com.amigosecreto.group.EventDetails;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Schema {@code EventDetails}: data {@code AAAA-MM-DD} e horário {@code HH:mm} (ou nulo), em Brasília, e o valor do
 * presente em reais (ou nulo, feature 003).
 */
public record EventDetailsResponse(
        String description, LocalDate eventDate, String eventTime, String location, Integer giftValue) {

    /** Sem valor do presente. */
    public EventDetailsResponse(String description, LocalDate eventDate, String eventTime, String location) {
        this(description, eventDate, eventTime, location, null);
    }

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static EventDetailsResponse from(EventDetails details) {
        return from(details, null);
    }

    public static EventDetailsResponse from(EventDetails details, Integer giftValue) {
        return new EventDetailsResponse(
                details.description(),
                details.eventDate(),
                formatTime(details.eventTime()),
                details.location(),
                giftValue);
    }

    /** {@code HH:mm}, ou {@code null} sem horário. */
    public static String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME);
    }
}
