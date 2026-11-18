package br.com.amigosecreto.web;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.amigosecreto.common.ProblemResponseWriter;
import br.com.amigosecreto.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.ProtectedController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, SecurityConfigTest.ProtectedController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Simula o navegador no padrão SPA: o JS lê o cookie XSRF-TOKEN e devolve o mesmo valor (cru) no
     * cabeçalho X-XSRF-TOKEN. O csrf() do spring-security-test manda o token mascarado (XOR), que o modo
     * SPA corretamente não aceita no cabeçalho.
     */
    private static RequestPostProcessor spaCsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "token-de-teste"));
            request.addHeader("X-XSRF-TOKEN", "token-de-teste");
            return request;
        };
    }

    @Test
    void xsrfHeaderDifferentFromCookieIs403() throws Exception {
        mvc.perform(post("/api/v1/protegida")
                        .with(user("ana"))
                        .cookie(new Cookie("XSRF-TOKEN", "token-do-cookie"))
                        .header("X-XSRF-TOKEN", "outro-valor"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedRouteWithoutSessionIs401ProblemWithoutRedirect() throws Exception {
        mvc.perform(get("/api/v1/protegida"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.type").value("/problems/unauthorized"))
                .andExpect(jsonPath("$.detail").value("Faça login para continuar."));
    }

    @Test
    void protectedRouteWithAuthenticatedUserIsAllowed() throws Exception {
        mvc.perform(get("/api/v1/protegida").with(user("ana"))).andExpect(status().isOk());
    }

    @Test
    void postWithoutXsrfHeaderIs403Problem() throws Exception {
        mvc.perform(post("/api/v1/protegida").with(user("ana")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/csrf-invalid"));
    }

    @Test
    void postWithXsrfHeaderIsAllowed() throws Exception {
        mvc.perform(post("/api/v1/protegida").with(user("ana")).with(spaCsrf())).andExpect(status().isOk());
    }

    @Test
    void anonymousPostWithoutXsrfToPublicRouteIsStill403() throws Exception {
        mvc.perform(post("/api/v1/auth/login")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/csrf", "/actuator/health"})
    void publicGetRoutesDoNotRequireSession(String path) throws Exception {
        // Ainda não há controller nesta fase: 404 (e não 401) prova que a rota é pública
        mvc.perform(get(path)).andExpect(status().is(not(401))).andExpect(status().is(not(403)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/register", "/api/v1/auth/login"})
    void publicPostRoutesDoNotRequireSession(String path) throws Exception {
        mvc.perform(post(path).with(spaCsrf())).andExpect(status().is(not(401))).andExpect(status().is(not(403)));
    }

    @Test
    void otherAuthRoutesAreProtected() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/v1/protegida"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void passwordsUseBcryptCost12() {
        String hash = passwordEncoder.encode("senha-de-teste");

        org.assertj.core.api.Assertions.assertThat(hash).startsWith("{bcrypt}$2a$12$");
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("senha-de-teste", hash))
                .isTrue();
    }

    @RestController
    static class ProtectedController {

        @GetMapping("/api/v1/protegida")
        String get() {
            return "ok";
        }

        @PostMapping("/api/v1/protegida")
        String post() {
            return "ok";
        }
    }
}
