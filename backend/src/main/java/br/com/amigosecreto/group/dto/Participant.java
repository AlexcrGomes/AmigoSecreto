package br.com.amigosecreto.group.dto;

import java.util.UUID;

/** Schema {@code Participant}: só o nome e o papel — nunca quem tirou quem (Constituição I). */
public record Participant(UUID userId, String name, boolean organizer) {}
