package br.com.amigosecreto.group;

import java.util.UUID;

/** Linha da lista "Meus grupos", com a contagem de membros calculada no banco. */
public record GroupSummaryView(UUID id, String name, GroupStatus status, UUID organizerId, Long participantCount) {}
