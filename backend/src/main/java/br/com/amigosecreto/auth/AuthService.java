package br.com.amigosecreto.auth;

import br.com.amigosecreto.auth.dto.LoginRequest;
import br.com.amigosecreto.auth.dto.RegisterRequest;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.UnauthorizedException;
import br.com.amigosecreto.common.UnprocessableException;
import br.com.amigosecreto.common.validation.MaxUtf8BytesValidator;
import br.com.amigosecreto.ratelimit.RateLimitPolicy;
import br.com.amigosecreto.ratelimit.RateLimiter;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro e autenticação por e-mail e senha (US1). */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos.";
    static final String EMAIL_ALREADY_USED = "Este e-mail já está cadastrado.";
    /** Só serve para gerar um hash fictício: nenhum usuário tem esta senha. */
    static final String DUMMY_PASSWORD = "senha-ficticia-para-tempo-constante";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    /** Comparado quando o e-mail não existe, para o tempo de resposta não revelar contas (anti-enumeração). */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, RateLimiter rateLimiter, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional
    public AppUser register(RegisterRequest request, String clientIp) {
        rateLimiter.consume(RateLimitPolicy.REGISTER, clientIp);
        // Defesa em profundidade: o DTO já valida, mas o BCrypt ignoraria o que passasse de 72 bytes
        if (MaxUtf8BytesValidator.utf8Length(request.password()) > RegisterRequest.PASSWORD_MAX_BYTES) {
            throw new UnprocessableException("password-too-long", RegisterRequest.PASSWORD_BYTES_MESSAGE);
        }
        String email = AppUser.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("email-already-used", EMAIL_ALREADY_USED);
        }
        AppUser user = new AppUser(request.name(), email, passwordEncoder.encode(request.password()), clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Dois cadastros simultâneos com o mesmo e-mail: a constraint única decide
            throw new ConflictException("email-already-used", EMAIL_ALREADY_USED);
        }
    }

    /**
     * Mesma exceção e mesma mensagem para e-mail inexistente e senha errada (FR-004). Cada falha conta
     * para o limite {@code LOGIN_FAILURE} da chave {@code email|ip}; o sucesso zera a contagem.
     */
    @Transactional(readOnly = true)
    public AppUser authenticate(LoginRequest request, String clientIp) {
        String email = AppUser.normalizeEmail(request.email());
        String key = email + "|" + clientIp;
        rateLimiter.ensureNotExhausted(RateLimitPolicy.LOGIN_FAILURE, key);

        Optional<AppUser> user = users.findByEmail(email);
        String hash = user.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches) {
            rateLimiter.consume(RateLimitPolicy.LOGIN_FAILURE, key);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        rateLimiter.reset(RateLimitPolicy.LOGIN_FAILURE, key);
        return user.get();
    }
}
