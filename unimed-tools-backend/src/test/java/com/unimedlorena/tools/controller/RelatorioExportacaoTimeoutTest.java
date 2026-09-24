package com.unimedlorena.tools.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.unimedlorena.tools.exception.ApiException;
import com.unimedlorena.tools.exception.GlobalExceptionHandler;
import com.unimedlorena.tools.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RelatorioExportacaoTimeoutTest {
  @Test
  void deveResponderJson504EmVezDeXlsxQuandoConsultaFalha() throws Exception {
    var exportacao = mock(ExportacaoRelatorioService.class);
    when(exportacao.descreverArquivo("xlsx")).thenReturn(
      new ExportacaoRelatorioService.DescricaoArquivo(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"));
    doThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "SGU_TIMEOUT", "O SGU excedeu o tempo de resposta."))
      .when(exportacao).exportarPara(eq("api-teste"), eq("xlsx"), any(), any());
    var controller = new RelatorioController(mock(SguRelatorioService.class), exportacao,
      mock(ExportacaoLoteRelatorioService.class), mock(RelatorioPersonalizadoService.class), mock(HospitalRelatorioService.class));
    var mvc = MockMvcBuilders.standaloneSetup(controller)
      .setControllerAdvice(new GlobalExceptionHandler()).build();
    var async = mvc.perform(post("/api/relatorios/sgu/exportar/api-teste")
      .contentType(MediaType.APPLICATION_JSON).content("{\"filtros\":{}}"))
      .andExpect(request().asyncStarted()).andReturn();
    mvc.perform(asyncDispatch(async)).andExpect(status().isGatewayTimeout())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.codigo").value("SGU_TIMEOUT"))
      .andExpect(jsonPath("$.message").value("O SGU excedeu o tempo de resposta."));
  }
}
