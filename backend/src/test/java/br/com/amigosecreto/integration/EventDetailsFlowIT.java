package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Descrição, data e local do evento (US1 da feature 002, quickstart passos 1–4). */
class EventDetailsFlowIT extends AbstractIntegrationTest {

    private static final String DESCRIPTION = "Traga um prato.\nValor sugerido: R$ 50";
    private static final String LOCATION = "<b>casa</b> & quintal";

    private ApiClient newUser(String name) {
        ApiClient client = newClient();
        client.register(name, name.toLowerCase() + "." + UUID.randomUUID() + "@ex.com", "senha-forte");
        return client;
    }

    private static String inAMonth() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(30).toString();
    }

    @Test
    void everyMemberSeesTheSameDetailsAndOnlyTheOrganizerEditsThemLive() {
        ApiClient ana = newUser("Ana");
        ApiClient bruno = newUser("Bruno");
        ApiClient carla = newUser("Carla");
        String date = inAMonth();

        Map<String, Object> details = new HashMap<>();
        details.put("description", DESCRIPTION);
        details.put("eventDate", date);
        details.put("eventTime", "19:30");
        details.put("location", LOCATION);
        ApiClient.Response created = ana.createGroup("Natal", details);
        assertThat(created.status()).isEqualTo(201);
        JsonNode group = created.json();
        String groupId = group.get("id").asString();
        assertThat(group.get("detailsVersion").asInt()).isEqualTo(1);
        assertThat(group.get("eventDetailsMissing").asBoolean()).isFalse();

        // B entra e vê exatamente o mesmo texto: sem escape nem HTML (FR-007)
        JsonNode joined = bruno.post(
                        "/api/v1/groups/join", Map.of("code", group.get("code").asString()))
                .json();
        JsonNode seen = joined.get("eventDetails");
        assertThat(seen.get("description").asString()).isEqualTo(DESCRIPTION);
        assertThat(seen.get("eventDate").asString()).isEqualTo(date);
        assertThat(seen.get("eventTime").asString()).isEqualTo("19:30");
        assertThat(seen.get("location").asString()).isEqualTo(LOCATION);

        // A tela inicial mostra a data
        JsonNode summary = bruno.get("/api/v1/groups").json().get(0);
        assertThat(summary.get("eventDate").asString()).isEqualTo(date);
        assertThat(summary.get("eventTime").asString()).isEqualTo("19:30");

        // A muda o local; B, na tela do grupo, recebe o sinal em até 5 s (SC-004)
        try (ApiClient.EventStream events = bruno.openEvents("/api/v1/groups/" + groupId + "/events")) {
            assertThat(events.status()).isEqualTo(200);

            ApiClient.Response updated = ana.put(
                    "/api/v1/groups/" + groupId + "/event-details",
                    Map.of("eventDate", date, "eventTime", "19:30", "location", "Salão"));

            assertThat(updated.status()).isEqualTo(200);
            assertThat(updated.json().get("eventDetails").get("location").asString())
                    .isEqualTo("Salão");
            assertThat(updated.json().get("eventDetails").get("description").isNull())
                    .isTrue();
            assertThat(updated.json().get("detailsVersion").asInt()).isEqualTo(2);
            assertThat(events.awaitLineContaining("group-updated", Duration.ofSeconds(5)))
                    .isTrue();
            assertThat(String.join("\n", events.lines()))
                    .doesNotContain("Salão")
                    .doesNotContain("Ana");
        }
        assertThat(bruno.get("/api/v1/groups/" + groupId)
                        .json()
                        .get("eventDetails")
                        .get("location")
                        .asString())
                .isEqualTo("Salão");

        // Só o organizador edita; quem não é membro nem sabe que o grupo existe
        Map<String, Object> attempt = Map.of("eventDate", date, "location", "Casa do Bruno");
        assertThat(bruno.put("/api/v1/groups/" + groupId + "/event-details", attempt)
                        .status())
                .isEqualTo(403);
        assertThat(carla.put("/api/v1/groups/" + groupId + "/event-details", attempt)
                        .status())
                .isEqualTo(404);
    }

    @Test
    void pastDatesAreRejectedWithAClearMessage() {
        ApiClient ana = newUser("Ana");
        String yesterday =
                LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1).toString();

        ApiClient.Response response = ana.createGroup("Natal", Map.of("eventDate", yesterday, "location", "Casa"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("type").asString()).isEqualTo("/problems/event-date-in-past");
        assertThat(response.json().get("detail").asString())
                .isEqualTo("A data do evento precisa ser hoje ou no futuro.");
        assertThat(ana.get("/api/v1/groups").json().size()).isZero();
    }
}
