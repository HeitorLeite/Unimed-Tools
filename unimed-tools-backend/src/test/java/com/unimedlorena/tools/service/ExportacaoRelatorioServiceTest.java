package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExportacaoRelatorioServiceTest {

  private final ExportacaoRelatorioService service =
    new ExportacaoRelatorioService(null, 1000, 10);

  @Test
  void deveGerarXlsxComDatasNumerosETextosTipados() throws Exception {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("COD_BENEFICIARIO", "001234");
    registro.put("VALOR_TOTAL", "1234,56");
    registro.put("QUANTIDADE", 2);
    registro.put("DATA_GUIA", "10/08/2026");
    registro.put("NOME", "Pessoa de teste");
    registro.put("OBSERVACAO", "=2+2");

    var arquivo = service.gerarArquivo("xlsx", List.of(registro));

    assertThat(arquivo.quantidadeRegistros()).isEqualTo(1);

    try (
      XSSFWorkbook workbook = new XSSFWorkbook(
        new ByteArrayInputStream(arquivo.conteudo())
      )
    ) {
      var linha = workbook.getSheetAt(0).getRow(1);

      assertThat(linha.getCell(0).getCellType()).isEqualTo(CellType.STRING);
      assertThat(linha.getCell(0).getStringCellValue()).isEqualTo("001234");
      assertThat(linha.getCell(0).getCellStyle().getDataFormatString())
        .isEqualTo("@");

      assertThat(linha.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(linha.getCell(1).getNumericCellValue()).isEqualTo(1234.56);
      assertThat(linha.getCell(1).getCellStyle().getDataFormatString())
        .isEqualTo("#,##0.00########");

      assertThat(linha.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(linha.getCell(2).getNumericCellValue()).isEqualTo(2);
      assertThat(linha.getCell(2).getCellStyle().getDataFormatString())
        .isEqualTo("#,##0");

      assertThat(linha.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(linha.getCell(3).getLocalDateTimeCellValue().toLocalDate())
        .isEqualTo(LocalDate.of(2026, 8, 10));
      assertThat(linha.getCell(3).getCellStyle().getDataFormatString())
        .isEqualTo("dd/mm/yyyy");

      assertThat(linha.getCell(4).getCellType()).isEqualTo(CellType.STRING);
      assertThat(linha.getCell(5).getCellType()).isEqualTo(CellType.STRING);
      assertThat(linha.getCell(5).getStringCellValue()).isEqualTo("=2+2");
    }
  }

  @Test
  void devePreservarCodigosNumericosComoTextoNoXlsx() throws Exception {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("CODIGO_EMPRESA", 123L);
    registro.put("PERIODO", 202608);

    var arquivo = service.gerarArquivo("xlsx", List.of(registro));

    try (
      XSSFWorkbook workbook = new XSSFWorkbook(
        new ByteArrayInputStream(arquivo.conteudo())
      )
    ) {
      var linha = workbook.getSheetAt(0).getRow(1);
      assertThat(linha.getCell(0).getCellType()).isEqualTo(CellType.STRING);
      assertThat(linha.getCell(0).getStringCellValue()).isEqualTo("123");
      assertThat(linha.getCell(1).getCellType()).isEqualTo(CellType.STRING);
      assertThat(linha.getCell(1).getStringCellValue()).isEqualTo("202608");
    }
  }

  @Test
  void deveFormatarCsvParaExcelENeutralizarFormula() throws Exception {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("COD_BENEFICIARIO", "001234");
    registro.put("VALOR_TOTAL", "1234,56");
    registro.put("DATA_GUIA", "2026-08-10");
    registro.put("OBSERVACAO", "=2+2");

    var arquivo = service.gerarArquivo("csv", List.of(registro));
    String conteudo = new String(arquivo.conteudo(), StandardCharsets.UTF_8);

    assertThat(conteudo).startsWith("\uFEFF");
    assertThat(conteudo).contains(
      "COD_BENEFICIARIO;VALOR_TOTAL;DATA_GUIA;OBSERVACAO\r\n"
    );
    assertThat(conteudo).contains("001234;1234,56;10/08/2026;'=2+2\r\n");
  }

  @Test
  void deveFormatarTxtComTabulacaoEValoresNormalizados() throws Exception {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("codigoBeneficiario", "001234");
    registro.put("valorTotal", "1.234,56");
    registro.put("dataPagamento", "2026-08-10 00:00:00");
    registro.put("@OBSERVACAO", "  =2+2");

    var arquivo = service.gerarArquivo("txt", List.of(registro));
    String conteudo = new String(arquivo.conteudo(), StandardCharsets.UTF_8);

    assertThat(conteudo).startsWith("\uFEFF");
    assertThat(conteudo).contains(
      "codigoBeneficiario\tvalorTotal\tdataPagamento\t'@OBSERVACAO\r\n"
    );
    assertThat(conteudo).contains(
      "001234\t1234,56\t10/08/2026\t'  =2+2\r\n"
    );
  }

  @Test
  void deveManterColunaInconsistenteComoTexto() throws Exception {
    LinkedHashMap<String, Object> primeiro = new LinkedHashMap<>();
    primeiro.put("VALOR_TOTAL", "123,45");
    LinkedHashMap<String, Object> segundo = new LinkedHashMap<>();
    segundo.put("VALOR_TOTAL", "não informado");

    var arquivo = service.gerarArquivo("xlsx", List.of(primeiro, segundo));

    try (
      XSSFWorkbook workbook = new XSSFWorkbook(
        new ByteArrayInputStream(arquivo.conteudo())
      )
    ) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(1).getCell(0).getCellType())
        .isEqualTo(CellType.STRING);
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
        .isEqualTo("123,45");
      assertThat(sheet.getRow(2).getCell(0).getCellType())
        .isEqualTo(CellType.STRING);
    }
  }

  @Test
  void deveAceitarDatasVaziasAoCombinarRelatoriosAutomaticos()
    throws Exception {
    LinkedHashMap<String, Object> primeiro = new LinkedHashMap<>();
    primeiro.put("DATA_PAGAMENTO", "");
    primeiro.put("NOME", "Primeiro relatório");

    LinkedHashMap<String, Object> segundo = new LinkedHashMap<>();
    segundo.put("DATA_PAGAMENTO", "10/08/2026");
    segundo.put("NOME", "Segundo relatório");

    LinkedHashMap<String, Object> terceiro = new LinkedHashMap<>();
    terceiro.put("NOME", "Terceiro relatório");

    List<LinkedHashMap<String, Object>> registros = List.of(
      primeiro,
      segundo,
      terceiro
    );

    for (String formato : List.of("xlsx", "csv", "txt")) {
      var arquivo = service.gerarArquivo(formato, registros);
      assertThat(arquivo.conteudo()).isNotEmpty();
    }
  }
}
