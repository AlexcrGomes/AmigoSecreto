package br.com.amigosecreto.draw;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Acesso mínimo aos pares (Constituição I): gravar o sorteio e ler UM par por (grupo, quem tirou). De
 * propósito NÃO estende {@code JpaRepository}: não existe {@code findAll} nem busca de todos os pares de
 * um grupo.
 */
public interface DrawAssignmentRepository extends Repository<DrawAssignment, DrawAssignmentId> {

    <S extends DrawAssignment> List<S> saveAll(Iterable<S> assignments);

    Optional<DrawAssignment> findByIdGroupIdAndIdGiverId(UUID groupId, UUID giverId);
}
