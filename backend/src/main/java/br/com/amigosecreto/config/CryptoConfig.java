package br.com.amigosecreto.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fonte de aleatoriedade criptográfica: códigos de grupo (R5) e sorteio (R6, Constituição II). */
@Configuration(proxyBeanMethods = false)
public class CryptoConfig {

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
