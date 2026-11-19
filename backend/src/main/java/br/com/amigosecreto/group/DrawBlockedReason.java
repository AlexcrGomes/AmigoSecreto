package br.com.amigosecreto.group;

import java.util.List;

/**
 * Por que o sorteio ainda não pode acontecer (research R16, FR-013, FR-014). A ordem das constantes é a
 * ordem em que as condições são verificadas: a primeira que falhar responde. {@code code()} é o
 * {@code type} do problema e o valor de {@code GroupDetail.drawBlockedReason}.
 */
public enum DrawBlockedReason {
    EVENT_DETAILS_REQUIRED("event-details-required", "Informe a data e o local do evento para sortear."),
    EVENT_DATE_PASSED("event-date-passed", "A data do evento já passou. Atualize a data para sortear."),
    NOT_ENOUGH_PARTICIPANTS("not-enough-participants", "São necessários pelo menos 3 participantes."),
    PARTICIPANTS_PENDING("participants-pending", "Ainda há participantes que não confirmaram.");

    private final String code;
    private final String message;

    DrawBlockedReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    /** "Falta 1 confirmar: Ana." ou "Faltam 2 confirmar: Ana, Beto." (FR-014). */
    public static String pendingMessage(List<String> names) {
        String verb = names.size() == 1 ? "Falta" : "Faltam";
        return verb + " " + names.size() + " confirmar: " + String.join(", ", names) + ".";
    }
}
