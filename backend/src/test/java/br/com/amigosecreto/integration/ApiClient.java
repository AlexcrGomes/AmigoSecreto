package br.com.amigosecreto.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP de um usuário simulado nos testes de integração. Mantém os cookies próprios (sessão
 * {@code SESSION} e {@code XSRF-TOKEN}) e envia o cabeçalho {@code X-XSRF-TOKEN} nas requisições que
 * alteram estado, exatamente como o frontend.
 */
public final class ApiClient {

    private final String baseUrl;
    private final JsonMapper jsonMapper;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;

    public ApiClient(String baseUrl, JsonMapper jsonMapper) {
        this.baseUrl = baseUrl;
        this.jsonMapper = jsonMapper;
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public record Response(int status, HttpHeaders headers, String body, JsonMapper jsonMapper) {

        public JsonNode json() {
            return jsonMapper.readTree(body);
        }

        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }
    }

    // ---- fluxos de autenticação ----

    public Response register(String name, String email, String password) {
        ensureCsrf();
        return post("/api/v1/auth/register", Map.of("name", name, "email", email, "password", password));
    }

    public Response login(String email, String password) {
        ensureCsrf();
        return post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    /** Garante o cookie XSRF-TOKEN, como o frontend faz ao abrir o app. */
    public void ensureCsrf() {
        if (cookie("XSRF-TOKEN").isEmpty()) {
            get("/api/v1/auth/csrf");
        }
    }

    // ---- grupos ----

    /**
     * Cria um grupo com detalhes do evento válidos: data daqui a 30 dias (em Brasília) e um local fixo. Os
     * testes que não tratam dos detalhes do evento usam esta forma.
     */
    public Response createGroup(String name) {
        return createGroup(
                name,
                Map.of(
                        "eventDate",
                        LocalDate.now(ZoneId.of("America/Sao_Paulo"))
                                .plusDays(30)
                                .toString(),
                        "location",
                        "Local de teste"));
    }

    /** Cria um grupo com os detalhes do evento informados (os testes da US1 da 002). */
    public Response createGroup(String name, Map<String, Object> eventDetails) {
        Map<String, Object> body = new LinkedHashMap<>(eventDetails);
        body.put("name", name);
        return post("/api/v1/groups", body);
    }

    /**
     * O usuário deste cliente confirma a participação na versão atual dos detalhes do evento (US2 da 002),
     * lida do próprio grupo, como a tela faz.
     */
    public Response confirm(String groupId) {
        int version =
                get("/api/v1/groups/" + groupId).json().get("detailsVersion").asInt();
        return post("/api/v1/groups/" + groupId + "/confirmation", Map.of("detailsVersion", version));
    }

    // ---- verbos ----

    public Response get(String path) {
        return send(request(path).GET());
    }

    public Response post(String path, Object body) {
        return send(withXsrf(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))));
    }

    public Response post(String path) {
        return send(withXsrf(request(path).POST(HttpRequest.BodyPublishers.noBody())));
    }

    public Response put(String path, Object body) {
        return send(withXsrf(request(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))));
    }

    public Response delete(String path) {
        return send(withXsrf(request(path).DELETE()));
    }

    /** Abre um fluxo SSE e passa a guardar as linhas recebidas, em segundo plano. */
    public EventStream openEvents(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        return new EventStream(http.sendAsync(request, HttpResponse.BodyHandlers.ofLines()));
    }

    /** Linhas de um fluxo SSE aberto. */
    public static final class EventStream implements AutoCloseable {

        private final List<String> lines = new CopyOnWriteArrayList<>();
        private final CompletableFuture<HttpResponse<Stream<String>>> response;
        private final CompletableFuture<Void> reader;

        EventStream(CompletableFuture<HttpResponse<Stream<String>>> response) {
            this.response = response;
            this.reader = response.thenAcceptAsync(
                    r -> {
                        try (Stream<String> body = r.body()) {
                            body.forEach(lines::add);
                        } catch (RuntimeException closed) {
                            // fluxo encerrado pelo teste
                        }
                    },
                    Executors.newVirtualThreadPerTaskExecutor());
        }

        public int status() {
            return response.join().statusCode();
        }

        public List<String> lines() {
            return List.copyOf(lines);
        }

        /** Espera até alguma linha conter o texto. */
        public boolean awaitLineContaining(String text, Duration timeout) {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (lines.stream().anyMatch(line -> line.contains(text))) {
                    return true;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }

        /** Espera o servidor encerrar o fluxo. */
        public boolean awaitClosedByServer(Duration timeout) {
            try {
                reader.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                return true;
            } catch (java.util.concurrent.TimeoutException e) {
                return false;
            } catch (java.util.concurrent.ExecutionException e) {
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public void close() {
            response.thenAccept(r -> r.body().close());
            reader.cancel(true);
        }
    }

    /** Valor atual de um cookie deste cliente. */
    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    // ---- internos ----

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/problem+json");
    }

    private HttpRequest.Builder withXsrf(HttpRequest.Builder builder) {
        cookie("XSRF-TOKEN").ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        return builder;
    }

    private String toJson(Object body) {
        return body instanceof String text ? text : jsonMapper.writeValueAsString(body);
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers(), response.body(), jsonMapper);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", e);
        }
    }
}
