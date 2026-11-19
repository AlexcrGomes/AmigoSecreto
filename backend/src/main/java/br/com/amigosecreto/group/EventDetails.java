package br.com.amigosecreto.group;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Detalhes do evento como o usuário os vê: data e horário em horário de Brasília (o horário é opcional),
 * local e uma descrição opcional (FR-001, FR-002). Texto simples, nunca HTML (FR-007).
 *
 * <p>A validação de formulário fica nos DTOs (Bean Validation); aqui ficam só as garantias do domínio.
 */
public record EventDetails(String description, LocalDate eventDate, LocalTime eventTime, String location) {

    public static final int MAX_DESCRIPTION = 500;
    public static final int MAX_LOCATION = 120;

    public EventDetails {
        description = description == null || description.isBlank() ? null : description.strip();
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("A descrição pode ter até " + MAX_DESCRIPTION + " caracteres");
        }
        Objects.requireNonNull(eventDate, "eventDate");
        location = Objects.requireNonNull(location, "location").strip();
        if (location.isEmpty() || location.length() > MAX_LOCATION) {
            throw new IllegalArgumentException("O local precisa ter de 1 a " + MAX_LOCATION + " caracteres");
        }
    }

    /** Data, horário e local iguais. A descrição não conta: alterá-la não reabre as confirmações (FR-015). */
    public boolean sameSchedule(EventDetails other) {
        return other != null
                && eventDate.equals(other.eventDate)
                && Objects.equals(eventTime, other.eventTime)
                && location.equals(other.location);
    }
}
