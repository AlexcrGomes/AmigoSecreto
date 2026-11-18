package br.com.amigosecreto.group;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecretGroupRepository extends JpaRepository<SecretGroup, UUID> {

    boolean existsByCode(String code);

    /** Só o ID: o grupo é carregado depois, já com o lock, para não usar um estado antigo. */
    @Query("select g.id from SecretGroup g where g.code = :code")
    Optional<UUID> findIdByCode(@Param("code") String code);

    /** {@code SELECT … FOR UPDATE}: serializa entradas, saídas e o sorteio do mesmo grupo (research R7). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SecretGroup g where g.id = :id")
    Optional<SecretGroup> findByIdForUpdate(@Param("id") UUID id);
}
