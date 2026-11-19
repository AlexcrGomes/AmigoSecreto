package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** O sorteio acontece uma única vez, mesmo com cliques simultâneos (Constituição II, research R7). */
class DrawConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tenSimultaneousDrawsProduceExactlyOneDraw() throws Exception {
        ApiClient ana = newClient();
        ana.register("Ana", "ana." + UUID.randomUUID() + "@ex.com", "senha-forte");
        JsonNode group = ana.createGroup("Firma").json();
        String groupId = group.get("id").asString();
        seedMembers(UUID.fromString(groupId), 9); // 10 membros

        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return ana.post("/api/v1/groups/" + groupId + "/draw").status();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).allMatch(s -> s == 200 || s == 409 || s == 429);
        assertThat(jdbc.queryForObject(
                        "select count(*) from draw_assignment where group_id = ?::uuid", Integer.class, groupId))
                .isEqualTo(10);
        assertThat(ana.get("/api/v1/groups/" + groupId).json().get("status").asString())
                .isEqualTo("DRAWN");

        // Depois do sorteio ninguém mais entra
        ApiClient late = newClient();
        late.register("Atrasado", "late." + UUID.randomUUID() + "@ex.com", "senha-forte");
        ApiClient.Response join = late.post(
                "/api/v1/groups/join", Map.of("code", group.get("code").asString()));
        assertThat(join.status()).isEqualTo(409);
        assertThat(join.json().get("type").asString()).isEqualTo("/problems/group-already-drawn");

        // E o sorteio não pode ser refeito (FR-025)
        assertThat(ana.post("/api/v1/groups/" + groupId + "/draw").status()).isIn(409, 429);
    }

    private void seedMembers(UUID groupId, int count) {
        Timestamp now = Timestamp.from(Instant.now());
        for (int i = 0; i < count; i++) {
            UUID userId = UUID.randomUUID();
            jdbc.update(
                    "insert into app_user (id, name, email, password_hash, created_at) values (?, ?, ?, ?, ?)",
                    userId,
                    "Semente " + i,
                    "semente." + userId + "@ex.com",
                    "{bcrypt}nao-usado",
                    now);
            jdbc.update(
                    "insert into group_member (group_id, user_id, joined_at, confirmed_at) values (?, ?, ?, ?)",
                    groupId,
                    userId,
                    now,
                    now);
        }
    }
}
