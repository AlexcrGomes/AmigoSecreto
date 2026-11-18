package br.com.amigosecreto.common;

import org.springframework.http.HttpStatus;

/** Requisição válida, mas que a regra de negócio não permite (ex.: {@code not-enough-participants}). */
public class UnprocessableException extends ApiException {

    public UnprocessableException(String type, String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, type, "Não foi possível concluir", detail);
    }
}
