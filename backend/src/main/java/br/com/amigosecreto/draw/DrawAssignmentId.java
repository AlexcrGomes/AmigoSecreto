package br.com.amigosecreto.draw;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/** Chave de {@code draw_assignment}: (grupo, quem tirou). Quem foi tirado nunca faz parte da chave. */
@Embeddable
public record DrawAssignmentId(
        @Column(name = "group_id", nullable = false) UUID groupId,
        @Column(name = "giver_id", nullable = false) UUID giverId)
        implements Serializable {}
