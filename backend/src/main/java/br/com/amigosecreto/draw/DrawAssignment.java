package br.com.amigosecreto.draw;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Um par do sorteio: quem tirou, e quem foi tirado SÓ cifrado (tabela {@code draw_assignment}, V4). */
@Entity
@Table(name = "draw_assignment")
public class DrawAssignment implements Persistable<DrawAssignmentId> {

    @EmbeddedId
    private DrawAssignmentId id;

    @Column(name = "receiver_enc", nullable = false)
    private byte[] receiverEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected DrawAssignment() {}

    public DrawAssignment(UUID groupId, UUID giverId, byte[] receiverEnc, Instant createdAt) {
        this.id = new DrawAssignmentId(Objects.requireNonNull(groupId), Objects.requireNonNull(giverId));
        this.receiverEnc = Objects.requireNonNull(receiverEnc, "receiverEnc").clone();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public DrawAssignmentId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public byte[] getReceiverEnc() {
        return receiverEnc.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Sem {@code receiverEnc}. */
    @Override
    public String toString() {
        return "DrawAssignment[groupId=" + id.groupId() + "]";
    }
}
