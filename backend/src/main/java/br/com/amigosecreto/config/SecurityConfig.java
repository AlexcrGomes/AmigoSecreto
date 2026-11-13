package br.com.amigosecreto.config;

import br.com.amigosecreto.common.ProblemResponseWriter;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Segurança da API (Constituição III, research R2): sessão em cookie HttpOnly, CSRF no padrão SPA e
 * erros 401/403 em {@code application/problem+json}, sem redirecionamentos.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    /** Custo do BCrypt (research R3). */
    static final int BCRYPT_COST = 12;

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http, ProblemResponseWriter problems, SecurityContextRepository securityContextRepository)
            throws Exception {
        http
                // Cookie XSRF-TOKEN legível pelo JS + cabeçalho X-XSRF-TOKEN em toda requisição que altera estado
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        // Únicas rotas públicas (Constituição III)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) -> problems.write(
                                response, 401, "unauthorized", "Não autenticado", "Faça login para continuar."))
                        .accessDeniedHandler((request, response, ex) -> {
                            if (ex instanceof CsrfException) {
                                problems.write(
                                        response,
                                        403,
                                        "csrf-invalid",
                                        "Requisição recusada",
                                        "Sua sessão de segurança expirou. Recarregue a página e tente novamente.");
                            } else {
                                problems.write(
                                        response,
                                        403,
                                        "forbidden",
                                        "Acesso negado",
                                        "Você não tem permissão para esta ação.");
                            }
                        }))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                // Não guardar a requisição negada na sessão (não há página de login para onde voltar)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // Mesma origem: o nginx faz proxy de /api (research R10)
                .cors(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(BCRYPT_COST)));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
