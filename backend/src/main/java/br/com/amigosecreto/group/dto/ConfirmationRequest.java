package br.com.amigosecreto.group.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Schema {@code ConfirmationRequest}: a versão dos detalhes que a pessoa viu ({@code GroupDetail.detailsVersion}).
 * A confirmação só vale para essa versão (FR-035). Não há campo de usuário: confirma sempre quem está na
 * sessão.
 */
public record ConfirmationRequest(
        @NotNull(message = VERSION_MESSAGE) @Min(value = 1, message = VERSION_MESSAGE)
        Integer detailsVersion) {

    public static final String VERSION_MESSAGE = "Recarregue o grupo e confirme de novo.";
}
