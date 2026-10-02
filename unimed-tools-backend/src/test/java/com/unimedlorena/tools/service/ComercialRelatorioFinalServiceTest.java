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
      int competenciaFiltro = ((Number) filtros.get("competencia")).intValue();
      return List.of(
        linha(
          "TIPO", "MENSALIDADE",
          "VALOR_TOTAL", BigDecimal.valueOf(1000 + competenciaFiltro % 100),
          "REGIAO", "Vale do Paraiba",
          "CODIGO_BENEFICIARIO", "090.2152.000001.00"
        ),
        linha(
          "TIPO", "MENSALIDADE RETROATIVA",
          "VALOR_TOTAL", BigDecimal.valueOf(200),
          "REGIAO", "Vale do Paraiba",
          "CODIGO_BENEFICIARIO", "090.9152.000002.00"
        ),
        linha(
          "TIPO", "COPARTICIPACAO",
          "VALOR_TOTAL", BigDecimal.valueOf(100)
        ),
        linha(
          "TIPO", "TAXA ADMINISTRATIVA",
          "VALOR_TOTAL", BigDecimal.valueOf(9000)
        )
      );
    });

    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()
    )).thenReturn(List.of(
      linha(
        "DESCRICAO_TIPO_GUIA", "Consulta",
        "GRUPO_PRESTADOR", "Recurso Próprio",
        "CODIGO_BENEFICIARIO", "090.2152.000001.00",
        "NOME_ESPECIALIDADE", "CLINICA MEDICA",
        "DESCRICAO_PROCEDIMENTO", "CONSULTA SINTETICA",
        "REGIAO", "Vale do Paraiba",
        "VALOR_TOTAL", BigDecimal.valueOf(400),
        "VALOR_TOTAL_21", BigDecimal.valueOf(420)
      ),
      linha(
        "DESCRICAO_TIPO_GUIA", "Exame",
        "GRUPO_PRESTADOR", "Sessões Multi",
        "CODIGO_BENEFICIARIO", "090.9152.000002.00",
        "NOME_ESPECIALIDADE", "PSICOLOGIA",
        "DESCRICAO_PROCEDIMENTO", "Descrição original do procedimento",
        "DESCRICAO_ITEM", "Sessao de Psicologia/Psicoterapia",
        "REGIAO", "Vale do Paraiba",
        "VALOR_TOTAL", BigDecimal.valueOf(600),
        "VALOR_TOTAL_21", BigDecimal.valueOf(630)
      )
    ));

    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_BENEFICIARIOS), anyMap()
    )).thenReturn(List.of(
      linha("CODIGO_BENEFICIARIO", "090.2152.000001.00", "STATUS", "INATIVO"),
      linha("CODIGO_BENEFICIARIO", "090.9152.000002.00", "STATUS", "ATIVO")
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

      // Receita ignora a taxa administrativa e mantém apenas mensalidades.
      assertEquals(
        1200d + Integer.parseInt(competencia.substring(4)),
        sheet.getRow(13).getCell(1).getNumericCellValue()
      );

      // Sinistro e rankings usam VALOR_TOTAL_21, nunca VALOR_TOTAL.
      assertEquals(420d, sheet.getRow(64).getCell(1).getNumericCellValue());
      assertEquals(630d, sheet.getRow(64).getCell(2).getNumericCellValue());
      assertEquals("090.9152.000002.00", sheet.getRow(113).getCell(1).getStringCellValue());
      assertEquals("INATIVO", sheet.getRow(113).getCell(2).getStringCellValue());
      assertEquals(630d, sheet.getRow(113).getCell(3).getNumericCellValue());
      assertEquals("PSICOLOGIA", sheet.getRow(113).getCell(7).getStringCellValue());

      // A tabela SADT também exibe a situação ao lado do código.
      assertEquals("090.9152.000002.00", sheet.getRow(176).getCell(1).getStringCellValue());
      assertEquals("INATIVO", sheet.getRow(176).getCell(2).getStringCellValue());

      // O bloco de Sessões Multi consome a descrição sintetizada do item.
      assertEquals(
        "Sessao de Psicologia/Psicoterapia",
        sheet.getRow(211).getCell(1).getStringCellValue()
      );

      assertEquals(CellType.FORMULA, sheet.getRow(14).getCell(1).getCellType());
      assertEquals(3, sheet.getDrawingPatriarch().getCharts().size());
    }
  }

  @Test
  void falhaSeDespesaNaoTrouxerValorTotal21() {
    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_RECEITA), anyMap()
    )).thenReturn(List.of(
      linha(
        "TIPO", "MENSALIDADE",
        "VALOR_TOTAL", 100,
        "CODIGO_BENEFICIARIO", "090.2152.000001.00"
      )
    ));
    when(exportacao.carregarRegistros(
      eq(ComercialRelatorioFinalService.API_DESPESA), anyMap()
    )).thenReturn(List.of(
      linha(
        "VALOR_TOTAL", 100,
        "DESCRICAO_TIPO_GUIA", "Consulta",
        "CODIGO_BENEFICIARIO", "090.2152.000001.00"
      )
    ));

    assertThrows(IllegalArgumentException.class, () -> service.gerar(request("202608")));
  }

  @Test
  void rejeitaReceitaEDespesaSemFiltroDeCompetencia() {
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
      List.of(Map.of("empresa", 1))
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
