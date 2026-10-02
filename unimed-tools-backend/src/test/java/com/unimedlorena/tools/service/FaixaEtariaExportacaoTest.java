package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.unimedlorena.tools.dto.RelatorioExportacaoRequest;
import com.unimedlorena.tools.dto.RelatorioLoteRequest;
import com.unimedlorena.tools.exception.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FaixaEtariaExportacaoTest {
  private static final String API = FaixaEtariaConsolidator.API;

  private LinkedHashMap<String, Object> faixa(int depMasc, int depFem, int titMasc, int titFem) {
    return faixa(depMasc, depFem, titMasc, titFem, 0, 0);
  }

  private LinkedHashMap<String, Object> faixa(
    int depMasc, int depFem, int titMasc, int titFem, int agrMasc, int agrFem
  ) {
    var linha = new LinkedHashMap<String, Object>();
    linha.put("faixa_etaria", "0 a 18");
    linha.put("dep_masc", String.valueOf(depMasc));
    linha.put("dep_fem", depFem);
    linha.put("tit_masc", titMasc);
    linha.put("tit_fem", titFem);
    linha.put("agr_masc", agrMasc);
    linha.put("agr_fem", agrFem);
    linha.put("total", agrMasc + agrFem + depMasc + depFem + titMasc + titFem);
    return linha;
  }

  @Test
  void consolidaContratosComAgregadosIgnoraSubtotaisEOrdenaAsDezFaixas() {
    var consolidado = new FaixaEtariaConsolidator();
    consolidado.aceitar(List.of(faixa(2, 3, 5, 7), faixa(2, 3, 5, 7, 11, 13),
      new LinkedHashMap<>(Map.of("FAIXA_ETARIA", "CONTRATO 100")),
      new LinkedHashMap<>(Map.of("FAIXA_ETARIA", "TOTAL GERAL", "TOTAL", 634))));
    var linhas = consolidado.resultado();
    assertThat(linhas).hasSize(11);
    assertThat(linhas.get(0).keySet()).containsExactly("FAIXA_ETARIA", "DEP", "TIT", "AGR", "FEM", "MASC", "TOTAL");
    assertThat(linhas).extracting(linha -> linha.get("FAIXA_ETARIA")).doesNotContain("GERAL");
    assertThat(linhas.get(0)).containsEntry("FAIXA_ETARIA", "0 a 18").containsEntry("DEP", 10L)
      .containsEntry("TIT", 24L).containsEntry("AGR", 24L).containsEntry("FEM", 33L)
      .containsEntry("MASC", 25L).containsEntry("TOTAL", 58L);
    assertThat(linhas.get(9)).containsEntry("FAIXA_ETARIA", "59 a 999").containsEntry("TOTAL", 0L);
    assertThat(linhas.get(10)).containsEntry("FAIXA_ETARIA", "TOTAL GERAL")
      .containsEntry("AGR", 24L).containsEntry("TOTAL", 58L);
  }

  @Test
  void omiteColunaAgrQuandoTodasAsContagensSaoZero() {
    var consolidado = new FaixaEtariaConsolidator();
    consolidado.aceitar(List.of(faixa(1, 2, 3, 4)));
    var linhas = consolidado.resultado();
    assertThat(linhas).allSatisfy(linha -> assertThat(linha).doesNotContainKey("AGR"));
    assertThat(linhas.get(0)).containsEntry("FEM", 6L).containsEntry("MASC", 4L)
      .containsEntry("TOTAL", 10L);
  }

  @Test
  void incluiColunaAgrNosArquivosQuandoExisteAgregado() throws Exception {
    var consolidado = new FaixaEtariaConsolidator();
    consolidado.aceitar(List.of(faixa(1, 2, 3, 4, 5, 6)));
    var service = new ExportacaoRelatorioService(mock(SguRelatorioService.class), 100, 0);

    var csv = service.gerarArquivoFaixaEtaria("csv", consolidado.resultado());
    assertThat(new String(csv.conteudo(), StandardCharsets.UTF_8))
      .startsWith("\uFEFFFAIXA_ETARIA;DEP;TIT;AGR;FEM;MASC;TOTAL\r\n")
      .contains("0 a 18;3;7;11;12;9;21\r\n");

    var xlsx = service.gerarArquivoFaixaEtaria("xlsx", consolidado.resultado());
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.conteudo()))) {
      var sheet = workbook.getSheet("Geral");
      assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("AGR");
      assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(11);
      assertThat(sheet.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(21);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "-1", "1.5", "texto", "9223372036854775808"})
  void rejeitaContagensInvalidasSemEsconderFalhasComoZero(String valor) {
    var linha = faixa(1, 2, 3, 4);
    linha.put("dep_fem", valor);
    assertThatThrownBy(() -> new FaixaEtariaConsolidator().aceitar(List.of(linha)))
      .isInstanceOf(ApiException.class);
  }

  @Test
  void rejeitaFaixaDesconhecidaEColunaAusente() {
    var linha = faixa(1, 2, 3, 4);
    linha.remove("dep_fem");
    assertThatThrownBy(() -> new FaixaEtariaConsolidator().aceitar(List.of(linha)))
      .isInstanceOf(ApiException.class);
    linha.put("faixa_etaria", "desconhecida");
    assertThatThrownBy(() -> new FaixaEtariaConsolidator().aceitar(List.of(linha)))
      .isInstanceOf(ApiException.class);
    assertThat(new FaixaEtariaConsolidator().resultado()).isEmpty();
  }

  private SguRelatorioService sguPaginado() {
    var sgu = mock(SguRelatorioService.class);
    when(sgu.listar(API)).thenReturn(Map.of("content", List.of(Map.of("nome", API, "ordenacao", "UT_EXPORT_ORD"))));
    when(sgu.executar(eq(API), anyMap())).thenAnswer(inv -> {
      Map<String, Object> parametros = inv.getArgument(1);
      assertThat(parametros).doesNotContainKey("combinacoesFiltros");
      int pagina = (int) parametros.get("page");
      int multiplicador = "B".equals(parametros.get("empresas")) ? 2 : 1;
      return Map.of("content", List.of(faixa(multiplicador, 2, 3, 4)),
        "last", pagina == 2, "totalElements", 2);
    });
    return sgu;
  }

  private Map<String, Object> parametros() {
    return Map.of("combinacoesFiltros", List.of(Map.of("empresas", "A"), Map.of("empresas", "B")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"csv", "txt", "xlsx"})
  void exportaTodasAsPaginasDasEmpresasEmUmaTabelaEConfereAPrevia(String formato) throws Exception {
    var sgu = sguPaginado();
    var service = new ExportacaoRelatorioService(sgu, 1, 0);
    var previa = service.executarPaginaNormalizada(API, parametros());
    var linhas = (List<?>) previa.get("content");
    assertThat(linhas).hasSize(11);
    assertThat(((Map<?, ?>) linhas.get(0)).get("TOTAL")).isEqualTo(42L);
    var destino = new ByteArrayOutputStream();
    assertThat(service.exportarPara(API, formato, new RelatorioExportacaoRequest(parametros(), "teste"), destino))
      .isEqualTo(11);
    if (formato.equals("xlsx")) {
      try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(destino.toByteArray()))) {
        assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
        var sheet = workbook.getSheet("Geral");
        assertThat(sheet.getLastRowNum()).isEqualTo(11);
        assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("DEP");
        assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("0 a 18");
        assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(42);
        assertThat(sheet.getRow(11).getCell(5).getNumericCellValue()).isEqualTo(42);
      }
    } else {
      assertThat(destino.toString(StandardCharsets.UTF_8))
        .startsWith("\uFEFFFAIXA_ETARIA;DEP;TIT;FEM;MASC;TOTAL\r\n0 a 18;")
        .contains("0 a 18;14;28;24;18;42\r\n", "TOTAL GERAL;14;28;24;18;42\r\n")
        .doesNotContain("CONTRATO", "agr_", "\r\nGERAL;");
    }
    verify(sgu, times(8)).executar(eq(API), anyMap());
  }

  @Test
  void umaEmpresaMantemSomenteSeusDadosEVazioNaoGeraArquivo() throws Exception {
    var sgu = sguPaginado();
    var service = new ExportacaoRelatorioService(sgu, 1, 0);
    var arquivo = service.exportar(API, "csv", new RelatorioExportacaoRequest(Map.of("empresas", "A"), "teste"));
    assertThat(new String(arquivo.conteudo(), StandardCharsets.UTF_8)).contains("TOTAL GERAL;6;14;12;8;20");
    when(sgu.executar(eq(API), anyMap())).thenReturn(Map.of("content", List.of(), "last", true));
    assertThat(service.executarPaginaNormalizada(API, Map.of()).get("content")).isEqualTo(List.of());
    assertThatThrownBy(() -> service.exportar(API, "csv", new RelatorioExportacaoRequest(Map.of(), "teste")))
      .isInstanceOf(ApiException.class);
  }

  @Test
  void loteTemUmConsolidadoEFalhaDeEmpresaNaoProduzConsolidadoParcial() throws Exception {
    var sgu = sguPaginado();
    when(sgu.executar(eq("outro"), anyMap())).thenReturn(Map.of("content", List.of(Map.of("ID", 1)), "last", true));
    var service = new ExportacaoLoteRelatorioService(new ExportacaoRelatorioService(sgu, 1, 0));
    var request = new RelatorioLoteRequest("pacote", "csv", List.of(
      new RelatorioLoteRequest.Item(API, "faixa_geral", List.of(Map.of("empresas", "A"), Map.of("empresas", "B"))),
      new RelatorioLoteRequest.Item("outro", "outro", List.of(Map.of()))));
    var resultado = service.exportar(request);
    assertThat(resultado.arquivosGerados()).isEqualTo(2);
    try (var zip = new ZipInputStream(new ByteArrayInputStream(resultado.conteudo()))) {
      assertThat(zip.getNextEntry().getName()).isEqualTo("faixa_geral.csv");
      assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).contains("TOTAL GERAL;14;28;24;18;42");
    }
    when(sgu.executar(eq(API), argThat(p -> "B".equals(p.get("empresas")))))
      .thenThrow(new IllegalStateException("falha interna"));
    resultado = service.exportar(request);
    assertThat(resultado.arquivosGerados()).isEqualTo(1);
    assertThat(resultado.arquivosComErro()).isEqualTo(1);
    try (var zip = new ZipInputStream(new ByteArrayInputStream(resultado.conteudo()))) {
      assertThat(zip.getNextEntry().getName()).isEqualTo("outro.csv");
    }
  }

  @Test
  void rejeitaEnvelopeInvalidoAntesDeChamarSgu() {
    var sgu = mock(SguRelatorioService.class);
    var service = new ExportacaoRelatorioService(sgu, 100, 0);
    for (Object valor : List.of("errado", List.of(), List.of("errado"), List.of(Map.of("empresas", List.of(1))))) {
      assertThatThrownBy(() -> service.executarPaginaNormalizada(API, Map.of("combinacoesFiltros", valor)))
        .isInstanceOf(IllegalArgumentException.class);
    }
    verifyNoInteractions(sgu);
  }
}
