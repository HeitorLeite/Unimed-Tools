/*
 * Responsabilidade: Verifica o contrato de nomes dos filtros do relatório personalizado.
 */
package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
  void devePublicarNomeEParametroComHifenNoSgu() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("competencia_inicio"));

    assertThat(api.filtros()).hasSize(1);
    Map<String, Object> filtro = api.filtros().getFirst();
    assertThat(filtro)
        .containsEntry("nomeFiltro", "competencia-inicio")
        .containsEntry(
            "conteudoFiltro",
            "and G.GUIA_NRO_COMPET >= :competencia-inicio");
  }

  @Test
  void deveGerarApiMesmoQuandoFiltroAtivoChegarComHifen() {
    RelatorioPersonalizadoSqlBuilder.ApiGerada api = builder.gerar(
        List.of("COD_BENEFICIARIO"),
        Set.of("competencia-inicio"));

    assertThat(api.filtros())
        .extracting(filtro -> filtro.get("nomeFiltro"))
        .containsExactly("competencia-inicio");
  }
}
