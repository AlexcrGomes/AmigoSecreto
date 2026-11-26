package br.com.amigosecreto.dependent;

import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.dependent.dto.DependentRequest;
import br.com.amigosecreto.dependent.dto.MyDependent;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas dos dependentes do usuário da sessão (contracts/openapi.yaml da 004, tag dependents). O caminho diz
 * {@code my-dependents}: o responsável vem SEMPRE da sessão, e dependente de outra pessoa responde 404.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/my-dependents")
public class DependentController {

    private final DependentService dependentService;
    private final CurrentUser currentUser;

    public DependentController(DependentService dependentService, CurrentUser currentUser) {
        this.dependentService = dependentService;
        this.currentUser = currentUser;
    }

    /** Adiciona um dependente; o usuário da sessão vira o responsável. */
    @PostMapping
    public ResponseEntity<MyDependent> add(@PathVariable UUID groupId, @Valid @RequestBody DependentRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dependentService.add(currentUser.id(), groupId, body));
    }

    /** Renomeia um dependente seu. */
    @PutMapping("/{dependentId}")
    public MyDependent rename(
            @PathVariable UUID groupId, @PathVariable UUID dependentId, @Valid @RequestBody DependentRequest body) {
        return dependentService.rename(currentUser.id(), groupId, dependentId, body);
    }

    /** Remove um dependente seu. */
    @DeleteMapping("/{dependentId}")
    public ResponseEntity<Void> remove(@PathVariable UUID groupId, @PathVariable UUID dependentId) {
        dependentService.remove(currentUser.id(), groupId, dependentId);
        return ResponseEntity.noContent().build();
    }
}
