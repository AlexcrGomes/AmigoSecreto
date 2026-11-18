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
