package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.amigosecreto.group.GroupFinalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Grupo finalizado depois do evento (US3 da feature 002, FR-017 a FR-021, research R17). A API não aceita
 * datas passadas, então o teste leva o evento para o passado direto no banco (quickstart §3) e chama a
 * tarefa {@link GroupFinalizer} em vez de esperar o agendamento.
 */
class FinalizationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GroupFinalizer finalizer;

    private record User(ApiClient client, String id, String name) {}

    private record Group(String id, String code, User ana, User bruno, User carla) {}

    private User newUser(String name) {
        ApiClient client = newClient();
        ApiClient.Response registered =
                client.register(name, name.toLowerCase() + "." + UUID.randomUUID() + "@ex.com", "senha-forte");
        return new User(client, registered.json().get("id").asString(), name);
    }

    private static String inAMonth() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(30).toString();
    }

    /** Grupo da Ana com Bruno e Carla, todos confirmados; sorteado se {@code drawn}. */
    private Group groupOfThree(boolean drawn) {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        JsonNode created = ana.client()
                .createGroup("Natal", Map.of("eventDate", inAMonth(), "location", "Casa da Ana"))
                .json();
        String groupId = created.get("id").asString();
        String code = created.get("code").asString();
        for (User guest : List.of(bruno, carla)) {
            assertThat(guest.client()
                            .post("/api/v1/groups/join", Map.of("code", code))
                            .status())
                    .isEqualTo(200);
            assertThat(guest.client().confirm(groupId).status()).isEqualTo(200);
        }
        if (drawn) {
            assertThat(ana.client().post("/api/v1/groups/" + groupId + "/draw").status())
                    .isEqualTo(200);
        }
        return new Group(groupId, code, ana, bruno, carla);
    }

    /** O evento foi anteontem e o grupo devia ter finalizado ontem (quickstart §3). */
    private void eventHappened(String groupId) {
        jdbc.update(
                "update secret_group set event_starts_at = now() - interval '2 days',"
                        + " finalizes_at = now() - interval '1 day' where id = ?",
                UUID.fromString(groupId));
    }

    private static JsonNode detail(User user, String groupId) {
        return user.client().get("/api/v1/groups/" + groupId).json();
    }

    private static Map<String, Object> newDetails(String location) {
        Map<String, Object> body = new HashMap<>();
        body.put("description", null);
        body.put("eventDate", inAMonth());
        body.put("location", location);
        return body;
    }

    private static void assertFinishedProblem(ApiClient.Response response) {
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("type").asString()).isEqualTo("/problems/group-finished");
        assertThat(response.json().get("detail").asString()).isEqualTo("Este grupo foi finalizado.");
    }

    @Test
    void drawnGroupIsFinishedByTheTaskAndBecomesReadOnly() {
        Group group = groupOfThree(true);
        String base = "/api/v1/groups/" + group.id();

        try (ApiClient.EventStream events = group.bruno().client().openEvents(base + "/events")) {
            assertThat(events.status()).isEqualTo(200);
            eventHappened(group.id());

            finalizer.finishDueGroups();

            // 1 e FR-021: quem está com o grupo aberto recebe o sinal, sem nomes nem pares
            assertThat(events.awaitLineContaining("group-finished", Duration.ofSeconds(5)))
                    .isTrue();
            String stream = String.join("\n", events.lines());
            assertThat(stream).contains("\"status\":\"FINISHED\"").doesNotContain("Ana", "Carla");
        }

        JsonNode finished = detail(group.bruno(), group.id());
        assertThat(finished.get("status").asString()).isEqualTo("FINISHED");
        assertThat(finished.get("finishedAt").isNull()).isFalse();
        assertThat(finished.get("eventDatePassed").asBoolean()).isFalse();
        assertThat(finished.get("canDraw").asBoolean()).isFalse();

        // 2: a lista de grupos traz a nova situação
        JsonNode mine = group.bruno().client().get("/api/v1/groups").json();
        assertThat(mine.get(0).get("status").asString()).isEqualTo("FINISHED");

        // 4 e FR-018: nada mais pode ser alterado
        assertFinishedProblem(group.ana().client().put(base + "/event-details", newDetails("Salão")));
        assertFinishedProblem(group.bruno().client().confirm(group.id()));
        assertFinishedProblem(group.carla().client().delete(base + "/participants/me"));
        assertFinishedProblem(group.ana()
                .client()
                .delete(base + "/participants/" + group.carla().id()));
        assertFinishedProblem(group.ana().client().post(base + "/draw"));
        User davi = newUser("Davi");
        assertFinishedProblem(davi.client().post("/api/v1/groups/join", Map.of("code", group.code())));
        assertThat(detail(group.ana(), group.id()).get("participantCount").asInt())
                .isEqualTo(3);

        // 3 e FR-019: cada um ainda revela o próprio amigo, só o dele
        ApiClient.Response friend = group.bruno().client().get(base + "/my-secret-friend");
        assertThat(friend.status()).isEqualTo(200);
        assertThat(friend.json().propertyNames()).containsExactlyInAnyOrder("name", "wishlist", "wishlistUpdatedAt");
        assertThat(friend.json().get("name").asString()).isIn("Ana", "Carla");
    }

    @Test
    void theFirstWriteAfterTheEventFinishesTheGroupEvenBeforeTheTask() {
        Group group = groupOfThree(true);
        String base = "/api/v1/groups/" + group.id();

        try (ApiClient.EventStream events = group.carla().client().openEvents(base + "/events")) {
            assertThat(events.status()).isEqualTo(200);
            eventHappened(group.id());

            assertFinishedProblem(group.ana().client().put(base + "/event-details", newDetails("Salão")));

            // A recusa não desfaz a finalização: ela fica gravada e é avisada
            assertThat(events.awaitLineContaining("group-finished", Duration.ofSeconds(5)))
                    .isTrue();
        }
        JsonNode finished = detail(group.ana(), group.id());
        assertThat(finished.get("status").asString()).isEqualTo("FINISHED");
        assertThat(finished.get("eventDetails").get("location").asString()).isEqualTo("Casa da Ana");
        assertThat(finalizer.finishDueGroups()).isZero();
    }

    @Test
    void openGroupWhoseDateHasPassedStaysOpenUntilTheOrganizerChangesTheDate() {
        Group group = groupOfThree(false);
        String base = "/api/v1/groups/" + group.id();
        eventHappened(group.id());

        finalizer.finishDueGroups();

        // 5: não finaliza; o organizador vê o motivo e o sorteio é recusado
        JsonNode stillOpen = detail(group.ana(), group.id());
        assertThat(stillOpen.get("status").asString()).isEqualTo("OPEN");
        assertThat(stillOpen.get("eventDatePassed").asBoolean()).isTrue();
        assertThat(stillOpen.get("drawBlockedReason").asString()).isEqualTo("event-date-passed");
        ApiClient.Response blocked = group.ana().client().post(base + "/draw");
        assertThat(blocked.status()).isEqualTo(422);
        assertThat(blocked.json().get("type").asString()).isEqualTo("/problems/event-date-passed");

        // Nova data: todos confirmam de novo e o sorteio acontece
        assertThat(group.ana()
                        .client()
                        .put(base + "/event-details", newDetails("Casa da Ana"))
                        .status())
                .isEqualTo(200);
        assertThat(detail(group.ana(), group.id()).get("eventDatePassed").asBoolean())
                .isFalse();
        group.bruno().client().confirm(group.id());
        group.carla().client().confirm(group.id());
        assertThat(group.ana().client().post(base + "/draw").status()).isEqualTo(200);
    }

    @Test
    void aPendingExitRequestIsCancelledWithANoticeWhenTheGroupFinishes() {
        Group group = groupOfThree(true);
        String base = "/api/v1/groups/" + group.id();
        assertThat(group.bruno().client().post(base + "/exit-requests").status())
                .isEqualTo(201);
        eventHappened(group.id());

        finalizer.finishDueGroups();

        // FR-033 e Edge Case: o pedido é cancelado e a pessoa é avisada
        JsonNode notices =
                group.bruno().client().get("/api/v1/exit-request-notices").json();
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(notices.get(0).get("groupName").asString()).isEqualTo("Natal");
        assertThat(detail(group.bruno(), group.id()).get("status").asString()).isEqualTo("FINISHED");
        assertFinishedProblem(group.bruno().client().post(base + "/exit-requests"));
    }
}
