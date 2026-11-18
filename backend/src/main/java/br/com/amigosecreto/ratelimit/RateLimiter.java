package br.com.amigosecreto.ratelimit;

import br.com.amigosecreto.common.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas em memória (Bucket4j). Cada política tem um cache Caffeine de buckets por chave,
 * que expira depois de uma janela sem uso — nessa altura o bucket já estaria cheio de novo.
 */
@Component
public class RateLimiter {

    private final RateLimitProperties properties;
    private final Map<RateLimitPolicy, Cache<String, Bucket>> buckets = new EnumMap<>(RateLimitPolicy.class);

    public RateLimiter(RateLimitProperties properties) {
        this.properties = properties;
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            buckets.put(
                    policy,
                    Caffeine.newBuilder()
                            .expireAfterAccess(properties.forPolicy(policy).window())
                            .maximumSize(100_000)
                            .build());
        }
    }

    /** Consome uma tentativa; lança {@link RateLimitExceededException} (429) se a chave esgotou o limite. */
    public void consume(RateLimitPolicy policy, String key) {
        ConsumptionProbe probe = bucket(policy, key).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(toRetryAfterSeconds(probe.getNanosToWaitForRefill()));
        }
    }

    /** Recusa (429) se a chave já esgotou o limite, sem consumir nada (ex.: login bloqueado). */
    public void ensureNotExhausted(RateLimitPolicy policy, String key) {
        Bucket bucket = buckets.get(policy).getIfPresent(key);
        if (bucket == null) {
            return;
        }
        EstimationProbe estimate = bucket.estimateAbilityToConsume(1);
        if (!estimate.canBeConsumed()) {
            throw new RateLimitExceededException(toRetryAfterSeconds(estimate.getNanosToWaitForRefill()));
        }
    }

    /** Libera a chave (ex.: login bem-sucedido zera as falhas). */
    public void reset(RateLimitPolicy policy, String key) {
        buckets.get(policy).invalidate(key);
    }

    private Bucket bucket(RateLimitPolicy policy, String key) {
        return buckets.get(policy).get(key, k -> newBucket(policy));
    }

    private Bucket newBucket(RateLimitPolicy policy) {
        RateLimitProperties.Limit limit = properties.forPolicy(policy);
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillIntervally(limit.capacity(), limit.window())
                        .build())
                .build();
    }

    private static long toRetryAfterSeconds(long nanos) {
        return Math.max(1, (long) Math.ceil(nanos / (double) TimeUnit.SECONDS.toNanos(1)));
    }
}
