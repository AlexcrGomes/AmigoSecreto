package br.com.amigosecreto.wishlist.dto;

import br.com.amigosecreto.wishlist.WebLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Schema {@code WishItemRequest}: descrição obrigatória e link opcional (FR-002, FR-003). */
public record WishItemRequest(
        @NotBlank(message = WishItemRequest.DESCRIPTION_MESSAGE)
        @Size(max = 120, message = WishItemRequest.DESCRIPTION_MESSAGE)
        String description,

        @WebLink String url) {

    public static final String DESCRIPTION_MESSAGE = "Descreva a sugestão (até 120 caracteres).";
}
