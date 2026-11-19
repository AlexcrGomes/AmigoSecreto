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
            assertThat(mine.json().propertyNames()).containsExactly("name");
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
                    .forEach(p ->
                            assertThat(p.propertyNames()).containsExactlyInAnyOrder("userId", "name", "organizer"));

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
