package br.com.amigosecreto.wishlist.dto;

import java.util.List;

/**
 * Schema {@code MyWishlist}: a lista do usuário da sessão no grupo, na ordem de cadastro. {@code editable} é
 * falso em grupo finalizado. Nunca traz a lista de outra pessoa.
 */
public record MyWishlist(List<WishItemResponse> items, int maxItems, boolean editable) {

    /** No máximo 5 sugestões por lista (FR-001). */
    public static final int MAX_ITEMS = 5;

    public static MyWishlist of(List<WishItemResponse> items, boolean editable) {
        return new MyWishlist(items, MAX_ITEMS, editable);
    }
}
