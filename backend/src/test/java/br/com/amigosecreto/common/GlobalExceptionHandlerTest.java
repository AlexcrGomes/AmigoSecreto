package br.com.amigosecreto.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    static Stream<Arguments> domainExceptions() {
        return Stream.of(
                Arguments.of("/t/not-found", 404, "/problems/not-found", "Grupo não encontrado."),
                Arguments.of("/t/forbidden", 403, "/problems/forbidden", "Só o organizador pode sortear."),
                Arguments.of("/t/conflict", 409, "/problems/group-full", "O grupo está cheio."),
                Arguments.of(
                        "/t/unprocessable",
                        422,
                        "/problems/not-enough-participants",
                        "São necessários pelo menos 3 participantes."),
                Arguments.of("/t/unauthorized", 401, "/problems/unauthorized", "E-mail ou senha inválidos."),
                Arguments.of(
                        "/t/rate-limited",
                        429,
                        "/problems/rate-limited",
                        "Muitas tentativas. Tente novamente em alguns minutos."));
    }

    @ParameterizedTest
    @MethodSource("domainExceptions")
    void domainExceptionBecomesProblemDetail(String path, int status, String type, String detail) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value(detail));
    }

    @Test
    void rateLimitSendsRetryAfterHeader() throws Exception {
        mvc.perform(get("/t/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"));
    }

    @Test
    void beanValidationErrorsBecome400WithFieldErrors() throws Exception {
        mvc.perform(post("/t/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.detail").value("Verifique os campos destacados."))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value("Informe o nome."))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("E-mail inválido."));
    }

    @Test
    void malformedJsonBecomes400InPortuguese() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{nao é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/invalid-request"))
                .andExpect(jsonPath("$.detail").value("Requisição inválida."));
    }

    @Test
    void unknownRouteBecomes404InPortuguese() throws Exception {
        mvc.perform(get("/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("/problems/not-found"))
                .andExpect(jsonPath("$.detail").value("Recurso não encontrado."));
    }

    @Test
    void unexpectedExceptionBecomesGeneric500WithoutInternals() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("/problems/internal-error"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente."))
                .andExpect(content().string(not(containsString("segredo interno"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at br.com"))));
    }

    @RestController
    @RequestMapping("/t")
    static class ThrowingController {

        record Body(
                @NotBlank(message = "Informe o nome.") String name,
                @Email(message = "E-mail inválido.") String email) {}

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Grupo não encontrado.");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("Só o organizador pode sortear.");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("group-full", "O grupo está cheio.");
        }

        @GetMapping("/unprocessable")
        void unprocessable() {
            throw new UnprocessableException("not-enough-participants", "São necessários pelo menos 3 participantes.");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("E-mail ou senha inválidos.");
        }

        @GetMapping("/rate-limited")
        void rateLimited() {
            throw new RateLimitExceededException(120);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("segredo interno");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Body body) {}
    }
}
