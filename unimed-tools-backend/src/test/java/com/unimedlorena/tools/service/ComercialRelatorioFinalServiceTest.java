package com.unimedlorena.tools.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unimedlorena.tools.dto.ComercialRelatorioFinalRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ComercialRelatorioFinalServiceTest {

  private ExportacaoRelatorioService exportacao;
  private ComercialRelatorioFinalService service;

  @BeforeEach
  void setUp() {
    exportacao = Mockito.mock(ExportacaoRelatorioService.class);
    service = new ComercialRelatorioFinalService(exportacao);
  }

  @Test
  void usaHistoricoDoXlsxEConsultaSomenteOMesAtual() throws Exception {
    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_RECEITA), anyMap()
    )).thenReturn(List.of(
      linha(
        "TIPO", "Cobrança normal de mensalidade",
        "VALOR_TOTAL", BigDecimal.valueOf(600),
        "REGIAO_BENEF", "Vale do Paraiba",
        "CODIGO_BENEFICIARIO", "090.2152.000001.00"
      ),
      linha(
        "TIPO", "Mensalidade retroativa",
        "VALOR_TOTAL", BigDecimal.valueOf(125.98),
        "REGIAO_BENEF", "Sudeste",
        "CODIGO_BENEFICIARIO", "090.2152.000002.00"
      ),
      linha(
        "TIPO", "Taxa administrativa",
        "VALOR_TOTAL", BigDecimal.valueOf(9999),
        "REGIAO_BENEF", "Sudeste",
        "CODIGO_BENEFICIARIO", "IGNORAR"
      )
    ));

    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()
    )).thenReturn(List.of(
      linha(
        "DESCRICAO_TIPO_GUIA", "Consulta",
        "GRUPO_PRESTADOR", "RECURSO PROPRIO",
        "CODIGO_BENEFICIARIO", "090.2152.000001.00",
        "NOME_ESPECIALIDADE", "CLINICO",
        "DESCRICAO_ITEM", "CONSULTA",
        "REGIAO_BENEF", "Vale do Paraiba",
        "ATIVO", "S",
        "VALOR_TOTAL", BigDecimal.valueOf(100),
        "VALOR_TOTAL_21", BigDecimal.valueOf(121)
      ),
      linha(
        "DESCRICAO_TIPO_GUIA", "Exame",
        "GRUPO_PRESTADOR", "SESSOES MULTI",
        "CODIGO_BENEFICIARIO", "090.2152.000002.00",
        "NOME_ESPECIALIDADE", "PSICOLOGIA",
        "DESCRICAO_ITEM", "Sessao de Psicologia/Psicoterapia",
        "REGIAO_BENEF", "Sudeste",
        "ATIVO", "S",
        "VALOR_TOTAL", BigDecimal.valueOf(500),
        "VALOR_TOTAL_21", BigDecimal.valueOf(630)
      )
    ));

    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_BENEFICIARIOS), anyMap()
    )).thenReturn(List.of(
      linha(
        "COD_BENEFICIARIO", "090.2152.000001.00",
        "DATA_CADASTRO", "01/01/2026",
        "DATA_EXCLUSAO", "",
        "ATIVO", "S"
      ),
      linha(
        "COD_BENEFICIARIO", "090.2152.000002.00",
        "DATA_CADASTRO", "01/01/2026",
        "DATA_EXCLUSAO", "15/08/2026",
        "ATIVO", "N"
      ),
      linha(
        "COD_BENEFICIARIO", "090.2152.000003.00",
        "DATA_CADASTRO", "01/09/2026",
        "DATA_EXCLUSAO", "",
        "ATIVO", "S"
      )
    ));

    when(exportacao.carregarFaixaEtaria(org.mockito.ArgumentMatchers.anyList()))
      .thenReturn(List.of(
        linha("FAIXA_ETARIA", "0 a 18", "DEP", 2, "TIT", 1, "FEM", 2, "MASC", 1, "TOTAL", 3)
      ));

    byte[] arquivo = service.gerar(request(), historicoAnterior());

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(arquivo))) {
      var sheet = workbook.getSheet("082026");
      assertNotNull(sheet);
      assertNotNull(workbook.getSheet("072026"));
      assertNotNull(workbook.getSheet("082025"));
      assertEquals("082026", workbook.getSheetAt(0).getSheetName());

      // Histórico: julho vem do arquivo anterior; agosto é a única competência nova.
      assertEquals(1110d, sheet.getRow(12).getCell(1).getNumericCellValue());
      assertEquals(725.98d, sheet.getRow(13).getCell(1).getNumericCellValue(), 0.001);
      assertEquals(751d, sheet.getRow(13).getCell(2).getNumericCellValue(), 0.001);

      // A população vigente exclui cadastros encerrados e futuros.
      assertEquals(1d, sheet.getRow(18).getCell(1).getNumericCellValue());
      assertEquals(0d, sheet.getRow(18).getCell(2).getNumericCellValue());

      // Vale do Paraíba e Sudeste ficam separados.
      assertEquals(1d, sheet.getRow(89).getCell(1).getNumericCellValue());
      assertEquals(125.98d, sheet.getRow(89).getCell(5).getNumericCellValue(), 0.001);
      assertEquals(630d, sheet.getRow(89).getCell(3).getNumericCellValue(), 0.001);

      assertEquals(1d, sheet.getRow(94).getCell(1).getNumericCellValue());
      assertEquals(600d, sheet.getRow(94).getCell(5).getNumericCellValue(), 0.001);
      assertEquals(121d, sheet.getRow(94).getCell(3).getNumericCellValue(), 0.001);

      // Sessões Multi não podem migrar para Outros.
      assertEquals(630d, sheet.getRow(80).getCell(5).getNumericCellValue(), 0.001);

      // Código 2152 permanece ativo mesmo com exclusão informada na base.
      assertEquals("090.2152.000002.00", sheet.getRow(113).getCell(1).getStringCellValue());
      assertEquals("ATIVO", sheet.getRow(113).getCell(2).getStringCellValue());

      assertEquals(CellType.FORMULA, sheet.getRow(14).getCell(1).getCellType());

      // A aba 082026 já existia no arquivo anterior. O gráfico dela deve ser
      // preservado, sem remover a aba e recriar chart*.xml.
      assertEquals(1, sheet.getDrawingPatriarch().getCharts().size());
      assertEquals(
        1,
        workbook.getSheet("072026").getDrawingPatriarch().getCharts().size()
      );
    }

    verify(exportacao, times(1)).carregarRegistros(
      eq(ComercialRelatorioFinalService.API_RECEITA), anyMap()
    );
    verify(exportacao, times(1)).carregarRegistros(
      eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()
    );

    ArgumentCaptor<Map<String, Object>> filtros = ArgumentCaptor.forClass(Map.class);
    verify(exportacao).carregarRegistros(
      eq(ComercialRelatorioFinalService.API_RECEITA), filtros.capture()
    );
    assertEquals(202608, ((Number) filtros.getValue().get("competencia")).intValue());
  }

  @Test
  void exigeArquivoAnterior() {
    assertThrows(IllegalArgumentException.class, () -> service.gerar(request()));
    assertThrows(IllegalArgumentException.class, () -> service.gerar(request(), new byte[0]));
  }

  @Test
  void reconheceCodigoCartaoEDeduplicaVidasDaReceita() throws Exception {
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_RECEITA), anyMap()))
      .thenReturn(List.of(
        linha("tipo", "Mensalidade", "valor_total", "10,25", "regiao_benef", "Vale do Paraiba",
          "codigo_cartao", "090.2152.000001.00", "codigo", ""),
        linha("tipo", "Mensalidade", "valor_total", "20,50", "regiao_benef", "Vale do Paraiba",
          "codigo_cartao", "090.2152.000001.00", "codigo", "")
      ));
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerar(request(), historicoAnterior())))) {
      var sheet = wb.getSheet("082026");
      assertEquals(30.75, sheet.getRow(13).getCell(1).getNumericCellValue(), 0.001);
      assertEquals(1, sheet.getRow(94).getCell(1).getNumericCellValue());
    }
  }

  @Test
  void rejeitaErroEmValorHistoricoSemSubstituirPorZero() throws Exception {
    byte[] historico;
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(historicoAnterior()));
         var out = new ByteArrayOutputStream()) {
      var cell = wb.getSheet("072026").getRow(12).getCell(1);
      cell.setCellFormula("1/0");
      wb.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
      wb.write(out);
      historico = out.toByteArray();
    }
    var erro = assertThrows(IllegalArgumentException.class, () -> service.gerar(request(), historico));
    org.junit.jupiter.api.Assertions.assertTrue(erro.getMessage().contains("072026!B13"));
  }

  @Test
  void exigeValorTotal21DaDespesa() throws Exception {
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()))
      .thenReturn(List.of(linha("valor_total", "123,45")));
    var erro = assertThrows(IllegalArgumentException.class, () -> service.gerar(request(), historicoAnterior()));
    org.junit.jupiter.api.Assertions.assertTrue(erro.getMessage().contains("VALOR_TOTAL_21"));
  }

  @Test
  void classificaPeloCodigoSomenteAPopulacaoVigente() throws Exception {
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()))
      .thenReturn(List.of(linha("cod_beneficiario", "090.9152.000002.00", "ativo", "S",
        "valor_total_21", "10,25", "descricao_tipo_guia", "CONSULTA")));
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_BENEFICIARIOS), anyMap()))
      .thenReturn(List.of(
        linha("cod_beneficiario", "090.0045.000001.00", "ativo", "S", "data_cadastro", "01/01/2026"),
        linha("cod_beneficiario", "090.0045.000001.00", "ativo", "S"),
        linha("cod_beneficiario", "090.0045.000002.00", "ativo", "N", "data_exclusao", "01/01/2020"),
        linha("cod_beneficiario", "090.5045.000001.00", "ativo", "S"),
        linha("cod_beneficiario", "090.9152.000001.00", "ativo", "S", "data_cadastro", "01/01/2027")
      ));
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerar(request(), historicoAnterior())))) {
      assertEquals(1, wb.getSheet("082026").getRow(18).getCell(1).getNumericCellValue());
      assertEquals(1, wb.getSheet("082026").getRow(18).getCell(2).getNumericCellValue());
      assertEquals(2, wb.getSheet("082026").getRow(13).getCell(4).getNumericCellValue());
      assertEquals("INATIVO", wb.getSheet("082026").getRow(113).getCell(2).getStringCellValue());
    }
  }

  @Test
  void distribuiDespesaPorPrestadorERateiaCentralSemDuplicarLocalOuHomeCare() throws Exception {
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()))
      .thenReturn(List.of(
        linha("regiao_prest", "LOCAL", "regiao_benef", "Sudeste", "valor_total_21", "100,25"),
        linha("regiao_prest", "Sudeste", "regiao_benef", "Sudeste", "nome_prestador", "CENTRAL NACIONAL TESTE", "valor_total_21", "25,50"),
        linha("regiao_prest", "Sudeste", "regiao_benef", "Vale do Paraiba", "valor_total_21", "10,10"),
        linha("regiao_prest", "", "regiao_benef", "Vale do Paraiba", "valor_total_21", "5,15"),
        linha("regiao_prest", "LOCAL", "regiao_benef", "Vale do Paraiba", "grupo_prestador", "HOME CARE", "valor_total_21", "7,05")
      ));
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerar(request(), historicoAnterior())))) {
      var sheet = wb.getSheet("082026");
      var eval = wb.getCreationHelper().createFormulaEvaluator();
      assertEquals(25.50, sheet.getRow(88).getCell(3).getNumericCellValue(), 0.001);
      assertEquals(35.60, sheet.getRow(89).getCell(3).getNumericCellValue(), 0.001);
      assertEquals(81.80, sheet.getRow(95).getCell(3).getNumericCellValue(), 0.001);
      assertEquals(25.50, sheet.getRow(89).getCell(8).getNumericCellValue(), 0.001);
      assertEquals(61.10, eval.evaluate(sheet.getRow(89).getCell(9)).getNumberValue(), 0.001);
      assertEquals(86.95, eval.evaluate(sheet.getRow(94).getCell(9)).getNumberValue(), 0.001);
      assertEquals(148.05, eval.evaluate(sheet.getRow(95).getCell(9)).getNumberValue(), 0.001);
      // O segundo cabeçalho é Região na coluna A, não o subtítulo Sinistro por Região.
      assertEquals(145.60, sheet.getRow(102).getCell(3).getNumericCellValue(), 0.001);
    }
  }

  @Test
  void mantemReceitaComCopartConsistenteNosIndicadores() throws Exception {
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_RECEITA), anyMap()))
      .thenReturn(List.of(linha("TIPO", "Mensalidade", "VALOR_TOTAL", "100,00"),
        linha("TIPO", "Coparticipação", "VALOR_TOTAL", "20,00")));
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerar(request(), historicoAnterior())))) {
      var sheet = wb.getSheet("082026");
      assertEquals(100, sheet.getRow(13).getCell(1).getNumericCellValue());
      assertEquals(20, sheet.getRow(13).getCell(3).getNumericCellValue());
      assertEquals(120, sheet.getRow(18).getCell(4).getNumericCellValue());
      assertEquals(120, sheet.getRow(48).getCell(1).getNumericCellValue());
    }
  }

  @Test
  void abreCompetenciaNoInicioMesmoComRolagemSalvaNoModelo() throws Exception {
    byte[] modelo;
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(historicoAnterior()));
         var out = new ByteArrayOutputStream()) {
      var sheet = wb.getSheet("082026");
      sheet.showInPane(207, 0);
      sheet.getCTWorksheet().getSheetViews().getSheetViewArray(0).setTopLeftCell("A208");
      sheet.setActiveCell(new org.apache.poi.ss.util.CellAddress("A208"));
      wb.setActiveSheet(wb.getSheetIndex("072026"));
      wb.getSheet("072026").showInPane(99, 0);
      wb.getSheet("072026").getCTWorksheet().getSheetViews().getSheetViewArray(0).setTopLeftCell("A100");
      wb.write(out);
      modelo = out.toByteArray();
    }
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerar(request(), modelo)))) {
      var sheet = wb.getSheet("082026");
      assertEquals(0, sheet.getTopRow());
      assertEquals("A1", sheet.getCTWorksheet().getSheetViews().getSheetViewArray(0).getTopLeftCell());
      assertEquals("A2", sheet.getActiveCell().formatAsString());
      assertEquals(wb.getSheetIndex("082026"), wb.getActiveSheetIndex());
      assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
      assertEquals("A100", wb.getSheet("072026").getCTWorksheet().getSheetViews().getSheetViewArray(0).getTopLeftCell());
    }
  }

  @Test
  void atualizaFaixasECachesDoGraficoLegadoSemDeslocarDuasVezes() throws Exception {
    byte[] legado;
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(historicoAnterior()));
         var out = new ByteArrayOutputStream()) {
      var sheet = wb.getSheet("082026");
      sheet.createRow(0).createCell(0).setCellValue("Comp");
      sheet.createRow(12).createCell(0).setCellValue("ago-26");
      sheet.getRow(12).createCell(1).setCellValue(9999);
      var chart = sheet.getDrawingPatriarch().getCharts().getFirst();
      var cat = chart.createCategoryAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.BOTTOM);
      var val = chart.createValueAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.LEFT);
      var data = chart.createData(org.apache.poi.xddf.usermodel.chart.ChartTypes.BAR, cat, val);
      data.addSeries(
        org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromStringCellRange(sheet, new org.apache.poi.ss.util.CellRangeAddress(12,12,0,0)),
        org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromNumericCellRange(sheet, new org.apache.poi.ss.util.CellRangeAddress(12,12,1,1)));
      data.addSeries(
        org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromStringCellRange(sheet, new org.apache.poi.ss.util.CellRangeAddress(12,12,0,0)),
        org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromNumericCellRange(sheet, new org.apache.poi.ss.util.CellRangeAddress(12,12,1,1)));
      chart.plot(data);
      // O gráfico de acumulado do modelo possui série sem categorias explícitas.
      chart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(1).unsetCat();
      wb.write(out);
      legado = out.toByteArray();
    }
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_RECEITA), anyMap()))
      .thenReturn(List.of(linha("TIPO", "Mensalidade", "VALOR_TOTAL", "100,00")));
    for (int i = 0; i < 2; i++) {
      legado = service.gerar(request(), legado);
      try (var wb = new XSSFWorkbook(new ByteArrayInputStream(legado))) {
        var serie = wb.getSheet("082026").getDrawingPatriarch().getCharts().getFirst().getChartSeries().getFirst().getSeries().getFirst();
        assertEquals("'082026'!$B$14", serie.getValuesData().getDataRangeReference());
        assertEquals(100d, serie.getValuesData().getPointAt(0).doubleValue());
        assertEquals(CellType.NUMERIC, wb.getSheet("082026").getRow(15).getCell(1).getCachedFormulaResultType());
      }
    }
  }

  @Test
  void rejeitaBeneficiarioSemCodigoClassificavel() throws Exception {
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_BENEFICIARIOS), anyMap()))
      .thenReturn(List.of(linha("cod_beneficiario", "")));
    assertThrows(IllegalArgumentException.class, () -> service.gerar(request(), historicoAnterior()));
  }

  @Test
  void reconstroiCompetenciaAntesDeClassificarCarteirinhaSemUsarStatusAtual() throws Exception {
    when(exportacao.carregarRegistros(eq(ComercialRelatorioFinalService.API_BENEFICIARIOS), anyMap()))
      .thenReturn(List.of(
        linha("cod_beneficiario", "090.2152.000001.00", "ativo", "N", "data_cadastro", "01/01/2026", "data_exclusao", "01/09/2026"),
        linha("cod_beneficiario", "090.9152.000001.00", "ativo", "N", "data_cadastro", "01/01/2026", "data_exclusao", "01/09/2026"),
        linha("cod_beneficiario", "090.2152.000002.00", "ativo", "S", "data_cadastro", "01/09/2026"),
        linha("cod_beneficiario", "090.2152.000003.00", "ativo", "S", "data_cadastro", "01/01/2026", "data_exclusao", "31/08/2026"),
        linha("cod_beneficiario", "090.2152.000004.00", "ativo", "S", "data_cadastro", "31/08/2026")
      ));
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerar(request(), historicoAnterior())))) {
      assertEquals(2, wb.getSheet("082026").getRow(18).getCell(1).getNumericCellValue());
      assertEquals(1, wb.getSheet("082026").getRow(18).getCell(2).getNumericCellValue());
      assertEquals(3, wb.getSheet("082026").getRow(13).getCell(4).getNumericCellValue());
    }
  }

  @Test
  void rejeitaHistoricoSemAbaDoMesAnterior() throws Exception {
    try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      wb.createSheet("062026");
      wb.write(out);
      assertThrows(IllegalArgumentException.class, () -> service.gerar(request(), out.toByteArray()));
    }
  }

  @Test
  void leHistoricoComTabelasEmLinhasDiferentesComoModeloYakult() throws Exception {
    byte[] modelo;
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(historicoAnterior()));
         var out = new ByteArrayOutputStream()) {
      var sheet = wb.getSheet("072026");
      sheet.shiftRows(68, 80, -2);
      sheet.shiftRows(52, 64, -2);
      sheet.shiftRows(1, 13, -1);
      for (int r = 86; r <= 108; r++) {
        if (sheet.getRow(r) != null) sheet.removeRow(sheet.getRow(r));
      }
      var excluida = wb.getSheet("082025");
      for (int r = 86; r <= 95; r++) {
        if (excluida.getRow(r) != null) excluida.removeRow(excluida.getRow(r));
      }
      sheet.createRow(110).createCell(0).setCellValue("MAIORES CUSTOS BENEFICIARIOS ACUMULADO");
      sheet.createRow(111).createCell(1).setCellValue("Cód. Beneficiário");
      sheet.getRow(111).createCell(2).setCellValue("Sinistro");
      sheet.createRow(112).createCell(1).setCellValue("090.2128.000001.00");
      sheet.getRow(112).createCell(2).setCellValue(123.45);
      wb.write(out);
      modelo = out.toByteArray();
    }
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerar(request(), modelo)))) {
      var sheet = wb.getSheet("082026");
      assertEquals(1010d, sheet.getRow(2).getCell(1).getNumericCellValue());
      assertEquals(810d, sheet.getRow(2).getCell(2).getNumericCellValue());
      assertEquals(100d, sheet.getRow(53).getCell(1).getNumericCellValue());
      assertEquals(100d, sheet.getRow(69).getCell(1).getNumericCellValue());
      assertEquals(1110d, sheet.getRow(12).getCell(1).getNumericCellValue());
      assertEquals(true, sheet.getRow(98).getCell(0).getStringCellValue().contains("indisponível"));
      assertEquals("090.2128.000001.00", sheet.getRow(126).getCell(1).getStringCellValue());
    }
  }

  private byte[] historicoAnterior() throws Exception {
    try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      // Reproduz o arquivo real: a competência que será recalculada já existe
      // e possui gráficos, assim como abas históricas posteriores na numeração
      // interna dos chart*.xml.
      var agosto26 = wb.createSheet("082026");
      adicionarGrafico(agosto26, "Gráfico agosto");
      agosto26.createRow(18).createCell(1).setCellValue(999999d);

      var julho = wb.createSheet("072026");
      adicionarGrafico(julho, "Gráfico julho");
      var agosto25 = wb.createSheet("082025");

      LocalDate inicio = LocalDate.of(2025, 8, 1);
      for (var cabecalho : java.util.Map.of(1, "Receita", 52, "Consultas", 68, "Recurso Próprio").entrySet()) {
        var row = julho.createRow(cabecalho.getKey());
        row.createCell(0).setCellValue("Comp");
        row.createCell(1).setCellValue(cabecalho.getValue());
      }
      for (int i = 0; i < 12; i++) {
        LocalDate mes = inicio.plusMonths(i);
        int rResumo = 2 + i;
        julho.createRow(rResumo).createCell(0).setCellValue(mes);
        julho.getRow(rResumo).createCell(1).setCellValue(1000 + i * 10);
        julho.getRow(rResumo).createCell(2).setCellValue(800 + i * 10);
        julho.getRow(rResumo).createCell(3).setCellValue(50);
        julho.getRow(rResumo).createCell(4).setCellValue(1600 + i);

        int rTipo = 53 + i;
        julho.createRow(rTipo).createCell(0).setCellValue(mes);
        julho.getRow(rTipo).createCell(1).setCellValue(100);
        julho.getRow(rTipo).createCell(2).setCellValue(200);
        julho.getRow(rTipo).createCell(3).setCellValue(300);
        julho.getRow(rTipo).createCell(4).setCellValue(200);

        int rGrupo = 69 + i;
        julho.createRow(rGrupo).createCell(0).setCellValue(mes);
        for (int c = 1; c <= 7; c++) julho.getRow(rGrupo).createCell(c).setCellValue(100);
      }

      // Primeira e segunda ocorrências da tabela regional.
      tabelaRegional(julho, 86, 10, 20);
      tabelaRegional(julho, 99, 120, 240);
      tabelaRegional(agosto25, 86, 10, 20);
      julho.getRow(88).createCell(9).setCellValue("Sinistro por Região (R$)");

      // Indicador sem receita, como nas abas do modelo legado. A busca por
      // títulos deve atravessar a fórmula com erro sem tentar lê-la como número.
      var indicador = julho.getRow(95).createCell(10);
      indicador.setCellFormula("1/0");
      wb.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(indicador);

      wb.write(out);
      return out.toByteArray();
    }
  }

  private void adicionarGrafico(
    org.apache.poi.xssf.usermodel.XSSFSheet sheet,
    String titulo
  ) {
    var drawing = sheet.createDrawingPatriarch();
    var anchor = drawing.createAnchor(0, 0, 0, 0, 10, 1, 15, 10);
    var chart = drawing.createChart(anchor);
    chart.setTitleText(titulo);
    chart.setTitleOverlay(false);
  }

  private void tabelaRegional(
    org.apache.poi.ss.usermodel.Sheet sheet,
    int headerRow,
    double sinistroBase,
    double receitaBase
  ) {
    var header = sheet.getRow(headerRow);
    if (header == null) header = sheet.createRow(headerRow);
    header.createCell(0).setCellValue("Região");

    String[] regioes = {
      "Módulo Coração", "Central Nacional", "Sudeste (Fora Vale)", "Centro Oeste",
      "Nordeste", "Norte", "Sul", "Vale do Paraiba", "Local"
    };
    for (int i = 0; i < regioes.length; i++) {
      var row = sheet.createRow(headerRow + 1 + i);
      row.createCell(0).setCellValue(regioes[i]);
      row.createCell(3).setCellValue(sinistroBase + i);
      row.createCell(5).setCellValue(receitaBase + i);
    }
  }

  private ComercialRelatorioFinalRequest request() {
    Map<String, List<Map<String, Object>>> filtros = new LinkedHashMap<>();
    filtros.put(
      ComercialRelatorioFinalService.API_BENEFICIARIOS,
      List.of(Map.of("empresa", 1))
    );
    filtros.put(
      ComercialRelatorioFinalService.API_RECEITA,
      List.of(Map.of("empresa", 1, "competencia", 202608))
    );
    filtros.put(
      ComercialRelatorioFinalService.API_DESPESA,
      List.of(Map.of("empresa", 1, "competencia", 202608))
    );
    filtros.put(
      ComercialRelatorioFinalService.API_FAIXA,
      List.of(Map.of("empresa", 1, "datareferencia", "31/08/2026"))
    );
    return new ComercialRelatorioFinalRequest("EMPRESA TESTE", "202608", filtros);
  }

  private LinkedHashMap<String, Object> linha(Object... pares) {
    LinkedHashMap<String, Object> linha = new LinkedHashMap<>();
    for (int i = 0; i < pares.length; i += 2) {
      linha.put(String.valueOf(pares[i]), pares[i + 1]);
    }
    return linha;
  }
}
