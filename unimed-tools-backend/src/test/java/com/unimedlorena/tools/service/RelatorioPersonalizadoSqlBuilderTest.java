/*
 * Responsabilidade: Verifica o contrato de nomes dos filtros do relatório personalizado.
 */
package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RelatorioPersonalizadoSqlBuilderTest {

  private final RelatorioPersonalizadoSqlBuilder builder =
      new RelatorioPersonalizadoSqlBuilder();

  @Test
  void deveAceitarFiltroComUnderscoreOuHifen() {
    assertThat(builder.filtro("competencia_inicio"))
        .isSameAs(builder.filtro("competencia-inicio"));
    assertThat(builder.filtro("nome_beneficiario"))
        .isSameAs(builder.filtro("nome-beneficiario"));
  }

  @Test
  void deveUsarMesmoNomeCompactoNoSguENoBindOracle() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("competencia_inicio"));

    assertThat(api.filtros()).hasSize(1);
    Map<String, Object> filtro = api.filtros().getFirst();
    assertThat(filtro)
        .containsEntry("nomeFiltro", "competenciainicio")
        .containsEntry(
            "conteudoFiltro",
            "and RP.F_COMPETENCIA >= :competenciainicio");
  }

  @Test
  void deveGerarNomesExternosAlfanumericosUnicosParaTodosOsFiltros() {
    Map<String, String> expressoesPorAlias = new HashMap<>();
    for (RelatorioPersonalizadoSqlBuilder.Filtro filtro : builder.filtros()) {
      String anterior = expressoesPorAlias.putIfAbsent(
          filtro.aliasSql(),
          filtro.expressaoSql());
      if (anterior != null) {
        assertThat(filtro.expressaoSql()).isEqualTo(anterior);
      }
    }

    Set<String> ids = builder.filtros().stream()
        .map(RelatorioPersonalizadoSqlBuilder.Filtro::id)
        .collect(Collectors.toSet());

    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        ids);

    Set<String> nomesExternos = new HashSet<>();
    for (Map<String, Object> filtro : api.filtros()) {
      String nomePublico = String.valueOf(filtro.get("nomeFiltro"));
      String conteudo = String.valueOf(filtro.get("conteudoFiltro"));

      assertThat(nomePublico).matches("[a-z0-9]+");
      assertThat(nomesExternos.add(nomePublico)).isTrue();
      assertThat(conteudo)
          .matches("and RP\\.F_[A-Z0-9_]+ (?:=|>=|<=|LIKE) :" + nomePublico);
      assertThat(conteudo).doesNotContain(
          "\n",
          "\r",
          "\t",
          "/*",
          "*/",
          "CASE",
          "UPPER(",
          "REPLACE(",
          "':" + nomePublico + "'");
    }
  }

  @Test
  void deveAdaptarFiltroNomeBeneficiarioRelatadoPeloSgu() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("NOME_BENEFICIARIO"),
        Set.of("nome_beneficiario"));

    assertThat(api.filtros()).singleElement().satisfies(filtro -> {
      assertThat(filtro).containsEntry("nomeFiltro", "nomebeneficiario");
      assertThat(String.valueOf(filtro.get("conteudoFiltro")))
          .isEqualTo("and RP.F_NOME_BENEFICIARIO LIKE :nomebeneficiario")
          .doesNotContain(":nome_beneficiario", ":nome-beneficiario");
    });
  }

  @Test
  void deveEnviarFiltroCodigoBeneficiarioEmUmaUnicaLinha() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("codigo_beneficiario"));

    assertThat(api.filtros()).singleElement().satisfies(filtro -> {
      assertThat(filtro).containsEntry("nomeFiltro", "codigobeneficiario");
      assertThat(String.valueOf(filtro.get("conteudoFiltro")))
          .isEqualTo("and RP.F_CODIGO_BENEFICIARIO = :codigobeneficiario")
          .doesNotContain("\n", "\r", "\t");
    });
  }

  @Test
  void deveAplicarFiltrosNaConsultaExternaEPreservarOrdenacaoTecnica() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("competencia_inicio", "competencia_fim", "codigo_beneficiario"));

    assertThat(api.consultaSql())
        .contains("SELECT\n  RP.COD_BENEFICIARIO\nFROM (")
        .contains("AS F_CODIGO_BENEFICIARIO")
        .contains("AS O_COMPETENCIA", "AS O_GUIA_ID", "AS O_ITEM_SEQ")
        .contains(") RP\nWHERE 1 = 1\n  /*FILTROS*/");
    assertThat(api.ordenacao())
        .isEqualTo("RP.O_COMPETENCIA, RP.O_GUIA_ID, RP.O_ITEM_SEQ");
  }

  @Test
  void deveGerarApiMesmoQuandoFiltroAtivoChegarComHifen() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("competencia-inicio"));

    assertThat(api.filtros())
        .extracting(filtro -> filtro.get("nomeFiltro"))
        .containsExactly("competenciainicio");
  }
}
