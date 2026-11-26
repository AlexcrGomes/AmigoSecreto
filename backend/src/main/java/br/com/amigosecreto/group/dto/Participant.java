package br.com.amigosecreto.group.dto;

import java.util.UUID;

/**
 * Schema {@code Participant}: só o nome, o papel, se já confirmou (FR-012 da 002) e, para dependente, o nome do
 * responsável (FR-005 da 004) — nunca quem tirou quem (Constituição I).
 */
public record Participant(
        UUID userId, String name, boolean organizer, boolean confirmed, boolean dependent, String guardianName) {

    /** Participante com conta. */
    public Participant(UUID userId, String name, boolean organizer, boolean confirmed) {
        this(userId, name, organizer, confirmed, false, null);
    }
}
