package br.com.amigosecreto.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único (UTC) para todas as datas gravadas, e o fuso em que as pessoas informam a data do evento
 * (horário de Brasília, research R13 da 002). O fuso só é usado para converter; o banco guarda UTC.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /** Fuso da data e do horário do evento, para todos os usuários. */
    public static final ZoneId EVENT_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId eventZone() {
        return EVENT_ZONE;
    }
}
