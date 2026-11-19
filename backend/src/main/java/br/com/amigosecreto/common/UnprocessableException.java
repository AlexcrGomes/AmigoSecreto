package br.com.amigosecreto.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Requisição válida, mas que a regra de negócio não permite (ex.: {@code not-enough-participants}). */
public class UnprocessableException extends ApiException {

    public UnprocessableException(String type, String detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, type, "Não foi possível concluir", detail);
    }

    public UnprocessableException(String type, String detail, Map<String, Object> properties) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, type, "Não foi possível concluir", detail, properties);
    }
}
