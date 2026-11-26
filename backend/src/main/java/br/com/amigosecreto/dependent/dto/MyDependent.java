package br.com.amigosecreto.dependent.dto;

import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
import java.util.UUID;

/**
 * Schema {@code MyDependent}: um dependente do usuário da sessão (feature 004) — nunca o resultado do sorteio
 * dele. {@code confirmed} é o espelho da confirmação do responsável (research R36); {@code exitRequest} é o
 * pedido de saída pendente do dependente, se houver.
 */
public record MyDependent(
        UUID id, String name, boolean confirmed, MyWishlist wishlist, ExitRequestResponse exitRequest) {}
