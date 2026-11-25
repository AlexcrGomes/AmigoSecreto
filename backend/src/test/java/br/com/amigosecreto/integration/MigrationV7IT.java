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

/** A migração V7: itens da lista de desejos e valor do presente (data-model.md da 003). */
class MigrationV7IT extends AbstractIntegrationTest {

    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-12-01T12:00:00Z"));

    @Autowired
    private JdbcTemplate jdbc;

    private UUID groupId;
    private UUID organizerId;
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
        outsiderId = UUID.fromString(newClient()
                .register("Eva", "eva." + UUID.randomUUID() + "@ex.com", "senha-forte")
                .json()
                .get("id")
                .asString());
    }

    private void insertItem(UUID userId, String description) {
        jdbc.update(
                "insert into wish_item (id, group_id, user_id, description, url, created_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                groupId,
                userId,
                description,
                null,
                NOW);
    }

    private int itemsOf(UUID userId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from wish_item where group_id = ? and user_id = ?", Integer.class, groupId, userId);
        return count == null ? 0 : count;
    }

    private void setGiftValue(Integer value) {
        jdbc.update("update secret_group set gift_value = ? where id = ?", value, groupId);
    }

    @Test
    void theNewTableAndColumnsExist() {
        Integer columns = jdbc.queryForObject(
                "select count(*) from information_schema.columns where (table_name = 'secret_group' and column_name ="
                        + " 'gift_value') or (table_name = 'group_member' and column_name = 'wishlist_updated_at')"
                        + " or (table_name = 'wish_item' and column_name = 'description')",
                Integer.class);
        assertThat(columns).isEqualTo(3);
    }

    @Test
    void giftValueIsOptionalAndBetweenOneAndOneHundredThousand() {
        setGiftValue(null);
        setGiftValue(1);
        setGiftValue(100_000);

        assertThatThrownBy(() -> setGiftValue(0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> setGiftValue(100_001)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDescriptionHasAtMost120Characters() {
        insertItem(organizerId, "a".repeat(120));

        assertThatThrownBy(() -> insertItem(organizerId, "a".repeat(121)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void itemsBelongToAParticipationAndGoAwayWithIt() {
        assertThatThrownBy(() -> insertItem(outsiderId, "Livro")).isInstanceOf(DataIntegrityViolationException.class);

        insertItem(organizerId, "Livro");
        insertItem(organizerId, "Caneca");
        assertThat(itemsOf(organizerId)).isEqualTo(2);

        jdbc.update("delete from group_member where group_id = ? and user_id = ?", groupId, organizerId);

        assertThat(itemsOf(organizerId)).isZero();
    }
}
