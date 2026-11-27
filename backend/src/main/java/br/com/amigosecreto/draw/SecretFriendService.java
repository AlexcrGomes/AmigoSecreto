package br.com.amigosecreto.draw;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.dependent.dto.DependentSecretFriend;
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
 * id vindo do cliente — e o id de quem foi tirado nunca sai na resposta. O responsável também vê o resultado dos
 * próprios dependentes (feature 004), achados pela sessão, nunca por um id do cliente (Constituição I, v1.3.0).
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
        SecretGroup group = drawnGroupOfMember(sessionUserId, groupId);
        return resultOf(group, sessionUserId);
    }

    /**
     * O amigo secreto de cada dependente do usuário da sessão no grupo (feature 004, research R38), na ordem em que
     * foram adicionados. Sem id vindo do cliente: o conjunto é o dos dependentes de que o usuário da sessão é
     * responsável. Sem dependentes, lista vazia.
     */
    @Transactional(readOnly = true)
    public List<DependentSecretFriend> getMyDependents(UUID sessionUserId, UUID groupId) {
        SecretGroup group = drawnGroupOfMember(sessionUserId, groupId);
        return members.findDependents(groupId, sessionUserId).stream()
                .map(member -> member.getId().userId())
                .map(dependentId -> new DependentSecretFriend(
                        dependentId, nameOf(dependentId, "Dependente não encontrado"), resultOf(group, dependentId)))
                .toList();
    }

    /** O grupo sorteado (ou finalizado) de que o usuário da sessão participa; 404 e 409 como antes. */
    private SecretGroup drawnGroupOfMember(UUID sessionUserId, UUID groupId) {
        SecretGroup group = groups.findById(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        if (!members.existsById(new GroupMemberId(groupId, sessionUserId))) {
            throw new NotFoundException(GROUP_NOT_FOUND);
        }
        // Depois do evento cada um ainda revela o próprio amigo (FR-019)
        if (group.getStatus() != GroupStatus.DRAWN && group.getStatus() != GroupStatus.FINISHED) {
            throw new ConflictException("group-not-drawn", "O sorteio deste grupo ainda não foi realizado.");
        }
        return group;
    }

    /**
     * O resultado de {@code giverId} — o usuário da sessão ou um dependente dele, já autorizado por quem chama:
     * decifra o par e traz o nome e a lista de quem foi tirado, sem o id dessa pessoa.
     */
    private SecretFriendResponse resultOf(SecretGroup group, UUID giverId) {
        UUID groupId = group.getId();
        DrawAssignment assignment = assignments
                .findByIdGroupIdAndIdGiverId(groupId, giverId)
                .orElseThrow(() -> new IllegalStateException("Par do sorteio ausente para um membro"));
        UUID receiverId = cipher.decrypt(assignment.getReceiverEnc(), groupId, giverId);
        String name = nameOf(receiverId, "Participante sorteado não encontrado");
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

    private String nameOf(UUID userId, String missing) {
        return users.findById(userId).map(AppUser::getName).orElseThrow(() -> new IllegalStateException(missing));
    }
}
