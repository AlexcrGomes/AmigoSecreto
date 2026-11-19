package br.com.amigosecreto.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Erro de domínio que vira um ProblemDetail (RFC 9457) com {@code type = /problems/<slug>}.
 * A mensagem ({@code detail}) é exibida ao usuário: MUST estar em pt-BR e MUST NOT conter pares do
 * sorteio nem dados de outros usuários (Constituição I).
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String title;
    private final Map<String, Object> properties;

    protected ApiException(HttpStatus status, String type, String title, String detail) {
        this(status, type, title, detail, Map.of());
    }

    /**
     * Com propriedades extras no problema (ex.: {@code pending} em {@code participants-pending}). Valem as
     * mesmas regras da mensagem: nunca pares do sorteio.
     */
    protected ApiException(
            HttpStatus status, String type, String title, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Slug do problema, sem o prefixo {@code /problems/}. */
    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    /** Propriedades extras do problema, além de {@code type}, {@code title}, {@code status} e {@code detail}. */
    public Map<String, Object> getProperties() {
        return properties;
    }
}
