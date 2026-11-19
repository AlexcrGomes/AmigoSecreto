package br.com.amigosecreto.group;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha da lista "Meus grupos", com a contagem de membros calculada no banco. O início do evento vem em UTC
 * e é convertido para a data e o horário de Brasília pelo {@link EventSchedule}.
 */
public record GroupSummaryView(
        UUID id,
        String name,
        GroupStatus status,
        UUID organizerId,
        Long participantCount,
        Instant eventStartsAt,
        Boolean eventHasTime) {}
