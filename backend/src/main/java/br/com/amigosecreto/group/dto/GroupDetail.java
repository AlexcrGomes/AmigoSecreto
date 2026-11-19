package br.com.amigosecreto.group.dto;

import br.com.amigosecreto.group.GroupStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema {@code GroupDetail}. {@code code} vem formatado ({@code XXXX-XXXX}); {@code canDraw} = organizador,
 * {@code OPEN} e pelo menos 3 participantes. {@code eventDetails} é nulo (e {@code eventDetailsMissing}
 * verdadeiro) só em grupo antigo ainda sem data e local. Nunca contém pares do sorteio.
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
        int detailsVersion) {}
