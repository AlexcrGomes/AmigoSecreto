package br.com.amigosecreto.common;

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

    protected ApiException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
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
}
