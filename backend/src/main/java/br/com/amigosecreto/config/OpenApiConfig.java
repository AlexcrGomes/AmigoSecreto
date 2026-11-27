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
 * Os contratos de referência são specs/001-amigo-secreto-sorteio/contracts/openapi.yaml,
 * specs/002-grupo-evento-confirmacao/contracts/openapi.yaml, specs/003-lista-desejos/contracts/openapi.yaml e
 * specs/004-participantes-dependentes/contracts/openapi.yaml (os da 002 em diante listam só o que é novo ou
 * mudou); o OpenApiContractIT garante que as rotas geradas são exatamente a união dos quatro. Os campos dos
 * schemas são descritos nos contratos e no Javadoc dos DTOs.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class OpenApiConfig {

    @Bean
    OpenAPI amigoSecretoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Amigo Secreto API")
                        .version("1.3.0")
                        .description("Grupos com detalhes do evento, confirmação de participação, sorteio"
                                + " sigiloso, grupos finalizados e saída depois do sorteio com aprovação do"
                                + " organizador, lista de desejos, valor do presente e participantes"
                                + " dependentes. Nenhuma rota devolve o par de outra pessoa, nem a lista de"
                                + " desejos de quem o usuário não tirou; o resultado de um dependente só vai"
                                + " para o responsável, sem id vindo do cliente."))
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
