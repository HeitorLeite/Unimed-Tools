package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.unimedlorena.tools.dto.RelatorioExportacaoRequest;
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
  void deveFormatarTxtComPontoEVirgulaEValoresNormalizados() throws Exception {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("codigoBeneficiario", "001234");
    registro.put("valorTotal", "1.234,56");
    registro.put("dataPagamento", "2026-08-10 00:00:00");
    registro.put("@OBSERVACAO", "  =2+2");
    registro.put("DESCRICAO", "texto; separado");

    var arquivo = service.gerarArquivo("txt", List.of(registro));
    String conteudo = new String(arquivo.conteudo(), StandardCharsets.UTF_8);

    assertThat(conteudo).startsWith("\uFEFF");
    assertThat(conteudo).contains(
      "codigoBeneficiario;valorTotal;dataPagamento;'@OBSERVACAO;DESCRICAO\r\n"
    );
    assertThat(conteudo).contains(
      "001234;1234,56;10/08/2026;'  =2+2;\"texto; separado\"\r\n"
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

  @Test
  void deveRemoverRnumDeTodosOsFormatos() throws Exception {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("NOME", "Linha válida");
    registro.put("rnum", 1);

    for (String formato : List.of("xlsx", "csv", "txt")) {
      var arquivo = service.gerarArquivo(formato, List.of(registro));

      if (formato.equals("xlsx")) {
        try (
          XSSFWorkbook workbook = new XSSFWorkbook(
            new ByteArrayInputStream(arquivo.conteudo())
          )
        ) {
          var cabecalho = workbook.getSheetAt(0).getRow(0);
          assertThat(cabecalho.getLastCellNum()).isEqualTo((short) 1);
          assertThat(cabecalho.getCell(0).getStringCellValue()).isEqualTo("NOME");
        }
      } else {
        String conteudo = new String(arquivo.conteudo(), StandardCharsets.UTF_8);
        assertThat(conteudo).doesNotContainIgnoringCase("rnum");
      }
    }
  }

  @Test
  void deveEncerrarSemTetoQuandoSguInformarUltimaPagina() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("NOME", "Teste");
    registro.put("RNUM", 1);

    when(sgu.executar(anyString(), anyMap()))
      .thenReturn(Map.of("content", List.of(registro), "last", true));

    var semTeto = new ExportacaoRelatorioService(sgu, 1, 0);
    var registros = semTeto.carregarRegistros("api-teste", Map.of());

    assertThat(registros).hasSize(1);
    assertThat(registros.get(0)).containsOnlyKeys("NOME");
  }

  @Test
  void naoAcusaLimiteQuandoUltimaPaginaCoincideComTetoConfigurado() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("NOME", "Teste");

    when(sgu.executar(anyString(), anyMap()))
      .thenReturn(Map.of("content", List.of(registro), "last", true));

    var limitado = new ExportacaoRelatorioService(sgu, 1, 1);

    assertThat(limitado.carregarRegistros("api-teste", Map.of())).hasSize(1);
  }

  @Test
  void deveTransmitirCsvPaginaPorPaginaSemRnum() throws Exception {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    LinkedHashMap<String, Object> primeiro = new LinkedHashMap<>();
    primeiro.put("NOME", "Primeiro");
    primeiro.put("RNUM", 1);
    LinkedHashMap<String, Object> segundo = new LinkedHashMap<>();
    segundo.put("NOME", "Segundo");
    segundo.put("RNUM", 2);
    LinkedHashMap<String, Object> terceiro = new LinkedHashMap<>();
    terceiro.put("NOME", "Terceiro");
    terceiro.put("RNUM", 3);

    when(sgu.executar(anyString(), anyMap()))
      .thenReturn(
        Map.of("content", List.of(primeiro, segundo), "last", false),
        Map.of("content", List.of(terceiro), "last", true)
      );

    var paginado = new ExportacaoRelatorioService(sgu, 2, 0);
    ByteArrayOutputStream destino = new ByteArrayOutputStream();
    paginado.exportarPara(
      "api-teste",
      "csv",
      new RelatorioExportacaoRequest(Map.of("competencia", 202607), "teste"),
      destino
    );

    String conteudo = destino.toString(StandardCharsets.UTF_8);
    assertThat(conteudo).contains("NOME\r\n");
    assertThat(conteudo).contains("Primeiro\r\n", "Segundo\r\n", "Terceiro\r\n");
    assertThat(conteudo).doesNotContainIgnoringCase("rnum");
    verify(sgu, times(2)).executar(anyString(), anyMap());
  }

  @Test
  void deveGerarXlsxPaginadoSemManterTodasAsPaginas() throws Exception {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    LinkedHashMap<String, Object> primeiro = new LinkedHashMap<>();
    primeiro.put("NOME", "Primeiro");
    primeiro.put("RNUM", 1);
    LinkedHashMap<String, Object> segundo = new LinkedHashMap<>();
    segundo.put("NOME", "Segundo");
    segundo.put("RNUM", 2);
    LinkedHashMap<String, Object> terceiro = new LinkedHashMap<>();
    terceiro.put("NOME", "Terceiro");
    terceiro.put("RNUM", 3);

    when(sgu.executar(anyString(), anyMap()))
      .thenReturn(
        Map.of("content", List.of(primeiro, segundo), "last", false),
        Map.of("content", List.of(terceiro), "last", true)
      );

    var paginado = new ExportacaoRelatorioService(sgu, 2, 0);
    ByteArrayOutputStream destino = new ByteArrayOutputStream();
    paginado.exportarPara(
      "api-teste",
      "xlsx",
      new RelatorioExportacaoRequest(Map.of(), "teste"),
      destino
    );

    try (
      XSSFWorkbook workbook = new XSSFWorkbook(
        new ByteArrayInputStream(destino.toByteArray())
      )
    ) {
      var planilha = workbook.getSheetAt(0);
      assertThat(planilha.getLastRowNum()).isEqualTo(3);
      assertThat(planilha.getRow(0).getLastCellNum()).isEqualTo((short) 1);
      assertThat(planilha.getRow(0).getCell(0).getStringCellValue())
        .isEqualTo("NOME");
      assertThat(planilha.getRow(3).getCell(0).getStringCellValue())
        .isEqualTo("Terceiro");
    }
    verify(sgu, times(2)).executar(anyString(), anyMap());
  }

  @Test
  void deveAplicarOrdemDeColunasESemCabecalhoNoArquivoAgendado() throws Exception {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("COLUNA_A", "primeiro");
    registro.put("COLUNA_B", "segundo");

    when(sgu.executar(anyString(), anyMap()))
      .thenReturn(Map.of("content", List.of(registro), "last", true));

    var paginado = new ExportacaoRelatorioService(sgu, 100, 0);
    ByteArrayOutputStream destino = new ByteArrayOutputStream();
    paginado.exportarPara(
      "api-teste",
      "csv",
      new RelatorioExportacaoRequest(Map.of(), "teste"),
      destino,
      new ExportacaoRelatorioService.OpcoesSaida(
        List.of("COLUNA_B", "COLUNA_A"),
        false
      )
    );

    String conteudo = destino.toString(StandardCharsets.UTF_8);
    assertThat(conteudo).startsWith("\uFEFFsegundo;primeiro\r\n");
    assertThat(conteudo).doesNotContain("COLUNA_A", "COLUNA_B");
  }
}
