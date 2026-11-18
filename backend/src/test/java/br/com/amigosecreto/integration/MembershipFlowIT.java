package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Sair e remover participantes antes do sorteio (US4, quickstart passo 11). */
class MembershipFlowIT extends AbstractIntegrationTest {

    private record User(ApiClient client, String id, String name) {}

    private User newUser(String name) {
        ApiClient client = newClient();
        ApiClient.Response response =
                client.register(name, name.toLowerCase() + "." + UUID.randomUUID() + "@ex.com", "senha-forte");
        return new User(client, response.json().get("id").asString(), name);
    }

    private static List<String> names(JsonNode group) {
        List<String> names = new ArrayList<>();
        group.get("participants").forEach(p -> names.add(p.get("name").asString()));
        return names;
    }

    @Test
    void participantLeavesAndOrganizerRemovesBeforeTheDraw() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        User davi = newUser("Davi");
        JsonNode group =
                ana.client().post("/api/v1/groups", Map.of("name", "Natal")).json();
        String groupId = group.get("id").asString();
        String code = group.get("code").asString();
        for (User user : List.of(bruno, carla, davi)) {
            user.client().post("/api/v1/groups/join", Map.of("code", code));
        }

        // B sai: perde o acesso e some da lista de todos
        assertThat(bruno.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/me")
                        .status())
                .isEqualTo(204);
        assertThat(bruno.client().get("/api/v1/groups/" + groupId).status()).isEqualTo(404);
        assertThat(names(ana.client().get("/api/v1/groups/" + groupId).json())).containsExactly("Ana", "Carla", "Davi");

        // O organizador não sai; e só ele remove
        assertThat(ana.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/me")
                        .status())
                .isEqualTo(409);
        assertThat(davi.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/" + carla.id())
                        .status())
                .isEqualTo(403);
        assertThat(ana.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/" + ana.id())
                        .status())
                .isEqualTo(409);

        // C está na tela do grupo: recebe membership-revoked e a conexão é encerrada
        ApiClient.EventStream carlaEvents = carla.client().openEvents("/api/v1/groups/" + groupId + "/events");
        ApiClient.EventStream daviEvents = davi.client().openEvents("/api/v1/groups/" + groupId + "/events");
        assertThat(carlaEvents.status()).isEqualTo(200);
        assertThat(daviEvents.status()).isEqualTo(200);

        assertThat(ana.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/" + carla.id())
                        .status())
                .isEqualTo(204);

        assertThat(carlaEvents.awaitLineContaining("membership-revoked", Duration.ofSeconds(5)))
                .isTrue();
        assertThat(carlaEvents.awaitClosedByServer(Duration.ofSeconds(5))).isTrue();
        assertThat(String.join("\n", carlaEvents.lines())).doesNotContain("Ana").doesNotContain("Davi");
        // Ex-membro não recebe mais nenhum sinal do grupo: a conexão fecha antes do aviso aos demais
        assertThat(carlaEvents.lines()).noneMatch(line -> line.contains("participants-changed"));
        // D só recebe o sinal de mudança, nunca o aviso de remoção de outra pessoa
        assertThat(daviEvents.awaitLineContaining("participants-changed", Duration.ofSeconds(5)))
                .isTrue();
        assertThat(daviEvents.lines()).noneMatch(line -> line.contains("membership-revoked"));
        daviEvents.close();

        assertThat(carla.client().get("/api/v1/groups/" + groupId).status()).isEqualTo(404);
        assertThat(ana.client()
                        .delete("/api/v1/groups/" + groupId + "/participants/" + carla.id())
                        .status())
                .isEqualTo(404);

        // C pode voltar enquanto o grupo está OPEN
        assertThat(carla.client()
                        .post("/api/v1/groups/join", Map.of("code", code))
                        .status())
                .isEqualTo(200);
        assertThat(names(ana.client().get("/api/v1/groups/" + groupId).json())).containsExactly("Ana", "Davi", "Carla");
    }

    @Test
    void afterTheDrawNobodyLeavesOrIsRemoved() {
        User ana = newUser("Ana");
        User bruno = newUser("Bruno");
        User carla = newUser("Carla");
        JsonNode group =
                ana.client().post("/api/v1/groups", Map.of("name", "Firma")).json();
        String groupId = group.get("id").asString();
        bruno.client()
                .post("/api/v1/groups/join", Map.of("code", group.get("code").asString()));
        carla.client()
                .post("/api/v1/groups/join", Map.of("code", group.get("code").asString()));
        assertThat(ana.client().post("/api/v1/groups/" + groupId + "/draw").status())
                .isEqualTo(200);

        ApiClient.Response leave = bruno.client().delete("/api/v1/groups/" + groupId + "/participants/me");
        ApiClient.Response remove = ana.client().delete("/api/v1/groups/" + groupId + "/participants/" + carla.id());

        assertThat(leave.status()).isEqualTo(409);
        assertThat(leave.json().get("type").asString()).isEqualTo("/problems/group-already-drawn");
        assertThat(remove.status()).isEqualTo(409);
        assertThat(remove.json().get("type").asString()).isEqualTo("/problems/group-already-drawn");
        assertThat(ana.client()
                        .get("/api/v1/groups/" + groupId)
                        .json()
                        .get("participantCount")
                        .asInt())
                .isEqualTo(3);
    }
}
