package br.com.amigosecreto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    @Test
    void normalizesNameAndEmail() {
        AppUser user = new AppUser("  Ana Souza ", "  Ana.Souza@Example.COM ", "{bcrypt}hash", NOW);

        assertThat(user.getName()).isEqualTo("Ana Souza");
        assertThat(user.getEmail()).isEqualTo("ana.souza@example.com");
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
        assertThat(user.getId()).isNotNull();
        assertThat(user.isNew()).isTrue();
    }

    @Test
    void generatesDifferentIds() {
        AppUser a = new AppUser("A", "a@x.com", "h", NOW);
        AppUser b = new AppUser("B", "b@x.com", "h", NOW);

        assertThat(a.getId()).isNotEqualTo(b.getId());
    }

    @Test
    void toStringNeverExposesPasswordHash() {
        AppUser user = new AppUser("Ana", "ana@x.com", "{bcrypt}$2a$12$segredo", NOW);

        assertThat(user.toString())
                .contains("ana@x.com")
                .doesNotContain("segredo")
                .doesNotContain("bcrypt");
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}$2a$12$segredo");
    }

    @Test
    void markNotNewAfterLoadOrPersist() {
        AppUser user = new AppUser("Ana", "ana@x.com", "h", NOW);

        user.markNotNew();

        assertThat(user.isNew()).isFalse();
    }
}
