package br.com.amigosecreto.draw;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.draw.dto.SecretFriendResponse;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.SecretGroupRepository;
import br.com.amigosecreto.wishlist.WishItemRepository;
import br.com.amigosecreto.wishlist.dto.FriendWishItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único caminho até um amigo secreto (FR-028 a FR-032, Constituição I): sempre o do usuário da sessão,
 * buscado por (grupo, quem tirou = usuário da sessão). Não existe variante que receba outro usuário. A lista de
 * desejos de quem foi tirado vem junto (feature 003, research R27), achada pelo id decifrado do par — nunca por um
 * id vindo do cliente — e o id de quem foi tirado nunca sai na resposta.
 */
@Service
public class SecretFriendService {

    private static final String GROUP_NOT_FOUND = "Grupo não encontrado.";

    private final SecretGroupRepository groups;
    private final GroupMemberRepository members;
    private final DrawAssignmentRepository assignments;
    private final AppUserRepository users;
    private final AssignmentCipher cipher;
    private final WishItemRepository wishItems;

    public SecretFriendService(
            SecretGroupRepository groups,
            GroupMemberRepository members,
            DrawAssignmentRepository assignments,
            AppUserRepository users,
            AssignmentCipher cipher,
            WishItemRepository wishItems) {
        this.groups = groups;
        this.members = members;
        this.assignments = assignments;
        this.users = users;
        this.cipher = cipher;
        this.wishItems = wishItems;
    }

    @Transactional(readOnly = true)
    public SecretFriendResponse getMine(UUID sessionUserId, UUID groupId) {
        SecretGroup group = groups.findById(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        if (!members.existsById(new GroupMemberId(groupId, sessionUserId))) {
            throw new NotFoundException(GROUP_NOT_FOUND);
        }
        // Depois do evento cada um ainda revela o próprio amigo (FR-019)
        if (group.getStatus() != GroupStatus.DRAWN && group.getStatus() != GroupStatus.FINISHED) {
            throw new ConflictException("group-not-drawn", "O sorteio deste grupo ainda não foi realizado.");
        }

        DrawAssignment assignment = assignments
                .findByIdGroupIdAndIdGiverId(groupId, sessionUserId)
                .orElseThrow(() -> new IllegalStateException("Par do sorteio ausente para um membro"));
        UUID receiverId = cipher.decrypt(assignment.getReceiverEnc(), groupId, sessionUserId);
        String name = users.findById(receiverId)
                .map(AppUser::getName)
                .orElseThrow(() -> new IllegalStateException("Participante sorteado não encontrado"));
        List<FriendWishItem> wishlist =
                wishItems.findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(groupId, receiverId).stream()
                        .map(FriendWishItem::from)
                        .toList();
        // "Atualizada em" só se a lista mudou depois do sorteio (FR-015a)
        Instant updatedAt = members.findById(new GroupMemberId(groupId, receiverId))
                .map(GroupMember::getWishlistUpdatedAt)
                .filter(changed -> changed.isAfter(group.getDrawnAt()))
                .orElse(null);
        return new SecretFriendResponse(name, wishlist, updatedAt);
    }
}
