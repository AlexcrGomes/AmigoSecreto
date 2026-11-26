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

/** A migração V8: dependentes e quem pediu a saída (data-model.md da 004). */
class MigrationV8IT extends AbstractIntegrationTest {

    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-12-01T12:00:00Z"));

    @Autowired
    private JdbcTemplate jdbc;

    private UUID groupId;
    private UUID organizerId;
    private UUID memberId;
    private UUID outsiderId;

    @BeforeEach
    void createGroup() {
        ApiClient ana = newClient();
        organizerId = UUID.fromString(ana.register("Ana", "ana." + UUID.randomUUID() + "@ex.com", "senha-forte")
                .json()
                .get("id")
                .asString());
        String date = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(30).toString();
        groupId = UUID.fromString(ana.createGroup("Natal", Map.of("eventDate", date, "location", "Casa"))
                .json()
                .get("id")
                .asString());
        memberId = UUID.fromString(newClient()
                .register("Bruno", "bruno." + UUID.randomUUID() + "@ex.com", "senha-forte")
                .json()
                .get("id")
                .asString());
        jdbc.update("insert into group_member (group_id, user_id, joined_at) values (?, ?, ?)", groupId, memberId, NOW);
        outsiderId = UUID.fromString(newClient()
                .register("Eva", "eva." + UUID.randomUUID() + "@ex.com", "senha-forte")
                .json()
                .get("id")
                .asString());
    }

    private UUID insertUser(String kind, String email, String passwordHash) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into app_user (id, name, email, password_hash, created_at, kind) values (?, ?, ?, ?, ?, ?)",
                id,
                "Lucas",
                email,
                passwordHash,
                NOW,
                kind);
        return id;
    }

    private UUID insertDependent() {
        return insertUser("DEPENDENT", null, null);
    }

    private void insertMembership(UUID userId, UUID guardianId) {
        jdbc.update(
                "insert into group_member (group_id, user_id, joined_at, guardian_id) values (?, ?, ?, ?)",
                groupId,
                userId,
                NOW,
                guardianId);
    }

    private int membershipsOf(UUID userId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from group_member where group_id = ? and user_id = ?", Integer.class, groupId, userId);
        return count == null ? 0 : count;
    }

    @Test
    void existingAccountsAreAccounts() {
        String kind = jdbc.queryForObject("select kind from app_user where id = ?", String.class, organizerId);

        assertThat(kind).isEqualTo("ACCOUNT");
    }

    @Test
    void aDependentHasNoCredentialsAndAnAccountAlwaysHasThem() {
        insertDependent();
        insertDependent(); // dois e-mails nulos não conflitam na constraint única

        assertThatThrownBy(() -> insertUser("DEPENDENT", "lucas." + UUID.randomUUID() + "@ex.com", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("DEPENDENT", null, "{bcrypt}hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("ACCOUNT", null, "{bcrypt}hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("ACCOUNT", "x." + UUID.randomUUID() + "@ex.com", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("OTHER", null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theGuardianMustTakePartInTheSameGroupAndCannotBeTheDependentItself() {
        UUID lucas = insertDependent();

        assertThatThrownBy(() -> insertMembership(lucas, outsiderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMembership(lucas, lucas)).isInstanceOf(DataIntegrityViolationException.class);

        insertMembership(lucas, memberId);
        assertThat(membershipsOf(lucas)).isEqualTo(1);
    }

    @Test
    void dependentsAndTheirWishlistsLeaveWithTheGuardian() {
        UUID lucas = insertDependent();
        insertMembership(lucas, memberId);
        jdbc.update(
                "insert into wish_item (id, group_id, user_id, description, url, created_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                groupId,
                lucas,
                "Bola",
                null,
                NOW);

        jdbc.update("delete from group_member where group_id = ? and user_id = ?", groupId, memberId);

        assertThat(membershipsOf(lucas)).isZero();
        Integer items = jdbc.queryForObject("select count(*) from wish_item where user_id = ?", Integer.class, lucas);
        assertThat(items).isZero();
    }

    @Test
    void exitRequestsRecordWhoAskedAndItIsRequired() {
        UUID requestId = UUID.randomUUID();
        jdbc.update(
                "insert into exit_request (id, group_id, user_id, status, requested_at, requested_by)"
                        + " values (?, ?, ?, 'PENDING', ?, ?)",
                requestId,
                groupId,
                memberId,
                NOW,
                memberId);

        UUID requestedBy =
                jdbc.queryForObject("select requested_by from exit_request where id = ?", UUID.class, requestId);
        assertThat(requestedBy).isEqualTo(memberId);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into exit_request (id, group_id, user_id, status, requested_at)"
                                + " values (?, ?, ?, 'PENDING', ?)",
                        UUID.randomUUID(),
                        groupId,
                        organizerId,
                        NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theNoticeIndexIsOnWhoAsked() {
        String definition = jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'ix_exit_request_unseen'", String.class);

        assertThat(definition).contains("(requested_by)");
    }
}
