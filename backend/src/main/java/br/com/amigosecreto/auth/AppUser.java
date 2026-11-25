package br.com.amigosecreto.auth;

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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Conta de usuário ou dependente (tabela {@code app_user}, migrações V1 e V8). O dependente (feature 004) não
 * tem e-mail nem senha e nunca entra no sistema: tudo o que é dele passa pelo responsável (research R33).
 */
@Entity
@Table(name = "app_user")
public class AppUser implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** Nulo só para dependente. */
    @Column(name = "email", length = 254)
    private String email;

    /** Nulo só para dependente. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private AppUserKind kind;

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
        this.kind = AppUserKind.ACCOUNT;
    }

    /** Dependente: só o nome, sem e-mail nem senha (FR-002). */
    public static AppUser dependent(String name, Instant createdAt) {
        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.name = Objects.requireNonNull(name, "name").strip();
        user.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        user.kind = AppUserKind.DEPENDENT;
        return user;
    }

    /** Novo nome de um dependente (FR-006). Contas não mudam de nome por aqui. */
    public void rename(String name) {
        if (!isDependent()) {
            throw new IllegalStateException("Só dependentes são renomeados pelo responsável");
        }
        this.name = Objects.requireNonNull(name, "name").strip();
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

    public AppUserKind getKind() {
        return kind;
    }

    public boolean isDependent() {
        return kind == AppUserKind.DEPENDENT;
    }

    /** Sem {@code passwordHash}. */
    @Override
    public String toString() {
        if (isDependent()) {
            return "AppUser[id=" + id + ", name=" + name + ", dependent]";
        }
        return "AppUser[id=" + id + ", name=" + name + ", email=" + email + "]";
    }
}
