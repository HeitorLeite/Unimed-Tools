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
  void deveRelacionarGruposSomenteQuandoFiltroEstiverAtivoSemMultiplicarBeneficiarios() {
    assertThat(builder.filtro("grupo_beneficiario")).satisfies(filtro -> {
      assertThat(filtro.rotulo()).isEqualTo("Grupo do beneficiário");
      assertThat(filtro.grupo()).isEqualTo("Beneficiário");
      assertThat(filtro.tipoTela()).isEqualTo("text");
      assertThat(filtro.placeholder()).isEqualTo("Código ou parte do nome");
      assertThat(filtro.obrigatorio()).isFalse();
    });

    RelatorioPersonalizadoSqlBuilder.ApiGerada semFiltro = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("competencia_inicio", "competencia_fim"));
    RelatorioPersonalizadoSqlBuilder.ApiGerada comFiltro = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("competencia_inicio", "competencia_fim", "grupo_beneficiario"));

    assertThat(semFiltro.consultaSql())
        .doesNotContain("DBAUNIMED.GRUPO_BNFRIO_ITEM", "DBAUNIMED.GRUPO_BNFRIO GB");
    assertThat(comFiltro.consultaSql())
        .contains(
            "FROM DBAUNIMED.GRUPO_BNFRIO_ITEM GBI",
            "INNER JOIN DBAUNIMED.GRUPO_BNFRIO GB",
            "GB.GRBNF_COD = GBI.GRBNF_COD",
            "'|C:' || TO_CHAR(MEM.GRBNF_COD)",
            "GRUPOS_BNF.GRBNI_COD_UNIMED_RESPON = G.GUIA_COD_UNIMED_BNFRIO",
            "GRUPOS_BNF.GRBNI_COD_CNTRAT_CART = G.GUIA_COD_CNTRAT_CART_BNFRIO",
            "GRUPOS_BNF.GRBNI_COD_BNFRIO = G.GUIA_COD_BNFRIO",
            "GRUPOS_BNF.GRBNI_COD_DEPNTE = G.GUIA_COD_DEPNTE_BNFRIO",
            "GRUPOS_BNF.GRUPOS_BUSCA AS F_GRUPO_BENEFICIARIO")
        .contains("SELECT DISTINCT")
        .contains("LISTAGG(");
    assertThat(comFiltro.filtros())
        .anySatisfy(filtro -> assertThat(filtro)
            .containsEntry("nomeFiltro", "grupobeneficiario")
            .containsEntry(
                "conteudoFiltro",
                "and RP.F_GRUPO_BENEFICIARIO LIKE :grupobeneficiario"));
  }

  @Test
  void deveSomarValoresPorBeneficiarioQuandoSomenteEssesGruposForemSelecionados() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of(
            "COD_BENEFICIARIO",
            "NOME_BENEFICIARIO",
            "VALOR_TOTAL"),
        Set.of("competencia_inicio", "competencia_fim"),
        true);

    assertThat(api.consultaSql())
        .contains(
            "RP.COD_BENEFICIARIO",
            "RP.NOME_BENEFICIARIO",
            "SUM(RP.VALOR_TOTAL) AS VALOR_TOTAL",
            "G.GUIA_COD_UNIMED_BNFRIO AS O_BNF_UNIMED",
            "G.GUIA_COD_CNTRAT_CART_BNFRIO AS O_BNF_CONTRATO",
            "G.GUIA_COD_BNFRIO AS O_BNF_CODIGO",
            "G.GUIA_COD_DEPNTE_BNFRIO AS O_BNF_DEPENDENTE",
            "GROUP BY\n  RP.O_BNF_UNIMED,\n  RP.O_BNF_CONTRATO,\n  RP.O_BNF_CODIGO,\n  RP.O_BNF_DEPENDENTE,\n  RP.COD_BENEFICIARIO,\n  RP.NOME_BENEFICIARIO")
        .doesNotContain("SELECT DISTINCT");
    assertThat(api.ordenacao())
        .isEqualTo(
            "RP.O_BNF_UNIMED, RP.O_BNF_CONTRATO, RP.O_BNF_CODIGO, " +
                "RP.O_BNF_DEPENDENTE, RP.COD_BENEFICIARIO, RP.NOME_BENEFICIARIO")
        .doesNotContain("O_GUIA_ID", "O_ITEM_SEQ");
  }

  @Test
  void deveManterDetalhamentoQuandoUmaColunaDeGuiaForSelecionada() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO", "NUMERO_GUIA", "VALOR_TOTAL"),
        Set.of("competencia_inicio", "competencia_fim"));

    assertThat(api.consultaSql())
        .contains("RP.VALOR_TOTAL")
        .doesNotContain("SUM(RP.VALOR_TOTAL)", "GROUP BY");
    assertThat(api.ordenacao())
        .isEqualTo("RP.O_COMPETENCIA, RP.O_GUIA_ID, RP.O_ITEM_SEQ");
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

  @Test
  void deveAplicarDistinctEOrdenarPelasColunasProjetadas() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("NUMERO_GUIA", "PERIODO"),
        Set.of("competencia_inicio", "competencia_fim"),
        true);

    assertThat(api.consultaSql())
        .contains("SELECT DISTINCT\n  RP.NUMERO_GUIA,\n  RP.PERIODO\nFROM (");
    assertThat(api.ordenacao())
        .isEqualTo("RP.NUMERO_GUIA, RP.PERIODO")
        .doesNotContain("O_GUIA_ID", "O_ITEM_SEQ");
  }
}
