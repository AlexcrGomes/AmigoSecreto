package br.com.amigosecreto.draw;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Acesso mínimo aos pares (Constituição I): gravar o sorteio e ler UM par por (grupo, quem tirou). De
 * propósito NÃO estende {@code JpaRepository}: não existe {@code findAll} nem busca de todos os pares de
 * um grupo. Apagar os pares de um grupo é permitido (sorteio desfeito, data-model.md §5), sem lê-los.
 */
public interface DrawAssignmentRepository extends Repository<DrawAssignment, DrawAssignmentId> {

    <S extends DrawAssignment> List<S> saveAll(Iterable<S> assignments);

    Optional<DrawAssignment> findByIdGroupIdAndIdGiverId(UUID groupId, UUID giverId);

    /** {@code DELETE} em massa, sem carregar nenhum par. Só para {@code DrawService.discardDraw}. */
    @Modifying
    @Query("delete from DrawAssignment a where a.id.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") UUID groupId);
}
