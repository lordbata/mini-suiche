package com.minisuiche.vault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Inyectar el reloj permite fijar la fecha en las pruebas (tarjetas vencidas, etc.). */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
