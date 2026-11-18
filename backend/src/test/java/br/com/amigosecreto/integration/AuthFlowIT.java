package br.com.amigosecreto.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cadastro, login, logout e sessão de ponta a ponta (US1, quickstart passos 1 a 4). */
class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private static String uniqueEmail() {
        return "ana." + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerStartsAnHttpOnlySessionAndLogoutEndsIt() {
        ApiClient ana = newClient();
        String email = uniqueEmail();

        ApiClient.Response register = ana.register("Ana", email, "senha-forte");

        assertThat(register.status()).isEqualTo(201);
        assertThat(register.json().has("password")).isFalse();
        List<String> setCookies = register.headers().allValues("Set-Cookie");
        assertThat(setCookies)
                .anySatisfy(cookie -> assertThat(cookie).startsWith("SESSION=").contains("HttpOnly"));

        ApiClient.Response me = ana.get("/api/v1/auth/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("email").asString()).isEqualTo(email);

        assertThat(ana.post("/api/v1/auth/logout").status()).isEqualTo(204);
        assertThat(ana.get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    void loginIssuesANewSessionId() {
        ApiClient ana = newClient();
        String email = uniqueEmail();
        ana.register("Ana", email, "senha-forte");
        String sessionBefore = ana.cookie("SESSION").orElseThrow();

        ApiClient.Response login = ana.login(email, "senha-forte");

        assertThat(login.status()).isEqualTo(200);
        assertThat(ana.cookie("SESSION"))
                .hasValueSatisfying(after -> assertThat(after).isNotEqualTo(sessionBefore));
        assertThat(ana.get("/api/v1/auth/me").status()).isEqualTo(200);
    }

    @Test
    void sessionIsStoredInTheDatabaseWithTheUserIdAsPrincipal() {
        ApiClient ana = newClient();
        String email = uniqueEmail();
        String id = ana.register("Ana", email, "senha-forte").json().get("id").asString();

        Integer sessions =
                jdbc.queryForObject("select count(*) from spring_session where principal_name = ?", Integer.class, id);

        assertThat(sessions).isEqualTo(1);
    }

    @Test
    void loginWithAnotherClientWorksAndWrongPasswordIsGeneric() {
        String email = uniqueEmail();
        newClient().register("Ana", email, "senha-forte");

        ApiClient other = newClient();
        ApiClient.Response wrong = other.login(email, "senha-errada");
        ApiClient.Response unknown = other.login(uniqueEmail(), "senha-errada");

        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.json().get("detail").asString())
                .isEqualTo(unknown.json().get("detail").asString())
                .isEqualTo("E-mail ou senha inválidos.");
        assertThat(other.login(email, "senha-forte").status()).isEqualTo(200);
    }

    @Test
    void sixthWrongPasswordInARowIsBlocked() {
        String email = uniqueEmail();
        newClient().register("Ana", email, "senha-forte");
        ApiClient attacker = newClient();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(attacker.login(email, "errada-" + attempt).status()).isEqualTo(401);
        }
        ApiClient.Response sixth = attacker.login(email, "errada-6");

        assertThat(sixth.status()).isEqualTo(429);
        assertThat(sixth.header("Retry-After")).isPresent();
        // Bloqueado inclusive com a senha certa, até a janela passar
        assertThat(attacker.login(email, "senha-forte").status()).isEqualTo(429);
    }

    @Test
    void duplicatedEmailIs409EvenWithDifferentCase() {
        String email = uniqueEmail();
        newClient().register("Ana", email, "senha-forte");

        ApiClient.Response duplicate = newClient().register("Outra Ana", email.toUpperCase(), "senha-forte");

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.json().get("detail").asString()).isEqualTo("Este e-mail já está cadastrado.");
    }
}
