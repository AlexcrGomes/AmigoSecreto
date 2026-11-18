package br.com.amigosecreto.config;

import br.com.amigosecreto.draw.AssignmentCipher;
import br.com.amigosecreto.draw.DerangementGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Criptografia e aleatoriedade: códigos de grupo (R5), sorteio (R6) e cifra dos pares (R9). */
@Configuration(proxyBeanMethods = false)
public class CryptoConfig {

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    DerangementGenerator derangementGenerator(SecureRandom secureRandom) {
        return new DerangementGenerator(secureRandom);
    }

    /**
     * A aplicação NÃO sobe sem uma chave válida: sem ela os pares não poderiam ser cifrados nem lidos. A
     * mensagem de erro nunca inclui o valor da chave.
     */
    @Bean
    AssignmentCipher assignmentCipher(
            @Value("${app.draw.encryption-key:}") String encodedKey, SecureRandom secureRandom) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedKey.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(missingKeyMessage());
        }
        if (key.length != 32) {
            throw new IllegalStateException(missingKeyMessage());
        }
        return new AssignmentCipher(key, secureRandom);
    }

    private static String missingKeyMessage() {
        return "DRAW_ENCRYPTION_KEY ausente ou inválida: informe 32 bytes em Base64 "
                + "(gere com: openssl rand -base64 32)";
    }
}
