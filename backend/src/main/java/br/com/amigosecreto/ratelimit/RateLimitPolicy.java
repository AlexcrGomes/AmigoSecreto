package br.com.amigosecreto.ratelimit;

/** Políticas de limite de tentativas (research R4). A chave de cada uma é definida por quem consome. */
public enum RateLimitPolicy {
    /** Login com falha — chave: e-mail normalizado + IP. */
    LOGIN_FAILURE,
    /** Cadastro — chave: IP. */
    REGISTER,
    /** Entrar com código inválido — chave: usuário. */
    JOIN_INVALID_CODE,
    /** Iniciar sorteio — chave: grupo. */
    DRAW
}
