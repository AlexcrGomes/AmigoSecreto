package br.com.amigosecreto.exitrequest;

import br.com.amigosecreto.exitrequest.dto.ExitRequestNotice;
import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExitRequestRepository extends JpaRepository<ExitRequest, UUID> {

    Optional<ExitRequest> findByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, ExitRequestStatus status);

    Optional<ExitRequest> findByIdAndUserId(UUID id, UUID userId);

    /** O pedido, se foi o usuário quem o fez (por ele mesmo ou por um dependente seu). */
    Optional<ExitRequest> findByIdAndRequestedBy(UUID id, UUID requestedBy);

    /**
     * Já houve saída aprovada de um dependente deste responsável no grupo? Se sim, ele não adiciona mais
     * dependentes ali (Clarifications Q3, research R37).
     */
    @Query("""
            select count(r) > 0 from ExitRequest r
            where r.groupId = :groupId and r.requestedBy = :guardianId and r.userId <> :guardianId
              and r.status = br.com.amigosecreto.exitrequest.ExitRequestStatus.APPROVED""")
    boolean existsApprovedDependentExit(@Param("groupId") UUID groupId, @Param("guardianId") UUID guardianId);

    /** Algum pedido de saída (de qualquer situação) é deste usuário? Dependentes assim não são apagados (R37). */
    boolean existsByUserId(UUID userId);

    /**
     * Pedidos pendentes do grupo, os mais antigos primeiro, com o nome de quem sai (FR-026) e, se for um
     * dependente, o do responsável (feature 004).
     */
    @Query("""
            select new br.com.amigosecreto.exitrequest.dto.ExitRequestResponse(
                r.id, r.userId, u.name, r.requestedAt, g.name)
            from ExitRequest r join AppUser u on u.id = r.userId
            left join AppUser g on g.id = r.requestedBy and r.requestedBy <> r.userId
            where r.groupId = :groupId
              and r.status = br.com.amigosecreto.exitrequest.ExitRequestStatus.PENDING
            order by r.requestedAt, u.name""")
    List<ExitRequestResponse> findPending(@Param("groupId") UUID groupId);

    /**
     * Decisões que o usuário ainda não viu, com o nome do grupo — mesmo de um grupo do qual já saiu (R20). Vão para
     * quem pediu: o próprio usuário ou o responsável pelo dependente, com o nome do dependente (research R37).
     */
    @Query("""
            select new br.com.amigosecreto.exitrequest.dto.ExitRequestNotice(
                r.id, r.groupId, g.name, r.status, r.decidedAt, s.name)
            from ExitRequest r join SecretGroup g on g.id = r.groupId
            left join AppUser s on s.id = r.userId and r.userId <> r.requestedBy
            where r.requestedBy = :userId
              and r.status <> br.com.amigosecreto.exitrequest.ExitRequestStatus.PENDING
              and r.decisionSeenAt is null
            order by r.decidedAt""")
    List<ExitRequestNotice> findUnseenNotices(@Param("userId") UUID userId);

    /**
     * Cancela, com aviso, todos os pedidos pendentes do grupo: outra saída foi aprovada ou o grupo foi
     * finalizado (FR-028, FR-033). Quem chama já tem o lock do grupo; as mudanças pendentes da transação são
     * gravadas antes (um pedido recém-aprovado não é cancelado).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ExitRequest r
            set r.status = br.com.amigosecreto.exitrequest.ExitRequestStatus.CANCELLED,
                r.decidedAt = :now,
                r.decisionSeenAt = null
            where r.groupId = :groupId
              and r.status = br.com.amigosecreto.exitrequest.ExitRequestStatus.PENDING""")
    int cancelAllPending(@Param("groupId") UUID groupId, @Param("now") Instant now);
}
