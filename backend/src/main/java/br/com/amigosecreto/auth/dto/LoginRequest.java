package br.com.amigosecreto.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Login (FR-003). As regras de formato ficam no cadastro: aqui só se exige o preenchimento. */
public record LoginRequest(
        @NotBlank(message = RegisterRequest.EMAIL_MESSAGE) String email,
        @NotBlank(message = "Informe a senha.") String password) {

    /** Sem a senha. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
