package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;

class AssistencialExportacaoTest {
  @Test
  void exportacaoSimplesRespeitaOrdemDeColunasDaPrevia() throws Exception {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
      .thenReturn(Map.of("content", List.of(Map.of("nome_beneficiario", "Pessoa teste", "valor_total", 12)), "last", true));
    var exportacao = new ExportacaoRelatorioService(sgu, 1000, 0);
    var assistencial = new RelatorioPersonalizadoService(sgu, exportacao, new RelatorioPersonalizadoSqlBuilder());
    var request = new com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest(
      List.of("NOME_BENEFICIARIO", "VALOR_TOTAL"), Map.of("competencia_inicio", "202601", "competencia_fim", "202601"),
      false, null, null, false, List.of(), null, List.of("VALOR_TOTAL", "NOME_BENEFICIARIO"), 1, 50, "teste");
    for (String formato : List.of("csv", "txt")) {
      assertThat(new String(assistencial.exportar(formato, request).conteudo(), StandardCharsets.UTF_8))
        .startsWith("\uFEFFVALOR_TOTAL;NOME_BENEFICIARIO\r\n12,00;Pessoa teste");
    }
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(assistencial.exportar("xlsx", request).conteudo()))) {
      assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("VALOR_TOTAL");
    }
  }
  @Test
  void preservaInteirosCodigosNulosEArredondaValoresNosTresFormatos() throws Exception {
    var service = new ExportacaoRelatorioService(mock(SguRelatorioService.class), 1000, 0);
    var linha = new LinkedHashMap<String, Object>();
    linha.put("ID_GUIA", "000123");
    linha.put("IDADE", 42);
    linha.put("QUANTIDADE", 3);
    linha.put("VALOR_TOTAL", "1.005");
    linha.put("VALOR_TOTAL__202601", 10);
    linha.put("VALOR_TOTAL_21", "1234567.8");
    linha.put("VALOR_RECEBER", "-2,345");
    linha.put("VALOR_FATOR", null);
    var dados = List.of(linha);
    var decimais = Set.of("VALOR_TOTAL", "VALOR_TOTAL__202601", "VALOR_TOTAL_21", "VALOR_RECEBER", "VALOR_FATOR");
    for (String formato : List.of("csv", "txt")) {
      String arquivo = new String(service.gerarArquivo(formato, dados, decimais).conteudo(), StandardCharsets.UTF_8);
      assertThat(arquivo).contains("000123;42;3;1,01;10,00;1234567,80;-2,35;");
    }
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.gerarArquivo("xlsx", dados, decimais).conteudo()))) {
      var row = workbook.getSheetAt(0).getRow(1);
      assertThat(row.getCell(0).getStringCellValue()).isEqualTo("000123");
      assertThat(row.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0");
      assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(1.01);
      assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(10);
      assertThat(row.getCell(4).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
      assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(1234567.8);
      assertThat(row.getCell(5).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
      assertThat(row.getCell(7).getCellType()).isEqualTo(CellType.BLANK);
    }
    // Outros relatórios mantêm a inferência e a precisão existentes.
    assertThat(new String(service.gerarArquivo("csv", List.of(new LinkedHashMap<>(Map.of("VALOR", 1.23456)))).conteudo(), StandardCharsets.UTF_8))
        .contains("1,23456");
  }
}
