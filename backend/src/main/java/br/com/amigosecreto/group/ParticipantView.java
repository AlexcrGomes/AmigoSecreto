package br.com.amigosecreto.group;

import java.util.UUID;

/** Participante de um grupo, como lido do banco (só ID e nome — nunca pares). */
public record ParticipantView(UUID userId, String name) {}
