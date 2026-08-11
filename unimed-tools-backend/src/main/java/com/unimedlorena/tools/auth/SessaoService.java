package com.unimedlorena.tools.auth;

import com.unimedlorena.tools.auth.AuthRepository.SessaoRow;
import com.unimedlorena.tools.auth.AuthRepository.UsuarioRow;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** Cria, valida e revoga sessões opacas persistidas somente por hash. */
@Service
public class SessaoService {

  public static final String COOKIE_NAME = "UNIMED_SESSION";
  public static final String TOKEN_HASH_ATTRIBUTE = "unimed.session.tokenHash";

  private final AuthRepository repository;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Duration idleTimeout;
  private final Duration absoluteTimeout;
  private final boolean cookieSecure;
  private final String cookieSameSite;

  public SessaoService(
    AuthRepository repository,
    @Value("${auth.session.idle-minutes:30}") long idleMinutes,
    @Value("${auth.session.absolute-hours:8}") long absoluteHours,
    @Value("${auth.cookie.secure:true}") boolean cookieSecure,
    @Value("${auth.cookie.same-site:Strict}") String cookieSameSite
  ) {
    this.repository = repository;
    this.idleTimeout = Duration.ofMinutes(Math.max(5, idleMinutes));
    this.absoluteTimeout = Duration.ofHours(Math.max(1, absoluteHours));
    this.cookieSecure = cookieSecure;
    this.cookieSameSite = cookieSameSite;
  }

  public String criar(long usuarioId, boolean mfaValidada, String ip, String userAgent) {
    String token = novoToken();
    repository.criarSessao(
      usuarioId,
      hash(token),
      LocalDateTime.now().plus(absoluteTimeout),
      mfaValidada ? LocalDateTime.now() : null,
      AuditoriaService.limitar(ip, 45),
      AuditoriaService.limitar(userAgent, 500)
    );
    return token;
  }

  public Optional<UsuarioPrincipal> autenticar(HttpServletRequest request) {
    String token = lerCookie(request);
    if (token == null) return Optional.empty();
    String tokenHash = hash(token);
    Optional<SessaoRow> encontrada = repository.buscarSessao(tokenHash);
    if (encontrada.isEmpty()) return Optional.empty();

    SessaoRow sessao = encontrada.get();
    LocalDateTime agora = LocalDateTime.now();
    if (
      sessao.revogadaEm() != null ||
      !sessao.expiraEm().isAfter(agora) ||
      !sessao.ultimaAtividadeEm().plus(idleTimeout).isAfter(agora)
    ) {
      repository.revogarSessao(tokenHash, "EXPIRADA");
      return Optional.empty();
    }

    Optional<UsuarioRow> encontrado = repository.buscarUsuarioPorId(sessao.usuarioId());
    if (encontrado.isEmpty()) return Optional.empty();
    UsuarioRow usuario = encontrado.get();
    if (
      !usuario.perfilAtivo() ||
      !"ATIVO".equals(usuario.status()) ||
      ("ADMINISTRADOR".equals(usuario.perfil()) && sessao.mfaValidadaEm() == null)
    ) return Optional.empty();

    repository.atualizarAtividade(tokenHash);
    request.setAttribute(TOKEN_HASH_ATTRIBUTE, tokenHash);
    return Optional.of(
      new UsuarioPrincipal(
        usuario.id(),
        usuario.nome(),
        usuario.login(),
        usuario.email(),
        usuario.perfil(),
        usuario.deveTrocarSenha(),
        Set.copyOf(repository.buscarPermissoes(usuario.id()))
      )
    );
  }

  public void gravarCookie(HttpServletResponse response, String token) {
    ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
      .httpOnly(true)
      .secure(cookieSecure)
      .sameSite(cookieSameSite)
      .path("/")
      .maxAge(absoluteTimeout)
      .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public void limparCookie(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
      .httpOnly(true)
      .secure(cookieSecure)
      .sameSite(cookieSameSite)
      .path("/")
      .maxAge(Duration.ZERO)
      .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public void revogarAtual(HttpServletRequest request, String motivo) {
    Object tokenHash = request.getAttribute(TOKEN_HASH_ATTRIBUTE);
    if (tokenHash instanceof String value) repository.revogarSessao(value, motivo);
  }

  public void revogarTodas(long usuarioId, String motivo) {
    repository.revogarSessoesDoUsuario(usuarioId, motivo);
  }

  public static String hash(String token) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))
      );
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 indisponível.", ex);
    }
  }

  private String novoToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String lerCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
