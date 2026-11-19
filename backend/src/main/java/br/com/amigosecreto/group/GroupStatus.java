package br.com.amigosecreto.group;

/**
 * Ciclo de vida do grupo: {@code OPEN → DRAWN → FINISHED}. A única volta é {@code DRAWN → OPEN}, quando o
 * organizador aprova um pedido de saída e o sorteio é desfeito (Constituição II; data-model.md §1 da 002).
 * {@code FINISHED} é definitivo.
 */
public enum GroupStatus {
    OPEN,
    DRAWN,
    FINISHED
}
