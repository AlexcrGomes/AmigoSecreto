package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Base da aplicação com banco real: migrações, segurança e persistência do usuário. */
class FoundationIT extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Test
    void migrationsCreateUsersAndSessionTables() {
        assertThat(jdbc.queryForList(
                        "select table_name from information_schema.tables where table_schema = 'public'", String.class))
                .contains("app_user", "spring_session", "spring_session_attributes");
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.table_constraints"
                                + " where constraint_name = 'uq_app_user_email'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void healthIsPublic() {
        ApiClient anonymous = newClient();

        ApiClient.Response response = anonymous.get("/actuator/health");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("status").asString()).isEqualTo("UP");
    }

    @Test
    void protectedRouteWithoutSessionIs401Problem() {
        ApiClient.Response response = newClient().get("/api/v1/groups");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.header("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.json().get("type").asString()).isEqualTo("/problems/unauthorized");
    }

    @Test
    void postWithoutXsrfIs403() {
        ApiClient.Response response = newClient().post("/api/v1/auth/login", "{}");

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.json().get("type").asString()).isEqualTo("/problems/csrf-invalid");
    }

    @Test
    void clockIsUtc() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void userIsPersistedWithNormalizedEmailAndUniqueConstraint() {
        String email = "  Ana." + UUID.randomUUID() + "@Example.COM ";
        AppUser saved = users.save(new AppUser(" Ana ", email, "{bcrypt}hash", clock.instant()));

        AppUser loaded = users.findByEmail(AppUser.normalizeEmail(email)).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getName()).isEqualTo("Ana");
        assertThat(loaded.getEmail()).isEqualTo(email.strip().toLowerCase());
        assertThat(loaded.isNew()).isFalse();
        assertThat(users.existsByEmail(loaded.getEmail())).isTrue();

        assertThatThrownBy(() -> users.saveAndFlush(new AppUser("Outra", email, "{bcrypt}hash", clock.instant())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
