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
        "nome_beneficiario", "João da Silva")));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(sgu).executar(eq(RelatorioPersonalizadoService.API_NOME), parametros.capture());
    assertThat(parametros.getValue())
        .containsEntry("competenciainicio", 202601)
        .containsEntry("competenciafim", 202601)
        .containsEntry("codigobeneficiario", "090000100000203")
        .containsEntry("cpf", "12345678900")
        .containsEntry("nomebeneficiario", "%JOÃO DA SILVA%")
        .doesNotContainKeys(
            "competencia_inicio",
            "competencia_fim",
            "competencia-inicio",
            "competencia-fim");
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

  private RelatorioPersonalizadoRequest requisicao(Map<String, Object> filtros) {
    return new RelatorioPersonalizadoRequest(
        List.of("COD_BENEFICIARIO"),
        filtros,
        1,
        50,
        "relatorio_personalizado");
  }
}
