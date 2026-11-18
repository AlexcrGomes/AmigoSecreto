package br.com.amigosecreto.auth;

import br.com.amigosecreto.auth.dto.LoginRequest;
import br.com.amigosecreto.auth.dto.RegisterRequest;
import br.com.amigosecreto.auth.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas de autenticação (contracts/openapi.yaml, tag auth). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentUser currentUser;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(
            AuthService authService, SecurityContextRepository securityContextRepository, CurrentUser currentUser) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.currentUser = currentUser;
    }

    /** Emite o cookie XSRF-TOKEN (o token é gerado sob demanda: ler o valor força a geração). */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        AppUser user = authService.register(body, request.getRemoteAddr());
        signIn(user, request, response);
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        AppUser user = authService.authenticate(body, request.getRemoteAddr());
        signIn(user, request, response);
        return UserResponse.from(user);
    }

    /** Invalida a sessão; o Spring Session expira o cookie SESSION na mesma resposta. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        contextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.get());
    }

    /**
     * Grava a autenticação na sessão com um ID de sessão novo (proteção contra session fixation). O
     * principal é sempre o {@link AuthenticatedUser} vindo do banco, nunca dados do cliente.
     */
    private void signIn(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getName(), user.getEmail());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
