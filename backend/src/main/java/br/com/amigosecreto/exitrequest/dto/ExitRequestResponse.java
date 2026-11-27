package br.com.amigosecreto.exitrequest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Schema {@code ExitRequest}: quem pediu para sair e quando. Nunca o amigo secreto de ninguém (FR-032).
 * {@code guardianName} é o responsável quando quem sai é um dependente (feature 004); senão nulo.
 */
public record ExitRequestResponse(UUID id, UUID userId, String name, Instant requestedAt, String guardianName) {

    /** Pedido de um participante com conta. */
    public ExitRequestResponse(UUID id, UUID userId, String name, Instant requestedAt) {
        this(id, userId, name, requestedAt, null);
    }
}
