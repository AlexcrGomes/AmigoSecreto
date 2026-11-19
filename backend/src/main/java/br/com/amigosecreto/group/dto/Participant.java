package br.com.amigosecreto.group.dto;

import java.util.UUID;

/**
 * Schema {@code Participant}: só o nome, o papel e se já confirmou (FR-012 da 002) — nunca quem tirou quem
 * (Constituição I).
 */
public record Participant(UUID userId, String name, boolean organizer, boolean confirmed) {}
