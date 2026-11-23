package br.com.amigosecreto.exitrequest;

import br.com.amigosecreto.common.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Pedido de saída depois do sorteio (tabela {@code exit_request}, data-model.md §3). Não guarda nada do
 * sorteio (FR-032). {@code decisionSeenAt} nulo numa decisão = o solicitante ainda não viu o aviso (R20).
 */
@Entity
@Table(name = "exit_request")
public class ExitRequest implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ExitRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_seen_at")
    private Instant decisionSeenAt;

    @Transient
    private boolean isNew = true;

    protected ExitRequest() {}

    public ExitRequest(UUID groupId, UUID userId, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.status = ExitRequestStatus.PENDING;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    }

    /** O organizador aprovou: o solicitante vê o aviso na tela inicial (FR-026). */
    public void approve(Instant now) {
        decide(ExitRequestStatus.APPROVED, now, true);
    }

    /** O organizador recusou: o solicitante vê o aviso na tela do grupo (FR-026). */
    public void reject(Instant now) {
        decide(ExitRequestStatus.REJECTED, now, true);
    }

    /**
     * Cancelado. Pelo próprio solicitante, sem aviso ({@code notify = false}: ele já sabe); automaticamente
     * (outra saída aprovada ou grupo finalizado), com aviso.
     */
    public void cancel(Instant now, boolean notify) {
        decide(ExitRequestStatus.CANCELLED, now, notify);
    }

    /** O solicitante viu o aviso da decisão. Idempotente: fica a primeira vez. */
    public void markSeen(Instant now) {
        if (status != ExitRequestStatus.PENDING && decisionSeenAt == null) {
            this.decisionSeenAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean isPending() {
        return status == ExitRequestStatus.PENDING;
    }

    private void decide(ExitRequestStatus decision, Instant now, boolean notify) {
        if (status != ExitRequestStatus.PENDING) {
            throw new ConflictException("exit-request-not-pending", "Este pedido de saída já foi decidido.");
        }
        this.status = decision;
        this.decidedAt = Objects.requireNonNull(now, "now");
        this.decisionSeenAt = notify ? null : now;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ExitRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getDecisionSeenAt() {
        return decisionSeenAt;
    }

    /** Só IDs e a situação: nada que identifique o sorteio. */
    @Override
    public String toString() {
        return "ExitRequest[id=" + id + ", groupId=" + groupId + ", status=" + status + "]";
    }
}
