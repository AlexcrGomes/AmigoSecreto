package br.com.amigosecreto.draw.dto;

import br.com.amigosecreto.group.GroupStatus;
import java.time.Instant;
import java.util.UUID;

/** Schema {@code DrawResult}: confirma o sorteio SEM nenhum par, nem o do organizador (Constituição I). */
public record DrawResult(UUID groupId, GroupStatus status, Instant drawnAt) {}
