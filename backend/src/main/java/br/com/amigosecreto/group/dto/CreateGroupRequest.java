package br.com.amigosecreto.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Schema {@code CreateGroupRequest}: nome de 1 a 60 caracteres após trim (FR-008). */
public record CreateGroupRequest(
        @NotBlank(message = NAME_MESSAGE) @Size(max = 60, message = NAME_MESSAGE)
        String name) {

    public static final String NAME_MESSAGE = "Dê um nome ao grupo (até 60 caracteres).";

    public CreateGroupRequest {
        name = name == null ? null : name.strip();
    }
}
