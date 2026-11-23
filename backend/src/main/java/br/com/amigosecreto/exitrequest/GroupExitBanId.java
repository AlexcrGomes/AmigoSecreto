package br.com.amigosecreto.exitrequest;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/** Chave de {@code group_exit_ban}: (grupo, usuário). */
@Embeddable
public record GroupExitBanId(
        @Column(name = "group_id", nullable = false) UUID groupId,
        @Column(name = "user_id", nullable = false) UUID userId)
        implements Serializable {}
