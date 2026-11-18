package br.com.amigosecreto.auth;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Principal guardado na sessão (Spring Session JDBC serializa este objeto).
 *
 * <p>{@link #getName()} devolve o ID: é o que o Spring Session grava em {@code PRINCIPAL_NAME}
 * (varchar 100), em vez de nome e e-mail.
 */
public record AuthenticatedUser(UUID id, String name, String email) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return id.toString();
    }
}
