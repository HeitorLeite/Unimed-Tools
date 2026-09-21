package com.unimedlorena.tools.service;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unimedlorena.tools.dto.HospitalRelatorioRequest;

class HospitalRelatorioServiceTest {

  @Test
  void deveNormalizarFiltrosPermitidosAntesDeExecutar() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    HospitalRelatorioService service = new HospitalRelatorioService(sgu, exportacao);

    when(sgu.criarOuAtualizar(any())).thenReturn(Map.of());
    when(sgu.executar(eq(HospitalRelatorioService.API_NOME), any())).thenReturn(
        Map.of("content", java.util.List.of()));

    service.executar(
        new HospitalRelatorioRequest(
            Map.of(
                "convenio", "unimed lorena",
                "nomebenef", "pessoa teste",
                "status", "2",
                "dataautorizacaoinicio", "2026-09-01"),
            1,
            25,
            null));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(sgu).executar(eq(HospitalRelatorioService.API_NOME), parametros.capture());

    assertThat(parametros.getValue())
        .containsEntry("convenio", "%UNIMED LORENA%")
        .containsEntry("nomebenef", "%PESSOA TESTE%")
        .containsEntry("status", 2)
        .containsEntry("dataautorizacaoinicio", "2026-09-01")
        .containsEntry("page", 1)
        .containsEntry("size", 25);
  }

  @Test
  void deveRejeitarFiltroForaDaAllowlist() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    ExportacaoRelatorioService exportacao = mock(ExportacaoRelatorioService.class);
    HospitalRelatorioService service = new HospitalRelatorioService(sgu, exportacao);

    when(sgu.criarOuAtualizar(any())).thenReturn(Map.of());

    assertThatThrownBy(
        () -> service.executar(
            new HospitalRelatorioRequest(
                Map.of("consultaSql", "valor"),
                1,
                25,
                null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Filtro não permitido");
  }

  @Test
  void deveExporSomenteColunasEFiltrosAprovados() {
    HospitalRelatorioService service = new HospitalRelatorioService(
        mock(SguRelatorioService.class),
        mock(ExportacaoRelatorioService.class));

    var configuracao = service.configuracao();

    assertThat(configuracao.colunas()).containsExactly(
        "CONVENIO",
        "NOME_BENEF",
        "DATA_AUTORIZACAO",
        "DATA_VALIDADE",
        "COD_GUIA",
        "PROCEDIMENTO",
        "DESCRICAO",
        "STATUS");
    assertThat(configuracao.filtros())
        .extracting(HospitalRelatorioService.Filtro::id)
        .containsExactly(
            "convenio",
            "nomebenef",
            "dataautorizacaoinicio",
            "dataautorizacaofim",
            "codguia",
            "procedimento",
            "descricao",
            "status");
  }
}
