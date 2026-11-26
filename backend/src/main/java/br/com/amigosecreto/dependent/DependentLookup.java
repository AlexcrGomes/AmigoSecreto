package br.com.amigosecreto.dependent;

import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Encontra um dependente do usuário da sessão (research R34). O responsável vem SEMPRE da sessão: dependente
 * de outra pessoa, de outro grupo ou inexistente responde o mesmo 404, sem dizer se existe (FR-007).
 */
@Component
public class DependentLookup {

    static final String DEPENDENT_NOT_FOUND = "Dependente não encontrado.";

    private final GroupMemberRepository members;

    public DependentLookup(GroupMemberRepository members) {
        this.members = members;
    }

    /** A participação do dependente {@code dependentId} de {@code sessionUserId} no grupo, ou 404. */
    public GroupMember require(UUID sessionUserId, UUID groupId, UUID dependentId) {
        return members.findDependent(dependentId, groupId, sessionUserId)
                .orElseThrow(() -> new NotFoundException(DEPENDENT_NOT_FOUND));
    }
}
