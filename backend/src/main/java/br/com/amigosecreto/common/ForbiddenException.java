package br.com.amigosecreto.common;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

    public ForbiddenException(String detail) {
        super(HttpStatus.FORBIDDEN, "forbidden", "Acesso negado", detail);
    }
}
