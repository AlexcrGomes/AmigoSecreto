package br.com.amigosecreto.common;

import org.springframework.http.HttpStatus;

/** Conflito com o estado atual (ex.: {@code group-full}, {@code group-already-drawn}). */
public class ConflictException extends ApiException {

    public ConflictException(String type, String detail) {
        super(HttpStatus.CONFLICT, type, "Conflito", detail);
    }
}
