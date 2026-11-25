package br.com.amigosecreto.wishlist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Acesso aos itens sempre pela lista de UM dono, (grupo, usuário). De propósito NÃO estende
 * {@code JpaRepository}: não existe {@code findAll} nem busca de todos os itens de um grupo (Constituição I).
 */
public interface WishItemRepository extends Repository<WishItem, UUID> {

    WishItem save(WishItem item);

    void delete(WishItem item);

    /** A lista do dono, na ordem de cadastro (FR-007). */
    List<WishItem> findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(UUID groupId, UUID userId);

    long countByGroupIdAndUserId(UUID groupId, UUID userId);

    /** Um item, só se for do dono informado e daquele grupo. */
    Optional<WishItem> findByIdAndGroupIdAndUserId(UUID id, UUID groupId, UUID userId);
}
