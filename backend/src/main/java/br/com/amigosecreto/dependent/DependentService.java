package br.com.amigosecreto.dependent;

import br.com.amigosecreto.auth.AppUser;
import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.dependent.dto.DependentRequest;
import br.com.amigosecreto.dependent.dto.MyDependent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.group.DependentAccounts;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupService;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dependentes do usuário da sessão (feature 004, US1, research R34): adicionar, renomear e remover, sempre
 * antes do sorteio e com o grupo travado. O responsável vem SEMPRE da sessão. Toda mudança publica
 * {@code participants-changed} depois do commit.
 */
@Service
public class DependentService {

    /** Até 5 dependentes por responsável e grupo (FR-003). */
    public static final int MAX_DEPENDENTS = GroupService.MAX_DEPENDENTS;

    private final GroupLock lock;
    private final GroupMemberRepository members;
    private final AppUserRepository users;
    private final DependentLookup lookup;
    private final DependentAccounts accounts;
    private final GroupService groupService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public DependentService(
            GroupLock lock,
            GroupMemberRepository members,
            AppUserRepository users,
            DependentLookup lookup,
            DependentAccounts accounts,
            GroupService groupService,
            Clock clock,
            ApplicationEventPublisher events) {
        this.lock = lock;
        this.members = members;
        this.users = users;
        this.lookup = lookup;
        this.accounts = accounts;
        this.groupService = groupService;
        this.clock = clock;
        this.events = events;
    }

    /** Adiciona um dependente, com a confirmação do responsável (FR-001 a FR-004, research R36). */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public MyDependent add(UUID userId, UUID groupId, DependentRequest request) {
        SecretGroup group = lockedGroup(groupId);
        GroupMember guardian = members.findById(new GroupMemberId(groupId, userId))
                .filter(member -> !member.isDependent())
                .orElseThrow(() -> new NotFoundException(GroupService.GROUP_NOT_FOUND));
        requireOpen(group);
        long count = members.countByIdGroupId(groupId);
        if (count >= GroupService.MAX_PARTICIPANTS) {
            throw new ConflictException("group-full", "Este grupo está cheio.");
        }
        if (members.countDependents(groupId, userId) >= MAX_DEPENDENTS) {
            throw new ConflictException("dependents-limit", "Você pode adicionar até 5 dependentes.");
        }

        Instant now = clock.instant();
        AppUser dependent = users.save(AppUser.dependent(request.name(), now));
        GroupMember member = members.save(GroupMember.dependentOf(guardian, dependent.getId(), now));
        events.publishEvent(new ParticipantsChangedEvent(groupId, GroupStatus.OPEN, (int) count + 1));
        return groupService.myDependent(group, member, dependent.getName());
    }

    /** Muda o nome de um dependente seu (FR-006, FR-007). */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public MyDependent rename(UUID userId, UUID groupId, UUID dependentId, DependentRequest request) {
        SecretGroup group = lockedGroup(groupId);
        GroupMember member = lookup.require(userId, groupId, dependentId);
        requireOpen(group);
        AppUser dependent = users.findById(dependentId)
                .orElseThrow(() -> new NotFoundException(DependentLookup.DEPENDENT_NOT_FOUND));
        dependent.rename(request.name());
        events.publishEvent(
                new ParticipantsChangedEvent(groupId, GroupStatus.OPEN, (int) members.countByIdGroupId(groupId)));
        return groupService.myDependent(group, member, dependent.getName());
    }

    /** Remove um dependente seu: a participação, a lista de desejos e o dependente (FR-006, FR-008). */
    @Transactional(noRollbackFor = GroupFinishedException.class)
    public void remove(UUID userId, UUID groupId, UUID dependentId) {
        SecretGroup group = lockedGroup(groupId);
        GroupMember member = lookup.require(userId, groupId, dependentId);
        requireOpen(group);
        members.delete(member);
        members.flush();
        accounts.deleteUnreferenced(List.of(dependentId));
        events.publishEvent(
                new ParticipantsChangedEvent(groupId, GroupStatus.OPEN, (int) members.countByIdGroupId(groupId)));
    }

    private SecretGroup lockedGroup(UUID groupId) {
        return lock.acquire(groupId).orElseThrow(() -> new NotFoundException(GroupService.GROUP_NOT_FOUND));
    }

    private static void requireOpen(SecretGroup group) {
        if (group.getStatus() == GroupStatus.FINISHED) {
            throw new GroupFinishedException();
        }
        if (group.getStatus() != GroupStatus.OPEN) {
            throw new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado.");
        }
    }
}
