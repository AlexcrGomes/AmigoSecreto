package br.com.amigosecreto.auth.dto;

import br.com.amigosecreto.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro (FR-001, FR-002). Nome e e-mail são validados já sem espaços nas pontas. */
public record RegisterRequest(
        @NotBlank(message = NAME_MESSAGE) @Size(min = 2, max = 80, message = NAME_MESSAGE)
        String name,

        @NotBlank(message = EMAIL_MESSAGE) @Email(message = EMAIL_MESSAGE) @Size(max = 254, message = EMAIL_MESSAGE)
        String email,

        @NotNull(message = PASSWORD_MESSAGE)
        @Size(min = 8, max = 64, message = PASSWORD_MESSAGE)
        @MaxUtf8Bytes(value = PASSWORD_MAX_BYTES, message = PASSWORD_BYTES_MESSAGE)
        String password) {

    public static final int PASSWORD_MAX_BYTES = 72;
    public static final String NAME_MESSAGE = "Informe seu nome (2 a 80 caracteres).";
    public static final String EMAIL_MESSAGE = "Informe um e-mail válido.";
    public static final String PASSWORD_MESSAGE = "A senha deve ter entre 8 e 64 caracteres.";
    public static final String PASSWORD_BYTES_MESSAGE =
            "A senha deve ter no máximo 72 bytes (acentos e emojis ocupam mais de um).";

    public RegisterRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip();
    }

    /** Sem a senha. */
    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + "]";
    }
}
