package br.com.amigosecreto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.auth.dto.LoginRequest;
import br.com.amigosecreto.auth.dto.RegisterRequest;
import br.com.amigosecreto.common.ApiException;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.RateLimitExceededException;
import br.com.amigosecreto.common.UnauthorizedException;
import br.com.amigosecreto.common.UnprocessableException;
import br.com.amigosecreto.ratelimit.RateLimitPolicy;
import br.com.amigosecreto.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final String IP = "203.0.113.7";
    private static final String DUMMY_HASH = "{bcrypt}hash-ficticio";

    @Mock
    private AppUserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimiter rateLimiter;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "{bcrypt}" + inv.getArgument(0));
        when(passwordEncoder.encode(AuthService.DUMMY_PASSWORD)).thenReturn(DUMMY_HASH);
        when(users.saveAndFlush(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AuthService(users, passwordEncoder, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- cadastro ----

    @Test
    void registerNormalizesEmailAndNameAndHashesPassword() {
        AppUser user = service.register(new RegisterRequest("  Ana Souza ", "  Ana@Example.COM ", "senha-forte"), IP);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ana Souza");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}senha-forte");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(user).isSameAs(saved.getValue());
        verify(users).existsByEmail("ana@example.com");
    }

    @Test
    void registerConsumesTheRegisterLimitByIp() {
        service.register(new RegisterRequest("Ana", "ana@x.com", "senha-forte"), IP);

        verify(rateLimiter).consume(RateLimitPolicy.REGISTER, IP);
    }

    @Test
    void registerBlockedByRateLimitDoesNotTouchTheDatabase() {
        doThrow(new RateLimitExceededException(60)).when(rateLimiter).consume(RateLimitPolicy.REGISTER, IP);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ana@x.com", "senha-forte"), IP))
                .isInstanceOf(RateLimitExceededException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicatedEmailIsConflict() {
        when(users.existsByEmail("ana@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", " ANA@x.com", "senha-forte"), IP))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("email-already-used");
                    assertThat(ex.getMessage()).isEqualTo("Este e-mail já está cadastrado.");
                });
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateDetectedByTheUniqueConstraintIsAlsoConflict() {
        when(users.saveAndFlush(any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException("uq_app_user_email"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ana@x.com", "senha-forte"), IP))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void passwordOver72BytesIsRejected() {
        // 64 caracteres, mas 128 bytes em UTF-8: o BCrypt ignoraria o excedente
        String password = "é".repeat(64);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ana@x.com", password), IP))
                .isInstanceOf(UnprocessableException.class);
        verify(passwordEncoder, never()).encode(password);
        verify(users, never()).saveAndFlush(any());
    }

    // ---- login ----

    @Test
    void loginSucceedsAndResetsFailures() {
        AppUser ana = new AppUser("Ana", "ana@x.com", "{bcrypt}certa", NOW);
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(ana));
        when(passwordEncoder.matches("certa", "{bcrypt}certa")).thenReturn(true);

        AppUser user = service.authenticate(new LoginRequest(" ANA@x.com ", "certa"), IP);

        assertThat(user).isSameAs(ana);
        verify(rateLimiter).ensureNotExhausted(RateLimitPolicy.LOGIN_FAILURE, "ana@x.com|" + IP);
        verify(rateLimiter).reset(RateLimitPolicy.LOGIN_FAILURE, "ana@x.com|" + IP);
        verify(rateLimiter, never()).consume(eq(RateLimitPolicy.LOGIN_FAILURE), anyString());
    }

    @Test
    void wrongPasswordAndUnknownEmailFailTheSameWay() {
        AppUser ana = new AppUser("Ana", "ana@x.com", "{bcrypt}certa", NOW);
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.of(ana));
        when(users.findByEmail("ninguem@x.com")).thenReturn(Optional.empty());

        Throwable wrongPassword =
                catchApiException(() -> service.authenticate(new LoginRequest("ana@x.com", "errada"), IP));
        Throwable unknownEmail =
                catchApiException(() -> service.authenticate(new LoginRequest("ninguem@x.com", "errada"), IP));

        assertThat(wrongPassword).isInstanceOf(UnauthorizedException.class);
        assertThat(unknownEmail).isInstanceOf(UnauthorizedException.class);
        assertThat(wrongPassword.getMessage())
                .isEqualTo(unknownEmail.getMessage())
                .isEqualTo("E-mail ou senha inválidos.");
    }

    @Test
    void unknownEmailStillChecksAPasswordAgainstADummyHash() {
        when(users.findByEmail("ninguem@x.com")).thenReturn(Optional.empty());

        catchApiException(() -> service.authenticate(new LoginRequest("ninguem@x.com", "qualquer"), IP));

        verify(passwordEncoder).matches("qualquer", DUMMY_HASH);
    }

    @Test
    void eachFailureConsumesTheLoginFailureLimitWithEmailAndIp() {
        when(users.findByEmail("ana@x.com")).thenReturn(Optional.empty());

        catchApiException(() -> service.authenticate(new LoginRequest("Ana@X.com", "errada"), IP));

        verify(rateLimiter).consume(RateLimitPolicy.LOGIN_FAILURE, "ana@x.com|" + IP);
        verify(rateLimiter, never()).reset(any(), anyString());
    }

    @Test
    void blockedLoginIsRejectedBeforeCheckingThePassword() {
        doThrow(new RateLimitExceededException(600))
                .when(rateLimiter)
                .ensureNotExhausted(RateLimitPolicy.LOGIN_FAILURE, "ana@x.com|" + IP);

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("ana@x.com", "certa"), IP))
                .isInstanceOf(RateLimitExceededException.class);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    private static Throwable catchApiException(Runnable action) {
        try {
            action.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("era esperada uma ApiException");
    }
}
