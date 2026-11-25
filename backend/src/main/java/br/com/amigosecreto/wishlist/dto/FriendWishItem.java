package br.com.amigosecreto.wishlist.dto;

import br.com.amigosecreto.wishlist.WishItem;

/**
 * Schema {@code FriendWishItem}: item da lista de quem o usuário tirou — sem id e sem dono (research R27 da
 * 003). Ninguém consegue usá-lo para alterar a lista de outra pessoa nem para identificar o dono.
 */
public record FriendWishItem(String description, String url) {

    public static FriendWishItem from(WishItem item) {
        return new FriendWishItem(item.getDescription(), item.getUrl());
    }
}
