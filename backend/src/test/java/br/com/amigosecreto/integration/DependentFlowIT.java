package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.amigosecreto.draw.AssignmentCipher;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Participantes dependentes de ponta a ponta (feature 004, quickstart.md §2). */
class DependentFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AssignmentCipher cipher;

    private record User(ApiClient client, String id, String name) {}

    private record Group(String id, String code) {}

    private User newUser(String name) {
        ApiClient client = newClient();
        String email = name.toLowerCase() + "." + UUID.randomUUID() + "@ex.com";
        ApiClient.Response registered = client.register(name, email, "senha-forte");
        return new User(client, registered.json().get("id").asString(), name);
    }

    private static String inDays(int days) {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(days).toString();
    }

    private static Group groupOf(User organizer, User... guests) {
        JsonNode created = organizer
                .client()
                .createGroup("Natal", Map.of("eventDate", inDays(30), "location", "Casa da Ana"))
                .json();
        Group group =
                new Group(created.get("id").asString(), created.get("code").asString());
        for (User guest : guests) {
            assertThat(guest.client()
                            .post("/api/v1/groups/join", Map.of("code", group.code()))
                            .status())
                    .isEqualTo(200);
        }
        return group;
    }

    private static String base(Group group) {
        return "/api/v1/groups/" + group.id();
    }

    private static String dependents(Group group) {
        return base(group) + "/my-dependents";
    }

    private static String addDependent(User guardian, Group group, String name) {
        ApiClient.Response added = guardian.client().post(dependents(group), Map.of("name", name));
        assertThat(added.status()).isEqualTo(201);
        return added.json().get("id").asString();
    }

    private static JsonNode detail(User user, Group group) {
        return user.client().get(base(group)).json();
    }

    private static JsonNode participant(JsonNode detail, String userId) {
        for (JsonNode participant : detail.get("participants")) {
            if (participant.get("userId").asString().equals(userId)) {
                return participant;
            }
        }
        return null;
    }

    private static List<String> names(JsonNode detail) {
        List<String> names = new ArrayList<>();
        detail.get("participants")
                .forEach(participant -> names.add(participant.get("name").asString()));
        return names;
    }

    private static void assertProblem(ApiClient.Response response, int status, String type) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.json().get("type").asString()).isEqualTo("/problems/" + type);
    }

    private int appUsers(String id) {
        Integer count = jdbc.queryForObject("select count(*) from app_user where id = ?::uuid", Integer.class, id);
        return count == null ? 0 : count;
    }

    // ---- US1 ----

    @Test
    void aGuardianAddsRenamesAndRemovesADependentAndEveryoneSeesIt() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);

        String lucas = addDependent(bruno, group, "  Lucas ");

        JsonNode forAna = detail(ana, group);
        assertThat(forAna.get("participantCount").asInt()).isEqualTo(4);
        JsonNode lucasRow = participant(forAna, lucas);
        assertThat(lucasRow.get("name").asString()).isEqualTo("Lucas");
        assertThat(lucasRow.get("dependent").asBoolean()).isTrue();
        assertThat(lucasRow.get("guardianName").asString()).isEqualTo("Bruno");
        assertThat(participant(forAna, bruno.id()).get("dependent").asBoolean()).isFalse();
        assertThat(forAna.get("myDependents")).isEmpty();
        JsonNode forBruno = detail(bruno, group);
        assertThat(forBruno.get("myDependents")).hasSize(1);
        assertThat(forBruno.get("myDependents").get(0).get("id").asString()).isEqualTo(lucas);
        assertThat(forBruno.get("myDependents")
                        .get(0)
                        .get("wishlist")
                        .get("maxItems")
                        .asInt())
                .isEqualTo(5);
        assertThat(forBruno.get("maxDependents").asInt()).isEqualTo(5);
        assertThat(forBruno.get("canAddDependents").asBoolean()).isTrue();

        assertProblem(bruno.client().post(dependents(group), Map.of("name", " L ")), 400, "validation-error");
        assertThat(bruno.client()
                        .put(dependents(group) + "/" + lucas, Map.of("name", "Lucas M."))
                        .status())
                .isEqualTo(200);
        assertThat(names(detail(carla, group))).contains("Lucas M.");

        // Quem não é o responsável não age pelo dependente (FR-007): 404 como se não existisse
        assertThat(carla.client()
                        .put(dependents(group) + "/" + lucas, Map.of("name", "Outro"))
                        .status())
                .isEqualTo(404);
        assertThat(carla.client().delete(dependents(group) + "/" + lucas).status())
                .isEqualTo(404);
        assertThat(ana.client().delete(dependents(group) + "/" + lucas).status())
                .isEqualTo(404);

        assertThat(bruno.client().delete(dependents(group) + "/" + lucas).status())
                .isEqualTo(204);
        assertThat(names(detail(ana, group))).doesNotContain("Lucas M.");
        assertThat(appUsers(lucas)).isZero();
    }

    @Test
    void atMostFiveDependentsAndNoneAfterTheDraw() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);
        for (int i = 1; i <= 5; i++) {
            addDependent(bruno, group, "Filho " + i);
        }

        assertProblem(bruno.client().post(dependents(group), Map.of("name", "Filho 6")), 409, "dependents-limit");
        JsonNode forBruno = detail(bruno, group);
        assertThat(forBruno.get("canAddDependents").asBoolean()).isFalse();
        assertThat(forBruno.get("dependentsBlockedReason").asString()).isEqualTo("dependents-limit");

        bruno.client().confirm(group.id());
        carla.client().confirm(group.id());
        assertThat(ana.client().post(base(group) + "/draw").status()).isEqualTo(200);

        assertProblem(carla.client().post(dependents(group), Map.of("name", "Davi")), 409, "group-already-drawn");
        assertThat(detail(carla, group).get("dependentsBlockedReason").asString())
                .isEqualTo("group-already-drawn");
    }

    @Test
    void dependentsConfirmWithTheGuardianAndOnlyAccountsAreListedAsPending() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);
        String lucas = addDependent(bruno, group, "Lucas");
        String bia = addDependent(ana, group, "Bia");
        carla.client().confirm(group.id());

        assertThat(participant(detail(ana, group), bia).get("confirmed").asBoolean())
                .isTrue();
        assertThat(participant(detail(ana, group), lucas).get("confirmed").asBoolean())
                .isFalse();
        ApiClient.Response pending = ana.client().post(base(group) + "/draw");
        assertProblem(pending, 422, "participants-pending");
        assertThat(pending.json().get("detail").asString()).isEqualTo("Falta 1 confirmar: Bruno.");

        bruno.client().confirm(group.id());
        assertThat(participant(detail(ana, group), lucas).get("confirmed").asBoolean())
                .isTrue();

        // Mudar a data volta todos a pendente, menos o organizador e os dependentes dele (R36)
        Map<String, Object> details = new HashMap<>();
        details.put("eventDate", inDays(40));
        details.put("location", "Casa da Ana");
        assertThat(ana.client().put(base(group) + "/event-details", details).status())
                .isEqualTo(200);
        JsonNode after = detail(ana, group);
        assertThat(participant(after, bruno.id()).get("confirmed").asBoolean()).isFalse();
        assertThat(participant(after, lucas).get("confirmed").asBoolean()).isFalse();
        assertThat(participant(after, bia).get("confirmed").asBoolean()).isTrue();
        assertThat(participant(after, ana.id()).get("confirmed").asBoolean()).isTrue();
    }

    @Test
    void aGroupWithoutAValidDrawIsRefusedAndOtherwiseGuardianAndDependentNeverDrawEachOther() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        Group group = groupOf(ana, bruno);
        String lucas = addDependent(bruno, group, "Lucas");
        bruno.client().confirm(group.id());

        JsonNode blocked = detail(ana, group);
        assertThat(blocked.get("drawBlockedReason").asString()).isEqualTo("no-valid-draw");
        assertThat(blocked.get("canDraw").asBoolean()).isFalse();
        ApiClient.Response refused = ana.client().post(base(group) + "/draw");
        assertProblem(refused, 422, "no-valid-draw");
        assertThat(refused.json().get("detail").asString())
                .isEqualTo("Com os dependentes deste grupo, não há sorteio possível. Convide mais participantes.");
        Integer pairs = jdbc.queryForObject(
                "select count(*) from draw_assignment where group_id = ?::uuid", Integer.class, group.id());
        assertThat(pairs).isZero();

        User carla = newUser("Carla");
        User davi = newUser("Davi");
        for (User guest : List.of(carla, davi)) {
            guest.client().post("/api/v1/groups/join", Map.of("code", group.code()));
            guest.client().confirm(group.id());
        }
        assertThat(ana.client().post(base(group) + "/draw").status()).isEqualTo(200);

        UUID groupId = UUID.fromString(group.id());
        UUID brunoId = UUID.fromString(bruno.id());
        UUID lucasId = UUID.fromString(lucas);
        assertThat(receiverOf(groupId, brunoId)).isNotEqualTo(lucasId).isNotEqualTo(brunoId);
        assertThat(receiverOf(groupId, lucasId)).isNotEqualTo(brunoId).isNotEqualTo(lucasId);
    }

    private UUID receiverOf(UUID groupId, UUID giverId) {
        byte[] encrypted = jdbc.queryForObject(
                "select receiver_enc from draw_assignment where group_id = ? and giver_id = ?",
                byte[].class,
                groupId,
                giverId);
        return cipher.decrypt(encrypted, groupId, giverId);
    }

    @Test
    void dependentsLeaveWithTheirGuardianBeforeTheDraw() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);
        String lucas = addDependent(bruno, group, "Lucas");
        String bia = addDependent(bruno, group, "Bia");
        String davi = addDependent(carla, group, "Davi");
        String eva = addDependent(carla, group, "Eva");

        assertThat(bruno.client().delete(base(group) + "/participants/me").status())
                .isEqualTo(204);
        assertThat(names(detail(ana, group))).doesNotContain("Bruno", "Lucas", "Bia");
        assertThat(appUsers(lucas) + appUsers(bia)).isZero();

        // O organizador remove um dependente direto e depois o responsável, que leva o outro
        assertThat(ana.client().delete(base(group) + "/participants/" + davi).status())
                .isEqualTo(204);
        assertThat(appUsers(davi)).isZero();
        assertThat(ana.client()
                        .delete(base(group) + "/participants/" + carla.id())
                        .status())
                .isEqualTo(204);
        assertThat(appUsers(eva)).isZero();
        assertThat(detail(ana, group).get("participantCount").asInt()).isEqualTo(1);
    }

    // ---- US2 ----

    @Test
    void theGuardianRevealsWhoEachDependentDrewUntilTheDrawIsUndone() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");
        Group group = groupOf(ana, bruno, carla, davi);
        String lucas = addDependent(bruno, group, "Lucas");
        String bia = addDependent(bruno, group, "Bia");
        Map<String, Object> item = new HashMap<>();
        item.put("description", "Livro");
        item.put("url", null);
        assertThat(carla.client()
                        .post(base(group) + "/wishlist/mine/items", item)
                        .status())
                .isEqualTo(201);
        for (User member : List.of(bruno, carla, davi)) {
            member.client().confirm(group.id());
        }

        assertProblem(bruno.client().get(base(group) + "/my-dependents/secret-friends"), 409, "group-not-drawn");
        assertThat(ana.client().post(base(group) + "/draw").status()).isEqualTo(200);

        JsonNode results = bruno.client()
                .get(base(group) + "/my-dependents/secret-friends")
                .json();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("dependentId").asString()).isEqualTo(lucas);
        assertThat(results.get(0).get("dependentName").asString()).isEqualTo("Lucas");
        assertThat(results.get(1).get("dependentId").asString()).isEqualTo(bia);
        for (JsonNode result : results) {
            String drew = result.get("secretFriend").get("name").asString();
            assertThat(drew)
                    .isNotEqualTo("Bruno")
                    .isNotEqualTo(result.get("dependentName").asString());
            if (drew.equals("Carla")) {
                assertThat(result.get("secretFriend")
                                .get("wishlist")
                                .get(0)
                                .get("description")
                                .asString())
                        .isEqualTo("Livro");
            }
        }

        // Uma saída aprovada desfaz o sorteio: o resultado dos dependentes some junto (como na 002)
        ApiClient.Response exit = davi.client().post(base(group) + "/exit-requests");
        assertThat(exit.status()).isEqualTo(201);
        String requestId = exit.json().get("id").asString();
        assertThat(ana.client()
                        .post(base(group) + "/exit-requests/" + requestId + "/approve")
                        .status())
                .isEqualTo(200);
        assertProblem(bruno.client().get(base(group) + "/my-dependents/secret-friends"), 409, "group-not-drawn");
    }

    // ---- US3 ----

    @Test
    void theGuardianBuildsTheDependentsListAsksForItsExitAndThenCannotAddMore() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");
        Group group = groupOf(ana, bruno, carla, davi);
        String lucas = addDependent(bruno, group, "Lucas");
        addDependent(bruno, group, "Bia");
        String lucasItems = dependents(group) + "/" + lucas + "/wishlist/items";

        // Lista de desejos do dependente (FR-014): só o responsável edita
        Map<String, Object> item = new HashMap<>();
        item.put("description", "Bola");
        item.put("url", null);
        ApiClient.Response added = bruno.client().post(lucasItems, item);
        assertThat(added.status()).isEqualTo(201);
        assertThat(added.json().get("items").get(0).get("description").asString())
                .isEqualTo("Bola");
        assertThat(carla.client().post(lucasItems, item).status()).isEqualTo(404);
        JsonNode lucasRow = detail(bruno, group).get("myDependents").get(0);
        assertThat(lucasRow.get("wishlist").get("items")).hasSize(1);
        assertThat(detail(bruno, group).get("myWishlist").get("items")).isEmpty();

        for (User member : List.of(bruno, carla, davi)) {
            member.client().confirm(group.id());
        }
        assertThat(ana.client().post(base(group) + "/draw").status()).isEqualTo(200);

        // Quem tirou o Lucas vê a lista dele
        for (User member : List.of(ana, carla, davi)) {
            JsonNode friend =
                    member.client().get(base(group) + "/my-secret-friend").json();
            if (friend.get("name").asString().equals("Lucas")) {
                assertThat(friend.get("wishlist").get(0).get("description").asString())
                        .isEqualTo("Bola");
            }
        }

        // Pedido de saída do dependente (FR-015): o organizador vê de quem é
        String exitPath = dependents(group) + "/" + lucas + "/exit-request";
        assertThat(carla.client().post(exitPath).status()).isEqualTo(404);
        ApiClient.Response asked = bruno.client().post(exitPath);
        assertThat(asked.status()).isEqualTo(201);
        assertProblem(bruno.client().post(exitPath), 409, "exit-request-already-pending");
        assertThat(detail(bruno, group)
                        .get("myDependents")
                        .get(0)
                        .get("exitRequest")
                        .get("id")
                        .asString())
                .isEqualTo(asked.json().get("id").asString());
        JsonNode pending = detail(ana, group).get("pendingExitRequests").get(0);
        assertThat(pending.get("name").asString()).isEqualTo("Lucas");
        assertThat(pending.get("guardianName").asString()).isEqualTo("Bruno");

        String requestId = asked.json().get("id").asString();
        assertThat(ana.client()
                        .post(base(group) + "/exit-requests/" + requestId + "/approve")
                        .status())
                .isEqualTo(200);
        JsonNode reopened = detail(bruno, group);
        assertThat(reopened.get("status").asString()).isEqualTo("OPEN");
        assertThat(names(reopened)).doesNotContain("Lucas").contains("Bia");
        assertThat(appUsers(lucas))
                .as("o pedido guarda o registro do dependente")
                .isEqualTo(1);

        // O aviso da decisão vai para o responsável, com o nome do dependente
        JsonNode notices = bruno.client().get("/api/v1/exit-request-notices").json();
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).get("subjectName").asString()).isEqualTo("Lucas");
        assertThat(notices.get(0).get("status").asString()).isEqualTo("APPROVED");
        assertThat(carla.client()
                        .post("/api/v1/exit-request-notices/" + requestId + "/ack")
                        .status())
                .isEqualTo(404);
        assertThat(bruno.client()
                        .post("/api/v1/exit-request-notices/" + requestId + "/ack")
                        .status())
                .isEqualTo(204);

        // Não readicionar (Clarifications Q3): só para o Bruno, naquele grupo
        assertProblem(bruno.client().post(dependents(group), Map.of("name", "Lucas")), 409, "dependents-locked");
        assertThat(detail(bruno, group).get("dependentsBlockedReason").asString())
                .isEqualTo("dependents-locked");
        assertThat(detail(bruno, group).get("canAddDependents").asBoolean()).isFalse();
        assertThat(carla.client()
                        .post(dependents(group), Map.of("name", "Davi Jr"))
                        .status())
                .isEqualTo(201);
    }

    @Test
    void theGuardiansCancelledRequestChangesNothing() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);
        String lucas = addDependent(bruno, group, "Lucas");
        bruno.client().confirm(group.id());
        carla.client().confirm(group.id());
        assertThat(ana.client().post(base(group) + "/draw").status()).isEqualTo(200);

        String exitPath = dependents(group) + "/" + lucas + "/exit-request";
        assertThat(bruno.client().post(exitPath).status()).isEqualTo(201);
        assertThat(bruno.client().delete(exitPath).status()).isEqualTo(204);
        assertProblem(bruno.client().delete(exitPath), 409, "exit-request-not-pending");

        JsonNode after = detail(bruno, group);
        assertThat(after.get("status").asString()).isEqualTo("DRAWN");
        assertThat(after.get("myDependents").get(0).hasNonNull("exitRequest")).isFalse();
        assertThat(bruno.client().get("/api/v1/exit-request-notices").json()).isEmpty();
    }
}
