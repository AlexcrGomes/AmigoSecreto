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

/**
 * Participação de um usuário em um grupo (tabela {@code group_member}). {@code confirmedAt} nulo = pendente
 * (FR-008): quem entra pelo código começa pendente; o organizador nasce confirmado.
 */
@Entity
@Table(name = "group_member")
public class GroupMember implements Persistable<GroupMemberId> {

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Transient
    private boolean isNew = true;

    protected GroupMember() {}

    /** Participante que entrou pelo código: pendente até confirmar. */
    public GroupMember(UUID groupId, UUID userId, Instant joinedAt) {
        this.id = new GroupMemberId(Objects.requireNonNull(groupId), Objects.requireNonNull(userId));
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
    }

    /** O organizador, que cria o grupo já confirmado e nunca volta a pendente. */
    public static GroupMember organizer(UUID groupId, UUID userId, Instant now) {
        GroupMember member = new GroupMember(groupId, userId, now);
        member.confirmedAt = member.joinedAt;
        return member;
    }

    /**
     * Confirma a participação (FR-009). Idempotente: quem já confirmou continua com a data da primeira
     * confirmação (FR-010).
     *
     * @return se a situação mudou
     */
    public boolean confirm(Instant now) {
        if (confirmedAt != null) {
            return false;
        }
        this.confirmedAt = Objects.requireNonNull(now, "now");
        return true;
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

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
