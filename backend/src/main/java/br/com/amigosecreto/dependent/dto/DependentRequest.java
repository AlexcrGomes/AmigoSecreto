package br.com.amigosecreto.dependent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Schema {@code DependentRequest}: só o nome do dependente (FR-001 da 004). Os espaços nas pontas saem antes da
 * validação, então " L " conta como 1 caractere.
 */
public record DependentRequest(
        @NotBlank(message = "Informe um nome de 2 a 80 caracteres.")
        @Size(min = 2, max = 80, message = "Informe um nome de 2 a 80 caracteres.")
        String name) {

    public DependentRequest {
        name = name == null ? null : name.strip();
    }
}
