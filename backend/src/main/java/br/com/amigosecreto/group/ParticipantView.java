package br.com.amigosecreto.group;

import java.time.Instant;
import java.util.UUID;

/**
 * Participante de um grupo, como lido do banco: ID, nome e quando confirmou ({@code null} = pendente).
 * Nunca pares.
 */
public record ParticipantView(UUID userId, String name, Instant confirmedAt) {

    public boolean confirmed() {
        return confirmedAt != null;
    }
}
