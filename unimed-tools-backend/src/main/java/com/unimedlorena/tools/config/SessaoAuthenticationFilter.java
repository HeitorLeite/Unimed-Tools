package com.unimedlorena.tools.config;

import com.unimedlorena.tools.auth.SessaoService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Converte uma sessão opaca válida em identidade do Spring Security. */
@Component
public class SessaoAuthenticationFilter extends OncePerRequestFilter {

  private final SessaoService sessaoService;

  public SessaoAuthenticationFilter(SessaoService sessaoService) {
    this.sessaoService = sessaoService;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      sessaoService.autenticar(request).ifPresent(this::autenticar);
    }
    filterChain.doFilter(request, response);
  }

  private void autenticar(UsuarioPrincipal principal) {
    var authorities = principal.deveTrocarSenha()
      ? java.util.List.of(new SimpleGrantedAuthority("TROCAR_SENHA"))
      : java.util.stream.Stream.concat(
          principal.permissoes().stream(),
          java.util.stream.Stream.of("ROLE_" + principal.perfil())
        )
        .map(SimpleGrantedAuthority::new)
        .toList();

    SecurityContextHolder.getContext().setAuthentication(
      new UsernamePasswordAuthenticationToken(principal, null, authorities)
    );
  }
}
