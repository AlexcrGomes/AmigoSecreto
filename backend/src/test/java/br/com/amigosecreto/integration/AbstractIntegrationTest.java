package br.com.amigosecreto.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base dos testes de integração: aplicação completa numa porta aleatória + PostgreSQL real
 * (Testcontainers, nunca H2 — Constituição IV).
 *
 * <p>O container é único para toda a suíte (padrão singleton): o contexto do Spring fica em cache entre
 * as classes de teste e precisa continuar apontando para um banco vivo. O Ryuk do Testcontainers remove
 * o container no fim.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            // Chave AES-256 só de teste (32 bytes em base64), sem valor fora daqui
            "app.draw.encryption-key=dGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtdGVzdGUtMTI="
        })
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.7-alpine3.24");

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JsonMapper jsonMapper;

    /** Um "navegador" novo: cookies (sessão e XSRF) próprios, como um usuário diferente. */
    protected ApiClient newClient() {
        return new ApiClient("http://localhost:" + port, jsonMapper);
    }
}
