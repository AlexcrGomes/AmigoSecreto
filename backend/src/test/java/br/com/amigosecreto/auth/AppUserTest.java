package br.com.amigosecreto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void anAccountHasEmailAndPassword() {
        AppUser user = new AppUser("Ana", "ana@x.com", "h", NOW);

        assertThat(user.getKind()).isEqualTo(AppUserKind.ACCOUNT);
        assertThat(user.isDependent()).isFalse();
    }

    @Test
    void aDependentHasOnlyAName() {
        AppUser lucas = AppUser.dependent("  Lucas ", NOW);

        assertThat(lucas.getKind()).isEqualTo(AppUserKind.DEPENDENT);
        assertThat(lucas.isDependent()).isTrue();
        assertThat(lucas.getName()).isEqualTo("Lucas");
        assertThat(lucas.getEmail()).isNull();
        assertThat(lucas.getPasswordHash()).isNull();
        assertThat(lucas.getCreatedAt()).isEqualTo(NOW);
        assertThat(lucas.getId()).isNotNull();
        assertThat(lucas.isNew()).isTrue();
    }

    @Test
    void onlyADependentCanBeRenamed() {
        AppUser lucas = AppUser.dependent("Lucas", NOW);

        lucas.rename("  Lucas M. ");

        assertThat(lucas.getName()).isEqualTo("Lucas M.");
        AppUser ana = new AppUser("Ana", "ana@x.com", "h", NOW);
        assertThatThrownBy(() -> ana.rename("Outra")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDependentToStringHasOnlyIdAndName() {
        AppUser lucas = AppUser.dependent("Lucas", NOW);

        assertThat(lucas.toString())
                .contains("Lucas")
                .contains(lucas.getId().toString())
                .doesNotContain("null");
    }
}
