package br.com.amigosecreto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.amigosecreto.common.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheAuthenticatedPrincipal() {
        AuthenticatedUser ana = new AuthenticatedUser(UUID.randomUUID(), "Ana", "ana@x.com");
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(ana, null, List.of()));

        assertThat(currentUser.get()).isEqualTo(ana);
        assertThat(currentUser.id()).isEqualTo(ana.id());
    }

    @Test
    void withoutAuthenticationIsUnauthorized() {
        assertThatThrownBy(currentUser::get).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void foreignPrincipalIsUnauthorized() {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("alguem", null, List.of()));

        assertThatThrownBy(currentUser::id).isInstanceOf(UnauthorizedException.class);
    }
}
