package br.com.amigosecreto.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** Grupo de amigo secreto (tabela {@code secret_group}, migração V3). */
@Entity
@Table(name = "secret_group")
public class SecretGroup implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Código sem hífen (8 caracteres do alfabeto de R5). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", nullable = false, length = 8)
    private String code;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private GroupStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "drawn_at")
    private Instant drawnAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Transient
    private boolean isNew = true;

    protected SecretGroup() {}

    public SecretGroup(String name, String code, UUID organizerId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.name = Objects.requireNonNull(name, "name").strip();
        this.code = Objects.requireNonNull(code, "code");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId");
        this.status = GroupStatus.OPEN;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Única transição permitida: OPEN → DRAWN. */
    public void markDrawn(Instant when) {
        if (status != GroupStatus.OPEN) {
            throw new IllegalStateException("O grupo já foi sorteado");
        }
        this.status = GroupStatus.DRAWN;
        this.drawnAt = Objects.requireNonNull(when, "when");
    }

    public boolean isOrganizer(UUID userId) {
        return organizerId.equals(userId);
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

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDrawnAt() {
        return drawnAt;
    }
}
