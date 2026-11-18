package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Metas de desempenho: sorteio de 100 pessoas em < 1 s; 100 usuários simultâneos em ≤ 2 s (SC-008). */
class DrawPerformanceIT extends AbstractIntegrationTest {

    private record Timed(int status, long millis) {}

    @Test
    void hundredMembersDrawUnderOneSecondAndHundredParallelReadsUnderTwo() throws Exception {
        // 100 contas reais (cadastro em paralelo: o BCrypt custo 12 é a parte cara)
        List<ApiClient> users = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<Future<ApiClient>> created = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int n = i;
                created.add(pool.submit(() -> {
                    ApiClient client = newClient();
                    assertThat(client.register(
                                            "Pessoa " + n, "p" + n + "." + UUID.randomUUID() + "@ex.com", "senha-forte")
                                    .status())
                            .isEqualTo(201);
                    return client;
                }));
            }
            for (Future<ApiClient> client : created) {
                users.add(client.get());
            }
        }

        ApiClient organizer = users.getFirst();
        var group =
                organizer.post("/api/v1/groups", Map.of("name", "Firma grande")).json();
        String groupId = group.get("id").asString();
        String code = group.get("code").asString();
        for (ApiClient member : users.subList(1, users.size())) {
            assertThat(member.post("/api/v1/groups/join", Map.of("code", code)).status())
                    .isEqualTo(200);
        }

        long start = System.nanoTime();
        ApiClient.Response draw = organizer.post("/api/v1/groups/" + groupId + "/draw");
        long drawMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(draw.status()).isEqualTo(200);
        assertThat(drawMillis).as("sorteio de 100 membros em ms").isLessThan(1_000);

        CountDownLatch go = new CountDownLatch(1);
        List<Timed> reads = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(100)) {
            List<Future<Timed>> results = new ArrayList<>();
            for (ApiClient member : users) {
                results.add(pool.submit(() -> {
                    go.await();
                    long t0 = System.nanoTime();
                    int status = member.get("/api/v1/groups/" + groupId).status();
                    return new Timed(
                            status, Duration.ofNanos(System.nanoTime() - t0).toMillis());
                }));
            }
            go.countDown();
            for (Future<Timed> result : results) {
                reads.add(result.get());
            }
        }

        assertThat(reads).allMatch(read -> read.status() == 200);
        assertThat(reads.stream().mapToLong(Timed::millis).max().orElseThrow())
                .as("pior tempo entre 100 leituras simultâneas, em ms")
                .isLessThanOrEqualTo(2_000);
    }
}
