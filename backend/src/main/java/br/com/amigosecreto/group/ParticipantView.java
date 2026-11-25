package br.com.amigosecreto.group;

import java.time.Instant;
import java.util.UUID;

/**
 * Participante de um grupo, como lido do banco: ID, nome, quando confirmou ({@code null} = pendente) e, se for
 * dependente, o responsável (feature 004). Nunca pares.
 */
public record ParticipantView(UUID userId, String name, Instant confirmedAt, UUID guardianId, String guardianName) {

    /** Participante com conta (sem responsável). */
    public ParticipantView(UUID userId, String name, Instant confirmedAt) {
        this(userId, name, confirmedAt, null, null);
    }

    /** Dependente de outro participante (feature 004). */
    public boolean dependent() {
        return guardianId != null;
    }

    public boolean confirmed() {
        return confirmedAt != null;
    }
}
