package br.com.amigosecreto.auth.dto;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AuthenticatedUser;
import java.util.UUID;

/** Schema {@code User} do contrato. Nunca contém senha nem hash. */
public record UserResponse(UUID id, String name, String email) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    public static UserResponse from(AuthenticatedUser user) {
        return new UserResponse(user.id(), user.name(), user.email());
    }
}
