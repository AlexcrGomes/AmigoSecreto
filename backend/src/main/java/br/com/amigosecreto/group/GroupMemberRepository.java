package br.com.amigosecreto.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    long countByIdGroupId(UUID groupId);

    /** IDs dos membros, na ordem de entrada (entrada do sorteio). */
    @Query("select m.id.userId from GroupMember m where m.id.groupId = :groupId order by m.joinedAt")
    List<UUID> findUserIds(@Param("groupId") UUID groupId);

    /**
     * Participantes na ordem de entrada (FR-014), com a situação de confirmação (FR-012 da 002) e, para
     * dependentes, o responsável (FR-005 da 004).
     */
    @Query("""
            select new br.com.amigosecreto.group.ParticipantView(u.id, u.name, m.confirmedAt, m.guardianId, g.name)
            from GroupMember m join AppUser u on u.id = m.id.userId
            left join AppUser g on g.id = m.guardianId
            where m.id.groupId = :groupId
            order by m.joinedAt, u.name""")
    List<ParticipantView> findParticipants(@Param("groupId") UUID groupId);

    /**
     * Nomes de quem ainda não confirmou, na ordem de entrada (FR-014 da 002). Só participantes com conta: a
     * confirmação dos dependentes acompanha a do responsável (research R36 da 004).
     */
    @Query("""
            select u.name
            from GroupMember m join AppUser u on u.id = m.id.userId
            where m.id.groupId = :groupId and m.confirmedAt is null and m.guardianId is null
            order by m.joinedAt, u.name""")
    List<String> findPendingNames(@Param("groupId") UUID groupId);

    /**
     * Volta todos, menos o organizador e os dependentes dele, a pendente (FR-015): data, horário ou local mudaram
     * antes do sorteio. Os dependentes acompanham o responsável (research R36 da 004). Quem chama já tem o lock
     * do grupo.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update GroupMember m set m.confirmedAt = null
            where m.id.groupId = :groupId and m.id.userId <> :organizerId
              and (m.guardianId is null or m.guardianId <> :organizerId)""")
    int resetConfirmations(@Param("groupId") UUID groupId, @Param("organizerId") UUID organizerId);

    /** Cada dependente do grupo e o responsável dele: os pares proibidos do sorteio (research R35). */
    @Query("""
            select new br.com.amigosecreto.group.DependentLink(m.id.userId, m.guardianId)
            from GroupMember m
            where m.id.groupId = :groupId and m.guardianId is not null""")
    List<DependentLink> findDependentLinks(@Param("groupId") UUID groupId);

    /** Dependentes de um responsável no grupo, na ordem em que foram adicionados (feature 004). */
    @Query("""
            select m from GroupMember m
            where m.id.groupId = :groupId and m.guardianId = :guardianId
            order by m.joinedAt, m.id.userId""")
    List<GroupMember> findDependents(@Param("groupId") UUID groupId, @Param("guardianId") UUID guardianId);

    /** Quantos dependentes o responsável tem no grupo (limite de 5, FR-003 da 004). */
    @Query("""
            select count(m) from GroupMember m
            where m.id.groupId = :groupId and m.guardianId = :guardianId""")
    long countDependents(@Param("groupId") UUID groupId, @Param("guardianId") UUID guardianId);

    /**
     * O dependente {@code dependentId} do responsável {@code guardianId} no grupo, ou vazio — mesmo resultado
     * para dependente de outra pessoa, de outro grupo ou inexistente (research R34).
     */
    @Query("""
            select m from GroupMember m
            where m.id.groupId = :groupId and m.id.userId = :dependentId and m.guardianId = :guardianId""")
    Optional<GroupMember> findDependent(
            @Param("dependentId") UUID dependentId,
            @Param("groupId") UUID groupId,
            @Param("guardianId") UUID guardianId);

    /** Grupos do usuário, mais recentes primeiro, com a contagem de membros (FR-016). */
    @Query("""
            select new br.com.amigosecreto.group.GroupSummaryView(
                g.id, g.name, g.status, g.organizerId,
                (select count(m2) from GroupMember m2 where m2.id.groupId = g.id),
                g.eventStartsAt, g.eventHasTime)
            from GroupMember m join SecretGroup g on g.id = m.id.groupId
            where m.id.userId = :userId
            order by g.createdAt desc""")
    List<GroupSummaryView> findSummaries(@Param("userId") UUID userId);
}
