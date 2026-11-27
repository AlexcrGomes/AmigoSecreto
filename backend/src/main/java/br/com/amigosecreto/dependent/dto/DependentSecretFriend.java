package br.com.amigosecreto.dependent.dto;

import br.com.amigosecreto.draw.dto.SecretFriendResponse;
import java.util.UUID;

/**
 * Schema {@code DependentSecretFriend}: o resultado de um dependente do usuário da sessão (feature 004, research
 * R38). {@code dependentId} é o dependente, nunca quem ele tirou.
 */
public record DependentSecretFriend(UUID dependentId, String dependentName, SecretFriendResponse secretFriend) {}
