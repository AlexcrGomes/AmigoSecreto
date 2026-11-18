package br.com.amigosecreto.group;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    long countByIdGroupId(UUID groupId);

    /** IDs dos membros, na ordem de entrada (entrada do sorteio). */
    @Query("select m.id.userId from GroupMember m where m.id.groupId = :groupId order by m.joinedAt")
    List<UUID> findUserIds(@Param("groupId") UUID groupId);

    /** Participantes na ordem de entrada (FR-014). */
    @Query("""
            select new br.com.amigosecreto.group.ParticipantView(u.id, u.name)
            from GroupMember m join AppUser u on u.id = m.id.userId
            where m.id.groupId = :groupId
            order by m.joinedAt, u.name""")
    List<ParticipantView> findParticipants(@Param("groupId") UUID groupId);

    /** Grupos do usuário, mais recentes primeiro, com a contagem de membros (FR-016). */
    @Query("""
            select new br.com.amigosecreto.group.GroupSummaryView(
                g.id, g.name, g.status, g.organizerId,
                (select count(m2) from GroupMember m2 where m2.id.groupId = g.id))
            from GroupMember m join SecretGroup g on g.id = m.id.groupId
            where m.id.userId = :userId
            order by g.createdAt desc""")
    List<GroupSummaryView> findSummaries(@Param("userId") UUID userId);
}
