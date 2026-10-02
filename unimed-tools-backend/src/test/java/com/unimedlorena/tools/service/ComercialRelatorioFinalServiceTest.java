package com.unimedlorena.tools.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.unimedlorena.tools.dto.ComercialRelatorioFinalRequest;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class ComercialRelatorioFinalServiceTest {

  private ExportacaoRelatorioService exportacao;
  private ComercialRelatorioFinalService service;

  @BeforeEach
  void setUp() {
    exportacao = Mockito.mock(ExportacaoRelatorioService.class);
    service = new ComercialRelatorioFinalService(exportacao);
  }

  @ParameterizedTest
  @ValueSource(strings = {"202606", "202607", "202608"})
  void geraXlsxFormatadoComJanelaMovelRankingsEgraficos(String competencia) throws Exception {
    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_RECEITA), anyMap()
    )).thenAnswer(invocacao -> {
      Map<String, Object> filtros = invocacao.getArgument(1);
      int competencia = ((Number) filtros.get("competencia")).intValue();
      return List.of(
        linha(
          "TIPO", "MENSALIDADE",
          "VALOR_TOTAL", BigDecimal.valueOf(1000 + competencia % 100),
          "REGIAO", "Vale do Paraiba",
          "CODIGO_BENEFICIARIO", "B-001"
        ),
        linha(
          "TIPO", "COPARTICIPACAO",
          "VALOR_TOTAL", BigDecimal.valueOf(100)
        )
      );
    });

    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()
    )).thenReturn(List.of(
      linha(
        "DESCRICAO_TIPO_GUIA", "Consulta",
        "GRUPO_PRESTADOR", "Recurso Próprio",
        "CODIGO_BENEFICIARIO", "B-001",
        "NOME_ESPECIALIDADE", "CLINICA MEDICA",
        "DESCRICAO_PROCEDIMENTO", "CONSULTA SINTETICA",
        "REGIAO", "Vale do Paraiba",
        "VALOR_TOTAL", BigDecimal.valueOf(400)
      ),
      linha(
        "DESCRICAO_TIPO_GUIA", "Exame",
        "GRUPO_PRESTADOR", "Sessões Multi",
        "CODIGO_BENEFICIARIO", "B-002",
        "NOME_ESPECIALIDADE", "PSICOLOGIA",
        "DESCRICAO_PROCEDIMENTO", "SESSAO PSICOLOGIA",
        "REGIAO", "Vale do Paraiba",
        "VALOR_TOTAL", BigDecimal.valueOf(600)
      )
    ));

    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_BENEFICIARIOS), anyMap()
    )).thenReturn(List.of(
      linha("CODIGO_BENEFICIARIO", "B-001", "STATUS", "ATIVO"),
      linha("CODIGO_BENEFICIARIO", "B-002", "STATUS", "INATIVO")
    ));

    when(exportacao.carregarFaixaEtaria(org.mockito.ArgumentMatchers.anyList()))
      .thenReturn(List.of(
        linha("FAIXA_ETARIA", "0 a 18", "DEP", 2, "TIT", 1, "FEM", 2, "MASC", 1, "TOTAL", 3)
      ));

    byte[] arquivo = service.gerar(request(competencia));
    YearMonth alvo = YearMonth.parse(competencia, DateTimeFormatter.ofPattern("yyyyMM"));
    String nomeAba = String.format("%02d%04d", alvo.getMonthValue(), alvo.getYear());

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(arquivo))) {
      var sheet = workbook.getSheet(nomeAba);
      assertNotNull(sheet);
      assertEquals("RELATÓRIO DE SINISTRALIDADE — EMPRESA TESTE", sheet.getRow(0).getCell(0).getStringCellValue());

      assertEquals(alvo.minusMonths(11).atDay(1), sheet.getRow(2).getCell(0).getLocalDateTimeCellValue().toLocalDate());
      assertEquals(alvo.atDay(1), sheet.getRow(13).getCell(0).getLocalDateTimeCellValue().toLocalDate());
      assertEquals(1d, sheet.getRow(13).getCell(4).getNumericCellValue());
      assertEquals(1d, sheet.getRow(18).getCell(1).getNumericCellValue());
      assertEquals(1d, sheet.getRow(18).getCell(2).getNumericCellValue());

      assertEquals(400d, sheet.getRow(64).getCell(1).getNumericCellValue());
      assertEquals(600d, sheet.getRow(64).getCell(2).getNumericCellValue());
      assertEquals("B-002", sheet.getRow(113).getCell(1).getStringCellValue());
      assertEquals("PSICOLOGIA", sheet.getRow(113).getCell(6).getStringCellValue());

      assertEquals(CellType.FORMULA, sheet.getRow(14).getCell(1).getCellType());
      assertEquals(3, sheet.getDrawingPatriarch().getCharts().size());
    }
  }

  @Test
  void rejeitaApiSemFiltroDeCompetencia() {
    Map<String, List<Map<String, Object>>> filtros = new LinkedHashMap<>();
    filtros.put(ComercialRelatorioFinalService.API_BENEFICIARIOS, List.of(Map.of("empresa", 1)));
    filtros.put(ComercialRelatorioFinalService.API_RECEITA, List.of(Map.of("empresa", 1)));
    filtros.put(ComercialRelatorioFinalService.API_DESPESA, List.of(Map.of("empresa", 1)));
    filtros.put(ComercialRelatorioFinalService.API_FAIXA, List.of(Map.of(
      "empresa", 1, "datareferencia", "31/08/2026"
    )));

    var request = new ComercialRelatorioFinalRequest("EMPRESA TESTE", "202608", filtros);

    assertThrows(IllegalArgumentException.class, () -> service.gerar(request));
  }

  private ComercialRelatorioFinalRequest request(String competencia) {
    Map<String, List<Map<String, Object>>> filtros = new LinkedHashMap<>();
    filtros.put(
      ComercialRelatorioFinalService.API_BENEFICIARIOS,
      List.of(Map.of("empresa", 1, "competencia", Integer.valueOf(competencia)))
    );
    filtros.put(
      ComercialRelatorioFinalService.API_RECEITA,
      List.of(Map.of("empresa", 1, "competencia", Integer.valueOf(competencia)))
    );
    filtros.put(
      ComercialRelatorioFinalService.API_DESPESA,
      List.of(Map.of("empresa", 1, "competencia", Integer.valueOf(competencia)))
    );
    filtros.put(
      ComercialRelatorioFinalService.API_FAIXA,
      List.of(Map.of("empresa", 1, "datareferencia", "31/08/2026"))
    );
    return new ComercialRelatorioFinalRequest("EMPRESA TESTE", competencia, filtros);
  }

  private LinkedHashMap<String, Object> linha(Object... pares) {
    LinkedHashMap<String, Object> linha = new LinkedHashMap<>();
    for (int i = 0; i < pares.length; i += 2) {
      linha.put(String.valueOf(pares[i]), pares[i + 1]);
    }
    return linha;
  }
}
