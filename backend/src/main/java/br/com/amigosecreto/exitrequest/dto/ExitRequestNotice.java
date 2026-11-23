package br.com.amigosecreto.exitrequest.dto;

import br.com.amigosecreto.exitrequest.ExitRequestStatus;
import java.time.Instant;
import java.util.UUID;

/** Schema {@code ExitRequestNotice}: decisão ainda não vista pelo solicitante (R20). */
public record ExitRequestNotice(UUID id, UUID groupId, String groupName, ExitRequestStatus status, Instant decidedAt) {}
