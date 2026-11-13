package br.com.amigosecreto.common;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Fábrica de ProblemDetail no formato do contrato: {@code type = /problems/<slug>}, textos em pt-BR. */
public final class Problems {

    public static final String PREFIX = "/problems/";

    private Problems() {}

    public static ProblemDetail of(HttpStatusCode status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PREFIX + slug));
        problem.setTitle(title);
        return problem;
    }
}
