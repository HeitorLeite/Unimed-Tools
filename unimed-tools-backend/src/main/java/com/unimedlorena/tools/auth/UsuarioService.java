package com.unimedlorena.tools.auth;

import com.unimedlorena.tools.auth.AuthRepository.PermissaoRow;
import com.unimedlorena.tools.auth.AuthRepository.UsuarioRow;
import com.unimedlorena.tools.dto.UsuarioDtos;
import com.unimedlorena.tools.exception.ApiException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gerencia contas e exige step-up TOTP nas alterações administrativas sensíveis. */
@Service
public class UsuarioService {

  private final AuthRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final PoliticaSenhaService politicaSenha;
  private final CriptografiaMfaService criptografiaMfa;
  private final TotpService totpService;
  private final AuditoriaService auditoria;

  public UsuarioService(
    AuthRepository repository,
    PasswordEncoder passwordEncoder,
    PoliticaSenhaService politicaSenha,
    CriptografiaMfaService criptografiaMfa,
    TotpService totpService,
    AuditoriaService auditoria
  ) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.politicaSenha = politicaSenha;
    this.criptografiaMfa = criptografiaMfa;
    this.totpService = totpService;
    this.auditoria = auditoria;
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UsuarioDtos.CriacaoResponse criar(
    UsuarioPrincipal administrador,
    UsuarioDtos.CriacaoRequest request,
    AuthService.RequestInfo info
  ) {
    UsuarioRow admin = validarAdministrador(administrador);
    String login = request.login().trim().toLowerCase(Locale.ROOT);
    String email = request.email() == null || request.email().isBlank()
      ? null
      : request.email().trim().toLowerCase(Locale.ROOT);
    String nome = request.nome().trim().replaceAll("\\s+", " ");
    if (repository.existeLoginOuEmail(login, email)) {
      throw new ApiException(HttpStatus.CONFLICT, "USUARIO_DUPLICADO", "Já existe uma conta com esse login ou e-mail.");
    }
    politicaSenha.validar(request.senhaTemporaria(), login, email);
    validarStepUp(admin, request.codigoMfaAdministrador(), "USUARIO_CRIAR", null, info);

    long id = repository.criarUsuario(
      nome,
      login,
      email,
      passwordEncoder.encode(request.senhaTemporaria()),
      request.perfilCodigo(),
      admin.id(),
      LocalDateTime.now().plusHours(24)
    );
    auditoria.registrar(
      admin.id(),
      id,
      "USUARIO_CRIAR",
      "SUCESSO",
      info.ip(),
      info.userAgent(),
      Map.of("perfil", request.perfilCodigo())
    );
    return new UsuarioDtos.CriacaoResponse(id, nome, login, email, request.perfilCodigo(), "ATIVO", true);
  }

  @Transactional(readOnly = true)
  public List<UsuarioDtos.ResumoResponse> listar(UsuarioPrincipal administrador) {
    validarAdministrador(administrador);
    return repository.listarUsuarios().stream()
      .map(usuario -> new UsuarioDtos.ResumoResponse(
        usuario.id(),
        usuario.nome(),
        usuario.login(),
        usuario.email(),
        usuario.perfil(),
        usuario.status(),
        usuario.deveTrocarSenha(),
        Set.copyOf(repository.buscarPermissoes(usuario.id()))
      ))
      .toList();
  }

  @Transactional(readOnly = true)
  public List<UsuarioDtos.PermissaoResponse> listarPermissoes(UsuarioPrincipal administrador) {
    validarAdministrador(administrador);
    return repository.listarPermissoesOperacionais().stream()
      .map(this::toResponse)
      .toList();
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UsuarioDtos.ResumoResponse atualizar(
    UsuarioPrincipal administrador,
    long usuarioId,
    UsuarioDtos.AtualizacaoDadosRequest request,
    AuthService.RequestInfo info
  ) {
    UsuarioRow admin = validarAdministrador(administrador);
    UsuarioRow alvo = buscarAlvo(usuarioId);
    String nome = normalizarNome(request.nome());
    String email = normalizarEmail(request.email());
    String novoPerfil = request.perfilCodigo();
    boolean perfilAlterado = !alvo.perfil().equals(novoPerfil);

    if (nome.length() < 3) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "NOME_INVALIDO",
        "Informe um nome com pelo menos 3 caracteres."
      );
    }
    if (alvo.id() == admin.id() && perfilAlterado) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "PERFIL_PROPRIO",
        "Não é permitido alterar o tipo de acesso da própria conta."
      );
    }
    if (repository.existeEmailOutroUsuario(email, alvo.id())) {
      throw new ApiException(
        HttpStatus.CONFLICT,
        "EMAIL_DUPLICADO",
        "Já existe uma conta com esse e-mail."
      );
    }
    if (perfilAlterado && "ADMINISTRADOR".equals(alvo.perfil())) {
      validarAdministradorRemanescente(alvo);
    }
    validarStepUp(admin, request.codigoMfaAdministrador(), "USUARIO_ATUALIZAR", alvo.id(), info);

    repository.atualizarDadosUsuario(alvo.id(), nome, email, novoPerfil, admin.id());
    if (perfilAlterado) {
      // A conta operacional volta ao estado negado por padrão e a sessão antiga
      // não pode conservar no frontend um tipo de acesso que acabou de mudar.
      repository.removerPermissoesUsuario(alvo.id());
      repository.revogarSessoesDoUsuario(alvo.id(), "PERFIL_ALTERADO_ADMIN");
    }
    auditoria.registrar(
      admin.id(),
      alvo.id(),
      "USUARIO_ATUALIZAR",
      "SUCESSO",
      info.ip(),
      info.userAgent(),
      Map.of(
        "perfil_anterior", alvo.perfil(),
        "perfil_atual", novoPerfil,
        "perfil_alterado", perfilAlterado
      )
    );
    return toResumo(repository.buscarUsuarioPorId(alvo.id()).orElseThrow());
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UsuarioDtos.OperacaoResponse excluir(
    UsuarioPrincipal administrador,
    long usuarioId,
    UsuarioDtos.ExclusaoRequest request,
    AuthService.RequestInfo info
  ) {
    UsuarioRow admin = validarAdministrador(administrador);
    UsuarioRow alvo = buscarAlvo(usuarioId);
    if (alvo.id() == admin.id()) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "EXCLUSAO_PROPRIA",
        "Não é permitido excluir a própria conta."
      );
    }
    if ("ADMINISTRADOR".equals(alvo.perfil())) validarAdministradorRemanescente(alvo);
    validarStepUp(admin, request.codigoMfaAdministrador(), "USUARIO_EXCLUIR", alvo.id(), info);

    repository.removerPermissoesUsuario(alvo.id());
    repository.desativarUsuario(alvo.id(), admin.id());
    repository.revogarSessoesDoUsuario(alvo.id(), "USUARIO_EXCLUIDO_ADMIN");
    auditoria.registrar(
      admin.id(),
      alvo.id(),
      "USUARIO_EXCLUIR",
      "SUCESSO",
      info.ip(),
      info.userAgent(),
      Map.of("perfil", alvo.perfil(), "sessoes_revogadas", true)
    );
    return new UsuarioDtos.OperacaoResponse("Usuário excluído com sucesso.");
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UsuarioDtos.OperacaoResponse atualizarPermissoes(
    UsuarioPrincipal administrador,
    long usuarioId,
    UsuarioDtos.AtualizacaoPermissoesRequest request,
    AuthService.RequestInfo info
  ) {
    UsuarioRow admin = validarAdministrador(administrador);
    UsuarioRow alvo = buscarAlvo(usuarioId);
    if (!"USUARIO".equals(alvo.perfil())) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "PERFIL_NAO_GERENCIAVEL",
        "As permissões individuais são destinadas a usuários operacionais."
      );
    }

    Set<String> solicitadas = Set.copyOf(request.permissoes());
    Set<String> permitidas = repository.buscarPermissoesOperacionaisAtivas(solicitadas);
    if (permitidas.size() != solicitadas.size()) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "PERMISSAO_INVALIDA",
        "Uma ou mais permissões informadas não podem ser concedidas."
      );
    }
    validarStepUp(admin, request.codigoMfaAdministrador(), "USUARIO_PERMISSOES_ALTERAR", alvo.id(), info);

    Set<String> persistidas = new HashSet<>(permitidas);
    if (!persistidas.isEmpty()) persistidas.add("APLICACAO_ACESSAR");
    repository.substituirPermissoesUsuario(alvo.id(), persistidas, admin.id());
    auditoria.registrar(
      admin.id(),
      alvo.id(),
      "USUARIO_PERMISSOES_ALTERAR",
      "SUCESSO",
      info.ip(),
      info.userAgent(),
      Map.of("permissoes", permitidas.stream().sorted().toList())
    );
    return new UsuarioDtos.OperacaoResponse("Permissões atualizadas com sucesso.");
  }

  @Transactional(noRollbackFor = ApiException.class)
  public UsuarioDtos.OperacaoResponse redefinirSenha(
    UsuarioPrincipal administrador,
    long usuarioId,
    UsuarioDtos.RedefinicaoSenhaRequest request,
    AuthService.RequestInfo info
  ) {
    UsuarioRow admin = validarAdministrador(administrador);
    UsuarioRow alvo = buscarAlvo(usuarioId);
    if (alvo.id() == admin.id()) {
      throw new ApiException(
        HttpStatus.BAD_REQUEST,
        "USE_TROCA_DE_SENHA",
        "Para sua própria conta, use a opção de alteração de senha."
      );
    }
    politicaSenha.validar(request.senhaTemporaria(), alvo.login(), alvo.email());
    validarStepUp(admin, request.codigoMfaAdministrador(), "USUARIO_SENHA_REDEFINIR", alvo.id(), info);

    repository.redefinirSenha(
      alvo.id(),
      passwordEncoder.encode(request.senhaTemporaria()),
      admin.id(),
      LocalDateTime.now().plusHours(24)
    );
    repository.revogarSessoesDoUsuario(alvo.id(), "SENHA_REDEFINIDA_ADMIN");
    auditoria.registrar(
      admin.id(),
      alvo.id(),
      "USUARIO_SENHA_REDEFINIR",
      "SUCESSO",
      info.ip(),
      info.userAgent(),
      Map.of("sessao_revogada", true)
    );
    return new UsuarioDtos.OperacaoResponse(
      "Senha temporária definida. O usuário deverá trocá-la no próximo acesso."
    );
  }

  private UsuarioRow validarAdministrador(UsuarioPrincipal administrador) {
    if (administrador == null || !"ADMINISTRADOR".equals(administrador.perfil())) {
      throw new ApiException(
        HttpStatus.FORBIDDEN,
        "ACESSO_NEGADO",
        "Somente administradores podem gerenciar usuários."
      );
    }
    UsuarioRow admin = repository.buscarUsuarioPorId(administrador.id()).orElseThrow(() ->
      new ApiException(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Faça login novamente para continuar.")
    );
    if (!"ADMINISTRADOR".equals(admin.perfil()) || !admin.perfilAtivo() || !"ATIVO".equals(admin.status())) {
      throw new ApiException(
        HttpStatus.FORBIDDEN,
        "ACESSO_NEGADO",
        "A conta não possui acesso administrativo ativo."
      );
    }
    return admin;
  }

  private UsuarioRow buscarAlvo(long usuarioId) {
    UsuarioRow alvo = repository.buscarUsuarioPorId(usuarioId).orElseThrow(() ->
      new ApiException(HttpStatus.NOT_FOUND, "USUARIO_NAO_ENCONTRADO", "Usuário não encontrado.")
    );
    if ("INATIVO".equals(alvo.status())) {
      throw new ApiException(HttpStatus.NOT_FOUND, "USUARIO_NAO_ENCONTRADO", "Usuário não encontrado.");
    }
    return alvo;
  }

  private void validarAdministradorRemanescente(UsuarioRow alvo) {
    List<Long> administradoresAtivos = repository.bloquearAdministradoresAtivos();
    if (administradoresAtivos.contains(alvo.id()) && administradoresAtivos.size() <= 1) {
      throw new ApiException(
        HttpStatus.CONFLICT,
        "ULTIMO_ADMINISTRADOR",
        "A aplicação precisa manter pelo menos um administrador ativo."
      );
    }
  }

  private void validarStepUp(
    UsuarioRow admin,
    String codigo,
    String evento,
    Long alvoId,
    AuthService.RequestInfo info
  ) {
    if (!admin.mfaAtivado() || admin.mfaSegredoCriptografado() == null) {
      throw new ApiException(HttpStatus.FORBIDDEN, "MFA_OBRIGATORIO", "O administrador precisa ativar o MFA.");
    }
    String segredo = criptografiaMfa.descriptografar(admin.mfaSegredoCriptografado());
    Long passo = totpService.validar(segredo, codigo, admin.ultimoPassoMfa());
    if (passo == null || !repository.atualizarPassoMfa(admin.id(), passo)) {
      auditoria.registrar(
        admin.id(),
        alvoId,
        evento,
        "FALHA",
        info.ip(),
        info.userAgent(),
        Map.of("motivo", "STEP_UP_INVALIDO")
      );
      throw new ApiException(
        HttpStatus.UNAUTHORIZED,
        "MFA_INVALIDO",
        "Use um código novo e válido do autenticador."
      );
    }
  }

  private UsuarioDtos.PermissaoResponse toResponse(PermissaoRow permissao) {
    return new UsuarioDtos.PermissaoResponse(permissao.codigo(), permissao.modulo(), permissao.descricao());
  }

  private UsuarioDtos.ResumoResponse toResumo(UsuarioRow usuario) {
    return new UsuarioDtos.ResumoResponse(
      usuario.id(),
      usuario.nome(),
      usuario.login(),
      usuario.email(),
      usuario.perfil(),
      usuario.status(),
      usuario.deveTrocarSenha(),
      Set.copyOf(repository.buscarPermissoes(usuario.id()))
    );
  }

  private String normalizarNome(String nome) {
    return nome.trim().replaceAll("\\s+", " ");
  }

  private String normalizarEmail(String email) {
    return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
