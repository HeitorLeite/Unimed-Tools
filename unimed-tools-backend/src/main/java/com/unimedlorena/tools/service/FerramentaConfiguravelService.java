package com.unimedlorena.tools.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimedlorena.tools.auth.AuditoriaService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.FerramentaDtos;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FerramentaConfiguravelService {

  private static final Set<String> FERRAMENTAS_NATIVAS = Set.of(
    "comercial",
    "assistencial",
    "revisao-contas",
    "unica",
    "hospital",
    "gestao-risco",
    "valorizar-guias-fusex-spa",
    "ti"
  );

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final AuditoriaService auditoria;

  public FerramentaConfiguravelService(
    JdbcTemplate jdbc,
    ObjectMapper objectMapper,
    AuditoriaService auditoria
  ) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.auditoria = auditoria;
  }

  public List<FerramentaDtos.Response> listar() {
    return jdbc.query(
      """
      SELECT id, slug, nome, descricao, api_nome, filtros_json, colunas_preview_json,
             criado_em, atualizado_em
      FROM ferramenta_configuravel
      WHERE ativo = TRUE
      ORDER BY nome
      """,
      (rs, rowNum) -> map(rs)
    );
  }

  public List<FerramentaDtos.NativaResponse> listarNativas() {
    return jdbc.query(
      """
      SELECT ferramenta_id, nome, descricao, ativo, atualizado_em
      FROM ferramenta_nativa_configuracao
      ORDER BY ferramenta_id
      """,
      (rs, rowNum) -> mapNativa(rs)
    );
  }

  @Transactional
  public FerramentaDtos.NativaResponse salvarNativa(
    String id,
    FerramentaDtos.NativaSalvarRequest request,
    UsuarioPrincipal principal
  ) {
    String ferramentaId = validarNativaId(id);
    boolean ativo = request.ativo() == null || request.ativo();
    if ("ti".equals(ferramentaId) && !ativo) {
      throw new IllegalArgumentException(
        "A área TI não pode ser ocultada para evitar bloqueio administrativo."
      );
    }

    String nome = textoOpcional(request.nome(), 120);
    String descricao = textoOpcional(request.descricao(), 500);

    jdbc.update(
      """
      INSERT INTO ferramenta_nativa_configuracao
        (ferramenta_id, nome, descricao, ativo, atualizado_por)
      VALUES (?, ?, ?, ?, ?)
      ON DUPLICATE KEY UPDATE
        nome = VALUES(nome),
        descricao = VALUES(descricao),
        ativo = VALUES(ativo),
        atualizado_por = VALUES(atualizado_por)
      """,
      ferramentaId,
      nome,
      descricao,
      ativo,
      principal.id()
    );
    auditoria.registrar(
      principal.id(),
      null,
      "FERRAMENTA_NATIVA_EDITAR",
      "SUCESSO",
      null,
      null,
      Map.of("id", ferramentaId, "ativo", ativo)
    );
    return buscarNativa(ferramentaId);
  }

  @Transactional
  public void resetarNativa(String id, UsuarioPrincipal principal) {
    String ferramentaId = validarNativaId(id);
    jdbc.update(
      "DELETE FROM ferramenta_nativa_configuracao WHERE ferramenta_id = ?",
      ferramentaId
    );
    auditoria.registrar(
      principal.id(),
      null,
      "FERRAMENTA_NATIVA_RESETAR",
      "SUCESSO",
      null,
      null,
      Map.of("id", ferramentaId)
    );
  }

  @Transactional
  public FerramentaDtos.Response criar(
    FerramentaDtos.SalvarRequest request,
    UsuarioPrincipal principal
  ) {
    Dados dados = validar(request);
    jdbc.update(
      """
      INSERT INTO ferramenta_configuravel
        (slug, nome, descricao, api_nome, filtros_json, colunas_preview_json, criado_por, atualizado_por)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      """,
      dados.slug(),
      dados.nome(),
      dados.descricao(),
      dados.apiNome(),
      json(dados.filtros()),
      json(dados.colunasPreview()),
      principal.id(),
      principal.id()
    );
    Long id = jdbc.queryForObject("SELECT id FROM ferramenta_configuravel WHERE slug = ?", Long.class, dados.slug());
    auditoria.registrar(principal.id(), null, "FERRAMENTA_CRIAR", "SUCESSO", null, null, Map.of("slug", dados.slug()));
    return buscar(id == null ? 0 : id);
  }

  @Transactional
  public FerramentaDtos.Response atualizar(
    long id,
    FerramentaDtos.SalvarRequest request,
    UsuarioPrincipal principal
  ) {
    Dados dados = validar(request);
    int alterados = jdbc.update(
      """
      UPDATE ferramenta_configuravel
      SET slug = ?, nome = ?, descricao = ?, api_nome = ?, filtros_json = ?,
          colunas_preview_json = ?, atualizado_por = ?, ativo = TRUE
      WHERE id = ?
      """,
      dados.slug(), dados.nome(), dados.descricao(), dados.apiNome(),
      json(dados.filtros()), json(dados.colunasPreview()), principal.id(), id
    );
    if (alterados != 1) throw new IllegalArgumentException("Ferramenta não encontrada.");
    auditoria.registrar(principal.id(), null, "FERRAMENTA_EDITAR", "SUCESSO", null, null, Map.of("id", id));
    return buscar(id);
  }

  @Transactional
  public void excluir(long id, UsuarioPrincipal principal) {
    int alterados = jdbc.update(
      "UPDATE ferramenta_configuravel SET ativo = FALSE, atualizado_por = ? WHERE id = ? AND ativo = TRUE",
      principal.id(),
      id
    );
    if (alterados != 1) throw new IllegalArgumentException("Ferramenta não encontrada.");
    auditoria.registrar(principal.id(), null, "FERRAMENTA_EXCLUIR", "SUCESSO", null, null, Map.of("id", id));
  }

  private FerramentaDtos.NativaResponse buscarNativa(String id) {
    return jdbc.query(
      """
      SELECT ferramenta_id, nome, descricao, ativo, atualizado_em
      FROM ferramenta_nativa_configuracao
      WHERE ferramenta_id = ?
      """,
      (rs, rowNum) -> mapNativa(rs),
      id
    ).stream().findFirst().orElseThrow(
      () -> new IllegalArgumentException("Configuração da ferramenta não encontrada.")
    );
  }

  private FerramentaDtos.Response buscar(long id) {
    return jdbc.query(
      """
      SELECT id, slug, nome, descricao, api_nome, filtros_json, colunas_preview_json,
             criado_em, atualizado_em
      FROM ferramenta_configuravel
      WHERE id = ? AND ativo = TRUE
      """,
      (rs, rowNum) -> map(rs),
      id
    ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Ferramenta não encontrada."));
  }

  private FerramentaDtos.NativaResponse mapNativa(ResultSet rs) throws SQLException {
    return new FerramentaDtos.NativaResponse(
      rs.getString("ferramenta_id"),
      rs.getString("nome"),
      rs.getString("descricao"),
      rs.getBoolean("ativo"),
      rs.getTimestamp("atualizado_em").toLocalDateTime()
    );
  }

  private FerramentaDtos.Response map(ResultSet rs) throws SQLException {
    return new FerramentaDtos.Response(
      rs.getLong("id"),
      rs.getString("slug"),
      rs.getString("nome"),
      rs.getString("descricao"),
      rs.getString("api_nome"),
      readList(rs.getString("filtros_json")),
      readList(rs.getString("colunas_preview_json")),
      rs.getTimestamp("criado_em").toLocalDateTime(),
      rs.getTimestamp("atualizado_em").toLocalDateTime()
    );
  }

  private String validarNativaId(String id) {
    String normalizado = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    if (!FERRAMENTAS_NATIVAS.contains(normalizado)) {
      throw new IllegalArgumentException("Ferramenta nativa não reconhecida.");
    }
    return normalizado;
  }

  private String textoOpcional(String valor, int limite) {
    if (valor == null || valor.isBlank()) return null;
    String normalizado = valor.trim().replaceAll("\\s+", " ");
    if (normalizado.length() > limite) {
      throw new IllegalArgumentException("Texto maior que o limite permitido.");
    }
    return normalizado;
  }

  private Dados validar(FerramentaDtos.SalvarRequest request) {
    String slug = request.slug().trim().toLowerCase(Locale.ROOT);
    if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
      throw new IllegalArgumentException("O endereço da ferramenta deve usar apenas letras, números e hífen.");
    }
    List<String> filtros = normalizarLista(request.filtros(), 30);
    List<String> colunas = normalizarLista(request.colunasPreview(), 80);
    return new Dados(
      slug,
      request.nome().trim().replaceAll("\\s+", " "),
      request.descricao().trim(),
      request.apiNome().trim(),
      filtros,
      colunas
    );
  }

  private List<String> normalizarLista(List<String> valores, int limite) {
    if (valores == null) return List.of();
    LinkedHashSet<String> itens = new LinkedHashSet<>();
    for (String valor : valores) {
      if (valor != null && !valor.isBlank()) itens.add(valor.trim());
      if (itens.size() > limite) throw new IllegalArgumentException("A configuração possui itens demais.");
    }
    return List.copyOf(itens);
  }

  private String json(List<String> valor) {
    try {
      return objectMapper.writeValueAsString(valor);
    } catch (Exception ex) {
      throw new IllegalStateException("Não foi possível salvar a configuração.", ex);
    }
  }

  private List<String> readList(String valor) {
    try {
      return objectMapper.readValue(valor, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }

  private record Dados(
    String slug,
    String nome,
    String descricao,
    String apiNome,
    List<String> filtros,
    List<String> colunasPreview
  ) {}
}
