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
 * Lista de desejos de um dependente do usuário da sessão (contracts/openapi.yaml da 004, research R39). O
 * responsável vem SEMPRE da sessão; dependente de outra pessoa responde 404.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/my-dependents/{dependentId}/wishlist/items")
public class DependentWishlistController {

    private final WishlistService wishlistService;
    private final CurrentUser currentUser;

    public DependentWishlistController(WishlistService wishlistService, CurrentUser currentUser) {
        this.wishlistService = wishlistService;
        this.currentUser = currentUser;
    }

    /** Adiciona um item à lista do dependente. */
    @PostMapping
    public ResponseEntity<MyWishlist> add(
            @PathVariable UUID groupId, @PathVariable UUID dependentId, @Valid @RequestBody WishItemRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wishlistService.addForDependent(currentUser.id(), groupId, dependentId, body));
    }

    /** Altera um item da lista do dependente. */
    @PutMapping("/{itemId}")
    public MyWishlist update(
            @PathVariable UUID groupId,
            @PathVariable UUID dependentId,
            @PathVariable UUID itemId,
            @Valid @RequestBody WishItemRequest body) {
        return wishlistService.updateForDependent(currentUser.id(), groupId, dependentId, itemId, body);
    }

    /** Remove um item da lista do dependente. */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID groupId, @PathVariable UUID dependentId, @PathVariable UUID itemId) {
        wishlistService.removeForDependent(currentUser.id(), groupId, dependentId, itemId);
        return ResponseEntity.noContent().build();
    }
}
