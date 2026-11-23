package br.com.amigosecreto.exitrequest.dto;

import java.time.Instant;
import java.util.UUID;

/** Schema {@code ExitRequest}: quem pediu para sair e quando. Nunca o amigo secreto de ninguém (FR-032). */
public record ExitRequestResponse(UUID id, UUID userId, String name, Instant requestedAt) {}
