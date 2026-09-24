package com.unimedlorena.tools.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.unimedlorena.tools.exception.*;
import com.unimedlorena.tools.service.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RelatorioDownloadTest {
  private MockMvc mvc(SguRelatorioService sgu) {
    return MockMvcBuilders.standaloneSetup(new RelatorioController(sgu,
      new ExportacaoRelatorioService(sgu, 2, 10), mock(ExportacaoLoteRelatorioService.class),
      mock(RelatorioPersonalizadoService.class), mock(HospitalRelatorioService.class)))
      .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @ParameterizedTest @ValueSource(strings = {"csv", "txt", "xlsx"})
  void entregaArquivoCompletoComContagemETamanho(String formato) throws Exception {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(anyString(), anyMap())).thenReturn(
      Map.of("content", List.of(Map.of("CODIGO", "001"), Map.of("CODIGO", "002")), "last", false),
      Map.of("content", List.of(Map.of("CODIGO", "003")), "last", true));
    when(sgu.listar(anyString())).thenReturn(Map.of("content", List.of(Map.of("nome", "teste", "ordenacao", "CODIGO"))));
    var mvc = mvc(sgu);
    var inicio = mvc.perform(post("/api/relatorios/sgu/exportar/teste").param("formato", formato)
      .contentType(MediaType.APPLICATION_JSON).content("{\"filtros\":{},\"nomeArquivo\":\"teste\"}"))
      .andExpect(request().asyncStarted()).andReturn();
    var resposta = mvc.perform(asyncDispatch(inicio)).andExpect(status().isOk())
      .andExpect(header().string("X-Total-Registros", "3"))
      .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
    assertThat(resposta.getContentAsByteArray()).hasSize(Integer.parseInt(resposta.getHeader("Content-Length")));
    assertThat(resposta.getHeader("Content-Disposition")).contains("teste." + formato);
    if (formato.equals("xlsx")) {
      try (var wb = new XSSFWorkbook(new ByteArrayInputStream(resposta.getContentAsByteArray()))) {
        assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(3);
        assertThat(wb.getSheetAt(0).getRow(3).getCell(0).getStringCellValue()).isEqualTo("003");
      }
    } else assertThat(resposta.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("001", "002", "003");
  }

  @ParameterizedTest @ValueSource(strings = {"csv", "txt", "xlsx"})
  void falhaNaSegundaPaginaNaoEntregaArquivoParcial(String formato) throws Exception {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(anyString(), anyMap())).thenReturn(
      Map.of("content", List.of(Map.of("ID", "A"), Map.of("ID", "B")), "last", false))
      .thenThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FALHA_TESTE", "Falha de consulta."));
    when(sgu.listar(anyString())).thenReturn(Map.of("content", List.of(Map.of("nome", "teste", "ordenacao", "ID"))));
    var mvc = mvc(sgu);
    var inicio = mvc.perform(post("/api/relatorios/sgu/exportar/teste").param("formato", formato))
      .andExpect(request().asyncStarted()).andReturn();
    mvc.perform(asyncDispatch(inicio)).andExpect(status().isUnprocessableEntity())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(header().doesNotExist("Content-Disposition"))
      .andExpect(jsonPath("$.codigo").value("FALHA_TESTE"));
  }

  @ParameterizedTest @ValueSource(strings = {"csv", "txt", "xlsx"})
  void resultadoVazioNaoViraPlanilhaEmBranco(String formato) throws Exception {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(anyString(), anyMap())).thenReturn(Map.of("content", List.of(), "last", true));
    var mvc = mvc(sgu);
    var inicio = mvc.perform(post("/api/relatorios/sgu/exportar/teste").param("formato", formato)).andReturn();
    mvc.perform(asyncDispatch(inicio)).andExpect(status().isUnprocessableEntity())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.codigo").value("RELATORIO_VAZIO"));
  }
}
