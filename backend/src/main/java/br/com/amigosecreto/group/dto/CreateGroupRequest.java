package br.com.amigosecreto.group.dto;

import static br.com.amigosecreto.group.dto.EventDetailsRequest.DATE_MESSAGE;
import static br.com.amigosecreto.group.dto.EventDetailsRequest.DESCRIPTION_MESSAGE;
import static br.com.amigosecreto.group.dto.EventDetailsRequest.LOCATION_MESSAGE;
import static br.com.amigosecreto.group.dto.EventDetailsRequest.TIME_MESSAGE;
import static br.com.amigosecreto.group.dto.EventDetailsRequest.TIME_PATTERN;

import br.com.amigosecreto.group.EventDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Schema {@code CreateGroupRequest}: nome de 1 a 60 caracteres após trim (FR-008 da 001) mais os detalhes
 * do evento, com as mesmas regras de {@link EventDetailsRequest} (FR-001, FR-002 da 002).
 */
public record CreateGroupRequest(
        @NotBlank(message = NAME_MESSAGE) @Size(max = 60, message = NAME_MESSAGE)
        String name,

        @Size(max = 500, message = DESCRIPTION_MESSAGE) String description,
        @NotNull(message = DATE_MESSAGE) LocalDate eventDate,

        @Pattern(regexp = TIME_PATTERN, message = TIME_MESSAGE)
        String eventTime,

        @NotBlank(message = LOCATION_MESSAGE) @Size(max = 120, message = LOCATION_MESSAGE)
        String location) {

    public static final String NAME_MESSAGE = "Dê um nome ao grupo (até 60 caracteres).";

    public CreateGroupRequest {
        name = name == null ? null : name.strip();
        description = EventDetailsRequest.blankToNull(description);
        eventTime = EventDetailsRequest.blankToNull(eventTime);
        location = location == null ? null : location.strip();
    }

    /** Os detalhes do evento para o domínio (já validados pelo Bean Validation). */
    public EventDetails eventDetails() {
        return EventDetailsRequest.toEventDetails(description, eventDate, eventTime, location);
    }
}
