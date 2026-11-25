package br.com.amigosecreto.group.dto;

import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import br.com.amigosecreto.group.ConfirmationStatus;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema {@code GroupDetail}. {@code code} vem formatado ({@code XXXX-XXXX}). {@code eventDetails} é nulo (e
 * {@code eventDetailsMissing} verdadeiro) só em grupo antigo ainda sem data e local. {@code drawBlockedReason}
 * é o código de research R16 (ou nulo) enquanto o grupo está {@code OPEN}; {@code canDraw} = organizador,
 * {@code OPEN} e nenhum motivo de bloqueio. {@code eventDatePassed} só vale para grupo {@code OPEN} cujo
 * evento já aconteceu (US3-5); {@code finishedAt} só para grupo {@code FINISHED}. {@code drawUndone}: grupo
 * {@code OPEN} cujo último sorteio foi desfeito por uma saída aprovada. {@code myExitRequest} é o pedido de
 * saída pendente do usuário; {@code pendingExitRequests} só vem preenchido para o organizador. Nunca contém
 * pares do sorteio, nem quem a pessoa que pediu para sair havia tirado (FR-032). {@code myWishlist} é só a
 * lista de desejos do usuário da sessão (feature 003): a de outra pessoa nunca vem aqui.
 */
public record GroupDetail(
        UUID id,
        String name,
        String code,
        GroupStatus status,
        Instant drawnAt,
        List<Participant> participants,
        int participantCount,
        int maxParticipants,
        boolean organizer,
        boolean canDraw,
        EventDetailsResponse eventDetails,
        boolean eventDetailsMissing,
        boolean eventDatePassed,
        int detailsVersion,
        int confirmedCount,
        ConfirmationStatus myConfirmation,
        String drawBlockedReason,
        Instant finishedAt,
        boolean drawUndone,
        ExitRequestResponse myExitRequest,
        List<ExitRequestResponse> pendingExitRequests,
        MyWishlist myWishlist) {}
