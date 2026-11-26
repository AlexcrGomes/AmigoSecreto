package br.com.amigosecreto.group;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.events.GroupEvents.MembershipRevokedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.events.GroupEvents.RevocationReason;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sair e remover participantes (US4, FR-018, FR-019), só enquanto o grupo está {@code OPEN}. Usa o mesmo
 * lock da linha do grupo que a entrada e o sorteio ({@link GroupLock}): ninguém sai no meio de um sorteio,
 * nem de um grupo finalizado.
 */
@Service
public class GroupMembershipService {

    private static final String GROUP_NOT_FOUND = "Grupo não encontrado.";

    private final GroupMemberRepository members;
    private final GroupLock lock;
    private final DependentAccounts dependentAccounts;
    private final ApplicationEventPublisher events;

    public GroupMembershipService(
            GroupMemberRepository members,
            GroupLock lock,
            DependentAccounts dependentAccounts,
            ApplicationEventPublisher events) {
        this.members = members;
        this.lock = lock;
        this.dependentAccounts = dependentAccounts;
        this.events = events;
    }

    /** O usuário da sessão sai do grupo. O organizador não pode sair. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public void leave(UUID sessionUserId, UUID groupId) {
        SecretGroup group = lockedGroupOfMember(sessionUserId, groupId);
        requireOpen(group);
        if (group.isOrganizer(sessionUserId)) {
            throw new ConflictException("organizer-cannot-leave", "O organizador não pode sair do grupo.");
        }
        delete(group, sessionUserId, RevocationReason.LEFT);
    }

    /** O organizador (usuário da sessão) remove outro participante. */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public void remove(UUID sessionUserId, UUID groupId, UUID targetUserId) {
        SecretGroup group = lockedGroupOfMember(sessionUserId, groupId);
        if (!group.isOrganizer(sessionUserId)) {
            throw new ForbiddenException("Só o organizador pode remover participantes.");
        }
        requireOpen(group);
        if (group.isOrganizer(targetUserId)) {
            throw new ConflictException("organizer-cannot-be-removed", "O organizador não pode ser removido.");
        }
        if (!members.existsById(new GroupMemberId(groupId, targetUserId))) {
            throw new NotFoundException("Participante não encontrado.");
        }
        delete(group, targetUserId, RevocationReason.REMOVED);
    }

    private SecretGroup lockedGroupOfMember(UUID userId, UUID groupId) {
        SecretGroup group = lock.acquire(groupId).orElseThrow(() -> new NotFoundException(GROUP_NOT_FOUND));
        if (!members.existsById(new GroupMemberId(groupId, userId))) {
            throw new NotFoundException(GROUP_NOT_FOUND);
        }
        return group;
    }

    private static void requireOpen(SecretGroup group) {
        if (group.getStatus() == GroupStatus.FINISHED) {
            throw new GroupFinishedException();
        }
        if (group.getStatus() != GroupStatus.OPEN) {
            throw new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado.");
        }
    }

    /**
     * Tira o participante e, se ele for responsável, os dependentes dele (FR-016 da 004), apagando também o
     * {@code app_user} dos dependentes. Um dependente removido não tem conexões, então não recebe
     * {@code membership-revoked}.
     */
    private void delete(SecretGroup group, UUID userId, RevocationReason reason) {
        GroupMemberId leavingId = new GroupMemberId(group.getId(), userId);
        boolean leavingIsDependent =
                members.findById(leavingId).map(GroupMember::isDependent).orElse(false);
        List<GroupMember> dependents = members.findDependents(group.getId(), userId);
        List<UUID> leavingDependents = new ArrayList<>();
        dependents.forEach(dependent -> leavingDependents.add(dependent.getId().userId()));
        if (leavingIsDependent) {
            leavingDependents.add(userId);
        }
        members.deleteAll(dependents);
        members.deleteById(leavingId);
        members.flush();
        dependentAccounts.deleteUnreferenced(leavingDependents);
        int count = (int) members.countByIdGroupId(group.getId());
        // A revogação vem primeiro (os listeners pós-commit rodam na ordem de publicação): as conexões de
        // quem saiu são fechadas antes do sinal aos demais, então um ex-membro não recebe mais nada
        if (!leavingIsDependent) {
            events.publishEvent(new MembershipRevokedEvent(group.getId(), userId, reason));
        }
        events.publishEvent(new ParticipantsChangedEvent(group.getId(), GroupStatus.OPEN, count));
    }
}
