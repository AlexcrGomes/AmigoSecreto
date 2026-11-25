package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.amigosecreto.group.GroupFinalizer;
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

/** Lista de desejos de ponta a ponta (feature 003): US1 — montar a própria lista. */
class WishlistFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GroupFinalizer finalizer;

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

    private static Group groupOf(User organizer, User... guests) {
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
        }
        return group;
    }

    private static String items(Group group) {
        return "/api/v1/groups/" + group.id() + "/wishlist/mine/items";
    }

    private static ApiClient.Response add(User user, Group group, String description, String url) {
        Map<String, Object> body = new HashMap<>();
        body.put("description", description);
        body.put("url", url);
        return user.client().post(items(group), body);
    }

    private static JsonNode myWishlist(User user, Group group) {
        return user.client().get("/api/v1/groups/" + group.id()).json().get("myWishlist");
    }

    private static List<String> descriptions(JsonNode wishlist) {
        List<String> names = new ArrayList<>();
        wishlist.get("items").forEach(item -> names.add(item.get("description").asString()));
        return names;
    }

    private static String idOf(JsonNode wishlist, String description) {
        for (JsonNode item : wishlist.get("items")) {
            if (item.get("description").asString().equals(description)) {
                return item.get("id").asString();
            }
        }
        throw new AssertionError("item não encontrado: " + description);
    }

    private static void assertProblem(ApiClient.Response response, int status, String type) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.json().get("type").asString()).isEqualTo("/problems/" + type);
    }

    @Test
    void eachParticipantBuildsTheirOwnListAndNobodyElseSeesIt() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);

        // 1: os itens aparecem na ordem de cadastro
        ApiClient.Response first = add(bruno, group, "Livro de receitas", null);
        assertThat(first.status()).isEqualTo(201);
        assertThat(add(bruno, group, "Caneca", "https://loja.com/caneca").status())
                .isEqualTo(201);
        ApiClient.Response third = add(bruno, group, "Meias", null);
        assertThat(descriptions(third.json())).containsExactly("Livro de receitas", "Caneca", "Meias");
        assertThat(third.json().get("maxItems").asInt()).isEqualTo(5);
        assertThat(third.json().get("editable").asBoolean()).isTrue();

        // 2 e 3: editar e remover
        JsonNode list = myWishlist(bruno, group);
        String meias = idOf(list, "Meias");
        ApiClient.Response edited =
                bruno.client().put(items(group) + "/" + idOf(list, "Caneca"), Map.of("description", "Caneca azul"));
        assertThat(edited.status()).isEqualTo(200);
        assertThat(bruno.client().delete(items(group) + "/" + meias).status()).isEqualTo(204);
        JsonNode after = myWishlist(bruno, group);
        assertThat(descriptions(after)).containsExactly("Livro de receitas", "Caneca azul");
        assertThat(after.get("items").get(1).get("url").isNull()).isTrue();

        // 7 e FR-012: ninguém mais vê a lista, nem a organizadora
        for (User other : List.of(ana, carla)) {
            ApiClient.Response detail = other.client().get("/api/v1/groups/" + group.id());
            assertThat(detail.body()).doesNotContain("Livro de receitas", "Caneca azul");
            assertThat(detail.json().get("myWishlist").get("items")).isEmpty();
            assertThat(other.client().get("/api/v1/groups").body()).doesNotContain("Livro de receitas");
        }

        // FR-006: ninguém altera a lista de outra pessoa
        String livro = idOf(after, "Livro de receitas");
        assertThat(carla.client()
                        .put(items(group) + "/" + livro, Map.of("description", "trocado"))
                        .status())
                .isEqualTo(404);
        assertThat(ana.client().delete(items(group) + "/" + livro).status()).isEqualTo(404);
        assertThat(descriptions(myWishlist(bruno, group))).contains("Livro de receitas");
    }

    @Test
    void invalidItemsAndTheSixthItemAreRefused() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        Group group = groupOf(ana, bruno);

        // 4: descrição vazia e link que não é endereço web
        ApiClient.Response empty = add(bruno, group, "  ", null);
        assertThat(empty.status()).isEqualTo(400);
        assertThat(empty.json().get("errors").get(0).get("field").asString()).isEqualTo("description");
        ApiClient.Response script = add(bruno, group, "Livro", "javascript:alert(1)");
        assertThat(script.status()).isEqualTo(400);
        assertThat(script.json().get("errors").get(0).get("field").asString()).isEqualTo("url");

        // 5: no máximo 5 sugestões
        for (int i = 1; i <= 5; i++) {
            assertThat(add(bruno, group, "Item " + i, null).status()).isEqualTo(201);
        }
        assertProblem(add(bruno, group, "Item 6", null), 409, "wishlist-full");
        assertThat(myWishlist(bruno, group).get("items")).hasSize(5);
    }

    @Test
    void theListIsPerGroupAndGoesAwayWhenThePersonLeaves() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        Group natal = groupOf(ana, bruno);
        Group firma = groupOf(ana, bruno);

        add(bruno, natal, "Livro", null);
        add(bruno, firma, "Caneca", null);
        assertThat(descriptions(myWishlist(bruno, natal))).containsExactly("Livro");
        assertThat(descriptions(myWishlist(bruno, firma))).containsExactly("Caneca");

        // FR-018: saiu do grupo, a lista daquele grupo é apagada
        assertThat(bruno.client()
                        .delete("/api/v1/groups/" + natal.id() + "/participants/me")
                        .status())
                .isEqualTo(204);
        Integer left = jdbc.queryForObject(
                "select count(*) from wish_item where group_id = ?::uuid and user_id = ?::uuid",
                Integer.class,
                natal.id(),
                bruno.id());
        assertThat(left).isZero();
        assertThat(descriptions(myWishlist(bruno, firma))).containsExactly("Caneca");
    }

    @Test
    void twoTabsEditingDifferentItemsKeepBothChanges() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        Group group = groupOf(ana, bruno);
        add(bruno, group, "Livro", null);
        JsonNode list = add(bruno, group, "Caneca", null).json();
        ApiClient otherTab = newClient();
        assertThat(otherTab.login(bruno.email(), "senha-forte").status()).isEqualTo(200);

        bruno.client().put(items(group) + "/" + idOf(list, "Livro"), Map.of("description", "Livro novo"));
        otherTab.put(items(group) + "/" + idOf(list, "Caneca"), Map.of("description", "Caneca nova"));

        assertThat(descriptions(myWishlist(bruno, group))).containsExactly("Livro novo", "Caneca nova");
    }

    @Test
    void aFinishedGroupKeepsTheListReadOnly() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);
        String itemId = idOf(add(bruno, group, "Livro", null).json(), "Livro");
        for (User guest : List.of(bruno, carla)) {
            assertThat(guest.client().confirm(group.id()).status()).isEqualTo(200);
        }
        assertThat(ana.client().post("/api/v1/groups/" + group.id() + "/draw").status())
                .isEqualTo(200);

        // Depois do sorteio, a lista ainda muda
        assertThat(add(bruno, group, "Caneca", null).status()).isEqualTo(201);

        jdbc.update(
                "update secret_group set event_starts_at = now() - interval '2 days',"
                        + " finalizes_at = now() - interval '1 day' where id = ?::uuid",
                group.id());
        finalizer.finishDueGroups();

        // 6: finalizado, a lista fica somente para leitura
        assertProblem(add(bruno, group, "Meias", null), 409, "group-finished");
        assertProblem(
                bruno.client().put(items(group) + "/" + itemId, Map.of("description", "x")), 409, "group-finished");
        assertProblem(bruno.client().delete(items(group) + "/" + itemId), 409, "group-finished");
        JsonNode list = myWishlist(bruno, group);
        assertThat(descriptions(list)).containsExactly("Livro", "Caneca");
        assertThat(list.get("editable").asBoolean()).isFalse();
    }
}
