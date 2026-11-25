package br.com.amigosecreto.wishlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Item da lista de desejos de um participante em um grupo (tabela {@code wish_item}, data-model.md da 003). Não
 * guarda nenhuma referência ao sorteio: quem pode ver o item depende só do par do leitor (research R27).
 */
@Entity
@Table(name = "wish_item")
public class WishItem implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "description", nullable = false, length = 120)
    private String description;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected WishItem() {}

    public WishItem(UUID groupId, UUID userId, String description, String url, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        update(description, url);
    }

    /** Descrição sem espaços nas pontas; link vazio vira {@code null}. */
    public void update(String description, String url) {
        this.description = Objects.requireNonNull(description, "description").strip();
        this.url = url == null || url.isBlank() ? null : url.strip();
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

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Só IDs: nem o dono nem o texto do item. */
    @Override
    public String toString() {
        return "WishItem[id=" + id + ", groupId=" + groupId + "]";
    }
}
