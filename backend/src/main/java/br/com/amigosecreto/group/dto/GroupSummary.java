package br.com.amigosecreto.group.dto;

import br.com.amigosecreto.group.GroupStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Schema {@code GroupSummary}: cartão da lista "Meus grupos". {@code eventDate} e {@code eventTime}
 * ({@code HH:mm}) ficam nulos em grupos antigos ainda sem data (FR-034).
 */
public record GroupSummary(
        UUID id,
        String name,
        GroupStatus status,
        int participantCount,
        boolean organizer,
        LocalDate eventDate,
        String eventTime) {}
