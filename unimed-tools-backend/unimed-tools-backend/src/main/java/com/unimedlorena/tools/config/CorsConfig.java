/*
 * Responsabilidade: Define a allowlist CORS e os cabeçalhos visíveis ao frontend.
 */
package com.unimedlorena.tools.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
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
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(
      List.of("Content-Type", "Accept", "X-XSRF-TOKEN", "X-CSRF-TOKEN")
    );
    cfg.setExposedHeaders(
      List.of(
        "X-Stats",
        "Content-Disposition",
        "X-Total-Registros",
        "X-Relatorios-Gerados",
        "X-Relatorios-Erros"
      )
    );
    // A origem continua em allowlist; credenciais habilitam o cookie HttpOnly.
    cfg.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }
}
