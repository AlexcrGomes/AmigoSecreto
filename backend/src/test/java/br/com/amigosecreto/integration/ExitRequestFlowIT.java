package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Sair de um grupo já sorteado, com aprovação do organizador (US4 da feature 002, cenários 1–8, FR-025 a
 * FR-033, SC-008).
 */
class ExitRequestFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record User(ApiClient client, String id, String name, String email) {}

    private record Group(String id, String code) {}

    private User newUser(String name) {
        ApiClient client = newClient();
        String email = name.toLowerCase() + "." + UUID.randomUUID() + "@ex.com";
        ApiClient.Response registered = client.register(name, email, "senha-forte");
        return new User(client, registered.json().get("id").asString(), name, email);
    }

    private static String inAMonth() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(30).toString();
    }

    /** Grupo do organizador com os convidados, todos confirmados, já sorteado. */
    private static Group drawnGroup(User organizer, User... guests) {
        JsonNode created = organizer
                .client()
                .createGroup("Natal", Map.of("eventDate", inAMonth(), "location", "Casa da Ana"))
                .json();
        Group group =
                new Group(created.get("id").asString(), created.get("code").asString());
        for (User guest : guests) {
            assertThat(guest.client()
                            .post("/api/v1/groups/join", Map.of("code", group.code()))
                            .status())
                    .isEqualTo(200);
            assertThat(guest.client().confirm(group.id()).status()).isEqualTo(200);
        }
        assertThat(organizer.client().post(base(group) + "/draw").status()).isEqualTo(200);
        return group;
    }

    private static String base(Group group) {
        return "/api/v1/groups/" + group.id();
    }

    private static JsonNode detail(User user, Group group) {
        return user.client().get(base(group)).json();
    }

    private static ApiClient.Response askToLeave(User user, Group group) {
        return user.client().post(base(group) + "/exit-requests");
    }

    private static ApiClient.Response decide(User organizer, Group group, String requestId, String decision) {
        return organizer.client().post(base(group) + "/exit-requests/" + requestId + "/" + decision);
    }

    private static JsonNode notices(User user) {
        return user.client().get("/api/v1/exit-request-notices").json();
    }

    private static String friendOf(User user, Group group) {
        ApiClient.Response friend = user.client().get(base(group) + "/my-secret-friend");
        assertThat(friend.status()).isEqualTo(200);
        return friend.json().get("name").asString();
    }

    private int pairsOf(Group group) {
        Integer count = jdbc.queryForObject(
                "select count(*) from draw_assignment where group_id = ?::uuid", Integer.class, group.id());
        return count == null ? 0 : count;
    }

    private static void assertProblem(ApiClient.Response response, int status, String type) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.json().get("type").asString()).isEqualTo("/problems/" + type);
    }

    @Test
    void askingRejectingApprovingAndDrawingAgain() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");
        Group group = drawnGroup(ana, bruno, carla, davi);
        String brunosFriend = friendOf(bruno, group);

        // 7: o organizador não pode sair
        assertProblem(askToLeave(ana, group), 409, "organizer-cannot-leave");

        // 1: o pedido fica pendente, o organizador o vê ao vivo e o sorteio continua valendo
        try (ApiClient.EventStream anaEvents = ana.client().openEvents(base(group) + "/events")) {
            assertThat(anaEvents.status()).isEqualTo(200);
            ApiClient.Response asked = askToLeave(bruno, group);
            assertThat(asked.status()).isEqualTo(201);
            assertThat(asked.json().propertyNames()).containsExactlyInAnyOrder("id", "userId", "name", "requestedAt");
            assertThat(asked.json().get("name").asString()).isEqualTo("Bruno");
            assertThat(anaEvents.awaitLineContaining("group-updated", Duration.ofSeconds(5)))
                    .isTrue();
        }
        JsonNode forAna = detail(ana, group);
        assertThat(forAna.get("pendingExitRequests")).hasSize(1);
        assertThat(forAna.get("pendingExitRequests").get(0).get("name").asString())
                .isEqualTo("Bruno");
        assertThat(detail(bruno, group).get("myExitRequest").get("name").asString())
                .isEqualTo("Bruno");
        assertThat(detail(carla, group).get("pendingExitRequests")).isEmpty();
        assertThat(friendOf(bruno, group)).isEqualTo(brunosFriend);
        assertProblem(askToLeave(bruno, group), 409, "exit-request-already-pending");

        // 2: o próprio participante cancela; nada muda e não há aviso para ele
        assertThat(bruno.client().delete(base(group) + "/exit-requests/mine").status())
                .isEqualTo(204);
        assertThat(detail(bruno, group).get("myExitRequest").isNull()).isTrue();
        assertThat(notices(bruno)).isEmpty();

        // 3: recusado — continua no grupo com o mesmo amigo, é avisado e pode pedir de novo
        String second = askToLeave(bruno, group).json().get("id").asString();
        try (ApiClient.EventStream brunoEvents = bruno.client().openEvents(base(group) + "/events")) {
            assertThat(decide(ana, group, second, "reject").status()).isEqualTo(200);
            assertThat(brunoEvents.awaitLineContaining("group-updated", Duration.ofSeconds(5)))
                    .isTrue();
        }
        JsonNode rejected = notices(bruno);
        assertThat(rejected).hasSize(1);
        assertThat(rejected.get(0).get("status").asString()).isEqualTo("REJECTED");
        assertThat(rejected.get(0).get("groupName").asString()).isEqualTo("Natal");
        String rejectedId = rejected.get(0).get("id").asString();
        assertThat(bruno.client()
                        .post("/api/v1/exit-request-notices/" + rejectedId + "/ack")
                        .status())
                .isEqualTo(204);
        assertThat(bruno.client()
                        .post("/api/v1/exit-request-notices/" + rejectedId + "/ack")
                        .status())
                .isEqualTo(204);
        assertThat(notices(bruno)).isEmpty();
        assertProblem(decide(ana, group, second, "approve"), 409, "exit-request-not-pending");
        assertThat(friendOf(bruno, group)).isEqualTo(brunosFriend);

        // Ninguém marca como visto o aviso de outra pessoa
        assertThat(carla.client()
                        .post("/api/v1/exit-request-notices/" + rejectedId + "/ack")
                        .status())
                .isEqualTo(404);

        // 4: aprovado — sai, o sorteio é desfeito para todos e quem está com o grupo aberto é avisado
        String third = askToLeave(bruno, group).json().get("id").asString();
        ApiClient.Response approved;
        try (ApiClient.EventStream brunoEvents = bruno.client().openEvents(base(group) + "/events");
                ApiClient.EventStream carlaEvents = carla.client().openEvents(base(group) + "/events")) {
            assertProblem(decide(carla, group, third, "approve"), 403, "forbidden");
            approved = decide(ana, group, third, "approve");
            assertThat(approved.status()).isEqualTo(200);
            assertThat(brunoEvents.awaitLineContaining("exit-approved", Duration.ofSeconds(5)))
                    .isTrue();
            assertThat(carlaEvents.awaitLineContaining("draw-undone", Duration.ofSeconds(5)))
                    .isTrue();
            assertThat(String.join("\n", carlaEvents.lines())).doesNotContain(brunosFriend, "Bruno");
        }
        assertThat(approved.json().get("status").asString()).isEqualTo("OPEN");
        assertThat(approved.json().get("participantCount").asInt()).isEqualTo(3);
        assertThat(approved.json().get("pendingExitRequests")).isEmpty();

        // SC-008: nenhum par sobra e ninguém vê o amigo do sorteio desfeito
        assertThat(pairsOf(group)).isZero();
        for (User stayed : List.of(ana, carla, davi)) {
            assertProblem(stayed.client().get(base(group) + "/my-secret-friend"), 409, "group-not-drawn");
        }
        assertThat(bruno.client().get(base(group)).status()).isEqualTo(404);
        JsonNode forCarla = detail(carla, group);
        assertThat(forCarla.get("drawUndone").asBoolean()).isTrue();
        assertThat(forCarla.get("myConfirmation").asString()).isEqualTo("CONFIRMED");

        // O aviso da aprovação fica guardado para quem saiu (tela inicial)
        JsonNode left = notices(bruno);
        assertThat(left).hasSize(1);
        assertThat(left.get(0).get("status").asString()).isEqualTo("APPROVED");
        assertThat(left.get(0).get("groupName").asString()).isEqualTo("Natal");
        assertThat(left.toString()).doesNotContain(brunosFriend);

        // 8: quem saiu não entra de novo
        assertProblem(
                bruno.client().post("/api/v1/groups/join", Map.of("code", group.code())), 409, "rejoin-not-allowed");

        // 5: novo sorteio só entre quem ficou
        assertThat(detail(ana, group).get("canDraw").asBoolean()).isTrue();
        assertThat(ana.client().post(base(group) + "/draw").status()).isEqualTo(200);
        assertThat(pairsOf(group)).isEqualTo(3);
        Map<String, String> drew = new HashMap<>();
        for (User stayed : List.of(ana, carla, davi)) {
            drew.put(stayed.name(), friendOf(stayed, group));
        }
        drew.forEach((giver, receiver) -> assertThat(receiver).isNotEqualTo(giver));
        assertThat(new HashSet<>(drew.values())).containsExactlyInAnyOrder("Ana", "Carla", "Davi");
        assertThat(detail(ana, group).get("drawUndone").asBoolean()).isFalse();
    }

    @Test
    void approvingOneRequestCancelsTheOthersAndFewerThanThreeCannotDraw() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = drawnGroup(ana, bruno, carla);
        String brunos = askToLeave(bruno, group).json().get("id").asString();
        askToLeave(carla, group);

        assertThat(decide(ana, group, brunos, "approve").status()).isEqualTo(200);

        // Edge Case: o outro pedido é cancelado e a pessoa é avisada
        JsonNode cancelled = notices(carla);
        assertThat(cancelled).hasSize(1);
        assertThat(cancelled.get(0).get("status").asString()).isEqualTo("CANCELLED");
        JsonNode forCarla = detail(carla, group);
        assertThat(forCarla.get("status").asString()).isEqualTo("OPEN");
        assertThat(forCarla.get("myExitRequest").isNull()).isTrue();

        // 6: com menos de 3, o novo sorteio é recusado
        assertProblem(ana.client().post(base(group) + "/draw"), 422, "not-enough-participants");
        // Antes do sorteio, a pessoa sai direto
        assertProblem(askToLeave(carla, group), 409, "group-not-drawn");
    }

    @Test
    void aPendingRequestSurvivesAChangeOfTheEventDetails() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = drawnGroup(ana, bruno, carla);
        askToLeave(bruno, group);

        Map<String, Object> details = new HashMap<>();
        details.put("description", null);
        details.put("eventDate", inAMonth());
        details.put("location", "Salão");
        assertThat(ana.client().put(base(group) + "/event-details", details).status())
                .isEqualTo(200);

        assertThat(detail(bruno, group).get("myExitRequest").isNull()).isFalse();
        assertThat(detail(ana, group).get("pendingExitRequests")).hasSize(1);
    }

    @Test
    void approvingAndDrawingAtTheSameTimeNeverKeepsPairsOfWhoLeft() throws Exception {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");
        // Uma segunda sessão da organizadora, como outra aba
        ApiClient anaOtherTab = newClient();
        assertThat(anaOtherTab.login(ana.email(), "senha-forte").status()).isEqualTo(200);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 50; round++) {
                Group group = drawnGroup(ana, bruno, carla, davi);
                String request = askToLeave(bruno, group).json().get("id").asString();

                CountDownLatch start = new CountDownLatch(1);
                Future<Integer> approve = pool.submit(() -> {
                    start.await();
                    return decide(ana, group, request, "approve").status();
                });
                Future<Integer> draw = pool.submit(() -> {
                    start.await();
                    return anaOtherTab.post(base(group) + "/draw").status();
                });
                start.countDown();

                assertThat(approve.get()).isEqualTo(200);
                int drawStatus = draw.get();
                Integer brunosPairs = jdbc.queryForObject(
                        "select count(*) from draw_assignment where group_id = ?::uuid and giver_id = ?::uuid",
                        Integer.class,
                        group.id(),
                        bruno.id());
                assertThat(brunosPairs).as("rodada %d", round).isZero();
                // O sorteio veio antes (409: já sorteado) ou depois da aprovação (novo sorteio entre os 3)
                if (drawStatus == 200) {
                    assertThat(pairsOf(group)).as("rodada %d", round).isEqualTo(3);
                } else {
                    assertThat(drawStatus).as("rodada %d", round).isEqualTo(409);
                    assertThat(pairsOf(group)).as("rodada %d", round).isZero();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void routesOnlyShowTheSessionUsersOwnData() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = drawnGroup(ana, bruno, carla);
        askToLeave(bruno, group);
        User outsider = newUser("Eva");

        assertThat(askToLeave(outsider, group).status()).isEqualTo(404);
        assertThat(outsider.client().delete(base(group) + "/exit-requests/mine").status())
                .isEqualTo(404);
        assertThat(notices(outsider)).isEmpty();
        // Carla não cancela o pedido do Bruno: só o próprio
        assertThat(carla.client().delete(base(group) + "/exit-requests/mine").status())
                .isEqualTo(404);
        assertThat(detail(bruno, group).get("myExitRequest").isNull()).isFalse();

        List<String> names = new ArrayList<>();
        detail(ana, group)
                .get("pendingExitRequests")
                .forEach(r -> names.add(r.get("name").asString()));
        assertThat(Set.copyOf(names)).containsExactly("Bruno");
    }
}
