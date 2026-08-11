package com.unimedlorena.tools.auth;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Acesso parametrizado às tabelas de identidade; nenhuma credencial é registrada. */
@Repository
public class AuthRepository {

  private final JdbcTemplate jdbc;

  public AuthRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long quantidadeUsuarios() {
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Long.class);
    return total == null ? 0 : total;
  }

  public Optional<UsuarioRow> buscarUsuarioPorLogin(String login) {
    return buscarUsuario(
      """
      SELECT u.*, p.codigo perfil_codigo, p.ativo perfil_ativo
      FROM usuario u
      INNER JOIN perfil_acesso p ON p.id = u.perfil_id
      WHERE u.login = ?
      """,
      login
    );
  }

  public Optional<UsuarioRow> buscarUsuarioPorId(long id) {
    return buscarUsuario(
      """
      SELECT u.*, p.codigo perfil_codigo, p.ativo perfil_ativo
      FROM usuario u
      INNER JOIN perfil_acesso p ON p.id = u.perfil_id
      WHERE u.id = ?
      """,
      id
    );
  }

  private Optional<UsuarioRow> buscarUsuario(String sql, Object parametro) {
    List<UsuarioRow> itens = jdbc.query(
      sql,
      (rs, rowNum) -> new UsuarioRow(
        rs.getLong("id"),
        rs.getString("nome"),
        rs.getString("login"),
        rs.getString("email"),
        rs.getString("senha_hash"),
        rs.getString("perfil_codigo"),
        rs.getBoolean("perfil_ativo"),
        rs.getString("status"),
        rs.getBoolean("deve_trocar_senha"),
        localDateTime(rs.getTimestamp("senha_temporaria_expira_em")),
        rs.getInt("tentativas_login"),
        localDateTime(rs.getTimestamp("bloqueado_ate")),
        rs.getString("mfa_segredo_criptografado"),
        rs.getBoolean("mfa_ativado"),
        nullableLong(rs.getObject("ultimo_passo_mfa"))
      ),
      parametro
    );
    return itens.stream().findFirst();
  }

  public Set<String> buscarPermissoes(long usuarioId) {
    return new HashSet<>(
      jdbc.queryForList(
        """
        SELECT pe.codigo
        FROM usuario u
        INNER JOIN perfil_permissao pp ON pp.perfil_id = u.perfil_id
        INNER JOIN permissao pe ON pe.id = pp.permissao_id
        WHERE u.id = ? AND pe.ativo = TRUE
        UNION
        SELECT pe.codigo
        FROM usuario_permissao up
        INNER JOIN permissao pe ON pe.id = up.permissao_id
        WHERE up.usuario_id = ? AND pe.ativo = TRUE
        """,
        String.class,
        usuarioId,
        usuarioId
      )
    );
  }

  public List<UsuarioRow> listarUsuarios() {
    return jdbc.query(
      """
      SELECT u.*, p.codigo perfil_codigo, p.ativo perfil_ativo
      FROM usuario u
      INNER JOIN perfil_acesso p ON p.id = u.perfil_id
      WHERE u.status <> 'INATIVO'
      ORDER BY u.nome, u.login
      """,
      (rs, rowNum) -> new UsuarioRow(
        rs.getLong("id"),
        rs.getString("nome"),
        rs.getString("login"),
        rs.getString("email"),
        rs.getString("senha_hash"),
        rs.getString("perfil_codigo"),
        rs.getBoolean("perfil_ativo"),
        rs.getString("status"),
        rs.getBoolean("deve_trocar_senha"),
        localDateTime(rs.getTimestamp("senha_temporaria_expira_em")),
        rs.getInt("tentativas_login"),
        localDateTime(rs.getTimestamp("bloqueado_ate")),
        rs.getString("mfa_segredo_criptografado"),
        rs.getBoolean("mfa_ativado"),
        nullableLong(rs.getObject("ultimo_passo_mfa"))
      )
    );
  }

  public List<PermissaoRow> listarPermissoesOperacionais() {
    return jdbc.query(
      """
      SELECT codigo, modulo, descricao
      FROM permissao
      WHERE ativo = TRUE
        AND codigo IN (
          'XML_ACESSAR', 'BI_ACESSAR', 'RELATORIOS_ACESSAR', 'ANS_ACESSAR'
        )
      ORDER BY modulo, codigo
      """,
      (rs, rowNum) -> new PermissaoRow(
        rs.getString("codigo"),
        rs.getString("modulo"),
        rs.getString("descricao")
      )
    );
  }

  public Set<String> buscarPermissoesOperacionaisAtivas(Set<String> codigos) {
    if (codigos.isEmpty()) return Set.of();
    String marcadores = String.join(",", java.util.Collections.nCopies(codigos.size(), "?"));
    return new HashSet<>(jdbc.queryForList(
      "SELECT codigo FROM permissao WHERE ativo = TRUE AND codigo IN (" + marcadores + ") " +
        "AND codigo IN ('XML_ACESSAR','BI_ACESSAR','RELATORIOS_ACESSAR','ANS_ACESSAR')",
      String.class,
      codigos.toArray()
    ));
  }

  public void substituirPermissoesUsuario(long usuarioId, Set<String> codigos, long concedidaPor) {
    jdbc.update("DELETE FROM usuario_permissao WHERE usuario_id = ?", usuarioId);
    if (codigos.isEmpty()) return;
    List<Object[]> lotes = codigos.stream()
      .map(codigo -> new Object[] {
        usuarioId,
        jdbc.queryForObject(
          "SELECT id FROM permissao WHERE codigo = ? AND ativo = TRUE",
          Long.class,
          codigo
        ),
        concedidaPor
      })
      .toList();
    jdbc.batchUpdate(
      "INSERT INTO usuario_permissao (usuario_id, permissao_id, concedida_por) VALUES (?, ?, ?)",
      lotes
    );
  }

  public boolean existeLoginOuEmail(String login, String email) {
    Integer total = jdbc.queryForObject(
      "SELECT COUNT(*) FROM usuario WHERE login = ? OR (? IS NOT NULL AND email = ?)",
      Integer.class,
      login,
      email,
      email
    );
    return total != null && total > 0;
  }

  public boolean existeEmailOutroUsuario(String email, long usuarioId) {
    if (email == null) return false;
    Integer total = jdbc.queryForObject(
      "SELECT COUNT(*) FROM usuario WHERE email = ? AND id <> ?",
      Integer.class,
      email,
      usuarioId
    );
    return total != null && total > 0;
  }

  public long buscarPerfilId(String codigo) {
    List<Long> ids = jdbc.queryForList(
      "SELECT id FROM perfil_acesso WHERE codigo = ? AND ativo = TRUE",
      Long.class,
      codigo
    );
    if (ids.isEmpty()) throw new IllegalStateException("Perfil de acesso não configurado: " + codigo);
    return ids.getFirst();
  }

  public long criarUsuario(
    String nome,
    String login,
    String email,
    String senhaHash,
    String perfilCodigo,
    long criadoPor,
    LocalDateTime senhaTemporariaExpiraEm
  ) {
    long perfilId = buscarPerfilId(perfilCodigo);
    KeyHolder chave = new GeneratedKeyHolder();
    jdbc.update(
      connection -> {
        PreparedStatement ps = connection.prepareStatement(
          """
          INSERT INTO usuario (
              nome, login, email, senha_hash, perfil_id, status,
              deve_trocar_senha, senha_temporaria_expira_em, criado_por
          ) VALUES (?, ?, ?, ?, ?, 'ATIVO', TRUE, ?, ?)
          """,
          Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, nome);
        ps.setString(2, login);
        ps.setString(3, email);
        ps.setString(4, senhaHash);
        ps.setLong(5, perfilId);
        ps.setTimestamp(6, Timestamp.valueOf(senhaTemporariaExpiraEm));
        if (criadoPor > 0) ps.setLong(7, criadoPor); else ps.setNull(7, java.sql.Types.BIGINT);
        return ps;
      },
      chave
    );
    Number id = chave.getKey();
    if (id == null) throw new IllegalStateException("O banco não retornou o identificador do usuário.");
    return id.longValue();
  }

  public void atualizarDadosUsuario(
    long usuarioId,
    String nome,
    String email,
    String perfilCodigo,
    long atualizadoPor
  ) {
    jdbc.update(
      "UPDATE usuario SET nome = ?, email = ?, perfil_id = ?, atualizado_por = ? WHERE id = ?",
      nome,
      email,
      buscarPerfilId(perfilCodigo),
      atualizadoPor,
      usuarioId
    );
  }

  public void removerPermissoesUsuario(long usuarioId) {
    jdbc.update("DELETE FROM usuario_permissao WHERE usuario_id = ?", usuarioId);
  }

  public void desativarUsuario(long usuarioId, long desativadoPor) {
    jdbc.update(
      """
      UPDATE usuario
      SET status = 'INATIVO', desativado_por = ?, desativado_em = CURRENT_TIMESTAMP(6),
          atualizado_por = ?, tentativas_login = 0, bloqueado_ate = NULL
      WHERE id = ?
      """,
      desativadoPor,
      desativadoPor,
      usuarioId
    );
  }

  public List<Long> bloquearAdministradoresAtivos() {
    return jdbc.queryForList(
      """
      SELECT u.id
      FROM usuario u
      INNER JOIN perfil_acesso p ON p.id = u.perfil_id
      WHERE p.codigo = 'ADMINISTRADOR' AND p.ativo = TRUE AND u.status = 'ATIVO'
      ORDER BY u.id
      FOR UPDATE
      """,
      Long.class
    );
  }

  public void registrarFalhaLogin(long usuarioId, int tentativas, LocalDateTime bloqueadoAte) {
    jdbc.update(
      "UPDATE usuario SET tentativas_login = ?, bloqueado_ate = ?, status = CASE WHEN ? IS NULL THEN status ELSE 'BLOQUEADO' END WHERE id = ?",
      tentativas,
      bloqueadoAte,
      bloqueadoAte,
      usuarioId
    );
  }

  public void registrarLoginComSucesso(long usuarioId) {
    jdbc.update(
      """
      UPDATE usuario
      SET tentativas_login = 0, bloqueado_ate = NULL, ultimo_login_em = CURRENT_TIMESTAMP(6),
          status = CASE WHEN status = 'BLOQUEADO' THEN 'ATIVO' ELSE status END
      WHERE id = ?
      """,
      usuarioId
    );
  }

  public void alterarSenha(long usuarioId, String senhaHash) {
    jdbc.update(
      """
      UPDATE usuario
      SET senha_hash = ?, deve_trocar_senha = FALSE, senha_temporaria_expira_em = NULL,
          senha_alterada_em = CURRENT_TIMESTAMP(6), atualizado_por = ?
      WHERE id = ?
      """,
      senhaHash,
      usuarioId,
      usuarioId
    );
  }

  public void redefinirSenha(long usuarioId, String senhaHash, long atualizadoPor, LocalDateTime expiraEm) {
    jdbc.update(
      """
      UPDATE usuario
      SET senha_hash = ?, deve_trocar_senha = TRUE, senha_temporaria_expira_em = ?,
          senha_alterada_em = CURRENT_TIMESTAMP(6), atualizado_por = ?,
          tentativas_login = 0, bloqueado_ate = NULL,
          status = CASE WHEN status = 'BLOQUEADO' THEN 'ATIVO' ELSE status END
      WHERE id = ?
      """,
      senhaHash,
      expiraEm,
      atualizadoPor,
      usuarioId
    );
  }

  public void ativarMfa(long usuarioId, String segredoCriptografado, long passo) {
    jdbc.update(
      """
      UPDATE usuario
      SET mfa_segredo_criptografado = ?, mfa_ativado = TRUE,
          mfa_ativado_em = CURRENT_TIMESTAMP(6), ultimo_passo_mfa = ?
      WHERE id = ?
      """,
      segredoCriptografado,
      passo,
      usuarioId
    );
  }

  public boolean atualizarPassoMfa(long usuarioId, long passo) {
    return jdbc.update(
      "UPDATE usuario SET ultimo_passo_mfa = ? WHERE id = ? AND (ultimo_passo_mfa IS NULL OR ultimo_passo_mfa < ?)",
      passo,
      usuarioId,
      passo
    ) == 1;
  }

  public void criarSessao(
    long usuarioId,
    String tokenHash,
    LocalDateTime expiraEm,
    LocalDateTime mfaValidadaEm,
    String ip,
    String userAgent
  ) {
    jdbc.update(
      """
      INSERT INTO sessao_usuario (
          usuario_id, token_hash, expira_em, mfa_validada_em, endereco_ip, user_agent
      ) VALUES (?, ?, ?, ?, ?, ?)
      """,
      usuarioId,
      tokenHash,
      expiraEm,
      mfaValidadaEm,
      ip,
      userAgent
    );
  }

  public Optional<SessaoRow> buscarSessao(String tokenHash) {
    List<SessaoRow> itens = jdbc.query(
      """
      SELECT usuario_id, ultima_atividade_em, expira_em, mfa_validada_em, revogada_em
      FROM sessao_usuario WHERE token_hash = ?
      """,
      (rs, rowNum) -> new SessaoRow(
        rs.getLong("usuario_id"),
        localDateTime(rs.getTimestamp("ultima_atividade_em")),
        localDateTime(rs.getTimestamp("expira_em")),
        localDateTime(rs.getTimestamp("mfa_validada_em")),
        localDateTime(rs.getTimestamp("revogada_em"))
      ),
      tokenHash
    );
    return itens.stream().findFirst();
  }

  public void atualizarAtividade(String tokenHash) {
    jdbc.update(
      "UPDATE sessao_usuario SET ultima_atividade_em = CURRENT_TIMESTAMP(6) WHERE token_hash = ? AND revogada_em IS NULL",
      tokenHash
    );
  }

  public void revogarSessao(String tokenHash, String motivo) {
    jdbc.update(
      "UPDATE sessao_usuario SET revogada_em = CURRENT_TIMESTAMP(6), motivo_revogacao = ? WHERE token_hash = ? AND revogada_em IS NULL",
      motivo,
      tokenHash
    );
  }

  public void revogarSessoesDoUsuario(long usuarioId, String motivo) {
    jdbc.update(
      "UPDATE sessao_usuario SET revogada_em = CURRENT_TIMESTAMP(6), motivo_revogacao = ? WHERE usuario_id = ? AND revogada_em IS NULL",
      motivo,
      usuarioId
    );
  }

  public void criarDesafio(
    long usuarioId,
    String tokenHash,
    String tipo,
    String segredoCriptografado,
    LocalDateTime expiraEm,
    String ip,
    String userAgent
  ) {
    jdbc.update(
      "UPDATE desafio_autenticacao SET consumido_em = CURRENT_TIMESTAMP(6) WHERE usuario_id = ? AND consumido_em IS NULL",
      usuarioId
    );
    jdbc.update(
      """
      INSERT INTO desafio_autenticacao (
          usuario_id, token_hash, tipo, mfa_segredo_criptografado,
          expira_em, endereco_ip, user_agent
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
      """,
      usuarioId,
      tokenHash,
      tipo,
      segredoCriptografado,
      expiraEm,
      ip,
      userAgent
    );
  }

  public Optional<DesafioRow> buscarDesafio(String tokenHash) {
    List<DesafioRow> itens = jdbc.query(
      """
      SELECT usuario_id, tipo, mfa_segredo_criptografado, tentativas, expira_em, consumido_em
      FROM desafio_autenticacao WHERE token_hash = ?
      """,
      (rs, rowNum) -> new DesafioRow(
        rs.getLong("usuario_id"),
        rs.getString("tipo"),
        rs.getString("mfa_segredo_criptografado"),
        rs.getInt("tentativas"),
        localDateTime(rs.getTimestamp("expira_em")),
        localDateTime(rs.getTimestamp("consumido_em"))
      ),
      tokenHash
    );
    return itens.stream().findFirst();
  }

  public void registrarFalhaDesafio(String tokenHash, boolean consumir) {
    jdbc.update(
      """
      UPDATE desafio_autenticacao
      SET tentativas = tentativas + 1,
          consumido_em = CASE WHEN ? THEN CURRENT_TIMESTAMP(6) ELSE consumido_em END
      WHERE token_hash = ? AND consumido_em IS NULL
      """,
      consumir,
      tokenHash
    );
  }

  public boolean consumirDesafio(String tokenHash) {
    return jdbc.update(
      "UPDATE desafio_autenticacao SET consumido_em = CURRENT_TIMESTAMP(6) WHERE token_hash = ? AND consumido_em IS NULL",
      tokenHash
    ) == 1;
  }

  public void auditar(
    Long executorId,
    Long alvoId,
    String evento,
    String resultado,
    String ip,
    String userAgent,
    String detalhesJson
  ) {
    jdbc.update(
      """
      INSERT INTO auditoria_acesso (
          usuario_executor_id, usuario_alvo_id, evento, resultado,
          endereco_ip, user_agent, detalhes
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
      """,
      executorId,
      alvoId,
      evento,
      resultado,
      ip,
      userAgent,
      detalhesJson
    );
  }

  private static LocalDateTime localDateTime(Timestamp value) {
    return value == null ? null : value.toLocalDateTime();
  }

  private static Long nullableLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  public record UsuarioRow(
    long id,
    String nome,
    String login,
    String email,
    String senhaHash,
    String perfil,
    boolean perfilAtivo,
    String status,
    boolean deveTrocarSenha,
    LocalDateTime senhaTemporariaExpiraEm,
    int tentativasLogin,
    LocalDateTime bloqueadoAte,
    String mfaSegredoCriptografado,
    boolean mfaAtivado,
    Long ultimoPassoMfa
  ) {}

  public record SessaoRow(
    long usuarioId,
    LocalDateTime ultimaAtividadeEm,
    LocalDateTime expiraEm,
    LocalDateTime mfaValidadaEm,
    LocalDateTime revogadaEm
  ) {}

  public record DesafioRow(
    long usuarioId,
    String tipo,
    String mfaSegredoCriptografado,
    int tentativas,
    LocalDateTime expiraEm,
    LocalDateTime consumidoEm
  ) {}

  public record PermissaoRow(String codigo, String modulo, String descricao) {}
}
