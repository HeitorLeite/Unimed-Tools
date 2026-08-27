package com.unimedlorena.tools.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistência JDBC parametrizada dos agendamentos e de suas reservas temporárias. */
@Repository
public class RelatorioAgendamentoRepository {

  private static final String SELECT_BASE = """
    SELECT a.*, u.nome usuario_nome
    FROM relatorio_agendamento a
    INNER JOIN usuario u ON u.id = a.usuario_id
    """;

  private final JdbcTemplate jdbc;

  public RelatorioAgendamentoRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void criar(AgendamentoRow row) {
    jdbc.update(
      """
      INSERT INTO relatorio_agendamento (
        id, usuario_id, tipo_relatorio, titulo_relatorio, api_nome,
        configuracao_criptografada, formato, nome_arquivo,
        diretorio_referencia, diretorio_nome, incluir_cabecalho,
        agendado_para_epoch_ms, recorrencia, dias_semana, dia_mes, fuso_horario,
        status, tentativas, execucoes_concluidas,
        criado_em_epoch_ms, atualizado_em_epoch_ms
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDENTE', 0, 0, ?, ?)
      """,
      row.id(),
      row.usuarioId(),
      row.tipoRelatorio(),
      row.tituloRelatorio(),
      row.apiNome(),
      row.configuracaoCriptografada(),
      row.formato(),
      row.nomeArquivo(),
      row.diretorioReferencia(),
      row.diretorioNome(),
      row.incluirCabecalho(),
      row.agendadoParaEpochMs(),
      row.recorrencia(),
      row.diasSemana(),
      row.diaMes(),
      row.fusoHorario(),
      row.criadoEmEpochMs(),
      row.atualizadoEmEpochMs()
    );
  }

  public List<AgendamentoRow> listar(long usuarioId, boolean administrador) {
    String filtro = administrador ? "" : " WHERE a.usuario_id = ?";
    String ordem = " ORDER BY a.criado_em_epoch_ms DESC LIMIT 500";
    return administrador
      ? jdbc.query(SELECT_BASE + filtro + ordem, this::mapear)
      : jdbc.query(SELECT_BASE + filtro + ordem, this::mapear, usuarioId);
  }

  public Optional<AgendamentoRow> buscar(
    String id,
    long usuarioId,
    boolean administrador
  ) {
    String filtro = administrador
      ? " WHERE a.id = ?"
      : " WHERE a.id = ? AND a.usuario_id = ?";
    List<AgendamentoRow> itens = administrador
      ? jdbc.query(SELECT_BASE + filtro, this::mapear, id)
      : jdbc.query(SELECT_BASE + filtro, this::mapear, id, usuarioId);
    return itens.stream().findFirst();
  }

  public List<String> listarPendentes(long usuarioId, long agoraEpochMs) {
    return jdbc.queryForList(
      """
      SELECT id
      FROM relatorio_agendamento
      WHERE usuario_id = ? AND status = 'PENDENTE'
        AND agendado_para_epoch_ms <= ?
      ORDER BY agendado_para_epoch_ms
      LIMIT 5
      """,
      String.class,
      usuarioId,
      agoraEpochMs
    );
  }

  public boolean reservar(
    String id,
    long usuarioId,
    long agoraEpochMs,
    long reservadoAteEpochMs
  ) {
    return jdbc.update(
      """
      UPDATE relatorio_agendamento
      SET status = 'EM_EXECUCAO', tentativas = tentativas + 1,
          reservado_ate_epoch_ms = ?, atualizado_em_epoch_ms = ?,
          erro_codigo = NULL, erro_mensagem = NULL, retencao_ate_epoch_ms = NULL
      WHERE id = ? AND usuario_id = ? AND status = 'PENDENTE'
        AND agendado_para_epoch_ms <= ?
      """,
      reservadoAteEpochMs,
      agoraEpochMs,
      id,
      usuarioId,
      agoraEpochMs
    ) == 1;
  }

  public boolean concluirUnico(
    String id,
    long usuarioId,
    long agoraEpochMs,
    long retencaoAteEpochMs
  ) {
    return jdbc.update(
      """
      UPDATE relatorio_agendamento
      SET status = 'CONCLUIDO', reservado_ate_epoch_ms = NULL,
          erro_codigo = NULL, erro_mensagem = NULL,
          execucoes_concluidas = execucoes_concluidas + 1,
          concluido_em_epoch_ms = ?, atualizado_em_epoch_ms = ?,
          retencao_ate_epoch_ms = ?
      WHERE id = ? AND usuario_id = ? AND status = 'EM_EXECUCAO'
      """,
      agoraEpochMs,
      agoraEpochMs,
      retencaoAteEpochMs,
      id,
      usuarioId
    ) == 1;
  }

  public boolean concluirRecorrente(
    String id,
    long usuarioId,
    long proximaExecucaoEpochMs,
    long agoraEpochMs
  ) {
    return jdbc.update(
      """
      UPDATE relatorio_agendamento
      SET status = 'PENDENTE', agendado_para_epoch_ms = ?,
          reservado_ate_epoch_ms = NULL, erro_codigo = NULL, erro_mensagem = NULL,
          execucoes_concluidas = execucoes_concluidas + 1,
          concluido_em_epoch_ms = ?, atualizado_em_epoch_ms = ?,
          retencao_ate_epoch_ms = NULL
      WHERE id = ? AND usuario_id = ? AND status = 'EM_EXECUCAO'
      """,
      proximaExecucaoEpochMs,
      agoraEpochMs,
      agoraEpochMs,
      id,
      usuarioId
    ) == 1;
  }

  public boolean falhar(
    String id,
    long usuarioId,
    String codigo,
    String mensagem,
    long agoraEpochMs,
    long retencaoAteEpochMs
  ) {
    return jdbc.update(
      """
      UPDATE relatorio_agendamento
      SET status = 'FALHA', reservado_ate_epoch_ms = NULL,
          erro_codigo = ?, erro_mensagem = ?, atualizado_em_epoch_ms = ?,
          retencao_ate_epoch_ms = ?
      WHERE id = ? AND usuario_id = ? AND status = 'EM_EXECUCAO'
      """,
      codigo,
      mensagem,
      agoraEpochMs,
      retencaoAteEpochMs,
      id,
      usuarioId
    ) == 1;
  }

  public boolean alterarDestino(
    String id,
    long usuarioId,
    String referencia,
    String nome,
    long agoraEpochMs
  ) {
    return jdbc.update(
      """
      UPDATE relatorio_agendamento
      SET diretorio_referencia = ?, diretorio_nome = ?, status = 'PENDENTE',
          agendado_para_epoch_ms = ?, reservado_ate_epoch_ms = NULL,
          erro_codigo = NULL, erro_mensagem = NULL, atualizado_em_epoch_ms = ?,
          retencao_ate_epoch_ms = NULL
      WHERE id = ? AND usuario_id = ? AND status = 'FALHA'
      """,
      referencia,
      nome,
      agoraEpochMs,
      agoraEpochMs,
      id,
      usuarioId
    ) == 1;
  }

  public boolean cancelar(
    String id,
    long usuarioId,
    boolean administrador,
    long agoraEpochMs,
    long retencaoAteEpochMs
  ) {
    String propriedade = administrador ? "" : " AND usuario_id = ?";
    String sql = """
      UPDATE relatorio_agendamento
      SET status = 'CANCELADO', reservado_ate_epoch_ms = NULL,
          atualizado_em_epoch_ms = ?, retencao_ate_epoch_ms = ?
      WHERE id = ? AND status IN ('PENDENTE', 'FALHA')
      """ + propriedade;
    return administrador
      ? jdbc.update(sql, agoraEpochMs, retencaoAteEpochMs, id) == 1
      : jdbc.update(sql, agoraEpochMs, retencaoAteEpochMs, id, usuarioId) == 1;
  }

  public int liberarReservasExpiradas(long agoraEpochMs) {
    return jdbc.update(
      """
      UPDATE relatorio_agendamento
      SET status = 'PENDENTE', reservado_ate_epoch_ms = NULL,
          atualizado_em_epoch_ms = ?
      WHERE status = 'EM_EXECUCAO' AND reservado_ate_epoch_ms < ?
      """,
      agoraEpochMs,
      agoraEpochMs
    );
  }

  public int excluirRetencaoExpirada(long agoraEpochMs) {
    return jdbc.update(
      """
      DELETE FROM relatorio_agendamento
      WHERE retencao_ate_epoch_ms IS NOT NULL AND retencao_ate_epoch_ms < ?
        AND status IN ('CONCLUIDO', 'FALHA', 'CANCELADO')
      """,
      agoraEpochMs
    );
  }

  private AgendamentoRow mapear(ResultSet rs, int rowNum) throws SQLException {
    return new AgendamentoRow(
      rs.getString("id"),
      rs.getLong("usuario_id"),
      rs.getString("usuario_nome"),
      rs.getString("tipo_relatorio"),
      rs.getString("titulo_relatorio"),
      rs.getString("api_nome"),
      rs.getString("configuracao_criptografada"),
      rs.getString("formato"),
      rs.getString("nome_arquivo"),
      rs.getString("diretorio_referencia"),
      rs.getString("diretorio_nome"),
      rs.getBoolean("incluir_cabecalho"),
      rs.getLong("agendado_para_epoch_ms"),
      rs.getString("recorrencia"),
      rs.getString("dias_semana"),
      nullableInteger(rs, "dia_mes"),
      rs.getString("fuso_horario"),
      rs.getString("status"),
      rs.getInt("tentativas"),
      rs.getInt("execucoes_concluidas"),
      nullableLong(rs, "reservado_ate_epoch_ms"),
      rs.getString("erro_codigo"),
      rs.getString("erro_mensagem"),
      rs.getLong("criado_em_epoch_ms"),
      rs.getLong("atualizado_em_epoch_ms"),
      nullableLong(rs, "concluido_em_epoch_ms"),
      nullableLong(rs, "retencao_ate_epoch_ms")
    );
  }

  private Long nullableLong(ResultSet rs, String coluna) throws SQLException {
    Object valor = rs.getObject(coluna);
    return valor == null ? null : ((Number) valor).longValue();
  }

  private Integer nullableInteger(ResultSet rs, String coluna) throws SQLException {
    Object valor = rs.getObject(coluna);
    return valor == null ? null : ((Number) valor).intValue();
  }

  public record AgendamentoRow(
    String id,
    long usuarioId,
    String usuarioNome,
    String tipoRelatorio,
    String tituloRelatorio,
    String apiNome,
    String configuracaoCriptografada,
    String formato,
    String nomeArquivo,
    String diretorioReferencia,
    String diretorioNome,
    boolean incluirCabecalho,
    long agendadoParaEpochMs,
    String recorrencia,
    String diasSemana,
    Integer diaMes,
    String fusoHorario,
    String status,
    int tentativas,
    int execucoesConcluidas,
    Long reservadoAteEpochMs,
    String erroCodigo,
    String erroMensagem,
    long criadoEmEpochMs,
    long atualizadoEmEpochMs,
    Long concluidoEmEpochMs,
    Long retencaoAteEpochMs
  ) {}
}
