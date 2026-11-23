package br.com.amigosecreto.exitrequest;

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

/** Quem saiu do grupo depois do sorteio e não pode entrar de novo (tabela {@code group_exit_ban}, FR-030). */
@Entity
@Table(name = "group_exit_ban")
public class GroupExitBan implements Persistable<GroupExitBanId> {

    @EmbeddedId
    private GroupExitBanId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected GroupExitBan() {}

    public GroupExitBan(UUID groupId, UUID userId, Instant createdAt) {
        this.id = new GroupExitBanId(Objects.requireNonNull(groupId), Objects.requireNonNull(userId));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public GroupExitBanId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
