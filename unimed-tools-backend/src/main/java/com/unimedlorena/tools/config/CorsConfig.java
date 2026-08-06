/*
 * Responsabilidade: Define a política CORS consumida pelo frontend e os cabeçalhos de resposta visíveis no navegador.
 */
package com.unimedlorena.tools.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Configura as origens e os cabeçalhos necessários para a comunicação entre
 * o frontend e a API. Os cabeçalhos expostos transportam estatísticas e
 * metadados dos arquivos gerados para o navegador.
 */
@Configuration
public class CorsConfig {

  /**
   * Aplica a política CORS a todos os endpoints sem habilitar credenciais de
   * navegador, pois a aplicação ainda não possui autenticação por sessão.
   */
  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(
      List.of(
        "http://localhost:4200",
        "http://localhost:3000",
        "https://corretor-de-arquivos.onrender.com",
        "https://corretor-de-arquivos.vercel.app",
        "https://*.vercel.app"
      )
    );

    cfg.setAllowedMethods(
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    );
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setExposedHeaders(
      List.of(
        "X-Stats",
        "Content-Disposition",
        "X-Relatorios-Gerados",
        "X-Relatorios-Erros"
      )
    );

    cfg.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source =
      new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);

    return new CorsFilter(source);
  }
}
