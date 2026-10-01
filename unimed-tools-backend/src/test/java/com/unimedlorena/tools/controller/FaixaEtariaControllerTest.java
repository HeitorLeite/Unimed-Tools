package com.unimedlorena.tools.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.unimedlorena.tools.exception.GlobalExceptionHandler;
import com.unimedlorena.tools.service.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FaixaEtariaControllerTest {
  @Test
  void retornaCsvConsolidadoComHeadersENaoEntregaArquivoQuandoVazio() throws Exception {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(eq("0090-faixa-etaria"), anyMap())).thenReturn(Map.of(
      "content", List.of(Map.of("FAIXA_ETARIA", "19 a 23", "DEP_MASC", 2,
        "DEP_FEM", 3, "TIT_MASC", 5, "TIT_FEM", 7)), "last", true));
    var exportacao = new ExportacaoRelatorioService(sgu, 100, 0);
    var controller = new RelatorioController(sgu, exportacao,
      new ExportacaoLoteRelatorioService(exportacao), mock(RelatorioPersonalizadoService.class),
      mock(HospitalRelatorioService.class));
    var mvc = MockMvcBuilders.standaloneSetup(controller)
      .setControllerAdvice(new GlobalExceptionHandler()).build();
    String corpo = """
      {"filtros":{"combinacoesFiltros":[{"empresas":"A"},{"empresas":"B"}]},"nomeArquivo":"geral"}
      """;
    var async = mvc.perform(post("/api/relatorios/sgu/exportar/0090-faixa-etaria?formato=csv")
      .contentType(MediaType.APPLICATION_JSON).content(corpo))
      .andExpect(request().asyncStarted()).andReturn();
    mvc.perform(asyncDispatch(async)).andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith("text/csv"))
      .andExpect(header().string("X-Total-Registros", "11"))
      .andExpect(header().exists("Content-Length"))
      .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("geral.csv")))
      .andExpect(content().string(org.hamcrest.Matchers.containsString("19 a 23;10;24;20;14;34")));

    when(sgu.executar(eq("0090-faixa-etaria"), anyMap())).thenReturn(Map.of("content", List.of(), "last", true));
    var vazio = mvc.perform(post("/api/relatorios/sgu/exportar/0090-faixa-etaria?formato=csv")
      .contentType(MediaType.APPLICATION_JSON).content(corpo))
      .andExpect(request().asyncStarted()).andReturn();
    mvc.perform(asyncDispatch(vazio)).andExpect(status().isUnprocessableEntity())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(header().doesNotExist("Content-Disposition"))
      .andExpect(jsonPath("$.codigo").value("RELATORIO_VAZIO"));
  }
}
