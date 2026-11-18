package br.com.amigosecreto.common;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "not-found", "Não encontrado", detail);
    }
}
