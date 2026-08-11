package com.unimedlorena.tools.auth;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cria a primeira conta somente quando o banco está vazio e o ambiente está completo. */
@Component
public class BootstrapAdminService implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(BootstrapAdminService.class);

  private final AuthRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final PoliticaSenhaService politicaSenha;
  private final AuditoriaService auditoria;
  private final String nome;
  private final String login;
  private final String email;
  private final String senha;

  public BootstrapAdminService(
    AuthRepository repository,
    PasswordEncoder passwordEncoder,
    PoliticaSenhaService politicaSenha,
    AuditoriaService auditoria,
    @Value("${auth.bootstrap.admin.name:}") String nome,
    @Value("${auth.bootstrap.admin.login:}") String login,
    @Value("${auth.bootstrap.admin.email:}") String email,
    @Value("${auth.bootstrap.admin.password:}") String senha
  ) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.politicaSenha = politicaSenha;
    this.auditoria = auditoria;
    this.nome = nome;
    this.login = login;
    this.email = email;
    this.senha = senha;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (repository.quantidadeUsuarios() > 0) return;
    if (nome.isBlank() || login.isBlank() || senha.isBlank()) {
      throw new IllegalStateException(
        "A tabela usuario está vazia. Defina AUTH_BOOTSTRAP_ADMIN_NAME, " +
        "AUTH_BOOTSTRAP_ADMIN_LOGIN e AUTH_BOOTSTRAP_ADMIN_PASSWORD para criar o primeiro administrador."
      );
    }
    String loginNormalizado = login.trim().toLowerCase(Locale.ROOT);
    if (!loginNormalizado.matches("[a-z0-9._-]{3,80}")) {
      throw new IllegalStateException("AUTH_BOOTSTRAP_ADMIN_LOGIN possui formato inválido.");
    }
    String emailNormalizado = email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    politicaSenha.validar(senha, loginNormalizado, emailNormalizado);
    long id = repository.criarUsuario(
      nome.trim().replaceAll("\\s+", " "),
      loginNormalizado,
      emailNormalizado,
      passwordEncoder.encode(senha),
      "ADMINISTRADOR",
      0,
      LocalDateTime.now().plusHours(24)
    );
    auditoria.registrar(null, id, "ADMIN_BOOTSTRAP", "SUCESSO", null, null, Map.of());
    // A senha e o login não são incluídos no log.
    logger.info("Conta administradora inicial criada; remova a senha de bootstrap do ambiente.");
  }
}
