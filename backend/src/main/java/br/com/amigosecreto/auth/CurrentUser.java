package br.com.amigosecreto.auth;

import br.com.amigosecreto.common.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Usuário da sessão atual. É a ÚNICA fonte do ID do usuário nas regras de negócio: nunca ler o ID do
 * corpo da requisição nem da URL (Constituição I e III).
 */
@Component
public class CurrentUser {

    public AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new UnauthorizedException("Faça login para continuar.");
    }

    public UUID id() {
        return get().id();
    }
}
