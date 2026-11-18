package br.com.amigosecreto.group.dto;

import br.com.amigosecreto.group.GroupStatus;
import java.util.UUID;

/** Schema {@code GroupSummary}: cartão da lista "Meus grupos". */
public record GroupSummary(UUID id, String name, GroupStatus status, int participantCount, boolean organizer) {}
