package br.com.amigosecreto.group;

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

/** Participação de um usuário em um grupo (tabela {@code group_member}). */
@Entity
@Table(name = "group_member")
public class GroupMember implements Persistable<GroupMemberId> {

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Transient
    private boolean isNew = true;

    protected GroupMember() {}

    public GroupMember(UUID groupId, UUID userId, Instant joinedAt) {
        this.id = new GroupMemberId(Objects.requireNonNull(groupId), Objects.requireNonNull(userId));
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public GroupMemberId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
