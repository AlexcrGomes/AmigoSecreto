package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** A migração V6: pedidos de saída e o registro de quem saiu depois do sorteio (data-model.md §3–§4). */
class MigrationV6IT extends AbstractIntegrationTest {

    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-12-01T12:00:00Z"));

    @Autowired
    private JdbcTemplate jdbc;

    private UUID groupId;
    private UUID userId;

    @BeforeEach
    void createGroupAndUser() {
        ApiClient ana = newClient();
        ana.register("Ana", "ana." + UUID.randomUUID() + "@ex.com", "senha-forte");
        String date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(30).toString();
        groupId = UUID.fromString(ana.createGroup("Natal", Map.of("eventDate", date, "location", "Casa"))
                .json()
                .get("id")
                .asString());
        ApiClient bruno = newClient();
        userId = UUID.fromString(bruno.register("Bruno", "bruno." + UUID.randomUUID() + "@ex.com", "senha-forte")
                .json()
                .get("id")
                .asString());
    }

    private UUID insertRequest(String status, Timestamp decidedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into exit_request (id, group_id, user_id, status, requested_at, decided_at)"
                        + " values (?, ?, ?, ?, ?, ?)",
                id,
                groupId,
                userId,
                status,
                NOW,
                decidedAt);
        return id;
    }

    @Test
    void theNewTablesExist() {
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables"
                        + " where table_name in ('exit_request', 'group_exit_ban')",
                Integer.class);
        assertThat(tables).isEqualTo(2);
    }

    @Test
    void onlyOnePendingRequestPerPersonAndGroupButAgainAfterARejection() {
        UUID first = insertRequest("PENDING", null);

        assertThatThrownBy(() -> insertRequest("PENDING", null)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("update exit_request set status = 'REJECTED', decided_at = ? where id = ?", NOW, first);
        insertRequest("PENDING", null);
        assertThat(jdbc.queryForObject(
                        "select count(*) from exit_request where group_id = ? and user_id = ?",
                        Integer.class,
                        groupId,
                        userId))
                .isEqualTo(2);
    }

    @Test
    void decidedAtGoesWithTheStatus() {
        assertThatThrownBy(() -> insertRequest("PENDING", NOW)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRequest("REJECTED", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRequest("LEFT", NOW)).isInstanceOf(DataIntegrityViolationException.class);
        insertRequest("CANCELLED", NOW);
    }

    @Test
    void thePersonIsBannedOnceFromTheGroup() {
        jdbc.update(
                "insert into group_exit_ban (group_id, user_id, created_at) values (?, ?, ?)", groupId, userId, NOW);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into group_exit_ban (group_id, user_id, created_at) values (?, ?, ?)",
                        groupId,
                        userId,
                        NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
