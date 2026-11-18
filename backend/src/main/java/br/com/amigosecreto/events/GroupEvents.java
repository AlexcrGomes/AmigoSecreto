package br.com.amigosecreto.events;

import br.com.amigosecreto.group.GroupStatus;
import java.util.UUID;

/**
 * Eventos de domínio dos grupos. São sinais: levam só IDs, status e contagem — nunca nomes nem pares
 * (Constituição I, contracts/sse-events.md). Publicados dentro da transação e entregues após o commit.
 */
public final class GroupEvents {

    private GroupEvents() {}

    /** Alguém entrou, saiu ou foi removido. */
    public record ParticipantsChangedEvent(UUID groupId, GroupStatus status, int participantCount) {}

    /** Sorteio concluído. */
    public record GroupDrawnEvent(UUID groupId, int participantCount) {}

    /** Por que alguém deixou de ser membro. */
    public enum RevocationReason {
        /** Saiu por conta própria. */
        LEFT,
        /** Foi removido pelo organizador: recebe o aviso {@code membership-revoked}. */
        REMOVED
    }

    /** O usuário {@code userId} deixou de ser membro (US4): suas conexões SSE do grupo são encerradas. */
    public record MembershipRevokedEvent(UUID groupId, UUID userId, RevocationReason reason) {}
}
