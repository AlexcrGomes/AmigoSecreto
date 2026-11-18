package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;

/**
 * A API implementada e o contrato (contracts/openapi.yaml) têm exatamente as mesmas rotas e métodos.
 * Roda no perfil {@code dev}, o único em que o springdoc gera a documentação.
 */
@ActiveProfiles({"test", "dev"})
class OpenApiContractIT extends AbstractIntegrationTest {

    private static final Path CONTRACT = Path.of("../specs/001-amigo-secreto-sorteio/contracts/openapi.yaml");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Test
    void implementedRoutesMatchTheContract() throws IOException {
        assertThat(CONTRACT)
                .as("o teste roda a partir de backend/ com o repositório completo")
                .exists();

        ApiClient client = newClient();
        client.register("Doc", "doc." + UUID.randomUUID() + "@ex.com", "senha-forte");
        ApiClient.Response docs = client.get("/v3/api-docs");
        assertThat(docs.status()).isEqualTo(200);

        Set<String> generated = new TreeSet<>();
        JsonNode paths = docs.json().get("paths");
        for (String path : paths.propertyNames()) {
            for (String method : paths.get(path).propertyNames()) {
                if (HTTP_METHODS.contains(method)) {
                    generated.add(method.toUpperCase() + " " + path);
                }
            }
        }

        assertThat(generated).isEqualTo(contractRoutes());
    }

    @Test
    void apiDocsRequireLoginEvenInDev() {
        assertThat(newClient().get("/v3/api-docs").status()).isEqualTo(401);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> contractRoutes() throws IOException {
        Map<String, Object> contract;
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            contract = new Yaml().load(in);
        }
        Set<String> routes = new TreeSet<>();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) contract.get("paths");
        paths.forEach((path, operations) -> operations.keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(method -> routes.add(method.toUpperCase() + " /api/v1" + path)));
        return routes;
    }
}
