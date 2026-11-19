package br.com.amigosecreto.group.dto;

import br.com.amigosecreto.group.EventDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Schema {@code EventDetailsRequest}: descrição opcional, data, horário opcional ({@code HH:mm}) e local, em
 * horário de Brasília (FR-001 a FR-003). Se a data é hoje ou futura quem decide é o domínio (research R14).
 */
public record EventDetailsRequest(
        @Size(max = 500, message = DESCRIPTION_MESSAGE) String description,
        @NotNull(message = DATE_MESSAGE) LocalDate eventDate,

        @Pattern(regexp = TIME_PATTERN, message = TIME_MESSAGE)
        String eventTime,

        @NotBlank(message = LOCATION_MESSAGE) @Size(max = 120, message = LOCATION_MESSAGE)
        String location) {

    public static final String DESCRIPTION_MESSAGE = "A descrição pode ter até 500 caracteres.";
    public static final String DATE_MESSAGE = "Informe a data do evento.";
    public static final String TIME_MESSAGE = "Informe o horário no formato HH:mm.";
    public static final String LOCATION_MESSAGE = "Informe o local do evento (até 120 caracteres).";
    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    public EventDetailsRequest {
        description = blankToNull(description);
        eventTime = blankToNull(eventTime);
        location = location == null ? null : location.strip();
    }

    /** Texto vazio vira {@code null}; o resto perde os espaços das pontas. */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Os detalhes para o domínio (já validados pelo Bean Validation). */
    public EventDetails toEventDetails() {
        return toEventDetails(description, eventDate, eventTime, location);
    }

    static EventDetails toEventDetails(String description, LocalDate eventDate, String eventTime, String location) {
        return new EventDetails(
                description, eventDate, eventTime == null ? null : LocalTime.parse(eventTime), location);
    }
}
