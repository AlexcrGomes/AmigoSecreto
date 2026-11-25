package br.com.amigosecreto.wishlist.dto;

import br.com.amigosecreto.wishlist.WishItem;
import java.util.UUID;

/** Schema {@code WishItem}: item da PRÓPRIA lista do usuário da sessão (com id, para editar e remover). */
public record WishItemResponse(UUID id, String description, String url) {

    public static WishItemResponse from(WishItem item) {
        return new WishItemResponse(item.getId(), item.getDescription(), item.getUrl());
    }
}
