package com.unimedlorena.tools.service;

import com.unimedlorena.tools.dto.ComercialRelatorioFinalRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Consolida as quatro APIs do Comercial em uma planilha executiva mensal.
 *
 * <p>O serviço não persiste linhas do SGU. As bases são consultadas, agregadas em
 * memória e descartadas após a geração do XLSX. O arquivo de referência usado
 * para definir o layout não faz parte do repositório e nenhum dado real é
 * incluído em testes.</p>
 */
@Service
public class ComercialRelatorioFinalService {
  public static final String API_BENEFICIARIOS = "0090-beneficiario-empresa";
  public static final String API_RECEITA = "0090-receita-empresa-com-grupo";
  public static final String API_DESPESA = "0090-despesa-empresas";
  public static final String API_FAIXA = "0090-faixa-etaria";

  private static final DateTimeFormatter COMPETENCIA = DateTimeFormatter.ofPattern("yyyyMM");
  private static final List<String> TIPOS_GUIA = List.of("Consultas", "SADT", "Internações", "PA/PS");
  private static final List<String> GRUPOS = List.of(
    "Recurso Próprio", "Médico Cooperado", "Clínica de Imagem", "Intercâmbio",
    "Sessões Multi", "Home-Care", "Outros"
  );
  private static final List<String> REGIOES = List.of(
    "Módulo Coração", "Central Nacional", "Sudeste (Fora Vale)", "Centro Oeste",
    "Nordeste", "Norte", "Sul", "Vale do Paraiba", "Local"
  );

  private final ExportacaoRelatorioService exportacao;

  public ComercialRelatorioFinalService(ExportacaoRelatorioService exportacao) {
    this.exportacao = exportacao;
  }

  public byte[] gerar(ComercialRelatorioFinalRequest request) throws IOException {
    validar(request);
    YearMonth alvo = YearMonth.parse(request.competencia(), COMPETENCIA);
    List<YearMonth> meses = new ArrayList<>();
    for (int i = 11; i >= 0; i--) meses.add(alvo.minusMonths(i));

    List<MesDados> dados = new ArrayList<>();
    for (YearMonth mes : meses) {
      List<LinkedHashMap<String, Object>> receita = carregar(
        API_RECEITA, filtrosMes(request, API_RECEITA, mes)
      );
      List<LinkedHashMap<String, Object>> despesa = carregar(
        API_DESPESA, filtrosMes(request, API_DESPESA, mes)
      );
      dados.add(agregarMes(mes, receita, despesa));
    }

    List<LinkedHashMap<String, Object>> beneficiarios = carregar(
      API_BENEFICIARIOS, filtrosMes(request, API_BENEFICIARIOS, alvo)
    );
    ContagemBeneficiarios contagem = contarBeneficiarios(beneficiarios);
    long ativosAnterior = dados.size() > 1
      ? dados.get(dados.size() - 2).beneficiariosAtivos
      : 0;

    List<LinkedHashMap<String, Object>> faixa = exportacao.carregarFaixaEtaria(
      filtrosObrigatorios(request, API_FAIXA)
    );

    return montarWorkbook(request.empresa(), alvo, dados, contagem, ativosAnterior, faixa);
  }

  private void validar(ComercialRelatorioFinalRequest request) {
    if (request == null) throw new IllegalArgumentException("Informe os dados do relatório final.");
    if (request.empresa() == null || request.empresa().isBlank()) {
      throw new IllegalArgumentException("Informe a empresa do relatório final.");
    }
    if (request.competencia() == null || !request.competencia().matches("\\d{6}")) {
      throw new IllegalArgumentException("Informe a competência no formato AAAAMM.");
    }
    try {
      YearMonth.parse(request.competencia(), COMPETENCIA);
    } catch (Exception ex) {
      throw new IllegalArgumentException("A competência informada é inválida.");
    }
    for (String api : List.of(API_BENEFICIARIOS, API_RECEITA, API_DESPESA, API_FAIXA)) {
      filtrosObrigatorios(request, api);
    }
  }

  private List<Map<String, Object>> filtrosObrigatorios(
    ComercialRelatorioFinalRequest request,
    String api
  ) {
    if (request.filtrosPorApi() == null) {
      throw new IllegalArgumentException("Os filtros do Comercial não foram informados.");
    }
    List<Map<String, Object>> filtros = request.filtrosPorApi().get(api);
    if (filtros == null || filtros.isEmpty()) {
      throw new IllegalArgumentException("Filtros ausentes para a API " + api + ".");
    }
    return filtros;
  }

  private List<Map<String, Object>> filtrosMes(
    ComercialRelatorioFinalRequest request,
    String api,
    YearMonth mes
  ) {
    List<Map<String, Object>> base = filtrosObrigatorios(request, api);
    List<Map<String, Object>> resultado = new ArrayList<>();
    boolean encontrouCompetencia = false;

    for (Map<String, Object> original : base) {
      LinkedHashMap<String, Object> copia = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entrada : original.entrySet()) {
        String normalizada = normalizar(entrada.getKey());
        Object valor = entrada.getValue();
        if (normalizada.contains("COMPETENCIA") || normalizada.equals("COMPET")) {
          valor = adaptarTipoCompetencia(valor, mes);
          encontrouCompetencia = true;
        }
        if (normalizada.contains("DATAREFERENCIA") || normalizada.equals("REFERENCIA")) {
          valor = adaptarDataReferencia(valor, mes.atEndOfMonth());
        }
        copia.put(entrada.getKey(), valor);
      }
      resultado.add(copia);
    }

    if (!encontrouCompetencia && !API_FAIXA.equals(api)) {
      throw new IllegalArgumentException(
        "A API " + api + " não expõe filtro de competência; não é possível montar os 12 meses."
      );
    }
    return resultado;
  }

  private Object adaptarTipoCompetencia(Object original, YearMonth mes) {
    String valor = mes.format(COMPETENCIA);
    return original instanceof Number ? Integer.valueOf(valor) : valor;
  }

  private Object adaptarDataReferencia(Object original, LocalDate data) {
    if (original instanceof Number) return original;
    String texto = String.valueOf(original == null ? "" : original);
    if (texto.matches("\\d{2}/\\d{2}/\\d{4}")) {
      return data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
    return data.toString();
  }

  private List<LinkedHashMap<String, Object>> carregar(
    String api,
    List<Map<String, Object>> combinacoes
  ) {
    List<LinkedHashMap<String, Object>> linhas = new ArrayList<>();
    for (Map<String, Object> filtros : new LinkedHashSet<>(combinacoes)) {
      linhas.addAll(exportacao.carregarRegistros(api, filtros));
    }
    return linhas;
  }

  private MesDados agregarMes(
    YearMonth mes,
    List<LinkedHashMap<String, Object>> receitaLinhas,
    List<LinkedHashMap<String, Object>> despesaLinhas
  ) {
    BigDecimal receita = ZERO;
    BigDecimal copart = ZERO;
    Map<String, BigDecimal> receitaRegiao = mapaDecimal(REGIOES);
    Map<String, Set<String>> vidasRegiao = new LinkedHashMap<>();
    Set<String> vidasAtivas = new LinkedHashSet<>();
    REGIOES.forEach(r -> vidasRegiao.put(r, new LinkedHashSet<>()));

    for (Map<String, Object> linha : receitaLinhas) {
      BigDecimal valor = valorFinanceiro(linha);
      String tipo = texto(linha, "TIPO", "TIPO_RECEITA", "DESCRICAO_TIPO");
      if (normalizar(tipo).contains("COPART")) copart = copart.add(valor);
      else receita = receita.add(valor);

      if (normalizar(tipo).contains("MENSALIDADE")) {
        String regiao = regiao(linha);
        receitaRegiao.merge(regiao, valor, BigDecimal::add);
        String id = identificadorBeneficiario(linha);
        if (id.isBlank()) id = "LINHA-" + vidasAtivas.size();
        vidasAtivas.add(id);
        vidasRegiao.get(regiao).add(id);
      }
    }

    BigDecimal sinistro = ZERO;
    Map<String, BigDecimal> tipoGuia = mapaDecimal(TIPOS_GUIA);
    Map<String, BigDecimal> grupo = mapaDecimal(GRUPOS);
    Map<String, BigDecimal> despesaRegiao = mapaDecimal(REGIOES);

    for (Map<String, Object> linha : despesaLinhas) {
      BigDecimal valor = valorFinanceiro(linha);
      sinistro = sinistro.add(valor);

      String guia = classificarTipoGuia(texto(
        linha, "DESCRICAO_TIPO_GUIA", "TIPO_GUIA", "DESCRICAO_GUIA"
      ));
      if (guia != null) tipoGuia.merge(guia, valor, BigDecimal::add);

      String grupoPrestador = classificarGrupo(texto(
        linha, "GRUPO_PRESTADOR", "GRUPO", "TIPO_PRESTADOR"
      ));
      grupo.merge(grupoPrestador, valor, BigDecimal::add);
      despesaRegiao.merge(regiao(linha), valor, BigDecimal::add);
    }

    Map<String, Integer> vidas = new LinkedHashMap<>();
    REGIOES.forEach(r -> vidas.put(r, vidasRegiao.get(r).size()));

    return new MesDados(
      mes, receita, copart, sinistro, tipoGuia, grupo, receitaRegiao,
      despesaRegiao, vidas, vidasAtivas.size(), despesaLinhas
    );
  }

  private ContagemBeneficiarios contarBeneficiarios(List<LinkedHashMap<String, Object>> linhas) {
    Map<String, Boolean> porBeneficiario = new LinkedHashMap<>();
    int fallback = 0;
    for (Map<String, Object> linha : linhas) {
      String id = identificadorBeneficiario(linha);
      if (id.isBlank()) id = "LINHA-" + fallback++;
      porBeneficiario.put(id, !inativo(linha));
    }
    long ativos = porBeneficiario.values().stream().filter(Boolean::booleanValue).count();
    return new ContagemBeneficiarios(ativos, porBeneficiario.size() - ativos);
  }

  private boolean inativo(Map<String, Object> linha) {
    String status = texto(
      linha, "SITUACAO", "STATUS", "SITUACAO_BENEFICIARIO", "STATUS_BENEFICIARIO", "ATIVO"
    );
    String n = normalizar(status);
    if (n.equals("N") || n.equals("NAO") || n.contains("INATIV") ||
        n.contains("EXCLU") || n.contains("CANCEL") || n.contains("DESLIG")) return true;

    String exclusao = texto(
      linha, "DATA_EXCLUSAO", "DT_EXCLUSAO", "BNF_DAT_EXCL", "DATA_INATIVACAO"
    ).trim();
    return !exclusao.isBlank() && !exclusao.startsWith("01/01/0001") &&
      !exclusao.startsWith("0001-01-01");
  }

  private byte[] montarWorkbook(
    String empresa,
    YearMonth alvo,
    List<MesDados> meses,
    ContagemBeneficiarios atual,
    long ativosAnterior,
    List<LinkedHashMap<String, Object>> faixa
  ) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      String aba = String.format("%02d%04d", alvo.getMonthValue(), alvo.getYear());
      var sheet = wb.createSheet(aba);
      wb.setForceFormulaRecalculation(true);
      Estilos e = new Estilos(wb);

      int[] larguras = {14,24,18,18,18,18,18,18,18,18,18,16,16,16,16};
      for (int i = 0; i < larguras.length; i++) sheet.setColumnWidth(i, larguras[i] * 256);
      sheet.setDisplayGridlines(false);
      sheet.createFreezePane(0, 1);

      titulo(sheet, 0, 0, 14, "RELATÓRIO DE SINISTRALIDADE — " + empresa, e.titulo);
      resumoDozeMeses(sheet, meses, atual, e);
      resumoAtual(sheet, alvo, atual, ativosAnterior, meses.getLast(), e);
      faixaEtaria(sheet, faixa, e);
      receitaDozeMeses(sheet, meses, e);
      porTipoGuia(sheet, meses, e);
      porGrupoPrestador(sheet, meses, e);
      regioes(sheet, meses, e);
      rankings(sheet, meses, e);
      analiseBeneficiarios(sheet, meses, "PA/PS", 139,
        "Análise Sintética dos 30+ Sinistro de Beneficiários em PA – Acumulado (12 meses)", e);
      analiseBeneficiarios(sheet, meses, "SADT", 174,
        "Análise Sintética dos 30+ Sinistro de Beneficiários em SADT – Acumulado (12 meses)", e);
      analiseSessoes(sheet, meses, 209, e);
      graficos(sheet);

      sheet.setZoom(80);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      wb.write(out);
      return out.toByteArray();
    }
  }

  private void resumoDozeMeses(
    org.apache.poi.ss.usermodel.Sheet sheet,
    List<MesDados> meses,
    ContagemBeneficiarios atual,
    Estilos e
  ) {
    String[] headers = {"Comp", "Receita", "Sinistro", "Co-part", "Benef. Ativo", "Variação Vidas", "Sinistralidade"};
    cabecalho(sheet, 1, headers, e);
    for (int i = 0; i < 12; i++) {
      int r = 2 + i;
      MesDados m = meses.get(i);
      data(sheet, r, 0, m.mes.atDay(1), e.mes);
      numero(sheet, r, 1, m.receita, e.moeda);
      numero(sheet, r, 2, m.sinistro, e.moeda);
      numero(sheet, r, 3, m.copart, e.moeda);
      long ativos = i == 11 ? atual.ativos : m.beneficiariosAtivos;
      inteiro(sheet, r, 4, ativos, e.inteiro);
      if (i > 0) {
        long anteriores = meses.get(i - 1).beneficiariosAtivos;
        if (anteriores > 0) {
          numero(sheet, r, 5,
            BigDecimal.valueOf(ativos)
              .divide(BigDecimal.valueOf(anteriores), 8, RoundingMode.HALF_UP)
              .subtract(BigDecimal.ONE),
            e.percentual);
        }
      }
      formula(sheet, r, 6, "C" + (r + 1) + "/(B" + (r + 1) + "+D" + (r + 1) + ")", e.percentual);
    }
    int r = 14;
    texto(sheet, r, 0, "Acumulado", e.total);
    formula(sheet, r, 1, "SUM(B3:B14)", e.totalMoeda);
    formula(sheet, r, 2, "SUM(C3:C14)", e.totalMoeda);
    formula(sheet, r, 3, "SUM(D3:D14)", e.totalMoeda);
    formula(sheet, r, 4, "E14", e.totalInteiro);
    formula(sheet, r, 6, "C15/(B15+D15)", e.totalPercentual);
  }

  private void resumoAtual(
    org.apache.poi.ss.usermodel.Sheet sheet,
    YearMonth alvo,
    ContagemBeneficiarios atual,
    long ativosAnterior,
    MesDados mes,
    Estilos e
  ) {
    String[] headers = {"Comp", "Benef. Ativo", "Benef. Inativo", "Var. Mês Anterior", "Receita", "Sinistro", "Sinistralidade"};
    cabecalho(sheet, 17, headers, e);
    data(sheet, 18, 0, alvo.atDay(1), e.mes);
    inteiro(sheet, 18, 1, atual.ativos, e.inteiro);
    inteiro(sheet, 18, 2, atual.inativos, e.inteiro);
    if (ativosAnterior > 0) {
      numero(sheet, 18, 3,
        BigDecimal.valueOf(atual.ativos).divide(BigDecimal.valueOf(ativosAnterior), 8, RoundingMode.HALF_UP)
          .subtract(BigDecimal.ONE), e.percentual);
    }
    numero(sheet, 18, 4, mes.receita.add(mes.copart), e.moeda);
    numero(sheet, 18, 5, mes.sinistro, e.moeda);
    formula(sheet, 18, 6, "F19/E19", e.percentual);
  }

  private void faixaEtaria(
    org.apache.poi.ss.usermodel.Sheet sheet,
    List<LinkedHashMap<String, Object>> faixa,
    Estilos e
  ) {
    String[] headers = {"Faixa Etária", "DEP", "TIT", "Fem", "Masc", "Total Geral"};
    cabecalho(sheet, 21, headers, e);
    int r = 22;
    for (Map<String, Object> linha : faixa) {
      String rotulo = texto(linha, "FAIXA_ETARIA");
      if (normalizar(rotulo).equals("TOTALGERAL")) continue;
      texto(sheet, r, 0, rotulo, e.corpo);
      inteiro(sheet, r, 1, inteiro(linha, "DEP"), e.inteiro);
      inteiro(sheet, r, 2, inteiro(linha, "TIT"), e.inteiro);
      inteiro(sheet, r, 3, inteiro(linha, "FEM"), e.inteiro);
      inteiro(sheet, r, 4, inteiro(linha, "MASC"), e.inteiro);
      formula(sheet, r, 5, "B" + (r + 1) + "+C" + (r + 1), e.inteiro);
      r++;
      if (r >= 32) break;
    }
    while (r < 32) {
      texto(sheet, r, 0, "", e.corpo);
      for (int c = 1; c <= 5; c++) inteiro(sheet, r, c, 0, e.inteiro);
      r++;
    }
    texto(sheet, 32, 0, "Total Geral", e.total);
    for (int c = 1; c <= 5; c++) {
      formula(sheet, 32, c, "SUM(" + coluna(c) + "23:" + coluna(c) + "32)", e.totalInteiro);
    }
  }

  private void receitaDozeMeses(org.apache.poi.ss.usermodel.Sheet sheet, List<MesDados> meses, Estilos e) {
    cabecalho(sheet, 36, new String[]{"Comp", "Receita", "Sinistro", "Sinistralidade", "Sinistralidade Acumulada"}, e);
    for (int i = 0; i < 12; i++) {
      int r = 37 + i;
      MesDados m = meses.get(i);
      data(sheet, r, 0, m.mes.atDay(1), e.mes);
      numero(sheet, r, 1, m.receita.add(m.copart), e.moeda);
      numero(sheet, r, 2, m.sinistro, e.moeda);
      formula(sheet, r, 3, "C" + (r + 1) + "/B" + (r + 1), e.percentual);
      formula(sheet, r, 4,
        "SUM(C$38:C" + (r + 1) + ")/SUM(B$38:B" + (r + 1) + ")", e.percentual);
    }
    texto(sheet, 49, 0, "Acumulado", e.total);
    formula(sheet, 49, 1, "SUM(B38:B49)", e.totalMoeda);
    formula(sheet, 49, 2, "SUM(C38:C49)", e.totalMoeda);
    formula(sheet, 49, 4, "C50/B50", e.totalPercentual);
  }

  private void porTipoGuia(org.apache.poi.ss.usermodel.Sheet sheet, List<MesDados> meses, Estilos e) {
    cabecalho(sheet, 52, new String[]{"Comp", "Consultas", "SADT", "Internações", "PA/PS", "Total"}, e);
    for (int i = 0; i < 12; i++) {
      int r = 53 + i;
      MesDados m = meses.get(i);
      data(sheet, r, 0, m.mes.atDay(1), e.mes);
      for (int c = 0; c < TIPOS_GUIA.size(); c++) {
        numero(sheet, r, c + 1, m.tipoGuia.get(TIPOS_GUIA.get(c)), e.moeda);
      }
      formula(sheet, r, 5, "SUM(B" + (r + 1) + ":E" + (r + 1) + ")", e.moeda);
    }
    texto(sheet, 65, 0, "Acumulado", e.total);
    for (int c = 1; c <= 5; c++) formula(sheet, 65, c,
      "SUM(" + coluna(c) + "54:" + coluna(c) + "65)", e.totalMoeda);
    texto(sheet, 66, 0, "% PART", e.total);
    for (int c = 1; c <= 4; c++) formula(sheet, 66, c,
      coluna(c) + "66/F66", e.totalPercentual);
    numero(sheet, 66, 5, BigDecimal.ONE, e.totalPercentual);
  }

  private void porGrupoPrestador(org.apache.poi.ss.usermodel.Sheet sheet, List<MesDados> meses, Estilos e) {
    String[] headers = {"Comp", "Recurso Próprio", "Médico Cooperado", "Clínica de Imagem", "Intercâmbio",
      "Sessões Multi", "Home-Care", "Outros", "Total"};
    cabecalho(sheet, 68, headers, e);
    for (int i = 0; i < 12; i++) {
      int r = 69 + i;
      MesDados m = meses.get(i);
      data(sheet, r, 0, m.mes.atDay(1), e.mes);
      for (int c = 0; c < GRUPOS.size(); c++) {
        numero(sheet, r, c + 1, m.grupoPrestador.get(GRUPOS.get(c)), e.moeda);
      }
      formula(sheet, r, 8, "SUM(B" + (r + 1) + ":H" + (r + 1) + ")", e.moeda);
    }
    texto(sheet, 81, 0, "Acumulado", e.total);
    for (int c = 1; c <= 8; c++) formula(sheet, 81, c,
      "SUM(" + coluna(c) + "70:" + coluna(c) + "81)", e.totalMoeda);
    texto(sheet, 82, 0, "% PART", e.total);
    for (int c = 1; c <= 7; c++) formula(sheet, 82, c,
      coluna(c) + "82/I82", e.totalPercentual);
    numero(sheet, 82, 8, BigDecimal.ONE, e.totalPercentual);
  }

  private void regioes(org.apache.poi.ss.usermodel.Sheet sheet, List<MesDados> meses, Estilos e) {
    MesDados atual = meses.getLast();
    Map<String, BigDecimal> sinistro12 = mapaDecimal(REGIOES);
    Map<String, BigDecimal> receita12 = mapaDecimal(REGIOES);
    for (MesDados mes : meses) {
      REGIOES.forEach(r -> {
        sinistro12.merge(r, mes.despesaRegiao.get(r), BigDecimal::add);
        receita12.merge(r, mes.receitaRegiao.get(r), BigDecimal::add);
      });
    }
    regiaoBloco(sheet, 85, atual.sinistro, "Mês", atual.vidasRegiao, atual.despesaRegiao,
      atual.receitaRegiao, atual.grupoPrestador.get("Home-Care"), e);
    BigDecimal total12 = meses.stream().map(MesDados::sinistro).reduce(ZERO, BigDecimal::add);
    regiaoBloco(sheet, 98, total12, "12 meses", atual.vidasRegiao, sinistro12,
      receita12, meses.stream().map(m -> m.grupoPrestador.get("Home-Care")).reduce(ZERO, BigDecimal::add), e);
  }

  private void regiaoBloco(
    org.apache.poi.ss.usermodel.Sheet sheet,
    int inicio,
    BigDecimal totalSinistro,
    String periodo,
    Map<String, Integer> vidas,
    Map<String, BigDecimal> sinistro,
    Map<String, BigDecimal> receita,
    BigDecimal homeCare,
    Estilos e
  ) {
    texto(sheet, inicio, 0, "Total", e.total);
    numero(sheet, inicio, 3, totalSinistro, e.totalMoeda);
    texto(sheet, inicio, 4, periodo, e.total);
    cabecalho(sheet, inicio + 1, new String[]{"Região", "Nº Vidas", "Percentual", "Sinistro", "Percentual"}, e);

    int totalVidas = vidas.values().stream().mapToInt(Integer::intValue).sum();
    BigDecimal modulo = sinistro.get("Módulo Coração");
    BigDecimal central = sinistro.get("Central Nacional");

    for (int i = 0; i < REGIOES.size(); i++) {
      String regiao = REGIOES.get(i);
      int r = inicio + 2 + i;
      texto(sheet, r, 0, regiao, e.corpo);
      Integer qtd = vidas.get(regiao);
      if (qtd != null && qtd > 0) inteiro(sheet, r, 1, qtd, e.inteiro);
      if (totalVidas > 0 && qtd != null) numero(sheet, r, 2,
        BigDecimal.valueOf(qtd).divide(BigDecimal.valueOf(totalVidas), 8, RoundingMode.HALF_UP), e.percentual);
      numero(sheet, r, 3, sinistro.get(regiao), e.moeda);
      if (totalSinistro.signum() != 0) numero(sheet, r, 4,
        sinistro.get(regiao).divide(totalSinistro, 8, RoundingMode.HALF_UP), e.percentual);

      if (i == 1) {
        cabecalho(sheet, r, 5, new String[]{"Receita", "Home Care", "Módulo Coração",
          "Central Nacional", "Sinistro por Região (R$)", "Sinistralidade por Região (%)"}, e);
      }
      if (i >= 2) {
        numero(sheet, r, 5, receita.get(regiao), e.moeda);
        numero(sheet, r, 6, regiao.equals("Vale do Paraiba") || regiao.equals("Local") ? homeCare : ZERO, e.moeda);
        numero(sheet, r, 7, regiao.equals("Vale do Paraiba") || regiao.equals("Local") ? modulo : ZERO, e.moeda);
        numero(sheet, r, 8, regiao.equals("Vale do Paraiba") || regiao.equals("Local") ? central : ZERO, e.moeda);
        formula(sheet, r, 9, "D" + (r + 1) + "+G" + (r + 1) + "+H" + (r + 1) + "+I" + (r + 1), e.moeda);
        if (receita.get(regiao).signum() != 0) formula(sheet, r, 10,
          "J" + (r + 1) + "/F" + (r + 1), e.percentual);
      }
    }
    int totalRow = inicio + 11;
    inteiro(sheet, totalRow, 1, totalVidas, e.totalInteiro);
    numero(sheet, totalRow, 2, BigDecimal.ONE, e.totalPercentual);
    numero(sheet, totalRow, 4, BigDecimal.ONE, e.totalPercentual);
    formula(sheet, totalRow, 5, "SUM(F" + (inicio + 5) + ":F" + (inicio + 11) + ")", e.totalMoeda);
  }

  private void rankings(org.apache.poi.ss.usermodel.Sheet sheet, List<MesDados> meses, Estilos e) {
    MesDados atual = meses.getLast();
    List<LinkedHashMap<String, Object>> todas = meses.stream()
      .flatMap(m -> m.despesaLinhas.stream()).toList();

    rankingDuplo(sheet, 111,
      "BENEFICIÁRIOS COM MAIORES CUSTOS DO MÊS", ranking(atual.despesaLinhas,
        this::identificadorBeneficiario, 10),
      "ESPECIALIDADE COM MAIORES CUSTOS DO MÊS", ranking(atual.despesaLinhas,
        this::especialidade, 10), atual.sinistro, e);

    BigDecimal total12 = meses.stream().map(MesDados::sinistro).reduce(ZERO, BigDecimal::add);
    rankingDuplo(sheet, 124,
      "BENEFICIÁRIOS COM MAIORES CUSTOS ACUMULADO", ranking(todas,
        this::identificadorBeneficiario, 10),
      "ESPECIALIDADE COM MAIORES CUSTOS ACUMULADO", ranking(todas,
        this::especialidade, 10), total12, e);
  }

  private void rankingDuplo(
    org.apache.poi.ss.usermodel.Sheet sheet,
    int tituloRow,
    String tituloEsquerda,
    List<Ranking> esquerda,
    String tituloDireita,
    List<Ranking> direita,
    BigDecimal total,
    Estilos e
  ) {
    titulo(sheet, tituloRow, 0, 3, tituloEsquerda, e.secao);
    titulo(sheet, tituloRow, 5, 8, tituloDireita, e.secao);
    String[] h = {"Ranking", "Código", "Valor", "% geral"};
    cabecalho(sheet, tituloRow + 1, h, e);
    cabecalho(sheet, tituloRow + 1, 5, new String[]{"Ranking", "Especialidade", "Valor", "% geral"}, e);
    for (int i = 0; i < 10; i++) {
      int r = tituloRow + 2 + i;
      if (i < esquerda.size()) {
        inteiro(sheet, r, 0, i + 1, e.inteiro);
        texto(sheet, r, 1, esquerda.get(i).chave, e.corpo);
        numero(sheet, r, 2, esquerda.get(i).valor, e.moeda);
        if (total.signum() != 0) numero(sheet, r, 3,
          esquerda.get(i).valor.divide(total, 8, RoundingMode.HALF_UP), e.percentual);
      }
      if (i < direita.size()) {
        inteiro(sheet, r, 5, i + 1, e.inteiro);
        texto(sheet, r, 6, direita.get(i).chave, e.corpo);
        numero(sheet, r, 7, direita.get(i).valor, e.moeda);
        if (total.signum() != 0) numero(sheet, r, 8,
          direita.get(i).valor.divide(total, 8, RoundingMode.HALF_UP), e.percentual);
      }
    }
    int subtotal = tituloRow + 12;
    texto(sheet, subtotal, 0, "Sub Total", e.total);
    formula(sheet, subtotal, 2, "SUM(C" + (tituloRow + 3) + ":C" + (tituloRow + 12) + ")", e.totalMoeda);
    if (total.signum() != 0) formula(sheet, subtotal, 3, "C" + (subtotal + 1) + "/" + total.toPlainString(), e.totalPercentual);
    texto(sheet, subtotal, 5, "Sub Total", e.total);
    formula(sheet, subtotal, 7, "SUM(H" + (tituloRow + 3) + ":H" + (tituloRow + 12) + ")", e.totalMoeda);
    if (total.signum() != 0) formula(sheet, subtotal, 8, "H" + (subtotal + 1) + "/" + total.toPlainString(), e.totalPercentual);
  }

  private void analiseBeneficiarios(
    org.apache.poi.ss.usermodel.Sheet sheet,
    List<MesDados> meses,
    String tipo,
    int tituloRow,
    String titulo,
    Estilos e
  ) {
    Predicate<Map<String, Object>> filtro = linha -> tipo.equals(classificarTipoGuia(texto(
      linha, "DESCRICAO_TIPO_GUIA", "TIPO_GUIA", "DESCRICAO_GUIA"
    )));
    List<LinkedHashMap<String, Object>> todas = meses.stream().flatMap(m -> m.despesaLinhas.stream())
      .filter(filtro).toList();
    List<Ranking> top = ranking(todas, this::identificadorBeneficiario, 30);

    titulo(sheet, tituloRow, 0, 14, titulo, e.secao);
    texto(sheet, tituloRow + 1, 0, "Ranking", e.cabecalho);
    texto(sheet, tituloRow + 1, 1, "Código", e.cabecalho);
    for (int i = 0; i < 12; i++) data(sheet, tituloRow + 1, 2 + i, meses.get(i).mes.atDay(1), e.cabecalhoMes);
    texto(sheet, tituloRow + 1, 14, "TOTAL", e.cabecalho);

    for (int i = 0; i < top.size(); i++) {
      int r = tituloRow + 2 + i;
      Ranking item = top.get(i);
      inteiro(sheet, r, 0, i + 1, e.inteiro);
      texto(sheet, r, 1, item.chave, e.corpo);
      for (int m = 0; m < 12; m++) {
        BigDecimal valor = somarFiltrado(meses.get(m).despesaLinhas, linha ->
          filtro.test(linha) && item.chave.equals(identificadorBeneficiario(linha)));
        if (valor.signum() != 0) numero(sheet, r, 2 + m, valor, e.moeda);
      }
      formula(sheet, r, 14, "SUM(C" + (r + 1) + ":N" + (r + 1) + ")", e.moeda);
    }
  }

  private void analiseSessoes(org.apache.poi.ss.usermodel.Sheet sheet, List<MesDados> meses, int tituloRow, Estilos e) {
    Predicate<Map<String, Object>> filtro = linha ->
      "Sessões Multi".equals(classificarGrupo(texto(linha, "GRUPO_PRESTADOR", "GRUPO", "TIPO_PRESTADOR")));
    List<LinkedHashMap<String, Object>> todas = meses.stream().flatMap(m -> m.despesaLinhas.stream())
      .filter(filtro).toList();
    List<Ranking> top = ranking(todas, this::descricaoUtilizacao, 10);

    titulo(sheet, tituloRow, 0, 14,
      "Análise Sintética das principais utilizações em SESSÕES MULTI – Acumulado (12 meses)", e.secao);
    texto(sheet, tituloRow + 1, 0, "Ranking", e.cabecalho);
    texto(sheet, tituloRow + 1, 1, "Tipo de Sessão", e.cabecalho);
    for (int i = 0; i < 12; i++) data(sheet, tituloRow + 1, 2 + i, meses.get(i).mes.atDay(1), e.cabecalhoMes);
    texto(sheet, tituloRow + 1, 14, "TOTAL", e.cabecalho);

    for (int i = 0; i < 10; i++) {
      int r = tituloRow + 2 + i;
      inteiro(sheet, r, 0, i + 1, e.inteiro);
      if (i >= top.size()) {
        numero(sheet, r, 14, ZERO, e.moeda);
        continue;
      }
      Ranking item = top.get(i);
      texto(sheet, r, 1, item.chave, e.corpo);
      for (int m = 0; m < 12; m++) {
        BigDecimal valor = somarFiltrado(meses.get(m).despesaLinhas, linha ->
          filtro.test(linha) && item.chave.equals(descricaoUtilizacao(linha)));
        if (valor.signum() != 0) numero(sheet, r, 2 + m, valor, e.moeda);
      }
      formula(sheet, r, 14, "SUM(C" + (r + 1) + ":N" + (r + 1) + ")", e.moeda);
    }
  }

  private void graficos(org.apache.poi.xssf.usermodel.XSSFSheet sheet) {
    XSSFDrawing drawing = sheet.createDrawingPatriarch();

    criarBarra(drawing, sheet, "Receita x Sinistro — 12 meses",
      new CellRangeAddress(37, 48, 0, 0), List.of(
        new Serie("Receita", new CellRangeAddress(37, 48, 1, 1)),
        new Serie("Sinistro", new CellRangeAddress(37, 48, 2, 2))
      ), 7, 1, 15, 15);

    criarLinha(drawing, sheet, "Sinistralidade — 12 meses",
      new CellRangeAddress(37, 48, 0, 0),
      new Serie("Sinistralidade", new CellRangeAddress(37, 48, 3, 3)),
      7, 36, 15, 50);

    criarBarra(drawing, sheet, "Despesas por tipo de guia",
      new CellRangeAddress(52, 52, 1, 4), List.of(
        new Serie("Acumulado 12 meses", new CellRangeAddress(65, 65, 1, 4))
      ), 6, 52, 15, 67);
  }

  private void criarBarra(
    XSSFDrawing drawing,
    org.apache.poi.xssf.usermodel.XSSFSheet sheet,
    String titulo,
    CellRangeAddress categorias,
    List<Serie> series,
    int c1, int r1, int c2, int r2
  ) {
    XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, c1, r1, c2, r2);
    XSSFChart chart = drawing.createChart(anchor);
    chart.setTitleText(titulo);
    chart.setTitleOverlay(false);
    var legend = chart.getOrAddLegend();
    legend.setPosition(LegendPosition.BOTTOM);
    XDDFCategoryAxis eixoCategoria = chart.createCategoryAxis(AxisPosition.BOTTOM);
    XDDFValueAxis eixoValor = chart.createValueAxis(AxisPosition.LEFT);
    eixoValor.setCrosses(org.apache.poi.xddf.usermodel.chart.AxisCrosses.AUTO_ZERO);
    XDDFChartData data = chart.createData(ChartTypes.BAR, eixoCategoria, eixoValor);
    if (data instanceof XDDFBarChartData barras) barras.setBarDirection(BarDirection.COL);
    XDDFDataSource<?> categoriasData = fonteCategorias(sheet, categorias);
    for (Serie serie : series) {
      XDDFNumericalDataSource<Double> valores =
        XDDFDataSourcesFactory.fromNumericCellRange(sheet, serie.valores);
      data.addSeries(categoriasData, valores).setTitle(serie.nome, null);
    }
    chart.plot(data);
  }

  private void criarLinha(
    XSSFDrawing drawing,
    org.apache.poi.xssf.usermodel.XSSFSheet sheet,
    String titulo,
    CellRangeAddress categorias,
    Serie serie,
    int c1, int r1, int c2, int r2
  ) {
    XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, c1, r1, c2, r2);
    XSSFChart chart = drawing.createChart(anchor);
    chart.setTitleText(titulo);
    chart.setTitleOverlay(false);
    XDDFCategoryAxis eixoCategoria = chart.createCategoryAxis(AxisPosition.BOTTOM);
    XDDFValueAxis eixoValor = chart.createValueAxis(AxisPosition.LEFT);
    XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, eixoCategoria, eixoValor);
    XDDFDataSource<?> categoriasData = fonteCategorias(sheet, categorias);
    XDDFNumericalDataSource<Double> valores =
      XDDFDataSourcesFactory.fromNumericCellRange(sheet, serie.valores);
    data.addSeries(categoriasData, valores).setTitle(serie.nome, null);
    chart.plot(data);
  }

  private XDDFDataSource<?> fonteCategorias(
    org.apache.poi.xssf.usermodel.XSSFSheet sheet,
    CellRangeAddress range
  ) {
    Cell primeira = sheet.getRow(range.getFirstRow()).getCell(range.getFirstColumn());
    if (primeira != null && primeira.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
      return XDDFDataSourcesFactory.fromStringCellRange(sheet, range);
    }
    return XDDFDataSourcesFactory.fromNumericCellRange(sheet, range);
  }

  private List<Ranking> ranking(
    List<? extends Map<String, Object>> linhas,
    java.util.function.Function<Map<String, Object>, String> chave,
    int limite
  ) {
    Map<String, BigDecimal> totais = new LinkedHashMap<>();
    for (Map<String, Object> linha : linhas) {
      String valor = chave.apply(linha);
      if (valor == null || valor.isBlank()) valor = "NÃO INFORMADO";
      totais.merge(valor.trim(), valorFinanceiro(linha), BigDecimal::add);
    }
    return totais.entrySet().stream()
      .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
      .limit(limite)
      .map(e -> new Ranking(e.getKey(), e.getValue()))
      .toList();
  }

  private BigDecimal somarFiltrado(
    List<LinkedHashMap<String, Object>> linhas,
    Predicate<Map<String, Object>> filtro
  ) {
    return linhas.stream().filter(filtro).map(this::valorFinanceiro).reduce(ZERO, BigDecimal::add);
  }

  private String classificarTipoGuia(String valor) {
    String n = " " + normalizarPalavras(valor) + " ";
    if (n.contains(" PRONTO ATENDIMENTO ") || n.contains(" PRONTO SOCORRO ") ||
        n.contains(" PA PS ") || n.contains(" PA ") || n.contains(" PS ")) return "PA/PS";
    if (n.contains(" INTERN")) return "Internações";
    if (n.contains(" CONSULT")) return "Consultas";
    if (n.contains(" EXAME ") || n.contains(" SADT ")) return "SADT";
    return null;
  }

  private String classificarGrupo(String valor) {
    String n = normalizarPalavras(valor);
    if (n.contains("RECURSO PROPRIO")) return "Recurso Próprio";
    if (n.contains("MEDICO COOPERADO")) return "Médico Cooperado";
    if (n.contains("CLINICA DE IMAGEM") || n.contains("IMAGEM")) return "Clínica de Imagem";
    if (n.contains("INTERCAMBIO")) return "Intercâmbio";
    if (n.contains("SESS") && n.contains("MULTI")) return "Sessões Multi";
    if (n.contains("HOME CARE") || n.contains("HOMECARE")) return "Home-Care";
    return "Outros";
  }

  private String regiao(Map<String, Object> linha) {
    String explicit = texto(linha, "REGIAO", "REGIAO_BENEFICIARIO", "REGIAO_ATENDIMENTO");
    String n = normalizarPalavras(explicit);
    if (n.contains("MODULO CORACAO")) return "Módulo Coração";
    if (n.contains("CENTRAL NACIONAL")) return "Central Nacional";
    if (n.contains("VALE")) return "Vale do Paraiba";
    if (n.contains("CENTRO OESTE")) return "Centro Oeste";
    if (n.contains("NORDESTE")) return "Nordeste";
    if (n.contains("NORTE")) return "Norte";
    if (n.contains("SUL") && !n.contains("SUDESTE")) return "Sul";
    if (n.contains("SUDESTE")) return "Sudeste (Fora Vale)";
    if (!n.isBlank()) return "Local";

    String uf = normalizar(texto(linha, "UF", "ESTADO", "UF_BENEFICIARIO", "UF_ATENDIMENTO"));
    if (Set.of("SP","RJ","MG","ES").contains(uf)) return "Sudeste (Fora Vale)";
    if (Set.of("DF","GO","MT","MS").contains(uf)) return "Centro Oeste";
    if (Set.of("PR","SC","RS").contains(uf)) return "Sul";
    if (Set.of("AC","AP","AM","PA","RO","RR","TO").contains(uf)) return "Norte";
    if (Set.of("AL","BA","CE","MA","PB","PE","PI","RN","SE").contains(uf)) return "Nordeste";
    return "Local";
  }

  private String identificadorBeneficiario(Map<String, Object> linha) {
    return texto(linha, "CODIGO_BENEFICIARIO", "COD_BENEFICIARIO", "BENEFICIARIO",
      "CARTEIRINHA", "CARTEIRA", "MATRICULA", "CODIGO");
  }

  private String especialidade(Map<String, Object> linha) {
    return texto(linha, "NOME_ESPECIALIDADE", "ESPECIALIDADE", "ESPECIALIDADE_MEDICA");
  }

  private String descricaoUtilizacao(Map<String, Object> linha) {
    return texto(linha, "DESCRICAO_PROCEDIMENTO", "DESCRICAO_ITEM", "PROCEDIMENTO",
      "DESC_PROCEDIMENTO", "NOME_PROCEDIMENTO", "TIPO_SESSAO");
  }

  private BigDecimal valorFinanceiro(Map<String, Object> linha) {
    Object valor = valor(linha, "VALOR_TOTAL", "TOTAL", "VALOR_PAGO", "VALOR", "DESPESA",
      "SINISTRO", "RECEITA", "VALOR_EVENTO", "PAGPD_VAL_LACTO", "VALOR_RECEBER");
    return numero(valor);
  }

  private long inteiro(Map<String, Object> linha, String... aliases) {
    return numero(valor(linha, aliases)).setScale(0, RoundingMode.HALF_UP).longValue();
  }

  private Object valor(Map<String, Object> linha, String... aliases) {
    for (String alias : aliases) {
      for (Map.Entry<String, Object> e : linha.entrySet()) {
        if (normalizar(e.getKey()).equals(normalizar(alias))) return e.getValue();
      }
    }
    return null;
  }

  private String texto(Map<String, Object> linha, String... aliases) {
    Object valor = valor(linha, aliases);
    return valor == null ? "" : String.valueOf(valor).trim();
  }

  private BigDecimal numero(Object valor) {
    if (valor == null) return ZERO;
    if (valor instanceof BigDecimal n) return n;
    if (valor instanceof Number n) return new BigDecimal(n.toString());
    String s = String.valueOf(valor).trim().replace("R$", "").replace(" ", "");
    if (s.isBlank()) return ZERO;
    if (s.matches("[-+]?\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?")) s = s.replace(".", "").replace(',', '.');
    else if (s.matches("[-+]?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?")) s = s.replace(",", "");
    else s = s.replace(',', '.');
    try {
      return new BigDecimal(s);
    } catch (NumberFormatException ex) {
      return ZERO;
    }
  }

  private Map<String, BigDecimal> mapaDecimal(List<String> chaves) {
    Map<String, BigDecimal> mapa = new LinkedHashMap<>();
    chaves.forEach(c -> mapa.put(c, ZERO));
    return mapa;
  }

  private String normalizar(String valor) {
    return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9]", "");
  }

  private String normalizarPalavras(String valor) {
    return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9]+", " ")
      .trim();
  }

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private record MesDados(
    YearMonth mes,
    BigDecimal receita,
    BigDecimal copart,
    BigDecimal sinistro,
    Map<String, BigDecimal> tipoGuia,
    Map<String, BigDecimal> grupoPrestador,
    Map<String, BigDecimal> receitaRegiao,
    Map<String, BigDecimal> despesaRegiao,
    Map<String, Integer> vidasRegiao,
    long beneficiariosAtivos,
    List<LinkedHashMap<String, Object>> despesaLinhas
  ) {}

  private record ContagemBeneficiarios(long ativos, long inativos) {}
  private record Ranking(String chave, BigDecimal valor) {}
  private record Serie(String nome, CellRangeAddress valores) {}

  private static void titulo(org.apache.poi.ss.usermodel.Sheet s, int r, int c1, int c2, String valor, CellStyle estilo) {
    Row row = linha(s, r);
    Cell cell = row.createCell(c1);
    cell.setCellValue(valor);
    cell.setCellStyle(estilo);
    if (c2 > c1) s.addMergedRegion(new CellRangeAddress(r, r, c1, c2));
  }

  private static void cabecalho(org.apache.poi.ss.usermodel.Sheet s, int r, String[] valores, Estilos e) {
    cabecalho(s, r, 0, valores, e);
  }

  private static void cabecalho(org.apache.poi.ss.usermodel.Sheet s, int r, int inicio, String[] valores, Estilos e) {
    for (int i = 0; i < valores.length; i++) texto(s, r, inicio + i, valores[i], e.cabecalho);
  }

  private static void texto(org.apache.poi.ss.usermodel.Sheet s, int r, int c, String valor, CellStyle estilo) {
    Cell cell = linha(s, r).createCell(c);
    cell.setCellValue(valor == null ? "" : valor);
    cell.setCellStyle(estilo);
  }

  private static void numero(org.apache.poi.ss.usermodel.Sheet s, int r, int c, BigDecimal valor, CellStyle estilo) {
    Cell cell = linha(s, r).createCell(c);
    cell.setCellValue(valor == null ? 0d : valor.doubleValue());
    cell.setCellStyle(estilo);
  }

  private static void inteiro(org.apache.poi.ss.usermodel.Sheet s, int r, int c, long valor, CellStyle estilo) {
    Cell cell = linha(s, r).createCell(c);
    cell.setCellValue(valor);
    cell.setCellStyle(estilo);
  }

  private static void data(org.apache.poi.ss.usermodel.Sheet s, int r, int c, LocalDate valor, CellStyle estilo) {
    Cell cell = linha(s, r).createCell(c);
    cell.setCellValue(valor);
    cell.setCellStyle(estilo);
  }

  private static void formula(org.apache.poi.ss.usermodel.Sheet s, int r, int c, String formula, CellStyle estilo) {
    Cell cell = linha(s, r).createCell(c);
    cell.setCellFormula(formula);
    cell.setCellStyle(estilo);
  }

  private static Row linha(org.apache.poi.ss.usermodel.Sheet s, int r) {
    Row row = s.getRow(r);
    return row == null ? s.createRow(r) : row;
  }

  private static String coluna(int zeroBased) {
    int n = zeroBased + 1;
    StringBuilder sb = new StringBuilder();
    while (n > 0) {
      int rem = (n - 1) % 26;
      sb.insert(0, (char) ('A' + rem));
      n = (n - 1) / 26;
    }
    return sb.toString();
  }

  private static boolean cellTemValor(org.apache.poi.ss.usermodel.Sheet s, int r, int c) {
    Row row = s.getRow(r);
    return row != null && row.getCell(c) != null;
  }

  private static final class Estilos {
    final CellStyle titulo;
    final CellStyle secao;
    final CellStyle cabecalho;
    final CellStyle cabecalhoMes;
    final CellStyle corpo;
    final CellStyle moeda;
    final CellStyle inteiro;
    final CellStyle percentual;
    final CellStyle mes;
    final CellStyle total;
    final CellStyle totalMoeda;
    final CellStyle totalInteiro;
    final CellStyle totalPercentual;

    Estilos(XSSFWorkbook wb) {
      DataFormat df = wb.createDataFormat();
      Font branca = wb.createFont(); branca.setBold(true); branca.setColor(IndexedColors.WHITE.getIndex());
      Font tituloFont = wb.createFont(); tituloFont.setBold(true); tituloFont.setFontHeightInPoints((short) 14);
      Font bold = wb.createFont(); bold.setBold(true);

      titulo = base(wb, IndexedColors.DARK_TEAL, branca, HorizontalAlignment.LEFT);
      titulo.setFont(tituloFont); tituloFont.setColor(IndexedColors.WHITE.getIndex());
      secao = base(wb, IndexedColors.DARK_TEAL, branca, HorizontalAlignment.LEFT);
      cabecalho = base(wb, IndexedColors.DARK_TEAL, branca, HorizontalAlignment.CENTER);
      cabecalhoMes = base(wb, IndexedColors.DARK_TEAL, branca, HorizontalAlignment.CENTER);
      cabecalhoMes.setDataFormat(df.getFormat("mmm-yy"));
      corpo = base(wb, IndexedColors.WHITE, null, HorizontalAlignment.LEFT);
      moeda = base(wb, IndexedColors.WHITE, null, HorizontalAlignment.RIGHT);
      moeda.setDataFormat(df.getFormat("#,##0.00"));
      inteiro = base(wb, IndexedColors.WHITE, null, HorizontalAlignment.RIGHT);
      inteiro.setDataFormat(df.getFormat("#,##0"));
      percentual = base(wb, IndexedColors.WHITE, null, HorizontalAlignment.RIGHT);
      percentual.setDataFormat(df.getFormat("0.00%"));
      mes = base(wb, IndexedColors.WHITE, null, HorizontalAlignment.CENTER);
      mes.setDataFormat(df.getFormat("mmm-yy"));

      total = base(wb, IndexedColors.LIGHT_GREEN, bold, HorizontalAlignment.LEFT);
      totalMoeda = base(wb, IndexedColors.LIGHT_GREEN, bold, HorizontalAlignment.RIGHT);
      totalMoeda.setDataFormat(df.getFormat("#,##0.00"));
      totalInteiro = base(wb, IndexedColors.LIGHT_GREEN, bold, HorizontalAlignment.RIGHT);
      totalInteiro.setDataFormat(df.getFormat("#,##0"));
      totalPercentual = base(wb, IndexedColors.LIGHT_GREEN, bold, HorizontalAlignment.RIGHT);
      totalPercentual.setDataFormat(df.getFormat("0.00%"));
    }

    private static CellStyle base(
      XSSFWorkbook wb,
      IndexedColors fill,
      Font font,
      HorizontalAlignment alignment
    ) {
      CellStyle style = wb.createCellStyle();
      style.setFillForegroundColor(fill.getIndex());
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      if (font != null) style.setFont(font);
      style.setAlignment(alignment);
      style.setVerticalAlignment(VerticalAlignment.CENTER);
      style.setBorderBottom(BorderStyle.THIN);
      style.setBorderTop(BorderStyle.THIN);
      style.setBorderLeft(BorderStyle.THIN);
      style.setBorderRight(BorderStyle.THIN);
      style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
      style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
      style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
      style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
      return style;
    }
  }
}
