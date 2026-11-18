package br.com.amigosecreto.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Documentação OpenAPI gerada (Constituição VIII), só no perfil {@code dev} e só para quem está logado.
 * O contrato de referência é specs/001-amigo-secreto-sorteio/contracts/openapi.yaml; o
 * OpenApiContractIT garante que os dois têm as mesmas rotas.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class OpenApiConfig {

    @Bean
    OpenAPI amigoSecretoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Amigo Secreto API")
                        .version("1.0.0")
                        .description("Grupos e sorteio sigiloso. Nenhuma rota devolve o par de outra pessoa."))
                .components(new Components()
                        .addSecuritySchemes(
                                "sessionCookie",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("SESSION")))
                .addSecurityItem(new SecurityRequirement().addList("sessionCookie"));
    }
}
