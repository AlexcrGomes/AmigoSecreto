package br.com.amigosecreto.web;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AuthController;
import br.com.amigosecreto.auth.AuthService;
import br.com.amigosecreto.auth.AuthenticatedUser;
import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ProblemResponseWriter;
import br.com.amigosecreto.common.RateLimitExceededException;
import br.com.amigosecreto.common.UnauthorizedException;
import br.com.amigosecreto.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, CurrentUser.class})
class AuthControllerWebTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    private static RequestPostProcessor spaCsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "token-de-teste"));
            request.addHeader("X-XSRF-TOKEN", "token-de-teste");
            return request;
        };
    }

    private static MockHttpServletRequestBuilder postJson(String path, String json) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json).with(spaCsrf());
    }

    @Test
    void csrfIssuesTheXsrfCookie() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void invalidRegisterIs400WithFieldErrorsInPortuguese() throws Exception {
        mvc.perform(postJson(
                        "/api/v1/auth/register", "{\"name\":\" a \",\"email\":\"nao-e-email\",\"password\":\"curta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/problems/validation-error"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message")
                        .value("Informe seu nome (2 a 80 caracteres)."))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message")
                        .value("A senha deve ter entre 8 e 64 caracteres."));
        verify(authService, never()).register(any(), anyString());
    }

    @Test
    void passwordOver72BytesIs400() throws Exception {
        String password = "é".repeat(40); // 40 caracteres, 80 bytes
        mvc.perform(postJson(
                        "/api/v1/auth/register",
                        "{\"name\":\"Ana\",\"email\":\"ana@x.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message")
                        .value("A senha deve ter no máximo 72 bytes (acentos e emojis ocupam mais de um)."));
    }

    @Test
    void emailLongerThan254IsInvalid() throws Exception {
        String email = "a".repeat(250) + "@x.com";
        mvc.perform(postJson(
                        "/api/v1/auth/register",
                        "{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senha-forte\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message").value("Informe um e-mail válido."));
    }

    @Test
    void registerReturns201WithUserWithoutPasswordAndStartsTheSession() throws Exception {
        AppUser ana = new AppUser("Ana", "ana@x.com", "{bcrypt}hash", NOW);
        when(authService.register(any(), anyString())).thenReturn(ana);

        mvc.perform(postJson(
                        "/api/v1/auth/register",
                        "{\"name\":\"Ana\",\"email\":\"ana@x.com\",\"password\":\"senha-forte\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/auth/me"))
                .andExpect(jsonPath("$.id").value(ana.getId().toString()))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@x.com"))
                .andExpect(jsonPath("$", not(hasKey("password"))))
                .andExpect(jsonPath("$", not(hasKey("passwordHash"))));
    }

    @Test
    void duplicatedEmailIs409() throws Exception {
        when(authService.register(any(), anyString()))
                .thenThrow(new ConflictException("email-already-used", "Este e-mail já está cadastrado."));

        mvc.perform(postJson(
                        "/api/v1/auth/register",
                        "{\"name\":\"Ana\",\"email\":\"ana@x.com\",\"password\":\"senha-forte\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/email-already-used"))
                .andExpect(jsonPath("$.detail").value("Este e-mail já está cadastrado."));
    }

    @Test
    void loginReturnsTheUser() throws Exception {
        AppUser ana = new AppUser("Ana", "ana@x.com", "{bcrypt}hash", NOW);
        when(authService.authenticate(any(), anyString())).thenReturn(ana);

        mvc.perform(postJson("/api/v1/auth/login", "{\"email\":\"ana@x.com\",\"password\":\"senha-forte\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@x.com"))
                .andExpect(jsonPath("$", not(hasKey("passwordHash"))));
    }

    @Test
    void wrongLoginIs401WithGenericMessage() throws Exception {
        when(authService.authenticate(any(), anyString()))
                .thenThrow(new UnauthorizedException("E-mail ou senha inválidos."));

        mvc.perform(postJson("/api/v1/auth/login", "{\"email\":\"ana@x.com\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void emptyLoginIs400() throws Exception {
        mvc.perform(postJson("/api/v1/auth/login", "{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void tooManyAttemptsIs429WithRetryAfter() throws Exception {
        when(authService.authenticate(any(), anyString())).thenThrow(new RateLimitExceededException(600));

        mvc.perform(postJson("/api/v1/auth/login", "{\"email\":\"ana@x.com\",\"password\":\"errada\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"));
    }

    @Test
    void logoutIs204() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").with(loggedIn()).with(spaCsrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void meWithoutSessionIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheSessionUser() throws Exception {
        mvc.perform(get("/api/v1/auth/me").with(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.email").value("ana@x.com"));
    }

    private static RequestPostProcessor loggedIn() {
        AuthenticatedUser ana = new AuthenticatedUser(java.util.UUID.randomUUID(), "Ana", "ana@x.com");
        return authentication(UsernamePasswordAuthenticationToken.authenticated(ana, null, List.of()));
    }
}
