package br.com.amigosecreto.exitrequest;

/** Situação do pedido de saída (data-model.md §3). Só {@code PENDING} pode mudar. */
public enum ExitRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
