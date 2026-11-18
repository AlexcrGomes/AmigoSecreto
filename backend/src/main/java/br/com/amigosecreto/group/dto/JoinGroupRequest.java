package br.com.amigosecreto.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Schema {@code JoinGroupRequest}: o código é normalizado no serviço ("k7pq-m2xa" → "K7PQM2XA"). */
public record JoinGroupRequest(
        @NotBlank(message = CODE_MESSAGE) @Size(max = 20, message = CODE_MESSAGE)
        String code) {

    public static final String CODE_MESSAGE = "Código inválido.";
}
