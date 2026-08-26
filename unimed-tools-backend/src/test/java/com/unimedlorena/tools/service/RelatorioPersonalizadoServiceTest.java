/*
 * Responsabilidade: Verifica a normalização dos filtros antes da integração com o SGU.
 */
package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest;
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
        .contains("SELECT DISTINCT\n  RP.NUMERO_GUIA");
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
            "GROUP BY\n  RP.O_BNF_UNIMED,\n  RP.O_BNF_CONTRATO,\n  " +
                "RP.O_BNF_CODIGO,\n  RP.O_BNF_DEPENDENTE");
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
            "NOME_BENEFICIARIO DESC");
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
