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

    // ---- US2: ver a lista de desejos de quem tirei ----

    private static void confirmAndDraw(User organizer, Group group, User... guests) {
        for (User guest : guests) {
            assertThat(guest.client().confirm(group.id()).status()).isEqualTo(200);
        }
        assertThat(organizer
                        .client()
                        .post("/api/v1/groups/" + group.id() + "/draw")
                        .status())
                .isEqualTo(200);
    }

    private static JsonNode friend(User user, Group group) {
        ApiClient.Response response = user.client().get("/api/v1/groups/" + group.id() + "/my-secret-friend");
        assertThat(response.status()).isEqualTo(200);
        return response.json();
    }

    private static List<String> friendWishes(JsonNode friend) {
        List<String> wishes = new ArrayList<>();
        friend.get("wishlist")
                .forEach(item -> wishes.add(item.get("description").asString()));
        return wishes;
    }

    @Test
    void revealingShowsTheListOfWhoIDrewAndWhenItChangedAfterTheDraw() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);
        Map<String, User> byName = Map.of("Ana", ana, "Bruno", bruno, "Carla", carla);
        add(bruno, group, "Livro", "https://loja.com/livro");
        add(carla, group, "Perfume", null);
        // Ana não cadastra nada
        confirmAndDraw(ana, group, bruno, carla);

        for (User user : List.of(ana, bruno, carla)) {
            JsonNode mine = friend(user, group);
            User drawn = byName.get(mine.get("name").asString());
            // 1 e 2: a lista de quem tirou; vazia se a pessoa não cadastrou nada
            assertThat(friendWishes(mine)).isEqualTo(descriptions(myWishlist(drawn, group)));
            // Mudanças antes do sorteio não contam como "Atualizada em"
            assertThat(mine.get("wishlistUpdatedAt").isNull()).isTrue();
        }

        // 4: o dono muda a lista depois do sorteio; quem o tirou vê a versão nova, com a data
        User whoDrewBruno = List.of(ana, carla).stream()
                .filter(user -> friend(user, group).get("name").asString().equals("Bruno"))
                .findFirst()
                .orElseThrow();
        add(bruno, group, "Caneca", null);
        JsonNode updated = friend(whoDrewBruno, group);
        assertThat(friendWishes(updated)).containsExactly("Livro", "Caneca");
        assertThat(updated.get("wishlistUpdatedAt").isNull()).isFalse();
        assertThat(updated.get("wishlist").get(0).get("url").asString()).isEqualTo("https://loja.com/livro");
    }

    @Test
    void anUndoneDrawTakesTheListAwayFromWhoDrewThePersonBefore() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");
        Group group = groupOf(ana, bruno, carla, davi);
        add(bruno, group, "Livro", null);
        add(davi, group, "Bola", null);
        confirmAndDraw(ana, group, bruno, carla, davi);

        // Davi pede para sair e a organizadora aprova: o sorteio é desfeito
        String request = davi.client()
                .post("/api/v1/groups/" + group.id() + "/exit-requests")
                .json()
                .get("id")
                .asString();
        assertThat(ana.client()
                        .post("/api/v1/groups/" + group.id() + "/exit-requests/" + request + "/approve")
                        .status())
                .isEqualTo(200);

        // FR-013: ninguém vê mais lista nenhuma até o novo sorteio
        for (User user : List.of(ana, bruno, carla)) {
            assertProblem(
                    user.client().get("/api/v1/groups/" + group.id() + "/my-secret-friend"), 409, "group-not-drawn");
        }
        // FR-018: a lista de quem saiu foi apagada
        Integer davis = jdbc.queryForObject(
                "select count(*) from wish_item where group_id = ?::uuid and user_id = ?::uuid",
                Integer.class,
                group.id(),
                davi.id());
        assertThat(davis).isZero();

        // Novo sorteio: só quem tirar o Bruno vê a lista dele, e ninguém vê a "Bola"
        assertThat(ana.client().post("/api/v1/groups/" + group.id() + "/draw").status())
                .isEqualTo(200);
        int seenByBruno = 0;
        for (User user : List.of(ana, bruno, carla)) {
            JsonNode mine = friend(user, group);
            assertThat(friendWishes(mine)).doesNotContain("Bola");
            if (mine.get("name").asString().equals("Bruno")) {
                assertThat(friendWishes(mine)).containsExactly("Livro");
                seenByBruno++;
            } else {
                assertThat(friendWishes(mine)).doesNotContain("Livro");
            }
        }
        assertThat(seenByBruno).isEqualTo(1);
    }

    @Test
    void aFinishedGroupStillShowsTheListWithTheName() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        Group group = groupOf(ana, bruno, carla);
        for (User user : List.of(ana, bruno, carla)) {
            add(user, group, "Desejo de " + user.name(), null);
        }
        confirmAndDraw(ana, group, bruno, carla);
        jdbc.update(
                "update secret_group set event_starts_at = now() - interval '2 days',"
                        + " finalizes_at = now() - interval '1 day' where id = ?::uuid",
                group.id());
        finalizer.finishDueGroups();

        // 7 e FR-014
        for (User user : List.of(ana, bruno, carla)) {
            JsonNode mine = friend(user, group);
            assertThat(friendWishes(mine))
                    .containsExactly("Desejo de " + mine.get("name").asString());
        }
    }

    // ---- US3: valor do presente no grupo ----

    private static Map<String, Object> details(String location, Object giftValue) {
        Map<String, Object> body = new HashMap<>();
        body.put("description", null);
        body.put("eventDate", inAMonth());
        body.put("location", location);
        body.put("giftValue", giftValue);
        return body;
    }

    private static JsonNode giftValueSeenBy(User user, Group group) {
        return user.client()
                .get("/api/v1/groups/" + group.id())
                .json()
                .get("eventDetails")
                .get("giftValue");
    }

    @Test
    void theGiftValueIsSharedAndOnlyChangesBeforeTheDraw() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        // 1: criado com o valor, todos o veem
        JsonNode created = ana.client()
                .createGroup("Natal", Map.of("eventDate", inAMonth(), "location", "Casa da Ana", "giftValue", 80))
                .json();
        Group group =
                new Group(created.get("id").asString(), created.get("code").asString());
        for (User guest : List.of(bruno, carla)) {
            guest.client().post("/api/v1/groups/join", Map.of("code", group.code()));
        }
        assertThat(giftValueSeenBy(bruno, group).asInt()).isEqualTo(80);
        String path = "/api/v1/groups/" + group.id() + "/event-details";

        // 2: mudar o valor antes do sorteio não pede nova confirmação
        assertThat(bruno.client().confirm(group.id()).status()).isEqualTo(200);
        assertThat(ana.client().put(path, details("Casa da Ana", 100)).status()).isEqualTo(200);
        JsonNode forBruno = bruno.client().get("/api/v1/groups/" + group.id()).json();
        assertThat(forBruno.get("eventDetails").get("giftValue").asInt()).isEqualTo(100);
        assertThat(forBruno.get("myConfirmation").asString()).isEqualTo("CONFIRMED");

        // 4: valor inválido
        assertThat(ana.client().put(path, details("Casa da Ana", 0)).status()).isEqualTo(400);

        // 5: quem não é organizador não muda
        assertThat(bruno.client().put(path, details("Casa da Ana", 50)).status())
                .isEqualTo(403);

        // 6: depois do sorteio, o valor fica fixo; os outros detalhes continuam editáveis
        carla.client().confirm(group.id());
        assertThat(ana.client().post("/api/v1/groups/" + group.id() + "/draw").status())
                .isEqualTo(200);
        assertProblem(ana.client().put(path, details("Salão", 50)), 409, "gift-value-locked");
        assertThat(ana.client().put(path, details("Salão", 100)).status()).isEqualTo(200);
        assertThat(giftValueSeenBy(carla, group).asInt()).isEqualTo(100);

        // Sorteio desfeito: o valor volta a ser editável até o novo sorteio
        String request = carla.client()
                .post("/api/v1/groups/" + group.id() + "/exit-requests")
                .json()
                .get("id")
                .asString();
        ana.client().post("/api/v1/groups/" + group.id() + "/exit-requests/" + request + "/approve");
        assertThat(ana.client().put(path, details("Salão", 120)).status()).isEqualTo(200);
        assertThat(giftValueSeenBy(bruno, group).asInt()).isEqualTo(120);
    }

    @Test
    void aGroupWithoutGiftValueShowsNone() {
        User ana = newUser("Ana");
        Group group = groupOf(ana);

        // 3
        assertThat(giftValueSeenBy(ana, group).isNull()).isTrue();
    }
}
