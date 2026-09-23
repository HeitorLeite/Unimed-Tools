package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AssistencialDesempenhoTest {
  private RelatorioPersonalizadoRequest request(String coluna, String competencia) {
    return new RelatorioPersonalizadoRequest(List.of(coluna),
        Map.of("competencia_inicio", competencia, "competencia_fim", competencia), false, 1, 50, "teste");
  }

  @Test
  void reutilizaEstruturaSemRegerarSqlMasConsultaDadosNovamente() {
    var sgu = mock(SguRelatorioService.class);
    var builder = spy(new RelatorioPersonalizadoSqlBuilder());
    var service = new RelatorioPersonalizadoService(sgu, mock(ExportacaoRelatorioService.class), builder);
    when(sgu.executar(anyString(), anyMap())).thenReturn(Map.of("content", List.of()));
    service.executar(request("VALOR_TOTAL", "202601"));
    service.executar(request("VALOR_TOTAL", "202602"));
    verify(builder, times(1)).gerar(anyList(), anySet(), anyBoolean(), any(), any());
    verify(sgu, times(1)).criarOuAtualizar(anyMap());
    verify(sgu, times(2)).executar(anyString(), anyMap());
    service.executar(request("IDADE", "202601"));
    verify(sgu, times(2)).criarOuAtualizar(anyMap());
  }

  @Test
  void invalidaCacheAposPublicacaoComResultadoIncerto() {
    var sgu = mock(SguRelatorioService.class);
    var service = new RelatorioPersonalizadoService(sgu, mock(ExportacaoRelatorioService.class), new RelatorioPersonalizadoSqlBuilder());
    when(sgu.executar(anyString(), anyMap())).thenReturn(Map.of("content", List.of()));
    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of()).thenThrow(new IllegalStateException("timeout")).thenReturn(Map.of());
    service.executar(request("VALOR_TOTAL", "202601"));
    assertThatThrownBy(() -> service.executar(request("IDADE", "202601"))).hasMessage("timeout");
    service.executar(request("VALOR_TOTAL", "202601"));
    verify(sgu, times(3)).criarOuAtualizar(anyMap());
  }

  @Test
  void exportaNoAmbienteConfiguradoELiberaLockAntesDeGerarArquivo() throws Exception {
    var sgu = mock(SguRelatorioService.class);
    var exportacao = mock(ExportacaoRelatorioService.class);
    var service = new RelatorioPersonalizadoService(sgu, exportacao, new RelatorioPersonalizadoSqlBuilder());
    ReflectionTestUtils.setField(service, "apiNome", "0090-relatorio-personalizado-dev");
    when(exportacao.carregarRegistros(anyString(), anyMap())).thenReturn(List.of(new LinkedHashMap<>(Map.of("VALOR_TOTAL", 10))));
    when(sgu.executar(anyString(), anyMap())).thenReturn(Map.of("content", List.of()));
    try (var executor = Executors.newSingleThreadExecutor()) {
      when(exportacao.gerarArquivo(eq("csv"), anyList(), eq(Set.of("VALOR_TOTAL")))).thenAnswer(invocation -> {
        executor.submit(() -> service.executar(request("IDADE", "202601"))).get(5, TimeUnit.SECONDS);
        return new ExportacaoRelatorioService.Arquivo(new byte[0], "text/csv", "csv", 1);
      });
      assertThat(service.exportar("csv", request("VALOR_TOTAL", "202601")).quantidadeRegistros()).isEqualTo(1);
    }
    verify(exportacao).carregarRegistros(eq("0090-relatorio-personalizado-dev"), anyMap());
    verify(exportacao, never()).carregarRegistros(eq(RelatorioPersonalizadoService.API_NOME), anyMap());
  }
}
