package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.unimedlorena.tools.exception.ApiException;
import org.springframework.http.HttpStatus;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

  @Test
  void usaMetadadosReaisDoSguEPreservaRepeticoesLegitimas() {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(anyString(), anyMap())).thenReturn(
      Map.of("content", List.of(Map.of("ID", "A", "rnum", "1"), Map.of("ID", "A", "rnum", "2")), "numberOfElements", "4", "totalPage", "2"),
      Map.of("content", List.of(Map.of("ID", "A", "rnum", "3"), Map.of("ID", "A", "rnum", "4")), "numberOfElements", "4", "totalPage", "2"));
    when(sgu.listar(anyString())).thenReturn(apiOrdenada());
    assertThat(new ExportacaoRelatorioService(sgu, 2, 0).carregarRegistros("api-teste", Map.of())).hasSize(4);
    verify(sgu, times(2)).executar(anyString(), anyMap());
  }

  @Test
  void rejeitaPaginaTruncadaOuRespostaSemConteudo() {
    for (Map<String,Object> resposta : List.of(
        Map.<String,Object>of("success", true),
        Map.<String,Object>of("content", List.of("inválido")),
        Map.<String,Object>of("content", List.of(Map.of("ID", "A")), "totalPage", "1", "numberOfElements", "5"))) {
      var sgu = mock(SguRelatorioService.class);
      when(sgu.executar(anyString(), anyMap())).thenReturn(resposta);
      assertThatThrownBy(() -> new ExportacaoRelatorioService(sgu, 1000, 0).carregarRegistros("api-teste", Map.of()))
        .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void respeitaLastFalseMesmoQuandoPaginaMenorQueSolicitado() {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(anyString(), anyMap())).thenReturn(
      Map.of("content", List.of(Map.of("ID", "A")), "last", false),
      Map.of("content", List.of(Map.of("ID", "B")), "last", true));
    when(sgu.listar(anyString())).thenReturn(apiOrdenada());
    assertThat(new ExportacaoRelatorioService(sgu, 1000, 0).carregarRegistros("api-teste", Map.of())).hasSize(2);
  }

  @Test
  void naoMultiplicaDecimalDoSguPorMil() throws Exception {
    var registro = new LinkedHashMap<String,Object>();
    registro.put("VALOR_TOTAL", "1.005");
    assertThat(new String(service.gerarArquivo("csv", List.of(registro)).conteudo(), StandardCharsets.UTF_8))
      .contains("1,005\r\n");
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(service.gerarArquivo("xlsx", List.of(registro)).conteudo()))) {
      assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue()).isEqualTo(1.005);
    }
  }

  @Test
  void decimalNaPaginaSeguinteNaoEhArredondadoComoInteiro() throws Exception {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.executar(anyString(), anyMap())).thenAnswer(inv -> {
      int pagina = (int) ((Map<?,?>)inv.getArgument(1)).get("page");
      return Map.of("content", List.of(Map.of("VALOR", pagina == 1 ? "1" : "1.005")), "last", pagina == 2);
    });
    when(sgu.listar(anyString())).thenReturn(apiOrdenada());
    var paginado = new ExportacaoRelatorioService(sgu, 1, 0);
    for (String formato : List.of("csv", "txt", "xlsx")) {
      var buffer = new ByteArrayOutputStream();
      paginado.exportarPara("api-teste", formato, null, buffer);
      if (formato.equals("xlsx")) {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(buffer.toByteArray()))) {
          var cell = wb.getSheetAt(0).getRow(2).getCell(0);
          assertThat(cell.getNumericCellValue()).isEqualTo(1.005);
          assertThat(cell.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00########");
        }
      } else assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("1,005\r\n");
    }
  }

  private Map<String, Object> apiOrdenada() {
    return Map.of(
      "content",
      List.of(Map.of("nome", "api-teste", "ordenacao", "ID"))
    );
  }

  private ExportacaoRelatorioService semEspera(SguRelatorioService sgu) {
    return new ExportacaoRelatorioService(sgu, 1, 0) {
      @Override void aguardarNovaTentativa() {}
    };
  }

  @Test
  void deveRepetirSomentePaginaComFalhaSemDuplicarLinhas() throws Exception {
    for (HttpStatus status : List.of(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)) {
      SguRelatorioService sgu = mock(SguRelatorioService.class);
      var primeira = Map.<String, Object>of("content", List.of(Map.of("ID", "A")), "last", false);
      var segunda = Map.<String, Object>of("content", List.of(Map.of("ID", "B")), "last", true);
      when(sgu.executar(anyString(), anyMap())).thenReturn(primeira)
        .thenThrow(new ApiException(status, status == HttpStatus.GATEWAY_TIMEOUT ? "SGU_TIMEOUT" : "SGU_INDISPONIVEL", "Falha temporária."))
        .thenReturn(segunda);
      when(sgu.listar("api-teste")).thenReturn(apiOrdenada());
      var destino = new ByteArrayOutputStream();
      semEspera(sgu).exportarPara("api-teste", "xlsx", new RelatorioExportacaoRequest(Map.of(), "teste"), destino);
      try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(destino.toByteArray()))) {
        var sheet = workbook.getSheetAt(0);
        assertThat(sheet.getLastRowNum()).isEqualTo(2);
        assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("A");
        assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("B");
      }
      verify(sgu, times(1)).executar("api-teste", Map.of("page", 1, "size", 1));
      verify(sgu, times(2)).executar("api-teste", Map.of("page", 2, "size", 1));
    }
  }

  @Test
  void devePararAposUmaNovaTentativaDaPagina() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    var erro = new ApiException(HttpStatus.GATEWAY_TIMEOUT, "SGU_TIMEOUT", "Falha temporária.");
    when(sgu.executar(anyString(), anyMap())).thenThrow(erro);
    assertThatThrownBy(() -> semEspera(sgu).carregarRegistros("api-teste", Map.of())).isSameAs(erro);
    verify(sgu, times(2)).executar(anyString(), anyMap());
  }

  @Test
  void naoDeveRepetirErrosDeFiltroOuAutorizacao() {
    for (RuntimeException erro : List.of(new IllegalArgumentException("Filtro inválido."),
      new ApiException(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Acesso negado."))) {
      SguRelatorioService sgu = mock(SguRelatorioService.class);
      when(sgu.executar(anyString(), anyMap())).thenThrow(erro);
      assertThatThrownBy(() -> semEspera(sgu).carregarRegistros("api-teste", Map.of())).isSameAs(erro);
      verify(sgu, times(1)).executar(anyString(), anyMap());
    }
  }

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
      assertThat(workbook.getSheetAt(0).getCTWorksheet().isSetAutoFilter()).isFalse();
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
  void bloqueiaExportacaoMultipaginaSemOrdenacaoAntesDeConsumirPrimeiraPagina() {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("ID", "A");

    when(sgu.executar(anyString(), anyMap()))
      .thenReturn(Map.of("content", List.of(registro), "last", false));
    when(sgu.listar("api-teste")).thenReturn(
      Map.of("content", List.of(Map.of("nome", "api-teste", "ordenacao", "")))
    );

    var paginado = new ExportacaoRelatorioService(sgu, 1, 0);

    assertThatThrownBy(() -> paginado.carregarRegistros("api-teste", Map.of()))
      .isInstanceOf(ApiException.class)
      .hasMessageContaining("não possui ordenação estável")
      .hasMessageContaining("repetir linhas")
      .hasMessageContaining("omitir registros");

    verify(sgu, times(1)).executar("api-teste", Map.of("page", 1, "size", 1));
    verify(sgu, never()).executar("api-teste", Map.of("page", 2, "size", 1));
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
    when(sgu.listar("api-teste")).thenReturn(apiOrdenada());

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
    when(sgu.listar("api-teste")).thenReturn(apiOrdenada());

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
      assertThat(planilha.getCTWorksheet().isSetAutoFilter()).isFalse();
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
  void previaECsvTxtXlsxDevemUsarOMesmoGrupoPrestadorCorrigido() throws Exception {
    String api = "0090-despesa-empresas";
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    when(sgu.executar(anyString(), anyMap())).thenAnswer(ignorada -> Map.of(
      "content",
      List.of(registroGrupoPrestador()),
      "last",
      true
    ));
    when(sgu.listar(api)).thenReturn(Map.of(
      "content",
      List.of(Map.of(
        "nome", api,
        "ordenacao", "grupo_prestador",
        "consultaSQL", "SELECT grupo_prestador, nome_prestador, tipo_prestador FROM TESTE"
      ))
    ));
    var relatorios = new ExportacaoRelatorioService(sgu, 1000, 0);

    Map<String, Object> previa = relatorios.executarPaginaNormalizada(
      api,
      Map.of("page", 1, "size", 10)
    );
    assertThat((List<?>) previa.get("content")).singleElement().satisfies(item -> {
      Map<?, ?> registro = (Map<?, ?>) item;
      assertThat(registro.get("grupo_prestador")).isEqualTo("CLINICA DE IMAGEM");
      assertThat(registro.get("nome_prestador")).isEqualTo("CAVALCA DIAGNOSTICOS");
      assertThat(registro.get("tipo_prestador")).isEqualTo("CLINICA");
    });

    for (String formato : List.of("csv", "txt", "xlsx")) {
      var destino = new ByteArrayOutputStream();
      int quantidade = relatorios.exportarPara(api, formato, null, destino);
      assertThat(quantidade).isEqualTo(1);

      if (!"xlsx".equals(formato)) {
        String conteudo = destino.toString(StandardCharsets.UTF_8);
        assertThat(conteudo)
          .contains("CLINICA DE IMAGEM")
          .contains("CAVALCA DIAGNOSTICOS")
          .contains("CLINICA");
        continue;
      }

      try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(destino.toByteArray()))) {
        var planilha = workbook.getSheetAt(0);
        Map<String, Integer> colunas = new LinkedHashMap<>();
        for (var celula : planilha.getRow(0)) {
          colunas.put(celula.getStringCellValue(), celula.getColumnIndex());
        }
        assertThat(planilha.getLastRowNum()).isEqualTo(1);
        assertThat(planilha.getRow(1).getCell(colunas.get("grupo_prestador")).getStringCellValue())
          .isEqualTo("CLINICA DE IMAGEM");
        assertThat(planilha.getRow(1).getCell(colunas.get("nome_prestador")).getStringCellValue())
          .isEqualTo("CAVALCA DIAGNOSTICOS");
        assertThat(planilha.getRow(1).getCell(colunas.get("tipo_prestador")).getStringCellValue())
          .isEqualTo("CLINICA");
      }
    }
  }

  @Test
  void previaEDownloadDevemUsarAMesmaEspecialidadeResolvidaSemFiltroNoXlsx() throws Exception {
    SguRelatorioService sgu = mock(SguRelatorioService.class);
    LinkedHashMap<String, Object> clinico = registroEspecialidade("PRONTO SOCORRO", "medicamento");
    LinkedHashMap<String, Object> cardio = registroEspecialidade("CARDIOLOGIA", "HOLTER 24 HORAS");
    when(sgu.executar(anyString(), anyMap())).thenReturn(
      Map.of("content", List.of(new LinkedHashMap<>(clinico)), "last", false),
      Map.of("content", List.of(new LinkedHashMap<>(clinico), new LinkedHashMap<>(cardio)), "last", true),
      Map.of("content", List.of(new LinkedHashMap<>(clinico), new LinkedHashMap<>(cardio)), "last", true)
    );
    when(sgu.listar("api-especialidade")).thenReturn(Map.of(
      "content", List.of(Map.of(
        "nome", "api-especialidade",
        "ordenacao", "NUMERO_GUIA",
        "consultaSQL", "SELECT NOME_ESPECIALIDADE, COD_BENEFICIARIO, NUMERO_GUIA, DATA_GUIA FROM TESTE"
      ))
    ));
    var relatorios = new ExportacaoRelatorioService(sgu, 1000, 0);

    Map<String, Object> previa = relatorios.executarPaginaNormalizada(
      "api-especialidade", Map.of("page", 1, "size", 1));
    assertThat((List<?>) previa.get("content")).singleElement().satisfies(item ->
      assertThat(((Map<?, ?>) item).get("NOME_ESPECIALIDADE")).isEqualTo("CARDIOLOGIA")
    );

    var destino = new ByteArrayOutputStream();
    relatorios.exportarPara("api-especialidade", "xlsx", null, destino);
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(destino.toByteArray()))) {
      var planilha = workbook.getSheetAt(0);
      assertThat(planilha.getCTWorksheet().isSetAutoFilter()).isFalse();
      int coluna = -1;
      for (var celula : planilha.getRow(0)) {
        if ("NOME_ESPECIALIDADE".equals(celula.getStringCellValue())) coluna = celula.getColumnIndex();
      }
      assertThat(coluna).isNotNegative();
      assertThat(planilha.getRow(1).getCell(coluna).getStringCellValue()).isEqualTo("CARDIOLOGIA");
      assertThat(planilha.getRow(2).getCell(coluna).getStringCellValue()).isEqualTo("CARDIOLOGIA");
    }
  }

  private LinkedHashMap<String, Object> registroEspecialidade(
    String especialidade,
    String descricao
  ) {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("COD_BENEFICIARIO", "BEN-1");
    registro.put("NUMERO_GUIA", "GUIA-1");
    registro.put("DATA_GUIA", "01/09/2026");
    registro.put("NOME_ESPECIALIDADE", especialidade);
    registro.put("DESCRICAO_ITEM", descricao);
    registro.put("CID", "");
    return registro;
  }

  private LinkedHashMap<String, Object> registroGrupoPrestador() {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("grupo_prestador", "MEDICA NAO COOPERADO");
    registro.put("nome_prestador", "CAVALCA DIAGNOSTICOS");
    registro.put("tipo_prestador", "CLINICA");
    return registro;
  }
}
