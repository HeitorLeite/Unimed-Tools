package com.unimedlorena.tools.controller;

import com.unimedlorena.tools.auth.AuthService;
import com.unimedlorena.tools.auth.SessaoService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.AuthDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final SessaoService sessaoService;

  public AuthController(AuthService authService, SessaoService sessaoService) {
    this.authService = authService;
    this.sessaoService = sessaoService;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
  }

  @PostMapping("/login")
  public AuthDtos.AuthFlowResponse login(
    @Valid @RequestBody AuthDtos.LoginRequest request,
    HttpServletRequest httpRequest,
    HttpServletResponse response
  ) {
    return concluir(authService.login(request, info(httpRequest)), response);
  }

  @PostMapping("/mfa/verificar")
  public AuthDtos.AuthFlowResponse verificarMfa(
    @Valid @RequestBody AuthDtos.MfaRequest request,
    HttpServletRequest httpRequest,
    HttpServletResponse response
  ) {
    return concluir(authService.verificarMfa(request, info(httpRequest)), response);
  }

  @GetMapping("/me")
  public AuthDtos.UsuarioResponse me(@AuthenticationPrincipal UsuarioPrincipal principal) {
    return authService.usuarioResponse(principal);
  }

  @PostMapping("/senha")
  public AuthDtos.AuthFlowResponse trocarSenha(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @Valid @RequestBody AuthDtos.TrocaSenhaRequest request,
    HttpServletRequest httpRequest,
    HttpServletResponse response
  ) {
    return concluir(authService.trocarSenha(principal, request, info(httpRequest)), response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    authService.logout(principal, request, info(request));
    sessaoService.limparCookie(response);
    return ResponseEntity.noContent().build();
  }

  private AuthDtos.AuthFlowResponse concluir(
    AuthService.AuthResult result,
    HttpServletResponse response
  ) {
    if (result.sessionToken() != null) sessaoService.gravarCookie(response, result.sessionToken());
    return result.response();
  }

  private AuthService.RequestInfo info(HttpServletRequest request) {
    // Não confia em X-Forwarded-For sem uma lista de proxies confiáveis configurada.
    return new AuthService.RequestInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
  }
}
