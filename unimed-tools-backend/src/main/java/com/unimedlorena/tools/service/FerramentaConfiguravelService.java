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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FerramentaConfiguravelService {

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
