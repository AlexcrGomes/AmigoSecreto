package br.com.amigosecreto.wishlist;

import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
import br.com.amigosecreto.wishlist.dto.WishItemRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas da própria lista de desejos (contracts/openapi.yaml da 003, tag wishlist). O caminho diz {@code mine}:
 * o usuário vem SEMPRE da sessão, e não existe rota que leia a lista de outra pessoa por id.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/wishlist/mine/items")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public WishlistController(WishlistService wishlistService, CurrentUser currentUser) {
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
    }

    /** Adiciona um item à própria lista. */
    @PostMapping
    public ResponseEntity<MyWishlist> add(@PathVariable UUID groupId, @Valid @RequestBody WishItemRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wishlistService.add(currentUser.id(), groupId, body));
    }

    /** Altera um item da própria lista. */
    @PutMapping("/{itemId}")
    public MyWishlist update(
            @PathVariable UUID groupId, @PathVariable UUID itemId, @Valid @RequestBody WishItemRequest body) {
        return wishlistService.update(currentUser.id(), groupId, itemId, body);
    }

    /** Remove um item da própria lista. */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remove(@PathVariable UUID groupId, @PathVariable UUID itemId) {
        wishlistService.remove(currentUser.id(), groupId, itemId);
        return ResponseEntity.noContent().build();
    }
}
