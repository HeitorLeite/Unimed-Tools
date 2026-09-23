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
  void preservaInteirosCodigosNulosEArredondaValoresNosTresFormatos() throws Exception {
    var service = new ExportacaoRelatorioService(mock(SguRelatorioService.class), 1000, 0);
    var linha = new LinkedHashMap<String, Object>();
    linha.put("ID_GUIA", "000123");
    linha.put("IDADE", 42);
    linha.put("QUANTIDADE", 3);
    linha.put("VALOR_TOTAL", "1.005");
    linha.put("VALOR_TOTAL__202601", 10);
    linha.put("VALOR_RECEBER", "-2,345");
    linha.put("VALOR_FATOR", null);
    var dados = List.of(linha);
    var decimais = Set.of("VALOR_TOTAL", "VALOR_TOTAL__202601", "VALOR_RECEBER", "VALOR_FATOR");
    for (String formato : List.of("csv", "txt")) {
      String arquivo = new String(service.gerarArquivo(formato, dados, decimais).conteudo(), StandardCharsets.UTF_8);
      assertThat(arquivo).contains("000123;42;3;1,01;10,00;-2,35;");
    }
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.gerarArquivo("xlsx", dados, decimais).conteudo()))) {
      var row = workbook.getSheetAt(0).getRow(1);
      assertThat(row.getCell(0).getStringCellValue()).isEqualTo("000123");
      assertThat(row.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0");
      assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(1.01);
      assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(10);
      assertThat(row.getCell(4).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
      assertThat(row.getCell(6).getCellType()).isEqualTo(CellType.BLANK);
    }
    // Outros relatórios mantêm a inferência e a precisão existentes.
    assertThat(new String(service.gerarArquivo("csv", List.of(new LinkedHashMap<>(Map.of("VALOR", 1.23456)))).conteudo(), StandardCharsets.UTF_8))
        .contains("1,23456");
  }
}
