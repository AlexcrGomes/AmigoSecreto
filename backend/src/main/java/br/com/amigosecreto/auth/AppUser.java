package br.com.amigosecreto.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** Conta de usuário (tabela {@code app_user}, migração V1). */
@Entity
@Table(name = "app_user")
public class AppUser implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** O ID é gerado pela aplicação: sem isso o Spring Data faria um SELECT antes de cada INSERT. */
    @Transient
    private boolean isNew = true;

    protected AppUser() {}

    public AppUser(String name, String email, String passwordHash, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.name = Objects.requireNonNull(name, "name").strip();
        this.email = normalizeEmail(Objects.requireNonNull(email, "email"));
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** E-mail sempre minúsculo e sem espaços nas pontas: use antes de qualquer busca por e-mail. */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Sem {@code passwordHash}. */
    @Override
    public String toString() {
        return "AppUser[id=" + id + ", name=" + name + ", email=" + email + "]";
    }
}
