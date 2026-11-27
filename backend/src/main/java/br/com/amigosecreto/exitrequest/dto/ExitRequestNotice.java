package br.com.amigosecreto.exitrequest.dto;

import br.com.amigosecreto.exitrequest.ExitRequestStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Schema {@code ExitRequestNotice}: decisão ainda não vista por quem pediu (R20). {@code subjectName} é o nome do
 * dependente quando o pedido foi de um dependente do usuário (feature 004, research R37); nulo quando foi o
 * próprio usuário que pediu para sair.
 */
public record ExitRequestNotice(
        UUID id, UUID groupId, String groupName, ExitRequestStatus status, Instant decidedAt, String subjectName) {

    /** Aviso de um pedido do próprio usuário. */
    public ExitRequestNotice(UUID id, UUID groupId, String groupName, ExitRequestStatus status, Instant decidedAt) {
        this(id, groupId, groupName, status, decidedAt, null);
    }
}
