/*
 * Responsabilidade: Verifica o contrato de nomes dos filtros do relatório personalizado.
 */
package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void deveExporNovosIndicadoresFinanceirosNoCatalogo() {
    assertThat(builder.campos()).hasSize(53);
    assertThat(builder.campo("VALOR_TOTAL").rotulo()).isEqualTo("Despesa total");
    assertThat(builder.campo("VALOR_TOTAL_21").rotulo())
        .isEqualTo("Despesa total com 21%");
    assertThat(builder.campo("RECEITA")).satisfies(campo -> {
      assertThat(campo.rotulo()).isEqualTo("Receita");
      assertThat(campo.grupo()).isEqualTo("Valores");
      assertThat(campo.sensivel()).isTrue();
    });
    assertThat(builder.campo("SINISTRALIDADE").rotulo())
        .isEqualTo("Sinistralidade (%)");
    assertThat(builder.campo("NOME_PESSOA_EMPRESA")).satisfies(campo -> {
      assertThat(campo.rotulo()).isEqualTo("Nome da pessoa da empresa");
      assertThat(campo.expressaoSql()).isEqualTo("PES_EMPRESA.PES_NOM_COMP");
    });
  }

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
      if (filtro.get("nomeFiltro").equals("codigoempresa")) {
        assertThat(conteudo).isEqualTo(
            "and :codigoempresa LIKE RP.F_CODIGO_EMPRESA");
      } else {
        assertThat(conteudo)
            .matches("and RP\\.F_[A-Z0-9_]+ (?:=|>=|<=|LIKE) :" + nomePublico);
      }
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
  void devePublicarFiltroDeMultiplosCodigosDeEmpresaComoBindTextual() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("CODIGO_EMPRESA", "NOME_PESSOA_EMPRESA"),
        Set.of("codigo_empresa"));

    assertThat(api.consultaSql()).contains(
        "PES_EMPRESA.PES_NOM_COMP AS NOME_PESSOA_EMPRESA",
        "WHEN BF.UNI_COD_RESPON <> 90 THEN EXT.US8UNIMED",
        "ELSE EC.EMPCN_COD_PESSOA");
    assertThat(api.filtros()).singleElement().satisfies(filtro -> assertThat(filtro)
        .containsEntry("nomeFiltro", "codigoempresa")
        .containsEntry("tipoDadoFiltro", "VARCHAR(240)")
        .containsEntry(
            "conteudoFiltro",
            "and :codigoempresa LIKE RP.F_CODIGO_EMPRESA"));
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

  @Test
  void deveOrdenarTodaConsultaPelaColunaSelecionada() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada crescente = builder.gerar(
        List.of("NOME_BENEFICIARIO", "VALOR_TOTAL"),
        Set.of("competencia_inicio", "competencia_fim"),
        false,
        "nome_beneficiario",
        "asc");
    RelatorioPersonalizadoSqlBuilder.ApiGerada decrescente = builder.gerar(
        List.of("NOME_BENEFICIARIO", "VALOR_TOTAL"),
        Set.of("competencia_inicio", "competencia_fim"),
        false,
        "VALOR_TOTAL",
        "DESC");

    assertThat(crescente.ordenacao()).isEqualTo("NOME_BENEFICIARIO ASC");
    assertThat(decrescente.ordenacao()).isEqualTo("VALOR_TOTAL DESC");
    assertThatThrownBy(() -> builder.gerar(
        List.of("NOME_BENEFICIARIO"),
        Set.of("competencia_inicio", "competencia_fim"),
        false,
        "CPF",
        "ASC"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("colunas selecionadas");
  }

  @Test
  void deveAgregarReceitaDespesaESinistralidadeSemMultiplicarItens() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of(
            "CODIGO_EMPRESA",
            "NOME_EMPRESA",
            "VALOR_TOTAL",
            "RECEITA",
            "SINISTRALIDADE"),
        Set.of("competencia_inicio", "competencia_fim", "codigo_empresa"),
        false,
        "SINISTRALIDADE",
        "DESC");

    assertThat(api.consultaSql())
        .contains(
            "RECEITA_TAXA AS (",
            "RECEITA_SERVICO AS (",
            "INNER JOIN MOVIMENTO_RECEITA MOV ON MOV.MR_COD = MRT.MR_COD",
            "INNER JOIN MOVIMENTO_RECEITA MOV ON MOV.MR_COD = MRS.MR_COD",
            "DESPESA_INDICADOR AS (",
            "RECEITA_INDICADOR AS (",
            "RECEITA_SEM_MOVIMENTO AS (",
            "AND GUIA.GUIA_NRO_COMPET >= :competenciainicio",
            "AND GUIA.GUIA_NRO_COMPET <= :competenciafim",
            "AND FR.FR_DAT_COMPET >= :competenciainicio",
            "AND FR.FR_DAT_COMPET <= :competenciafim",
            "SUM(RP.M_DESPESA) AS VALOR_TOTAL",
            "SUM(RP.M_RECEITA) AS RECEITA",
            "(SUM(RP.M_DESPESA) - SUM(RP.M_COPART)) /",
            "SUM(RP.M_MENSALIDADE) * 100",
            "GROUP BY\n  RP.O_EMPRESA,\n  RP.CODIGO_EMPRESA,\n  RP.NOME_EMPRESA")
        .containsOnlyOnce("LEFT JOIN RECEITA_TAXA RT ON RT.MR_COD = MR.MR_COD")
        .doesNotContain(
            "MRT_VAL_FINAL + MRS_VAL_FINAL",
            "/*FILTRO_COMPETENCIA_DESPESA*/",
            "/*FILTRO_COMPETENCIA_FATURA*/");
    assertThat(api.ordenacao()).isEqualTo("SINISTRALIDADE DESC");
    assertThat(api.filtros())
        .extracting(filtro -> filtro.get("nomeFiltro"))
        .containsExactly("competenciainicio", "competenciafim", "codigoempresa");
  }

  @Test
  void deveRestringirIndicadoresAosRecortesComGranularidadeCompativel() {
    assertThatThrownBy(() -> builder.gerar(
        List.of("NOME_PRESTADOR", "RECEITA"),
        Set.of("competencia_inicio", "competencia_fim")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Beneficiário, Contrato e empresa");

    assertThatThrownBy(() -> builder.gerar(
        List.of("NOME_EMPRESA", "SINISTRALIDADE"),
        Set.of("competencia_inicio", "competencia_fim", "nome_prestador")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("filtros de período, beneficiário, contrato ou empresa");
  }
}
