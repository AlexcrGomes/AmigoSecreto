package br.com.amigosecreto.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte toda exceção em {@code application/problem+json} (RFC 9457) com textos em pt-BR.
 * Erros inesperados viram um 500 genérico: nada de stack trace nem mensagem interna na resposta.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Erro de um campo na validação: {@code {field, message}}. */
    public record FieldError(String field, String message) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail problem = Problems.of(ex.getStatus(), ex.getType(), ex.getTitle(), ex.getMessage());
        ex.getProperties().forEach(problem::setProperty);
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(ex.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex instanceof RateLimitExceededException rateLimit) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.getRetryAfterSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro inesperado ao processar a requisição", ex);
        ProblemDetail problem = Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente.");
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Uma mensagem por campo (a primeira violação), que é o que o formulário exibe
        Map<String, String> firstByField = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> firstByField.putIfAbsent(error.getField(), error.getDefaultMessage()));
        List<FieldError> errors = firstByField.entrySet().stream()
                .map(entry -> new FieldError(entry.getKey(), entry.getValue()))
                .toList();
        ProblemDetail problem = Problems.of(
                HttpStatus.BAD_REQUEST, "validation-error", "Dados inválidos", "Verifique os campos destacados.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem =
                Problems.of(HttpStatus.BAD_REQUEST, "invalid-request", "Requisição inválida", "Requisição inválida.");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Padroniza também as exceções do Spring MVC (404, 405, 415…) no formato do contrato. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = isContractProblem(body) ? (ProblemDetail) body : standardProblem(statusCode);
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static boolean isContractProblem(@Nullable Object body) {
        return body instanceof ProblemDetail problem
                && problem.getType().toString().startsWith(Problems.PREFIX);
    }

    private static ProblemDetail standardProblem(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> Problems.of(status, "not-found", "Não encontrado", "Recurso não encontrado.");
            case 405 -> Problems.of(status, "method-not-allowed", "Método não permitido", "Método não permitido.");
            case 406, 415 ->
                Problems.of(status, "unsupported-media-type", "Formato não suportado", "Formato não suportado.");
            default -> {
                if (status.is4xxClientError()) {
                    yield Problems.of(status, "invalid-request", "Requisição inválida", "Requisição inválida.");
                }
                yield Problems.of(
                        status, "internal-error", "Erro interno", "Ocorreu um erro inesperado. Tente novamente.");
            }
        };
    }
}
