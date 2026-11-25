package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sigilo dos pares de ponta a ponta (Constituição I, NÃO NEGOCIÁVEL): cada um vê só o próprio amigo
 * secreto, e nenhum outro caminho — resposta, evento, banco ou log — revela quem tirou quem.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecrecyIT extends AbstractIntegrationTest {

    private static final List<String> PAIR_KEYS = List.of("receiver", "secretfriend", "assignment", "giver");

    @Autowired
    private JdbcTemplate jdbc;

    private record Member(ApiClient client, UUID id, String name) {}

    private Member newMember(String name) {
        ApiClient client = newClient();
        ApiClient.Response response = client.register(
                name, name.toLowerCase(Locale.ROOT) + "." + UUID.randomUUID() + "@ex.com", "senha-forte");
        return new Member(client, UUID.fromString(response.json().get("id").asString()), name);
    }

    @Test
    void nobodyCanLearnAnotherPersonsPair(CapturedOutput output) {
        List<Member> members = new ArrayList<>();
        for (String name : List.of("Ana", "Bruno", "Carla", "Davi", "Eva")) {
            members.add(newMember(name));
        }
        Member organizer = members.getFirst();
        ApiClient.Response created = organizer.client().createGroup("Sigilo");
        String groupId = created.json().get("id").asString();
        String code = created.json().get("code").asString();
        for (Member member : members.subList(1, members.size())) {
            assertThat(member.client()
                            .post("/api/v1/groups/join", Map.of("code", code))
                            .status())
                    .isEqualTo(200);
            assertThat(member.client().confirm(groupId).status()).isEqualTo(200);
        }

        List<ApiClient.EventStream> streams = new ArrayList<>();
        for (Member member : members) {
            ApiClient.EventStream stream = member.client().openEvents("/api/v1/groups/" + groupId + "/events");
            assertThat(stream.status()).isEqualTo(200);
            streams.add(stream);
        }

        ApiClient.Response draw = organizer.client().post("/api/v1/groups/" + groupId + "/draw");
        assertThat(draw.status()).isEqualTo(200);
        assertNoPairKeys(draw.body());
        assertThat(draw.json().propertyNames()).containsExactlyInAnyOrder("groupId", "status", "drawnAt");

        // (a) cada um vê um nome diferente do próprio, e os 5 formam um derangement
        Map<String, String> drew = new HashMap<>();
        for (Member member : members) {
            ApiClient.Response mine = member.client().get("/api/v1/groups/" + groupId + "/my-secret-friend");
            assertThat(mine.status()).isEqualTo(200);
            assertThat(mine.header("Cache-Control")).contains("no-store");
            assertThat(mine.json().propertyNames()).containsExactlyInAnyOrder("name", "wishlist", "wishlistUpdatedAt");
            drew.put(member.name(), mine.json().get("name").asString());
        }
        drew.forEach((giver, receiver) -> assertThat(receiver).isNotEqualTo(giver));
        assertThat(new HashSet<>(drew.values()))
                .containsExactlyInAnyOrderElementsOf(
                        members.stream().map(Member::name).toList());

        // (b) nenhuma outra resposta nem evento traz pares — nem para o organizador
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            ApiClient.Response list = member.client().get("/api/v1/groups");
            ApiClient.Response detail = member.client().get("/api/v1/groups/" + groupId);
            assertNoPairKeys(list.body());
            assertNoPairKeys(detail.body());
            detail.json()
                    .get("participants")
                    .forEach(p -> assertThat(p.propertyNames())
                            .containsExactlyInAnyOrder("userId", "name", "organizer", "confirmed"));

            ApiClient.EventStream stream = streams.get(i);
            assertThat(stream.awaitLineContaining("group-drawn", Duration.ofSeconds(5)))
                    .as("aviso do sorteio em até 5 s (SC-004)")
                    .isTrue();
            String events = String.join("\n", stream.lines());
            assertNoPairKeys(events);
            for (Member other : members) {
                assertThat(events).doesNotContain(other.id().toString()).doesNotContain(other.name());
            }
            stream.close();
        }

        // (c) não há como pedir o resultado de outra pessoa: o parâmetro é ignorado
        Member bruno = members.get(1);
        for (Member other : members) {
            ApiClient.Response attempt = bruno.client()
                    .get("/api/v1/groups/" + groupId + "/my-secret-friend?userId=" + other.id() + "&giverId="
                            + other.id());
            assertThat(attempt.json().get("name").asString()).isEqualTo(drew.get("Bruno"));
        }

        // (d) no banco, só bytes cifrados: nenhum UUID de usuário em texto ou em bytes
        List<byte[]> stored = jdbc.queryForList(
                "select receiver_enc from draw_assignment where group_id = ?::uuid", byte[].class, groupId);
        assertThat(stored).hasSize(5);
        for (byte[] encrypted : stored) {
            String hex = HexFormat.of().formatHex(encrypted);
            String text = new String(encrypted, StandardCharsets.ISO_8859_1);
            for (Member member : members) {
                assertThat(hex).doesNotContain(HexFormat.of().formatHex(bytes(member.id())));
                assertThat(text).doesNotContain(member.id().toString());
            }
        }

        // (e) nada de pares nos logs da execução inteira
        for (Member member : members) {
            assertThat(output.getAll()).doesNotContain(member.id().toString());
        }
    }

    /**
     * Pedido de saída e sorteio desfeito (US4 da 002, FR-032, SC-008): nenhuma resposta, aviso ou evento das
     * rotas novas diz quem a pessoa que saiu havia tirado, e os pares somem do banco.
     */
    @Test
    void exitRequestsNeverRevealWhoTheLeavingPersonDrew() {
        List<Member> members = new ArrayList<>();
        for (String name : List.of("Ana", "Bruno", "Carla", "Davi")) {
            members.add(newMember(name));
        }
        Member organizer = members.getFirst();
        Member bruno = members.get(1);
        ApiClient.Response created = organizer.client().createGroup("Saída");
        String groupId = created.json().get("id").asString();
        String code = created.json().get("code").asString();
        for (Member member : members.subList(1, members.size())) {
            member.client().post("/api/v1/groups/join", Map.of("code", code));
            member.client().confirm(groupId);
        }
        assertThat(organizer
                        .client()
                        .post("/api/v1/groups/" + groupId + "/draw")
                        .status())
                .isEqualTo(200);
        String brunosFriend = bruno.client()
                .get("/api/v1/groups/" + groupId + "/my-secret-friend")
                .json()
                .get("name")
                .asString();

        List<ApiClient.EventStream> streams = new ArrayList<>();
        for (Member member : members) {
            streams.add(member.client().openEvents("/api/v1/groups/" + groupId + "/events"));
        }

        ApiClient.Response asked = bruno.client().post("/api/v1/groups/" + groupId + "/exit-requests");
        assertThat(asked.status()).isEqualTo(201);
        assertNoPairKeys(asked.body());
        assertThat(asked.body()).doesNotContain(brunosFriend);

        ApiClient.Response forOrganizer = organizer.client().get("/api/v1/groups/" + groupId);
        assertNoPairKeys(forOrganizer.body());
        forOrganizer
                .json()
                .get("pendingExitRequests")
                .forEach(r ->
                        assertThat(r.propertyNames()).containsExactlyInAnyOrder("id", "userId", "name", "requestedAt"));

        String requestId = asked.json().get("id").asString();
        ApiClient.Response approved =
                organizer.client().post("/api/v1/groups/" + groupId + "/exit-requests/" + requestId + "/approve");
        assertThat(approved.status()).isEqualTo(200);
        assertNoPairKeys(approved.body());

        ApiClient.Response notices = bruno.client().get("/api/v1/exit-request-notices");
        assertNoPairKeys(notices.body());
        assertThat(notices.body()).doesNotContain(brunosFriend);

        for (int i = 0; i < members.size(); i++) {
            ApiClient.EventStream stream = streams.get(i);
            String expected = i == 1 ? "membership-revoked" : "draw-undone";
            assertThat(stream.awaitLineContaining(expected, Duration.ofSeconds(5)))
                    .isTrue();
            String events = String.join("\n", stream.lines());
            assertNoPairKeys(events);
            assertThat(events).doesNotContain(brunosFriend).doesNotContain("Bruno");
            stream.close();
        }

        Integer pairs = jdbc.queryForObject(
                "select count(*) from draw_assignment where group_id = ?::uuid", Integer.class, groupId);
        assertThat(pairs).isZero();
        for (Member member : members) {
            ApiClient.Response mine = member.client().get("/api/v1/groups/" + groupId + "/my-secret-friend");
            assertThat(mine.status()).isIn(404, 409);
            assertThat(mine.body()).doesNotContain(brunosFriend);
        }
    }

    /**
     * Lista de desejos (feature 003, FR-009, FR-016, SC-002, SC-005): cada um recebe só a lista de quem tirou, pelo
     * resultado do próprio sorteio; nenhuma outra resposta, evento ou log mostra a lista de outra pessoa nem diz
     * se ela tem itens — nem para o organizador.
     */
    @Test
    void eachWishlistOnlyReachesWhoDrewItsOwner(CapturedOutput output) {
        List<Member> members = new ArrayList<>();
        for (String name : List.of("Ana", "Bruno", "Carla")) {
            members.add(newMember(name));
        }
        Member organizer = members.getFirst();
        ApiClient.Response created = organizer.client().createGroup("Listas");
        String groupId = created.json().get("id").asString();
        String code = created.json().get("code").asString();
        for (Member member : members.subList(1, members.size())) {
            member.client().post("/api/v1/groups/join", Map.of("code", code));
            member.client().confirm(groupId);
        }
        Map<String, String> wishOf = new HashMap<>();
        for (Member member : members) {
            String wish = "Desejo de " + member.name() + " " + UUID.randomUUID();
            wishOf.put(member.name(), wish);
            assertThat(member.client()
                            .post("/api/v1/groups/" + groupId + "/wishlist/mine/items", Map.of("description", wish))
                            .status())
                    .isEqualTo(201);
        }

        List<ApiClient.EventStream> streams = new ArrayList<>();
        for (Member member : members) {
            streams.add(member.client().openEvents("/api/v1/groups/" + groupId + "/events"));
        }
        assertThat(organizer
                        .client()
                        .post("/api/v1/groups/" + groupId + "/draw")
                        .status())
                .isEqualTo(200);

        for (Member member : members) {
            ApiClient.Response mine = member.client().get("/api/v1/groups/" + groupId + "/my-secret-friend");
            String drew = mine.json().get("name").asString();
            List<String> received = new ArrayList<>();
            mine.json()
                    .get("wishlist")
                    .forEach(item -> received.add(item.get("description").asString()));
            assertThat(received)
                    .as("%s recebe só a lista de quem tirou", member.name())
                    .containsExactly(wishOf.get(drew));
            mine.json()
                    .get("wishlist")
                    .forEach(item -> assertThat(item.propertyNames()).containsExactlyInAnyOrder("description", "url"));

            // Trocar o usuário na URL não muda nada
            ApiClient.Response attempt =
                    member.client().get("/api/v1/groups/" + groupId + "/my-secret-friend?userId=" + organizer.id());
            assertThat(attempt.body()).isEqualTo(mine.body());

            // FR-009 e FR-016: o detalhe e a lista de grupos só têm a própria lista e nada sobre a dos outros
            ApiClient.Response detail = member.client().get("/api/v1/groups/" + groupId);
            ApiClient.Response list = member.client().get("/api/v1/groups");
            for (Member other : members) {
                if (other != member) {
                    assertThat(detail.body()).doesNotContain(wishOf.get(other.name()));
                    assertThat(list.body()).doesNotContain(wishOf.get(other.name()));
                }
            }
            assertThat(detail.json().get("myWishlist").get("items")).hasSize(1);
            detail.json()
                    .get("participants")
                    .forEach(p -> assertThat(p.propertyNames())
                            .containsExactlyInAnyOrder("userId", "name", "organizer", "confirmed"));
            assertThat(list.json().get(0).toString().toLowerCase(Locale.ROOT)).doesNotContain("wish");
        }

        // SC-005: nenhum evento nem log traz itens de lista
        for (ApiClient.EventStream stream : streams) {
            assertThat(stream.awaitLineContaining("group-drawn", Duration.ofSeconds(5)))
                    .isTrue();
            String events = String.join("\n", stream.lines());
            wishOf.values().forEach(wish -> assertThat(events).doesNotContain(wish));
            stream.close();
        }
        wishOf.values().forEach(wish -> assertThat(output.getAll()).doesNotContain(wish));
    }

    private static void assertNoPairKeys(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        for (String key : PAIR_KEYS) {
            assertThat(lower).doesNotContain(key);
        }
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
