package com.unimedlorena.tools.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** Nega acesso por padrão e associa cada módulo à permissão persistida. */
@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    SessaoAuthenticationFilter sessaoFilter,
    ObjectMapper objectMapper,
    @Value("${auth.cookie.secure:true}") boolean cookieSecure,
    @Value("${auth.cookie.same-site:Strict}") String cookieSameSite
  ) throws Exception {
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookieCustomizer(builder -> builder
      .secure(cookieSecure)
      .sameSite(cookieSameSite)
      .path("/")
    );

    http
      .cors(Customizer.withDefaults())
      .csrf(csrf -> csrf
        .csrfTokenRepository(csrfRepository)
        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
      )
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .httpBasic(httpBasic -> httpBasic.disable())
      .formLogin(form -> form.disable())
      .logout(logout -> logout.disable())
      .headers(headers -> headers
        .contentTypeOptions(Customizer.withDefaults())
        .frameOptions(frame -> frame.deny())
        .referrerPolicy(referrer -> referrer.policy(
          org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER
        ))
        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
      )
      .exceptionHandling(errors -> errors
        .authenticationEntryPoint((request, response, ex) -> {
          response.setStatus(401);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          objectMapper.writeValue(response.getWriter(), Map.of(
            "codigo", "NAO_AUTENTICADO",
            "message", "Faça login para continuar."
          ));
        })
        .accessDeniedHandler((request, response, ex) -> {
          response.setStatus(403);
          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
          objectMapper.writeValue(response.getWriter(), Map.of(
            "codigo", "ACESSO_NEGADO",
            "message", "Você não possui permissão para esta operação."
          ));
        })
      )
      .authorizeHttpRequests(authorize -> authorize
        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers(
          "/health",
          "/api/auth/csrf",
          "/api/auth/login",
          "/api/auth/mfa/verificar"
        ).permitAll()
        .requestMatchers("/api/auth/**").authenticated()
        .requestMatchers(HttpMethod.GET, "/api/usuarios", "/api/usuarios/permissoes-disponiveis")
          .hasAuthority("USUARIOS_VISUALIZAR")
        .requestMatchers(HttpMethod.POST, "/api/usuarios").hasAuthority("USUARIOS_CRIAR")
        .requestMatchers(HttpMethod.PUT, "/api/usuarios/*").hasAuthority("USUARIOS_EDITAR")
        .requestMatchers(HttpMethod.DELETE, "/api/usuarios/*").hasAuthority("USUARIOS_EDITAR")
        .requestMatchers(HttpMethod.PUT, "/api/usuarios/*/permissoes")
          .hasAuthority("USUARIOS_EDITAR")
        .requestMatchers(HttpMethod.POST, "/api/usuarios/*/resetar-senha")
          .hasAuthority("USUARIOS_EDITAR")
        .requestMatchers("/api/usuarios/**").denyAll()
        .requestMatchers("/api/xml/**").hasAuthority("XML_ACESSAR")
        .requestMatchers("/api/bi/**").hasAuthority("BI_ACESSAR")
        .requestMatchers("/api/ans/**").hasAuthority("ANS_ACESSAR")
        .requestMatchers(HttpMethod.POST, "/api/relatorios/sgu/criar")
          .hasAuthority("RELATORIOS_ADMINISTRAR")
        .requestMatchers(HttpMethod.DELETE, "/api/relatorios/sgu/**")
          .hasAuthority("RELATORIOS_ADMINISTRAR")
        .requestMatchers("/api/relatorios/**").hasAuthority("RELATORIOS_ACESSAR")
        .requestMatchers("/api/**").hasAuthority("APLICACAO_ACESSAR")
        .anyRequest().denyAll()
      )
      .addFilterBefore(sessaoFilter, AnonymousAuthenticationFilter.class);

    return http.build();
  }
}
