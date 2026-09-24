/*
 * Responsabilidade: Verifica a normalização dos filtros antes da integração com o SGU.
 */
package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RelatorioPersonalizadoServiceTest {

  @Test
  void deveReceberUnderscoreEEnviarNomeCompactoAoSgu() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(requisicao(Map.of(
        "competencia_inicio", "202601",
        "competencia_fim", "202601",
        "codigo_beneficiario", "090.0001.000002.03",
        "cpf", "123.456.789-00",
        "nome_beneficiario", "João da Silva",
        "grupo_beneficiario", "Grupo Crônicos")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(sgu).executar(eq(RelatorioPersonalizadoService.API_NOME), parametros.capture());
    assertThat(parametros.getValue())
        .containsEntry("competenciainicio", 202601)
        .containsEntry("competenciafim", 202601)
        .containsEntry("codigobeneficiario", "090000100000203")
        .containsEntry("cpf", "12345678900")
        .containsEntry("nomebeneficiario", "%JOÃO DA SILVA%")
        .containsEntry("grupobeneficiario", "%|N:%GRUPO CRÔNICOS%")
        .doesNotContainKeys(
            "competencia_inicio",
            "competencia_fim",
            "competencia-inicio",
            "competencia-fim");
  }

  @Test
  void deveInterpretarNumeroComoCodigoExatoDoGrupoBeneficiario() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(requisicao(Map.of(
        "competencia_inicio", "202601",
        "competencia_fim", "202601",
        "grupo_beneficiario", "0012")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(sgu).executar(eq(RelatorioPersonalizadoService.API_NOME), parametros.capture());
    assertThat(parametros.getValue())
        .containsEntry("grupobeneficiario", "%|C:12|%");
  }

  @Test
  void deveNormalizarListaDeCodigosDeEmpresa() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(requisicao(Map.of(
        "competencia_inicio", "202601",
        "competencia_fim", "202601",
        "codigo_empresa", "2010038, 2011533, 2011372, 2010038")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(sgu).executar(eq(RelatorioPersonalizadoService.API_NOME), parametros.capture());
    assertThat(parametros.getValue())
        .containsEntry("codigoempresa", ",2010038,2011533,2011372,");

    assertThatThrownBy(() -> service.executar(requisicao(Map.of(
        "competencia_inicio", "202601",
        "competencia_fim", "202601",
        "codigo_empresa", "2010038, empresa"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("números separados por vírgula");
  }

  @Test
  void deveNormalizarIdsDeGuiaSeparadosPorVirgulaESemAlterarNumeroDaGuia() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(requisicao(Map.of(
        "competencia_inicio", "202601",
        "competencia_fim", "202601",
        "id_guia", "375354, 375355, 377434, 377435, 375354",
        "numero_guia", "GUIA-2026-001")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(sgu).executar(eq(RelatorioPersonalizadoService.API_NOME), parametros.capture());
    assertThat(parametros.getValue())
        .containsEntry("idguia", ",375354,375355,377434,377435,")
        .containsEntry("numeroguia", "GUIA-2026-001");

    assertThatThrownBy(() -> service.executar(requisicao(Map.of(
        "competencia_inicio", "202601",
        "competencia_fim", "202601",
        "id_guia", "375354, guia"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("números separados por vírgula");
  }

  @Test
  void deveManterCompatibilidadeComRequisicaoQueUsaHifen() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(requisicao(Map.of(
        "competencia-inicio", "202601",
        "competencia-fim", "202601")));

    verify(sgu).executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap());
  }

  @Test
  void deveReutilizarDefinicaoPublicadaQuandoEstruturaNaoMudar() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    RelatorioPersonalizadoRequest request = requisicao(Map.of(
        "competencia_inicio", "202601",
        "competencia_fim", "202601"));
    service.executar(request);
    service.executar(request);

    verify(sgu, times(1)).criarOuAtualizar(anyMap());
    verify(sgu, times(2)).executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap());
  }

  @Test
  void devePublicarConsultaDistinctQuandoSolicitado() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(new RelatorioPersonalizadoRequest(
        List.of("NUMERO_GUIA"),
        Map.of(
            "competencia_inicio", "202601",
            "competencia_fim", "202601"),
        true,
        1,
        50,
        "guias_distintas"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> definicao = ArgumentCaptor.forClass(Map.class);
    verify(sgu).criarOuAtualizar(definicao.capture());
    assertThat(String.valueOf(definicao.getValue().get("consultaSQL")))
        .contains("SELECT DISTINCT RP.NUMERO_GUIA")
        .doesNotContain("\n", "\r", "\t");
  }

  @Test
  void devePublicarSomaPorBeneficiarioParaSelecaoSomenteDeBeneficiarioEValor() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(new RelatorioPersonalizadoRequest(
        List.of("COD_BENEFICIARIO", "NOME_BENEFICIARIO", "VALOR_TOTAL"),
        Map.of(
            "competencia_inicio", "202601",
            "competencia_fim", "202608",
            "grupo_beneficiario", "2"),
        false,
        1,
        50,
        "totais_por_beneficiario"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> definicao = ArgumentCaptor.forClass(Map.class);
    verify(sgu).criarOuAtualizar(definicao.capture());
    assertThat(String.valueOf(definicao.getValue().get("consultaSQL")))
        .contains(
            "SUM(RP.VALOR_TOTAL) AS VALOR_TOTAL",
            "GROUP BY RP.O_BNF_UNIMED, RP.O_BNF_CONTRATO, " +
                "RP.O_BNF_CODIGO, RP.O_BNF_DEPENDENTE");
  }

  @Test
  void deveNormalizarOrdenacaoEPublicarDirecaoSolicitada() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(sgu.executar(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(Map.of("content", List.of(), "last", true));

    service.executar(new RelatorioPersonalizadoRequest(
        List.of("COD_BENEFICIARIO", "NOME_BENEFICIARIO"),
        Map.of(
            "competencia_inicio", "202601",
            "competencia_fim", "202601"),
        false,
        "nome_beneficiario",
        "desc",
        1,
        50,
        "beneficiarios"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> definicao = ArgumentCaptor.forClass(Map.class);
    verify(sgu).criarOuAtualizar(definicao.capture());
    assertThat(definicao.getValue())
        .containsEntry(
            "ordenacao",
            "NOME_BENEFICIARIO DESC,COD_BENEFICIARIO");
  }

  @Test
  void deveExporEmpresaPorCatalogoEOcultarBuscaLivrePorNome() {
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        mock(SguRelatorioService.class),
        mock(ExportacaoRelatorioService.class),
        new RelatorioPersonalizadoSqlBuilder());

    RelatorioPersonalizadoService.Configuracao configuracao = service.configuracao();

    RelatorioPersonalizadoService.Filtro empresa = configuracao.filtros().stream()
        .filter(filtro -> "codigo_empresa".equals(filtro.id()))
        .findFirst()
        .orElseThrow();
    assertThat(empresa.rotulo()).isEqualTo("Nome da empresa");
    assertThat(empresa.tipo()).isEqualTo("empresa");
    assertThat(configuracao.filtros())
        .extracting(RelatorioPersonalizadoService.Filtro::id)
        .doesNotContain("nome_empresa");

    assertThat(configuracao.colunas())
        .filteredOn(coluna -> "REGIAO_BENEFICIARIO".equals(coluna.id()))
        .singleElement()
        .extracting(RelatorioPersonalizadoService.Coluna::rotulo)
        .isEqualTo("Região do beneficiário");
    assertThat(configuracao.colunas())
        .filteredOn(coluna -> "ATIVO".equals(coluna.id()))
        .singleElement()
        .extracting(RelatorioPersonalizadoService.Coluna::rotulo)
        .isEqualTo("Beneficiário ativo");
  }

  @Test
  void deveAplicarRankingEPivotMensalNoBackend() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(exportacao.carregarRegistros(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(List.of(
            new LinkedHashMap<>(Map.of(
                "NOME_EMPRESA", "ACME",
                "VALOR_TOTAL", 10,
                "PERIODO", 202601)),
            new LinkedHashMap<>(Map.of(
                "NOME_EMPRESA", "ACME",
                "VALOR_TOTAL", 20,
                "PERIODO", 202602)),
            new LinkedHashMap<>(Map.of(
                "NOME_EMPRESA", "BETA",
                "VALOR_TOTAL", 5,
                "PERIODO", 202601)),
            new LinkedHashMap<>(Map.of(
                "NOME_EMPRESA", "BETA",
                "VALOR_TOTAL", 6,
                "PERIODO", 202602))));

    RelatorioPersonalizadoRequest request = new RelatorioPersonalizadoRequest(
        List.of("NOME_EMPRESA", "VALOR_TOTAL"),
        Map.of(
            "competencia_inicio", "202601",
            "competencia_fim", "202602"),
        false,
        null,
        null,
        true,
        List.of("VALOR_TOTAL"),
        new RelatorioPersonalizadoRequest.Ranking(
            "MAIORES",
            1,
            "NOME_EMPRESA",
            "VALOR_TOTAL"),
        List.of(),
        1,
        50,
        "top_empresa_mes");

    Map<String, Object> resposta = service.executar(request);

    assertThat(resposta.get("colunas"))
        .isEqualTo(List.of(
            "NOME_EMPRESA",
            "VALOR_TOTAL__202601",
            "VALOR_TOTAL__202602"));
    assertThat((List<?>) resposta.get("content")).singleElement().satisfies(item -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> linha = (Map<String, Object>) item;
      assertThat(linha)
          .containsEntry("NOME_EMPRESA", "ACME")
          .containsEntry("VALOR_TOTAL__202601", new java.math.BigDecimal("10"))
          .containsEntry("VALOR_TOTAL__202602", new java.math.BigDecimal("20"));
    });
  }

  @Test
  void deveRankearPeloTotalDoPeriodoAntesDeSepararMesesETratarPontoComoDecimalDoSgu() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    RelatorioPersonalizadoService service = new RelatorioPersonalizadoService(
        sgu,
        exportacao,
        new RelatorioPersonalizadoSqlBuilder());

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of());
    when(exportacao.carregarRegistros(eq(RelatorioPersonalizadoService.API_NOME), anyMap()))
        .thenReturn(List.of(
            new LinkedHashMap<>(Map.of(
                "COD_BENEFICIARIO", "001",
                "NOME_BENEFICIARIO", "BENEF A",
                "VALOR_TOTAL", "1.005",
                "PERIODO", 202601)),
            new LinkedHashMap<>(Map.of(
                "COD_BENEFICIARIO", "001",
                "NOME_BENEFICIARIO", "BENEF A",
                "VALOR_TOTAL", "1.005",
                "PERIODO", 202602)),
            new LinkedHashMap<>(Map.of(
                "COD_BENEFICIARIO", "002",
                "NOME_BENEFICIARIO", "BENEF B",
                "VALOR_TOTAL", "2.50",
                "PERIODO", 202601)),
            new LinkedHashMap<>(Map.of(
                "COD_BENEFICIARIO", "002",
                "NOME_BENEFICIARIO", "BENEF B",
                "VALOR_TOTAL", "0",
                "PERIODO", 202602))));

    RelatorioPersonalizadoRequest request = new RelatorioPersonalizadoRequest(
        List.of("COD_BENEFICIARIO", "NOME_BENEFICIARIO", "VALOR_TOTAL"),
        Map.of(
            "competencia_inicio", "202601",
            "competencia_fim", "202602",
            "codigo_empresa", "90"),
        false,
        null,
        null,
        true,
        List.of("VALOR_TOTAL"),
        new RelatorioPersonalizadoRequest.Ranking(
            "MAIORES",
            1,
            "COD_BENEFICIARIO",
            "VALOR_TOTAL"),
        List.of(),
        1,
        50,
        "top_beneficiarios");

    Map<String, Object> resposta = service.executar(request);

    assertThat((List<?>) resposta.get("content")).singleElement().satisfies(item -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> linha = (Map<String, Object>) item;
      assertThat(linha)
          .containsEntry("COD_BENEFICIARIO", "002")
          .containsEntry("NOME_BENEFICIARIO", "BENEF B")
          .containsEntry("VALOR_TOTAL__202601", new java.math.BigDecimal("2.50"))
          .containsEntry("VALOR_TOTAL__202602", java.math.BigDecimal.ZERO);
    });
  }

  private RelatorioPersonalizadoRequest requisicao(Map<String, Object> filtros) {
    return new RelatorioPersonalizadoRequest(
        List.of("COD_BENEFICIARIO"),
        filtros,
        false,
        1,
        50,
        "relatorio_personalizado");
  }
}
