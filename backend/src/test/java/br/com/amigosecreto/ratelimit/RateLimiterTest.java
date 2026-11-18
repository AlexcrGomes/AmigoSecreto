package br.com.amigosecreto.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.amigosecreto.common.RateLimitExceededException;
import br.com.amigosecreto.ratelimit.RateLimitProperties.Limit;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RateLimiterTest {

    /** Valores reais da research R4, fixados aqui de propósito (o perfil test relaxa o cadastro). */
    private static final RateLimitProperties R4 = new RateLimitProperties(
            new Limit(5, Duration.ofMinutes(15)),
            new Limit(10, Duration.ofHours(1)),
            new Limit(10, Duration.ofMinutes(10)),
            new Limit(5, Duration.ofMinutes(1)));

    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RateLimiter(R4);
    }

    @ParameterizedTest
    @CsvSource({"LOGIN_FAILURE, 5", "REGISTER, 10", "JOIN_INVALID_CODE, 10", "DRAW, 5"})
    void attemptAfterCapacityIsDeniedWithRetryAfter(RateLimitPolicy policy, int capacity) {
        for (int i = 0; i < capacity; i++) {
            limiter.consume(policy, "chave");
        }

        assertThatThrownBy(() -> limiter.consume(policy, "chave"))
                .isInstanceOfSatisfying(
                        RateLimitExceededException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds())
                                .isPositive()
                                .isLessThanOrEqualTo(
                                        R4.forPolicy(policy).window().toSeconds()));
    }

    @Test
    void differentKeysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.consume(RateLimitPolicy.LOGIN_FAILURE, "a@x.com|1.1.1.1");
        }

        assertThatCode(() -> limiter.consume(RateLimitPolicy.LOGIN_FAILURE, "b@x.com|1.1.1.1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> limiter.consume(RateLimitPolicy.DRAW, "a@x.com|1.1.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void resetReleasesTheKeyAgain() {
        for (int i = 0; i < 5; i++) {
            limiter.consume(RateLimitPolicy.LOGIN_FAILURE, "chave");
        }

        limiter.reset(RateLimitPolicy.LOGIN_FAILURE, "chave");

        assertThatCode(() -> limiter.consume(RateLimitPolicy.LOGIN_FAILURE, "chave"))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureNotExhaustedOnlyBlocksAfterCapacityIsUsed() {
        for (int i = 0; i < 5; i++) {
            assertThatCode(() -> limiter.ensureNotExhausted(RateLimitPolicy.LOGIN_FAILURE, "chave"))
                    .doesNotThrowAnyException();
            limiter.consume(RateLimitPolicy.LOGIN_FAILURE, "chave");
        }

        assertThatThrownBy(() -> limiter.ensureNotExhausted(RateLimitPolicy.LOGIN_FAILURE, "chave"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void limitsMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Limit(0, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Limit(1, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new Limit(1, null));
    }

    @Test
    void propertiesRequireEveryPolicy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateLimitProperties(null, R4.register(), R4.joinInvalidCode(), R4.draw()));
    }
}
