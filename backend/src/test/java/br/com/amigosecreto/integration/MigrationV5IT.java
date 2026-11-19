package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * A migração V5 aplicada sobre dados da feature 001 (research R22): grupos antigos ficam sem detalhes do
 * evento, e a confirmação é preenchida só onde não muda o resultado (organizador e grupos já sorteados).
 *
 * <p>Usa um banco próprio no mesmo container: o banco da suíte já está na última versão.
 */
class MigrationV5IT {

    private static final Instant CREATED = Instant.parse("2026-11-01T12:00:00Z");
    private static final Instant JOINED = Instant.parse("2026-11-02T12:00:00Z");
    private static final Instant DRAWN = Instant.parse("2026-11-10T12:00:00Z");

    private final String database = "mig_v5_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String url;

    @BeforeEach
    void createDatabase() {
        PostgresAccess postgres = new PostgresAccess();
        admin = new JdbcTemplate(postgres.dataSource(postgres.jdbcUrl()));
        admin.execute("CREATE DATABASE " + database);
        url = postgres.jdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        jdbc = new JdbcTemplate(postgres.dataSource(url));
    }

    @AfterEach
    void dropDatabase() {
        admin.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
    }

    @Test
    void oldGroupsKeepTheirDataAndWaitForTheOrganizer() {
        flyway(MigrationVersion.fromVersion("4")).migrate();

        UUID ana = user("Ana");
        UUID bruno = user("Bruno");
        UUID carla = user("Carla");
        UUID davi = user("Davi");

        UUID open = group("Aberto", "AAAAAAAA", ana, "OPEN", null);
        member(open, ana);
        member(open, bruno);
        member(open, carla);

        UUID drawn = group("Sorteado", "BBBBBBBB", bruno, "DRAWN", DRAWN);
        member(drawn, bruno);
        member(drawn, carla);
        member(drawn, davi);
        byte[] secret = {1, 2, 3, 4};
        for (UUID giver : List.of(bruno, carla, davi)) {
            jdbc.update(
                    "INSERT INTO draw_assignment (group_id, giver_id, receiver_enc, created_at) VALUES (?, ?, ?, ?)",
                    drawn,
                    giver,
                    secret,
                    Timestamp.from(DRAWN));
        }

        flyway(MigrationVersion.LATEST).migrate();

        for (UUID group : List.of(open, drawn)) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT description, location, event_starts_at, event_has_time, finalizes_at, details_version,"
                            + " finished_at, draw_undone_at FROM secret_group WHERE id = ?",
                    group);
            assertThat(row)
                    .containsEntry("description", null)
                    .containsEntry("location", null)
                    .containsEntry("event_starts_at", null)
                    .containsEntry("event_has_time", null)
                    .containsEntry("finalizes_at", null)
                    .containsEntry("details_version", 1)
                    .containsEntry("finished_at", null)
                    .containsEntry("draw_undone_at", null);
        }

        assertThat(confirmedAt(open, ana)).isEqualTo(JOINED);
        assertThat(confirmedAt(open, bruno)).isNull();
        assertThat(confirmedAt(open, carla)).isNull();

        assertThat(confirmedAt(drawn, bruno)).isEqualTo(JOINED);
        assertThat(confirmedAt(drawn, carla)).isEqualTo(DRAWN);
        assertThat(confirmedAt(drawn, davi)).isEqualTo(DRAWN);

        assertThat(jdbc.queryForList(
                        "SELECT receiver_enc FROM draw_assignment WHERE group_id = ?", byte[].class, drawn))
                .hasSize(3)
                .allSatisfy(stored -> assertThat(stored).isEqualTo(secret));
    }

    @Test
    void newConstraintsRejectInconsistentGroups() {
        flyway(MigrationVersion.LATEST).migrate();
        UUID ana = user("Ana");

        // Finalizado sem finished_at (todo o resto coerente)
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO secret_group (id, name, code, organizer_id, status, created_at, drawn_at, location,"
                                + " event_starts_at, event_has_time, finalizes_at) VALUES (?, 'X', 'CCCCCCCC', ?,"
                                + " 'FINISHED', ?, ?, 'Casa', ?, false, ?)",
                        UUID.randomUUID(),
                        ana,
                        Timestamp.from(CREATED),
                        Timestamp.from(DRAWN),
                        Timestamp.from(DRAWN),
                        Timestamp.from(DRAWN)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_secret_group_finished");

        // Local sem data: os detalhes do evento são "tudo ou nada"
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO secret_group (id, name, code, organizer_id, status, created_at, location)"
                                + " VALUES (?, 'X', 'DDDDDDDD', ?, 'OPEN', ?, 'Casa')",
                        UUID.randomUUID(),
                        ana,
                        Timestamp.from(CREATED)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_secret_group_event");

        // Finalizado sem data do evento
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO secret_group (id, name, code, organizer_id, status, created_at, drawn_at,"
                                + " finished_at) VALUES (?, 'X', 'EEEEEEEE', ?, 'FINISHED', ?, ?, ?)",
                        UUID.randomUUID(),
                        ana,
                        Timestamp.from(CREATED),
                        Timestamp.from(DRAWN),
                        Timestamp.from(DRAWN)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_secret_group_finish_needs_event");

        // Um grupo finalizado completo é aceito
        jdbc.update(
                "INSERT INTO secret_group (id, name, code, organizer_id, status, created_at, drawn_at, finished_at,"
                        + " location, event_starts_at, event_has_time, finalizes_at) VALUES (?, 'X', 'FFFFFFFF', ?,"
                        + " 'FINISHED', ?, ?, ?, 'Casa', ?, false, ?)",
                UUID.randomUUID(),
                ana,
                Timestamp.from(CREATED),
                Timestamp.from(DRAWN),
                Timestamp.from(DRAWN),
                Timestamp.from(DRAWN),
                Timestamp.from(DRAWN));
    }

    private Flyway flyway(MigrationVersion target) {
        PostgresAccess postgres = new PostgresAccess();
        return Flyway.configure()
                .dataSource(url, postgres.username(), postgres.password())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (?, ?, ?, 'x', ?)",
                id,
                name,
                id + "@ex.com",
                Timestamp.from(CREATED));
        return id;
    }

    private UUID group(String name, String code, UUID organizer, String status, Instant drawnAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO secret_group (id, name, code, organizer_id, status, created_at, drawn_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                name,
                code,
                organizer,
                status,
                Timestamp.from(CREATED),
                drawnAt == null ? null : Timestamp.from(drawnAt));
        return id;
    }

    private void member(UUID group, UUID user) {
        jdbc.update(
                "INSERT INTO group_member (group_id, user_id, joined_at) VALUES (?, ?, ?)",
                group,
                user,
                Timestamp.from(JOINED));
    }

    private Instant confirmedAt(UUID group, UUID user) {
        Timestamp value = jdbc.queryForObject(
                "SELECT confirmed_at FROM group_member WHERE group_id = ? AND user_id = ?",
                Timestamp.class,
                group,
                user);
        return value == null ? null : value.toInstant();
    }

    /** Acesso ao container único da suíte (o mesmo de {@link AbstractIntegrationTest}). */
    private static final class PostgresAccess {

        String jdbcUrl() {
            return AbstractIntegrationTest.POSTGRES.getJdbcUrl();
        }

        String username() {
            return AbstractIntegrationTest.POSTGRES.getUsername();
        }

        String password() {
            return AbstractIntegrationTest.POSTGRES.getPassword();
        }

        DriverManagerDataSource dataSource(String jdbcUrl) {
            return new DriverManagerDataSource(jdbcUrl, username(), password());
        }
    }
}
