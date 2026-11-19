package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Confirmação de participação antes do sorteio (US2 da feature 002, cenários 1–10, FR-035, SC-003). */
class ConfirmationFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record User(ApiClient client, String id) {}

    private User newUser(String name) {
        ApiClient client = newClient();
        ApiClient.Response registered =
                client.register(name, name.toLowerCase() + "." + UUID.randomUUID() + "@ex.com", "senha-forte");
        return new User(client, registered.json().get("id").asString());
    }

    private static String inAMonth() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(30).toString();
    }

    private static JsonNode join(User user, String code) {
        ApiClient.Response joined = user.client().post("/api/v1/groups/join", Map.of("code", code));
        assertThat(joined.status()).isEqualTo(200);
        return joined.json();
    }

    private static JsonNode detail(User user, String groupId) {
        return user.client().get("/api/v1/groups/" + groupId).json();
    }

    private static ApiClient.Response draw(User organizer, String groupId) {
        return organizer.client().post("/api/v1/groups/" + groupId + "/draw");
    }

    private static ApiClient.Response changeDetails(
            User organizer, String groupId, String description, String location) {
        Map<String, Object> body = new HashMap<>();
        body.put("description", description);
        body.put("eventDate", inAMonth());
        body.put("location", location);
        return organizer.client().put("/api/v1/groups/" + groupId + "/event-details", body);
    }

    private static List<String> pendingIn(ApiClient.Response problem) {
        List<String> names = new ArrayList<>();
        problem.json().get("pending").forEach(name -> names.add(name.asString()));
        return names;
    }

    @Test
    void everyoneMustConfirmBeforeTheDraw() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");
        User eva = newUser("Eva");

        // 3: o organizador já nasce confirmado
        JsonNode created = ana.client()
                .createGroup("Natal", Map.of("eventDate", inAMonth(), "location", "Casa da Ana"))
                .json();
        String groupId = created.get("id").asString();
        String code = created.get("code").asString();
        assertThat(created.get("myConfirmation").asString()).isEqualTo("CONFIRMED");
        assertThat(created.get("confirmedCount").asInt()).isEqualTo(1);

        // 1: quem entra começa pendente e vê os detalhes
        JsonNode joined = join(bruno, code);
        assertThat(joined.get("myConfirmation").asString()).isEqualTo("PENDING");
        assertThat(joined.get("participants").get(1).get("confirmed").asBoolean())
                .isFalse();
        assertThat(joined.get("eventDetails").get("location").asString()).isEqualTo("Casa da Ana");
        join(carla, code);

        // 4: com pendentes, o sorteio é recusado e diz quem falta
        JsonNode forAna = detail(ana, groupId);
        assertThat(forAna.get("drawBlockedReason").asString()).isEqualTo("participants-pending");
        assertThat(forAna.get("canDraw").asBoolean()).isFalse();
        ApiClient.Response blocked = draw(ana, groupId);
        assertThat(blocked.status()).isEqualTo(422);
        assertThat(blocked.json().get("type").asString()).isEqualTo("/problems/participants-pending");
        assertThat(pendingIn(blocked)).containsExactly("Bruno", "Carla");

        // 2: B confirma e A recebe o sinal ao vivo, sem dados
        try (ApiClient.EventStream events = ana.client().openEvents("/api/v1/groups/" + groupId + "/events")) {
            assertThat(events.status()).isEqualTo(200);
            ApiClient.Response confirmed = bruno.client().confirm(groupId);
            assertThat(confirmed.status()).isEqualTo(200);
            assertThat(confirmed.json().get("myConfirmation").asString()).isEqualTo("CONFIRMED");
            assertThat(events.awaitLineContaining("group-updated", Duration.ofSeconds(5)))
                    .isTrue();
            assertThat(String.join("\n", events.lines())).doesNotContain("Bruno");
        }
        assertThat(detail(ana, groupId).get("confirmedCount").asInt()).isEqualTo(2);

        // 6: C não quer participar e sai; sobram 2
        assertThat(carla.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/me")
                        .status())
                .isEqualTo(204);
        assertThat(detail(ana, groupId).get("drawBlockedReason").asString()).isEqualTo("not-enough-participants");

        // 8: D entra com todos confirmados e o sorteio volta a ficar bloqueado
        join(davi, code);
        assertThat(pendingIn(draw(ana, groupId))).containsExactly("Davi");
        davi.client().confirm(groupId);
        assertThat(detail(ana, groupId).get("canDraw").asBoolean()).isTrue();

        // 7: o organizador remove um pendente
        join(eva, code);
        assertThat(ana.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/" + eva.id())
                        .status())
                .isEqualTo(204);
        assertThat(detail(ana, groupId).get("canDraw").asBoolean()).isTrue();

        // 9: mudar o local volta todos, menos o organizador, a pendente — e avisa
        try (ApiClient.EventStream events = bruno.client().openEvents("/api/v1/groups/" + groupId + "/events")) {
            assertThat(events.status()).isEqualTo(200);
            assertThat(changeDetails(ana, groupId, null, "Salão").status()).isEqualTo(200);
            assertThat(events.awaitLineContaining("\"confirmationsReset\":true", Duration.ofSeconds(5)))
                    .isTrue();
        }
        JsonNode reset = detail(ana, groupId);
        assertThat(reset.get("confirmedCount").asInt()).isEqualTo(1);
        assertThat(reset.get("myConfirmation").asString()).isEqualTo("CONFIRMED");
        assertThat(reset.get("detailsVersion").asInt()).isEqualTo(2);
        assertThat(pendingIn(draw(ana, groupId))).containsExactly("Bruno", "Davi");

        // FR-035: confirmar a versão antiga é recusado
        ApiClient.Response stale =
                bruno.client().post("/api/v1/groups/" + groupId + "/confirmation", Map.of("detailsVersion", 1));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.json().get("type").asString()).isEqualTo("/problems/details-changed");
        assertThat(stale.json().get("detail").asString())
                .isEqualTo("Os detalhes do evento mudaram. Confira e confirme de novo.");

        bruno.client().confirm(groupId);
        davi.client().confirm(groupId);

        // 10: mudar só a descrição mantém as confirmações
        assertThat(changeDetails(ana, groupId, "Traga um prato", "Salão").status())
                .isEqualTo(200);
        assertThat(detail(ana, groupId).get("confirmedCount").asInt()).isEqualTo(3);

        // 5: todos confirmados, o sorteio acontece como na 001
        assertThat(draw(ana, groupId).status()).isEqualTo(200);
        ApiClient.Response late = bruno.client().confirm(groupId);
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.json().get("type").asString()).isEqualTo("/problems/group-already-drawn");
    }

    @Test
    void confirmingTwiceKeepsASingleConfirmation() throws Exception {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        JsonNode created = ana.client().createGroup("Firma").json();
        String groupId = created.get("id").asString();
        join(bruno, created.get("code").asString());

        List<Integer> statuses =
                runTogether(5, () -> bruno.client().confirm(groupId).status());

        assertThat(statuses).containsOnly(200);
        Timestamp first = confirmedAt(groupId, bruno.id());
        assertThat(first).isNotNull();
        assertThat(bruno.client().confirm(groupId).status()).isEqualTo(200);
        assertThat(confirmedAt(groupId, bruno.id())).isEqualTo(first);
    }

    @Test
    void confirmingWhileTheLocationChangesNeverKeepsAnOldConfirmation() throws Exception {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        JsonNode created = ana.client().createGroup("Firma").json();
        String groupId = created.get("id").asString();
        join(bruno, created.get("code").asString());

        for (int round = 0; round < 50; round++) {
            int version = detail(bruno, groupId).get("detailsVersion").asInt();
            String location = "Local " + round;
            List<Integer> statuses = runTogether(
                    () -> bruno.client()
                            .post("/api/v1/groups/" + groupId + "/confirmation", Map.of("detailsVersion", version))
                            .status(),
                    () -> changeDetails(ana, groupId, null, location).status());

            // Se a confirmação veio antes, a mudança a desfez; se veio depois, foi recusada
            assertThat(statuses.get(0)).isIn(200, 409);
            assertThat(statuses.get(1)).isEqualTo(200);
            JsonNode seen = detail(bruno, groupId);
            assertThat(seen.get("detailsVersion").asInt()).isEqualTo(version + 1);
            assertThat(seen.get("myConfirmation").asString())
                    .as("rodada %d", round)
                    .isEqualTo("PENDING");
        }
    }

    @Test
    void joiningWhileTheOrganizerDrawsNeverDrawsWithSomeonePending() throws Exception {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");

        for (int round = 0; round < 5; round++) {
            User late = newUser("Atrasado");
            JsonNode created = ana.client().createGroup("Rodada " + round).json();
            String groupId = created.get("id").asString();
            String code = created.get("code").asString();
            for (User member : List.of(bruno, carla)) {
                join(member, code);
                member.client().confirm(groupId);
            }

            List<Integer> statuses = runTogether(
                    () -> late.client()
                            .post("/api/v1/groups/join", Map.of("code", code))
                            .status(),
                    () -> draw(ana, groupId).status());

            int joinStatus = statuses.get(0);
            int drawStatus = statuses.get(1);
            if (drawStatus == 200) {
                assertThat(joinStatus).isEqualTo(409);
                assertThat(jdbc.queryForObject(
                                "select count(*) from group_member where group_id = ?::uuid", Integer.class, groupId))
                        .isEqualTo(3);
            } else {
                assertThat(joinStatus).isEqualTo(200);
                assertThat(drawStatus).isEqualTo(422);
                assertThat(detail(ana, groupId).get("status").asString()).isEqualTo("OPEN");
            }
        }
    }

    private Timestamp confirmedAt(String groupId, String userId) {
        return jdbc.queryForObject(
                "select confirmed_at from group_member where group_id = ?::uuid and user_id = ?::uuid",
                Timestamp.class,
                groupId,
                userId);
    }

    /** Executa as tarefas ao mesmo tempo (liberadas juntas) e devolve os resultados na ordem dada. */
    @SafeVarargs
    private static List<Integer> runTogether(Callable<Integer>... tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.length)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
        }
        return results;
    }

    private static List<Integer> runTogether(int times, Callable<Integer> task) throws Exception {
        @SuppressWarnings("unchecked")
        Callable<Integer>[] copies = new Callable[times];
        Arrays.fill(copies, task);
        return runTogether(copies);
    }
}
