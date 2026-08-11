package com.unimedlorena.tools.auth;

import com.unimedlorena.tools.auth.AuthRepository.DesafioRow;
import com.unimedlorena.tools.auth.AuthRepository.UsuarioRow;
import com.unimedlorena.tools.dto.AuthDtos;
import com.unimedlorena.tools.exception.ApiException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordena senha, bloqueio, desafio TOTP, sessão e troca obrigatória de senha. */
@Service
public class AuthService {

  private static final int MAX_MFA_ATTEMPTS = 5;

  private final AuthRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final PoliticaSenhaService politicaSenha;
  private final TotpService totpService;
  private final CriptografiaMfaService criptografiaMfa;
  private final SessaoService sessaoService;
  private final AuditoriaService auditoria;
  private final SecureRandom secureRandom = new SecureRandom();
  private final String dummyPasswordHash;
  private final int maxLoginAttempts;
  private final long blockMinutes;
  private final long challengeMinutes;

  public AuthService(
    AuthRepository repository,
    PasswordEncoder passwordEncoder,
    PoliticaSenhaService politicaSenha,
    TotpService totpService,
    CriptografiaMfaService criptografiaMfa,
    SessaoService sessaoService,
    AuditoriaService auditoria,
    @Value("${auth.login.max-attempts:5}") int maxLoginAttempts,
    @Value("${auth.login.block-minutes:15}") long blockMinutes,
    @Value("${auth.challenge.minutes:5}") long challengeMinutes
  ) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.politicaSenha = politicaSenha;
    this.totpService = totpService;
    this.criptografiaMfa = criptografiaMfa;
    this.sessaoService = sessaoService;
    this.auditoria = auditoria;
    this.maxLoginAttempts = Math.max(3, maxLoginAttempts);
    this.blockMinutes = Math.max(1, blockMinutes);
    this.challengeMinutes = Math.max(1, challengeMinutes);
    this.dummyPasswordHash = passwordEncoder.encode("comparacao-constante-sem-usuario");
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult login(AuthDtos.LoginRequest request, RequestInfo info) {
    String login = normalizarLogin(request.login());
    UsuarioRow usuario = repository.buscarUsuarioPorLogin(login).orElse(null);
    if (usuario == null) {
      passwordEncoder.matches(request.senha(), dummyPasswordHash);
      auditoria.registrar(null, null, "LOGIN", "FALHA", info.ip(), info.userAgent(), Map.of("motivo", "CREDENCIAL_INVALIDA"));
      throw credencialInvalida();
    }

    LocalDateTime agora = LocalDateTime.now();
    if (usuario.bloqueadoAte() != null && usuario.bloqueadoAte().isAfter(agora)) {
      auditoria.registrar(usuario.id(), usuario.id(), "LOGIN", "BLOQUEADO", info.ip(), info.userAgent(), Map.of("motivo", "LIMITE_TENTATIVAS"));
      throw new ApiException(HttpStatus.LOCKED, "USUARIO_BLOQUEADO", "A conta está temporariamente bloqueada. Tente novamente mais tarde.");
    }
    if (!usuario.perfilAtivo() || "INATIVO".equals(usuario.status()) || "PENDENTE_ATIVACAO".equals(usuario.status())) {
      passwordEncoder.matches(request.senha(), usuario.senhaHash());
      auditoria.registrar(usuario.id(), usuario.id(), "LOGIN", "FALHA", info.ip(), info.userAgent(), Map.of("motivo", "CONTA_INDISPONIVEL"));
      throw credencialInvalida();
    }

    if (!passwordEncoder.matches(request.senha(), usuario.senhaHash())) {
      int tentativas = usuario.tentativasLogin() + 1;
      LocalDateTime bloqueadoAte = tentativas >= maxLoginAttempts
        ? agora.plusMinutes(blockMinutes)
        : null;
      repository.registrarFalhaLogin(usuario.id(), tentativas, bloqueadoAte);
      auditoria.registrar(
        usuario.id(),
        usuario.id(),
        "LOGIN",
        bloqueadoAte == null ? "FALHA" : "BLOQUEADO",
        info.ip(),
        info.userAgent(),
        Map.of("motivo", "CREDENCIAL_INVALIDA", "tentativas", tentativas)
      );
      if (bloqueadoAte != null) {
        throw new ApiException(HttpStatus.LOCKED, "USUARIO_BLOQUEADO", "A conta está temporariamente bloqueada. Tente novamente mais tarde.");
      }
      throw credencialInvalida();
    }

    if (
      usuario.deveTrocarSenha() &&
      usuario.senhaTemporariaExpiraEm() != null &&
      !usuario.senhaTemporariaExpiraEm().isAfter(agora)
    ) {
      auditoria.registrar(usuario.id(), usuario.id(), "LOGIN", "FALHA", info.ip(), info.userAgent(), Map.of("motivo", "SENHA_TEMPORARIA_EXPIRADA"));
      throw new ApiException(HttpStatus.UNAUTHORIZED, "SENHA_TEMPORARIA_EXPIRADA", "A senha temporária expirou. Solicite uma nova senha ao administrador.");
    }

    if ("ADMINISTRADOR".equals(usuario.perfil())) {
      return criarDesafioMfa(usuario, info);
    }
    return autenticar(usuario, false, info);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult verificarMfa(AuthDtos.MfaRequest request, RequestInfo info) {
    String tokenHash = SessaoService.hash(request.desafioToken());
    DesafioRow desafio = repository.buscarDesafio(tokenHash).orElseThrow(this::desafioInvalido);
    if (desafio.consumidoEm() != null || !desafio.expiraEm().isAfter(LocalDateTime.now())) {
      throw desafioInvalido();
    }
    UsuarioRow usuario = repository.buscarUsuarioPorId(desafio.usuarioId()).orElseThrow(this::desafioInvalido);
    validarUsuarioDuranteDesafio(usuario);
    String segredoCriptografado = "MFA_CONFIGURACAO".equals(desafio.tipo())
      ? desafio.mfaSegredoCriptografado()
      : usuario.mfaSegredoCriptografado();
    if (segredoCriptografado == null) throw desafioInvalido();

    String segredo = criptografiaMfa.descriptografar(segredoCriptografado);
    Long ultimoPasso = "MFA_CONFIGURACAO".equals(desafio.tipo()) ? null : usuario.ultimoPassoMfa();
    Long passo = totpService.validar(segredo, request.codigo(), ultimoPasso);
    if (passo == null) {
      int tentativas = desafio.tentativas() + 1;
      repository.registrarFalhaDesafio(tokenHash, tentativas >= MAX_MFA_ATTEMPTS);
      auditoria.registrar(usuario.id(), usuario.id(), "MFA_VALIDAR", tentativas >= MAX_MFA_ATTEMPTS ? "BLOQUEADO" : "FALHA", info.ip(), info.userAgent(), Map.of("motivo", "CODIGO_INVALIDO"));
      throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_INVALIDO", "Código do autenticador inválido ou já utilizado.");
    }

    // A baixa atômica impede que duas requisições concorrentes usem o mesmo desafio.
    if (!repository.consumirDesafio(tokenHash)) {
      throw desafioInvalido();
    }

    if ("MFA_CONFIGURACAO".equals(desafio.tipo())) {
      repository.ativarMfa(usuario.id(), segredoCriptografado, passo);
      auditoria.registrar(usuario.id(), usuario.id(), "MFA_ATIVAR", "SUCESSO", info.ip(), info.userAgent(), Map.of());
    } else if (!repository.atualizarPassoMfa(usuario.id(), passo)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_INVALIDO", "Código do autenticador inválido ou já utilizado.");
    }

    UsuarioRow atualizado = repository.buscarUsuarioPorId(usuario.id()).orElseThrow(this::desafioInvalido);
    return autenticar(atualizado, true, info);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult trocarSenha(
    UsuarioPrincipal principal,
    AuthDtos.TrocaSenhaRequest request,
    RequestInfo info
  ) {
    UsuarioRow usuario = repository.buscarUsuarioPorId(principal.id()).orElseThrow(credencialInvalidaSupplier());
    if (!passwordEncoder.matches(request.senhaAtual(), usuario.senhaHash())) {
      auditoria.registrar(usuario.id(), usuario.id(), "SENHA_ALTERAR", "FALHA", info.ip(), info.userAgent(), Map.of("motivo", "SENHA_ATUAL_INVALIDA"));
      throw new ApiException(HttpStatus.BAD_REQUEST, "SENHA_ATUAL_INVALIDA", "A senha atual não confere.");
    }
    if (passwordEncoder.matches(request.novaSenha(), usuario.senhaHash())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "SENHA_REPETIDA", "A nova senha deve ser diferente da senha atual.");
    }
    politicaSenha.validar(request.novaSenha(), usuario.login(), usuario.email());
    repository.alterarSenha(usuario.id(), passwordEncoder.encode(request.novaSenha()));
    sessaoService.revogarTodas(usuario.id(), "SENHA_ALTERADA");
    auditoria.registrar(usuario.id(), usuario.id(), "SENHA_ALTERAR", "SUCESSO", info.ip(), info.userAgent(), Map.of());
    UsuarioRow atualizado = repository.buscarUsuarioPorId(usuario.id()).orElseThrow();
    return autenticar(atualizado, "ADMINISTRADOR".equals(usuario.perfil()), info);
  }

  @Transactional
  public void logout(UsuarioPrincipal principal, jakarta.servlet.http.HttpServletRequest request, RequestInfo info) {
    sessaoService.revogarAtual(request, "LOGOUT");
    auditoria.registrar(principal.id(), principal.id(), "LOGOUT", "SUCESSO", info.ip(), info.userAgent(), Map.of());
  }

  public AuthDtos.UsuarioResponse usuarioResponse(UsuarioPrincipal principal) {
    return new AuthDtos.UsuarioResponse(
      principal.id(), principal.nome(), principal.login(), principal.email(),
      principal.perfil(), principal.deveTrocarSenha(), principal.permissoes()
    );
  }

  private AuthResult criarDesafioMfa(UsuarioRow usuario, RequestInfo info) {
    String challengeToken = novoToken();
    String tipo;
    String segredo = null;
    String segredoCriptografado = null;
    if (usuario.mfaAtivado() && usuario.mfaSegredoCriptografado() != null) {
      tipo = "MFA_VALIDACAO";
    } else {
      tipo = "MFA_CONFIGURACAO";
      segredo = totpService.gerarSegredo();
      segredoCriptografado = criptografiaMfa.criptografar(segredo);
    }
    repository.criarDesafio(
      usuario.id(),
      SessaoService.hash(challengeToken),
      tipo,
      segredoCriptografado,
      LocalDateTime.now().plusMinutes(challengeMinutes),
      AuditoriaService.limitar(info.ip(), 45),
      AuditoriaService.limitar(info.userAgent(), 500)
    );
    auditoria.registrar(usuario.id(), usuario.id(), "MFA_DESAFIO", "SUCESSO", info.ip(), info.userAgent(), Map.of("tipo", tipo));
    AuthDtos.AuthFlowResponse response = new AuthDtos.AuthFlowResponse(
      tipo,
      challengeToken,
      segredo,
      segredo == null ? null : totpService.gerarUri(usuario.login(), segredo),
      null
    );
    return new AuthResult(response, null);
  }

  private AuthResult autenticar(UsuarioRow usuario, boolean mfaValidada, RequestInfo info) {
    repository.registrarLoginComSucesso(usuario.id());
    String token = sessaoService.criar(usuario.id(), mfaValidada, info.ip(), info.userAgent());
    var permissoes = repository.buscarPermissoes(usuario.id());
    AuthDtos.UsuarioResponse responseUsuario = new AuthDtos.UsuarioResponse(
      usuario.id(), usuario.nome(), usuario.login(), usuario.email(), usuario.perfil(),
      usuario.deveTrocarSenha(), permissoes
    );
    auditoria.registrar(usuario.id(), usuario.id(), "LOGIN", "SUCESSO", info.ip(), info.userAgent(), Map.of("mfa", mfaValidada));
    return new AuthResult(
      new AuthDtos.AuthFlowResponse("AUTENTICADO", null, null, null, responseUsuario),
      token
    );
  }

  private void validarUsuarioDuranteDesafio(UsuarioRow usuario) {
    LocalDateTime agora = LocalDateTime.now();
    boolean contaIndisponivel =
      !usuario.perfilAtivo() ||
      "INATIVO".equals(usuario.status()) ||
      "PENDENTE_ATIVACAO".equals(usuario.status()) ||
      (usuario.bloqueadoAte() != null && usuario.bloqueadoAte().isAfter(agora));
    boolean senhaTemporariaExpirada =
      usuario.deveTrocarSenha() &&
      usuario.senhaTemporariaExpiraEm() != null &&
      !usuario.senhaTemporariaExpiraEm().isAfter(agora);
    if (contaIndisponivel || senhaTemporariaExpirada) {
      throw desafioInvalido();
    }
  }

  private String novoToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String normalizarLogin(String login) {
    return login == null ? "" : login.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private ApiException credencialInvalida() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIAL_INVALIDA", "Login ou senha inválidos.");
  }

  private java.util.function.Supplier<ApiException> credencialInvalidaSupplier() {
    return this::credencialInvalida;
  }

  private ApiException desafioInvalido() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "DESAFIO_INVALIDO", "O desafio de autenticação expirou. Faça login novamente.");
  }

  public record AuthResult(AuthDtos.AuthFlowResponse response, String sessionToken) {}

  public record RequestInfo(String ip, String userAgent) {}
}
