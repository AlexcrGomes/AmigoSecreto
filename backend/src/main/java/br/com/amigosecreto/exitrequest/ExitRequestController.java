package br.com.amigosecreto.exitrequest;

import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.exitrequest.dto.ExitRequestNotice;
import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import br.com.amigosecreto.group.dto.GroupDetail;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas dos pedidos de saída (contracts/openapi.yaml da 002, tag exit-requests). O usuário vem SEMPRE da
 * sessão: ninguém pede, cancela ou vê avisos por outra pessoa. Nenhuma resposta tem pares do sorteio.
 */
@RestController
@RequestMapping("/api/v1")
public class ExitRequestController {

    private final ExitRequestService exitRequestService;
    private final ExitRequestNoticeService noticeService;
    private final CurrentUser currentUser;

    public ExitRequestController(
            ExitRequestService exitRequestService, ExitRequestNoticeService noticeService, CurrentUser currentUser) {
        this.exitRequestService = exitRequestService;
        this.noticeService = noticeService;
        this.currentUser = currentUser;
    }

    /** O participante pede para sair de um grupo sorteado (FR-025). */
    @PostMapping("/groups/{groupId}/exit-requests")
    public ResponseEntity<ExitRequestResponse> request(@PathVariable UUID groupId) {
        ExitRequestResponse created = exitRequestService.request(currentUser.id(), groupId);
        return ResponseEntity.created(URI.create("/api/v1/groups/" + groupId + "/exit-requests/" + created.id()))
                .body(created);
    }

    /** O solicitante cancela o próprio pedido pendente. */
    @DeleteMapping("/groups/{groupId}/exit-requests/mine")
    public ResponseEntity<Void> cancel(@PathVariable UUID groupId) {
        exitRequestService.cancel(currentUser.id(), groupId);
        return ResponseEntity.noContent().build();
    }

    /** O responsável pede a saída de um dependente seu depois do sorteio (FR-015 da 004). */
    @PostMapping("/groups/{groupId}/my-dependents/{dependentId}/exit-request")
    public ResponseEntity<ExitRequestResponse> requestForDependent(
            @PathVariable UUID groupId, @PathVariable UUID dependentId) {
        ExitRequestResponse created = exitRequestService.requestForDependent(currentUser.id(), groupId, dependentId);
        return ResponseEntity.created(URI.create("/api/v1/groups/" + groupId + "/exit-requests/" + created.id()))
                .body(created);
    }

    /** O responsável cancela o pedido pendente de um dependente seu. */
    @DeleteMapping("/groups/{groupId}/my-dependents/{dependentId}/exit-request")
    public ResponseEntity<Void> cancelForDependent(@PathVariable UUID groupId, @PathVariable UUID dependentId) {
        exitRequestService.cancelForDependent(currentUser.id(), groupId, dependentId);
        return ResponseEntity.noContent().build();
    }

    /** O organizador aprova: a pessoa sai e o sorteio é desfeito (FR-028). */
    @PostMapping("/groups/{groupId}/exit-requests/{requestId}/approve")
    public GroupDetail approve(@PathVariable UUID groupId, @PathVariable UUID requestId) {
        return exitRequestService.approve(currentUser.id(), groupId, requestId);
    }

    /** O organizador recusa: nada muda no grupo (FR-026). */
    @PostMapping("/groups/{groupId}/exit-requests/{requestId}/reject")
    public GroupDetail reject(@PathVariable UUID groupId, @PathVariable UUID requestId) {
        return exitRequestService.reject(currentUser.id(), groupId, requestId);
    }

    /** Decisões ainda não vistas pelo usuário da sessão (R20). */
    @GetMapping("/exit-request-notices")
    public List<ExitRequestNotice> notices() {
        return noticeService.unseen(currentUser.id());
    }

    /** Marca o aviso como visto (idempotente). */
    @PostMapping("/exit-request-notices/{requestId}/ack")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID requestId) {
        noticeService.acknowledge(currentUser.id(), requestId);
        return ResponseEntity.noContent().build();
    }
}
