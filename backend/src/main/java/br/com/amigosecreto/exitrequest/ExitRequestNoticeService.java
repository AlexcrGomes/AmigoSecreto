package br.com.amigosecreto.exitrequest;

import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.exitrequest.dto.ExitRequestNotice;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avisos guardados das decisões sobre os pedidos de saída (FR-026, research R20): quem estava fora vê a
 * decisão na próxima entrada. Sempre os pedidos do próprio usuário da sessão.
 */
@Service
public class ExitRequestNoticeService {

    private final ExitRequestRepository exitRequests;
    private final Clock clock;

    public ExitRequestNoticeService(ExitRequestRepository exitRequests, Clock clock) {
        this.exitRequests = exitRequests;
        this.clock = clock;
    }

    /** Decisões ainda não vistas, inclusive de grupos dos quais a pessoa já saiu. */
    @Transactional(readOnly = true)
    public List<ExitRequestNotice> unseen(UUID userId) {
        return exitRequests.findUnseenNotices(userId);
    }

    /**
     * Marca o aviso como visto. Idempotente; pedido de outra pessoa ou ainda pendente responde 404. Quem dispensa
     * é quem pediu: o próprio usuário ou o responsável pelo dependente (research R37).
     */
    @Transactional
    public void acknowledge(UUID userId, UUID requestId) {
        ExitRequest request = exitRequests
                .findByIdAndRequestedBy(requestId, userId)
                .filter(found -> !found.isPending())
                .orElseThrow(() -> new NotFoundException(ExitRequestService.REQUEST_NOT_FOUND));
        request.markSeen(clock.instant());
    }
}
