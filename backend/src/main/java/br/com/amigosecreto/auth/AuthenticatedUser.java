package br.com.amigosecreto.auth;

import java.io.Serializable;
import java.util.UUID;

/** Principal guardado na sessão (Spring Session JDBC serializa este objeto). */
public record AuthenticatedUser(UUID id, String name, String email) implements Serializable {}
