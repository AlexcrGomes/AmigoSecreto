package br.com.amigosecreto.group;

import br.com.amigosecreto.auth.AppUserRepository;
import br.com.amigosecreto.exitrequest.ExitRequestRepository;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apaga o {@code app_user} de dependentes que deixaram o grupo (feature 004, research R33): o dependente só
 * existe dentro do grupo (FR-008). Fica só o dependente referenciado por um pedido de saída próprio, como
 * registro e para a regra de não readicionar (research R37). Quem chama já apagou as participações.
 */
@Component
public class DependentAccounts {

    private final AppUserRepository users;
    private final ExitRequestRepository exitRequests;

    public DependentAccounts(AppUserRepository users, ExitRequestRepository exitRequests) {
        this.users = users;
        this.exitRequests = exitRequests;
    }

    public void deleteUnreferenced(Collection<UUID> dependentIds) {
        for (UUID dependentId : dependentIds) {
            if (!exitRequests.existsByUserId(dependentId)) {
                users.deleteById(dependentId);
            }
        }
    }
}
