package br.com.amigosecreto.group.dto;

import br.com.amigosecreto.group.EventDetails;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Schema {@code EventDetails}: data {@code AAAA-MM-DD} e horário {@code HH:mm} (ou nulo), em Brasília. */
public record EventDetailsResponse(String description, LocalDate eventDate, String eventTime, String location) {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static EventDetailsResponse from(EventDetails details) {
        return new EventDetailsResponse(
                details.description(), details.eventDate(), formatTime(details.eventTime()), details.location());
    }

    /** {@code HH:mm}, ou {@code null} sem horário. */
    public static String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME);
    }
}
