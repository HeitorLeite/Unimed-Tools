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

      // Beneficiários são reconstruídos na data da competência:
      // um ativo, um já excluído e um cadastro futuro ignorado.
      assertEquals(1d, sheet.getRow(18).getCell(1).getNumericCellValue());
      assertEquals(1d, sheet.getRow(18).getCell(2).getNumericCellValue());

      // Vale do Paraíba e Sudeste ficam separados.
      assertEquals(1d, sheet.getRow(89).getCell(1).getNumericCellValue());
      assertEquals(125.98d, sheet.getRow(89).getCell(5).getNumericCellValue(), 0.001);
      assertEquals(630d, sheet.getRow(89).getCell(3).getNumericCellValue(), 0.001);

      assertEquals(1d, sheet.getRow(94).getCell(1).getNumericCellValue());
      assertEquals(600d, sheet.getRow(94).getCell(5).getNumericCellValue(), 0.001);
      assertEquals(121d, sheet.getRow(94).getCell(3).getNumericCellValue(), 0.001);

      // Sessões Multi não podem migrar para Outros.
      assertEquals(630d, sheet.getRow(80).getCell(5).getNumericCellValue(), 0.001);

      // Ranking usa a situação proveniente da base do beneficiário.
      assertEquals("090.2152.000002.00", sheet.getRow(113).getCell(1).getStringCellValue());
      assertEquals("INATIVO", sheet.getRow(113).getCell(2).getStringCellValue());

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
  void rejeitaHistoricoSemAbaDoMesAnterior() throws Exception {
    try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      wb.createSheet("062026");
      wb.write(out);
      assertThrows(IllegalArgumentException.class, () -> service.gerar(request(), out.toByteArray()));
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
