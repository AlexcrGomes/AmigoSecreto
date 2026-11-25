package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;

/**
 * A API implementada e os contratos (contracts/openapi.yaml das features 001, 002 e 003) batem. Os contratos
 * da 002 e da 003 listam só o que é novo ou mudou; as rotas valem pela união dos três arquivos (research R24 da
 * 002).
 *
 * <p>Enquanto a feature 003 é entregue em vários PRs, as rotas dela ainda não existem todas: o teste exige que
 * toda rota gerada esteja documentada e que todas as rotas da 001 e da 002 existam. A tarefa T040 da 003 (fim da
 * entrega) volta à igualdade completa. Roda no perfil {@code dev}, o único em que o springdoc gera a
 * documentação.
 */
@ActiveProfiles({"test", "dev"})
class OpenApiContractIT extends AbstractIntegrationTest {

    private static final Path CONTRACT_001 = Path.of("../specs/001-amigo-secreto-sorteio/contracts/openapi.yaml");
    private static final Path CONTRACT_002 = Path.of("../specs/002-grupo-evento-confirmacao/contracts/openapi.yaml");
    private static final Path CONTRACT_003 = Path.of("../specs/003-lista-desejos/contracts/openapi.yaml");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Test
    void implementedRoutesMatchTheContracts() throws IOException {
        for (Path contract : List.of(CONTRACT_001, CONTRACT_002, CONTRACT_003)) {
            assertThat(contract)
                    .as("o teste roda a partir de backend/ com o repositório completo")
                    .exists();
        }

        Set<String> generated = generatedRoutes();
        Set<String> delivered = new TreeSet<>(contractRoutes(CONTRACT_001));
        delivered.addAll(contractRoutes(CONTRACT_002));
        Set<String> documented = new TreeSet<>(delivered);
        documented.addAll(contractRoutes(CONTRACT_003));

        assertThat(documented)
                .as("toda rota implementada precisa estar em um dos contratos 001, 002 ou 003")
                .containsAll(generated);
        assertThat(generated)
                .as("todas as rotas das features 001 e 002 continuam existindo")
                .containsAll(delivered);
    }

    @Test
    void apiDocsRequireLoginEvenInDev() {
        assertThat(newClient().get("/v3/api-docs").status()).isEqualTo(401);
    }

    private Set<String> generatedRoutes() {
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
        return generated;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> contractRoutes(Path file) throws IOException {
        Map<String, Object> contract;
        try (InputStream in = Files.newInputStream(file)) {
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
