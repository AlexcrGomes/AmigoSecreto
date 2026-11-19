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

    /**
     * Algo mudou no grupo sem mudar quem participa: detalhes do evento, uma confirmação ou um pedido de
     * saída. {@code confirmationsReset} diz se a mudança voltou as confirmações a pendente (FR-015).
     */
    public record GroupUpdatedEvent(
            UUID groupId, GroupStatus status, int participantCount, boolean confirmationsReset) {}

    /** Saída aprovada: o sorteio foi desfeito e o grupo voltou a {@code OPEN} (FR-028). */
    public record DrawUndoneEvent(UUID groupId, int participantCount) {}

    /** O evento passou e o grupo foi finalizado (FR-017). */
    public record GroupFinishedEvent(UUID groupId, int participantCount) {}

    /** Por que alguém deixou de ser membro. */
    public enum RevocationReason {
        /** Saiu por conta própria. */
        LEFT,
        /** Foi removido pelo organizador: recebe o aviso {@code membership-revoked}. */
        REMOVED,
        /** O organizador aprovou o pedido de saída depois do sorteio: também recebe o aviso. */
        EXIT_APPROVED
    }

    /** O usuário {@code userId} deixou de ser membro (US4): suas conexões SSE do grupo são encerradas. */
    public record MembershipRevokedEvent(UUID groupId, UUID userId, RevocationReason reason) {}
}
