package br.com.amigosecreto.wishlist;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.dependent.DependentLookup;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
import br.com.amigosecreto.wishlist.dto.WishItemRequest;
import br.com.amigosecreto.wishlist.dto.WishItemResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A própria lista de desejos (US1 da 003, FR-001 a FR-007, research R28). Sempre a lista do usuário da sessão:
 * ninguém altera a de outra pessoa. Trava o grupo pelo {@link GroupLock}, como as demais escritas, e não
 * publica sinal nenhum — alterar a lista não muda nada que os outros vejam (FR-015).
 */
@Service
public class WishlistService {

    static final String GROUP_NOT_FOUND = "Grupo não encontrado.";
    static final String ITEM_NOT_FOUND = "Sugestão não encontrada.";

    private final GroupLock lock;
    private final GroupMemberRepository members;
    private final WishItemRepository items;
    private final DependentLookup dependentLookup;
    private final Clock clock;

    public WishlistService(
            GroupLock lock,
            GroupMemberRepository members,
            WishItemRepository items,
            DependentLookup dependentLookup,
            Clock clock) {
        this.lock = lock;
        this.members = members;
        this.items = items;
        this.dependentLookup = dependentLookup;
        this.clock = clock;
    }

    /** Adiciona um item à própria lista, até {@link MyWishlist#MAX_ITEMS} (FR-001). */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public MyWishlist add(UUID userId, UUID groupId, WishItemRequest request) {
        return addTo(lockedMember(userId, groupId), request);
    }

    /** Altera descrição e link de um item da própria lista. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public MyWishlist update(UUID userId, UUID groupId, UUID itemId, WishItemRequest request) {
        return updateIn(lockedMember(userId, groupId), itemId, request);
    }

    /** Remove um item da própria lista. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public void remove(UUID userId, UUID groupId, UUID itemId) {
        removeFrom(lockedMember(userId, groupId), itemId);
    }

    /** Adiciona um item à lista de um dependente seu (FR-014 da 004), com as mesmas regras. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public MyWishlist addForDependent(UUID userId, UUID groupId, UUID dependentId, WishItemRequest request) {
        return addTo(lockedDependent(userId, groupId, dependentId), request);
    }

    /** Altera um item da lista de um dependente seu. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public MyWishlist updateForDependent(
            UUID userId, UUID groupId, UUID dependentId, UUID itemId, WishItemRequest request) {
        return updateIn(lockedDependent(userId, groupId, dependentId), itemId, request);
    }

    /** Remove um item da lista de um dependente seu. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public void removeForDependent(UUID userId, UUID groupId, UUID dependentId, UUID itemId) {
        removeFrom(lockedDependent(userId, groupId, dependentId), itemId);
    }

    private MyWishlist addTo(GroupMember owner, WishItemRequest request) {
        UUID groupId = owner.getId().groupId();
        UUID ownerId = owner.getId().userId();
        if (items.countByGroupIdAndUserId(groupId, ownerId) >= MyWishlist.MAX_ITEMS) {
            throw new ConflictException("wishlist-full", "A lista tem no máximo 5 sugestões.");
        }
        Instant now = clock.instant();
        items.save(new WishItem(groupId, ownerId, request.description(), WishItemUrl.normalize(request.url()), now));
        owner.touchWishlist(now);
        return mine(groupId, ownerId);
    }

    private MyWishlist updateIn(GroupMember owner, UUID itemId, WishItemRequest request) {
        UUID groupId = owner.getId().groupId();
        UUID ownerId = owner.getId().userId();
        WishItem item = ownItem(ownerId, groupId, itemId);
        item.update(request.description(), WishItemUrl.normalize(request.url()));
        owner.touchWishlist(clock.instant());
        return mine(groupId, ownerId);
    }

    private void removeFrom(GroupMember owner, UUID itemId) {
        items.delete(ownItem(owner.getId().userId(), owner.getId().groupId(), itemId));
        owner.touchWishlist(clock.instant());
    }

    /**
     * Grupo travado; o dependente só se for do usuário da sessão (404 igual para qualquer outro caso, research
     * R34); 409 se finalizado.
     */
    private GroupMember lockedDependent(UUID userId, UUID groupId, UUID dependentId) {
        SecretGroup group = lock.acquire(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        GroupMember dependent = dependentLookup.require(userId, groupId, dependentId);
        if (group.getStatus() == GroupStatus.FINISHED) {
            throw new GroupFinishedException();
        }
        return dependent;
    }

    /** Grupo travado (e finalizado, se for a hora); 404 para quem não é membro, 409 se finalizado. */
    private GroupMember lockedMember(UUID userId, UUID groupId) {
        SecretGroup group = lock.acquire(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        GroupMember member = members.findById(new GroupMemberId(groupId, userId))
                .orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        if (group.getStatus() == GroupStatus.FINISHED) {
            throw new GroupFinishedException();
        }
        return member;
    }

    /** O item, só se for do usuário da sessão naquele grupo; qualquer outro caso é o mesmo 404. */
    private WishItem ownItem(UUID userId, UUID groupId, UUID itemId) {
        return items.findByIdAndGroupIdAndUserId(itemId, groupId, userId)
                .orElseThrow(() -> new NotFoundException(ITEM_NOT_FOUND));
    }

    private MyWishlist mine(UUID groupId, UUID userId) {
        return MyWishlist.of(
                items.findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(groupId, userId).stream()
                        .map(WishItemResponse::from)
                        .toList(),
                true);
    }
}
