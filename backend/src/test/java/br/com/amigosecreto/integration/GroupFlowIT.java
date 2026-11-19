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

/** Criar grupo, entrar pelo código e ver os participantes (US2, quickstart passos 5 a 10). */
class GroupFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record User(ApiClient client, String id, String name) {}

    private User newUser(String name) {
        ApiClient client = newClient();
        ApiClient.Response response = client.register(
                name, name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@ex.com", "senha-forte");
        assertThat(response.status()).isEqualTo(201);
        return new User(client, response.json().get("id").asString(), name);
    }

    private static List<String> names(JsonNode group) {
        List<String> names = new ArrayList<>();
        group.get("participants").forEach(p -> names.add(p.get("name").asString()));
        return names;
    }

    @Test
    void organizerCreatesAndOthersJoinByCodeSeeingTheSameList() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");

        ApiClient.Response created = ana.client().createGroup("  Natal 2026 ");
        assertThat(created.status()).isEqualTo(201);
        JsonNode group = created.json();
        String groupId = group.get("id").asString();
        String code = group.get("code").asString();
        assertThat(created.header("Location")).contains("/api/v1/groups/" + groupId);
        assertThat(code).matches("[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(group.get("name").asString()).isEqualTo("Natal 2026");
        assertThat(group.get("organizer").asBoolean()).isTrue();

        // B com o código em minúsculas e com hífen; C sem hífen
        assertThat(bruno.client()
                        .post("/api/v1/groups/join", Map.of("code", code.toLowerCase()))
                        .status())
                .isEqualTo(200);
        assertThat(carla.client()
                        .post("/api/v1/groups/join", Map.of("code", code.replace("-", "")))
                        .status())
                .isEqualTo(200);
        // Entrar de novo não duplica
        ApiClient.Response again = bruno.client().post("/api/v1/groups/join", Map.of("code", code));
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.json().get("participantCount").asInt()).isEqualTo(3);

        // Quem entrou começa pendente; com todos confirmados, só o organizador pode sortear
        assertThat(bruno.client().confirm(groupId).status()).isEqualTo(200);
        assertThat(carla.client().confirm(groupId).status()).isEqualTo(200);
        for (User member : List.of(ana, bruno, carla)) {
            JsonNode seen = member.client().get("/api/v1/groups/" + groupId).json();
            assertThat(names(seen)).containsExactly("Ana", "Bruno", "Carla");
            assertThat(seen.get("participantCount").asInt()).isEqualTo(3);
            assertThat(seen.get("canDraw").asBoolean()).isEqualTo(member == ana);
            assertThat(seen.get("participants").get(0).get("organizer").asBoolean())
                    .isTrue();
        }

        // D não é membro: 404 e nenhum dado
        ApiClient.Response outsider = davi.client().get("/api/v1/groups/" + groupId);
        assertThat(outsider.status()).isEqualTo(404);
        assertThat(outsider.body()).doesNotContain("Natal").doesNotContain(code);
        assertThat(davi.client().get("/api/v1/groups").json().size()).isZero();

        JsonNode anaGroups = ana.client().get("/api/v1/groups").json();
        assertThat(anaGroups.size()).isEqualTo(1);
        assertThat(anaGroups.get(0).get("organizer").asBoolean()).isTrue();
        assertThat(anaGroups.get(0).get("participantCount").asInt()).isEqualTo(3);
        assertThat(bruno.client()
                        .get("/api/v1/groups")
                        .json()
                        .get(0)
                        .get("organizer")
                        .asBoolean())
                .isFalse();
    }

    @Test
    void unknownCodeIs404WithGenericMessage() {
        User bruno = newUser("Bruno");

        ApiClient.Response response = bruno.client().post("/api/v1/groups/join", Map.of("code", "ZZZZ-ZZZZ"));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("detail").asString()).isEqualTo("Código inválido.");
    }

    @Test
    void concurrentJoinsNeverExceedOneHundredMembers() throws Exception {
        User ana = newUser("Ana");
        JsonNode group = ana.client().createGroup("Firma").json();
        String groupId = group.get("id").asString();
        String code = group.get("code").asString();
        seedMembers(UUID.fromString(groupId), 94); // 1 + 94 = 95

        List<User> joiners = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            joiners.add(newUser("Pessoa " + i));
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (User joiner : joiners) {
                results.add(pool.submit(() -> {
                    start.await();
                    return joiner.client()
                            .post("/api/v1/groups/join", Map.of("code", code))
                            .status();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(15);
        assertThat(jdbc.queryForObject(
                        "select count(*) from group_member where group_id = ?::uuid", Integer.class, groupId))
                .isEqualTo(100);
    }

    @Test
    void groupCodeHasAUniqueConstraint() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.table_constraints"
                                + " where constraint_name = 'uq_secret_group_code' and constraint_type = 'UNIQUE'",
                        Integer.class))
                .isEqualTo(1);
    }

    /** Insere membros direto no banco (sem 94 cadastros com BCrypt custo 12). */
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
                    "insert into group_member (group_id, user_id, joined_at) values (?, ?, ?)", groupId, userId, now);
        }
    }
}
