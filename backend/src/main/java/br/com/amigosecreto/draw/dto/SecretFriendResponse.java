package br.com.amigosecreto.draw.dto;

import br.com.amigosecreto.wishlist.dto.FriendWishItem;
import java.time.Instant;
import java.util.List;

/**
 * Schema {@code SecretFriend}: o nome de quem o usuário da sessão tirou e a lista de desejos dessa pessoa
 * (feature 003). {@code wishlistUpdatedAt} só vem preenchido se a lista mudou depois do sorteio (FR-015a). Nunca
 * traz o id de quem foi tirado.
 */
public record SecretFriendResponse(String name, List<FriendWishItem> wishlist, Instant wishlistUpdatedAt) {

    /** Só o nome, com a lista vazia (como antes da feature 003). */
    public SecretFriendResponse(String name) {
        this(name, List.of(), null);
    }
}
