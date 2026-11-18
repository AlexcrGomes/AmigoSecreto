package br.com.amigosecreto.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Capacidade e janela de cada política, em {@code app.rate-limit.*} (padrões em application.yml). */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Limit loginFailure, Limit register, Limit joinInvalidCode, Limit draw) {

    public RateLimitProperties {
        if (loginFailure == null || register == null || joinInvalidCode == null || draw == null) {
            throw new IllegalArgumentException("app.rate-limit: todas as políticas precisam ser configuradas");
        }
    }

    public Limit forPolicy(RateLimitPolicy policy) {
        return switch (policy) {
            case LOGIN_FAILURE -> loginFailure;
            case REGISTER -> register;
            case JOIN_INVALID_CODE -> joinInvalidCode;
            case DRAW -> draw;
        };
    }

    /** {@code capacity} tentativas a cada {@code window}. */
    public record Limit(int capacity, Duration window) {

        public Limit {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity precisa ser positivo");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("window precisa ser positiva");
            }
        }
    }
}
