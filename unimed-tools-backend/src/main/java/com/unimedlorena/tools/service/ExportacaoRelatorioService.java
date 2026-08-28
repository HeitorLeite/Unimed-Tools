/*
 * Responsabilidade: Transforma páginas retornadas pelo SGU nos formatos CSV, TXT e XLSX.
 */
package com.unimedlorena.tools.service;

import com.unimedlorena.tools.dto.RelatorioExportacaoRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExportacaoRelatorioService {

  private static final Logger log = LoggerFactory.getLogger(
    ExportacaoRelatorioService.class
  );

  private enum TipoColuna {
    TEXTO,
    INTEIRO,
    DECIMAL,
    DATA,
    BOOLEANO,
  }

  private static class EstadoColunas {

    protected boolean inicializado;
    protected List<String> colunas = List.of();
    protected Map<String, TipoColuna> tipos = Map.of();
  }

  private static final class EstadoXlsx extends EstadoColunas {

    private int indiceLinha = 1;
    private int[] larguras = new int[0];
  }

  private static final DateTimeFormatter DATA_BRASILEIRA =
    DateTimeFormatter.ofPattern("dd/MM/yyyy");

  public record Arquivo(
    byte[] conteudo,
    String contentType,
    String extensao,
    int quantidadeRegistros
  ) {}

  public record DescricaoArquivo(String contentType, String extensao) {}

  @FunctionalInterface
  private interface ConsumidorPagina {
    void aceitar(List<LinkedHashMap<String, Object>> registros)
      throws IOException;
  }

  private final SguRelatorioService sgu;
  private final int tamanhoLote;
  private final int maximoPaginas;

  public ExportacaoRelatorioService(
    SguRelatorioService sgu,
    @Value("${sgu.api.export.page-size:1000}") int tamanhoLote,
    @Value("${sgu.api.export.max-pages:0}") int maximoPaginas
  ) {
    this.sgu = sgu;
    this.tamanhoLote = Math.max(1, tamanhoLote);
    this.maximoPaginas = Math.max(0, maximoPaginas);
  }

  public Arquivo exportar(
    String apiNome,
    String formato,
    RelatorioExportacaoRequest request
  ) throws IOException {
    List<LinkedHashMap<String, Object>> registros = carregarRegistros(
      apiNome,
      request == null ? null : request.filtros()
    );
    return gerarArquivo(formato, registros);
  }

  public DescricaoArquivo descreverArquivo(String formato) {
    String tipo = formato == null ? "xlsx" : formato.toLowerCase(Locale.ROOT);
    return switch (tipo) {
      case "csv" -> new DescricaoArquivo("text/csv; charset=UTF-8", "csv");
      case "txt" -> new DescricaoArquivo("text/plain; charset=UTF-8", "txt");
      case "xlsx" -> new DescricaoArquivo(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xlsx"
      );
      default -> throw new IllegalArgumentException(
        "Formato inválido. Use csv, txt ou xlsx."
      );
    };
  }

  /**
   * Exporta o relatório manual sem materializar todas as páginas nem criar uma
   * segunda cópia integral do arquivo em memória.
   */
  public void exportarPara(
    String apiNome,
    String formato,
    RelatorioExportacaoRequest request,
    OutputStream destino
  ) throws IOException {
    DescricaoArquivo descricao = descreverArquivo(formato);
    Map<String, Object> filtros = request == null ? null : request.filtros();

    log.info(
      "Iniciando exportação paginada. api={}, formato={}, lote={}",
      apiNome,
      descricao.extensao(),
      tamanhoLote
    );

    long inicio = System.nanoTime();
    int quantidade = switch (descricao.extensao()) {
      case "csv" -> escreverCsvPaginado(apiNome, filtros, destino, ';');
      case "txt" -> escreverCsvPaginado(apiNome, filtros, destino, ';');
      case "xlsx" -> escreverXlsxPaginado(apiNome, filtros, destino);
      default -> throw new IllegalStateException("Formato de exportação não suportado.");
    };

    log.info(
      "Exportação paginada concluída. api={}, formato={}, registros={}, duracaoMs={}",
      apiNome,
      descricao.extensao(),
      quantidade,
      (System.nanoTime() - inicio) / 1_000_000
    );
  }

  /**
   * Carrega todas as páginas de uma API do SGU. O método é público para que
   * a exportação em lote possa reunir vários valores do mesmo filtro em um
   * único arquivo final.
   */
  public List<LinkedHashMap<String, Object>> carregarRegistros(
    String apiNome,
    Map<String, Object> filtros
  ) {
    List<LinkedHashMap<String, Object>> todos = new ArrayList<>();
    try {
      percorrerPaginas(apiNome, filtros, todos::addAll);
    } catch (IOException ex) {
      // ArrayList.addAll não realiza I/O; esta exceção só existe pelo contrato
      // compartilhado com os escritores paginados.
      throw new IllegalStateException("Falha inesperada ao reunir as páginas.", ex);
    }
    return todos;
  }

  private int percorrerPaginas(
    String apiNome,
    Map<String, Object> filtros,
    ConsumidorPagina consumidor
  ) throws IOException {
    String assinaturaAnterior = null;
    boolean ultimaPaginaEncontrada = false;
    int quantidade = 0;

    for (
      int pagina = 1;
      maximoPaginas == 0 || pagina <= maximoPaginas;
      pagina++
    ) {
      Map<String, Object> parametros = new LinkedHashMap<>();
      if (filtros != null) parametros.putAll(filtros);
      parametros.put("page", pagina);
      parametros.put("size", tamanhoLote);

      Map<String, Object> resposta = sgu.executar(apiNome, parametros);
      List<LinkedHashMap<String, Object>> lote = extrairRegistros(
        resposta.get("content")
      );

      if (lote.isEmpty()) {
        ultimaPaginaEncontrada = true;
        break;
      }

      String assinatura = assinatura(lote);
      // Protege contra endpoints que ignoram page/size e repetem eternamente.
      if (pagina > 1 && assinatura.equals(assinaturaAnterior)) {
        throw new IllegalStateException(
          "A API repetiu a mesma página durante a exportação. " +
            "Verifique a paginação do endpoint."
        );
      }

      assinaturaAnterior = assinatura;
      consumidor.aceitar(lote);
      quantidade += lote.size();

      if (pagina == 1 || pagina % 10 == 0) {
        log.info(
          "Exportação em andamento. api={}, pagina={}, registros={}",
          apiNome,
          pagina,
          quantidade
        );
      }

      if (
        Boolean.TRUE.equals(resposta.get("last")) || lote.size() < tamanhoLote
      ) {
        ultimaPaginaEncontrada = true;
        break;
      }
    }

    if (maximoPaginas > 0 && !ultimaPaginaEncontrada) {
      throw new IllegalStateException(
        "O relatório atingiu o limite de páginas configurado no backend."
      );
    }

    return quantidade;
  }

  /**
   * Converte uma lista já carregada no formato solicitado.
   */
  public Arquivo gerarArquivo(
    String formato,
    List<LinkedHashMap<String, Object>> registros
  ) throws IOException {
    String tipo = formato == null ? "xlsx" : formato.toLowerCase(Locale.ROOT);

    List<LinkedHashMap<String, Object>> dados =
      registros == null ? List.of() : registros;

    return switch (tipo) {
      case "csv" -> new Arquivo(
        gerarCsv(dados, ';'),
        "text/csv; charset=UTF-8",
        "csv",
        dados.size()
      );
      case "txt" -> new Arquivo(
        gerarTxt(dados),
        "text/plain; charset=UTF-8",
        "txt",
        dados.size()
      );
      case "xlsx" -> new Arquivo(
        gerarXlsx(dados),
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xlsx",
        dados.size()
      );
      default -> throw new IllegalArgumentException(
        "Formato inválido. Use csv, txt ou xlsx."
      );
    };
  }

  private List<LinkedHashMap<String, Object>> extrairRegistros(Object content) {
    if (!(content instanceof List<?> lista)) return List.of();

    List<LinkedHashMap<String, Object>> registros = new ArrayList<>();
    for (Object item : lista) {
      if (item instanceof Map<?, ?> mapa) {
        LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
        mapa.forEach((chave, valor) -> {
          String nomeColuna = String.valueOf(chave);
          if (!ehColunaTecnicaPaginacao(nomeColuna)) {
            registro.put(nomeColuna, valor);
          }
        });
        registros.add(registro);
      }
    }
    return registros;
  }

  private String assinatura(List<LinkedHashMap<String, Object>> lote) {
    return lote.size() + "|" + lote.get(0) + "|" + lote.get(lote.size() - 1);
  }

  private int escreverCsvPaginado(
    String apiNome,
    Map<String, Object> filtros,
    OutputStream destino,
    char delimitador
  ) throws IOException {
    escreverBom(destino);
    EstadoColunas estado = new EstadoColunas();

    try (
      Writer writer = new OutputStreamWriter(destino, StandardCharsets.UTF_8);
      CSVPrinter printer = new CSVPrinter(
        writer,
        CSVFormat.DEFAULT.builder()
          .setDelimiter(delimitador)
          .setRecordSeparator("\r\n")
          .build()
      )
    ) {
      int quantidade = percorrerPaginas(apiNome, filtros, lote -> {
        if (!estado.inicializado) {
          inicializarColunas(estado, lote);
          for (String coluna : estado.colunas) {
            printer.print(neutralizarFormula(coluna));
          }
          printer.println();
        }

        for (Map<String, Object> registro : lote) {
          for (String coluna : estado.colunas) {
            printer.print(
              formatarTextoSeguro(registro.get(coluna), estado.tipos.get(coluna))
            );
          }
          printer.println();
        }

        // Cada lote enviado mantém a conexão ativa e libera memória cedo.
        printer.flush();
      });
      printer.flush();
      return quantidade;
    }
  }

  private int escreverXlsxPaginado(
    String apiNome,
    Map<String, Object> filtros,
    OutputStream destino
  ) throws IOException {
    try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
      workbook.setCompressTempFiles(true);
      Sheet sheet = workbook.createSheet("Relatório");
      sheet.createFreezePane(0, 1);

      CellStyle cabecalho = workbook.createCellStyle();
      Font fonte = workbook.createFont();
      fonte.setBold(true);
      cabecalho.setFont(fonte);
      cabecalho.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      DataFormat formato = workbook.createDataFormat();
      CellStyle texto = workbook.createCellStyle();
      texto.setDataFormat(formato.getFormat("@"));
      CellStyle inteiro = workbook.createCellStyle();
      inteiro.setDataFormat(formato.getFormat("#,##0"));
      CellStyle decimal = workbook.createCellStyle();
      decimal.setDataFormat(formato.getFormat("#,##0.00########"));
      CellStyle data = workbook.createCellStyle();
      data.setDataFormat(formato.getFormat("dd/mm/yyyy"));
      Map<TipoColuna, CellStyle> estilos = Map.of(
        TipoColuna.TEXTO,
        texto,
        TipoColuna.INTEIRO,
        inteiro,
        TipoColuna.DECIMAL,
        decimal,
        TipoColuna.DATA,
        data,
        TipoColuna.BOOLEANO,
        texto
      );

      EstadoXlsx estado = new EstadoXlsx();
      int quantidade = percorrerPaginas(apiNome, filtros, lote -> {
        if (!estado.inicializado) {
          inicializarColunas(estado, lote);
          estado.larguras = new int[estado.colunas.size()];
          Row header = sheet.createRow(0);
          for (int i = 0; i < estado.colunas.size(); i++) {
            String coluna = estado.colunas.get(i);
            Cell cell = header.createCell(i);
            cell.setCellValue(coluna);
            cell.setCellStyle(cabecalho);
            estado.larguras[i] = Math.max(12, coluna.length() + 3);
            sheet.setDefaultColumnStyle(i, estilos.get(estado.tipos.get(coluna)));
          }
        }

        for (Map<String, Object> registro : lote) {
          if (estado.indiceLinha >= 1_048_576) {
            throw new IllegalStateException(
              "O relatório ultrapassou o limite de linhas de uma planilha XLSX. " +
                "Exporte em CSV ou TXT."
            );
          }

          Row row = sheet.createRow(estado.indiceLinha++);
          for (int i = 0; i < estado.colunas.size(); i++) {
            String coluna = estado.colunas.get(i);
            Object valor = registro.get(coluna);
            TipoColuna tipo = estado.tipos.get(coluna);
            preencherCelulaSeguro(
              row.createCell(i),
              valor,
              tipo,
              estilos.get(tipo),
              texto
            );
            estado.larguras[i] = Math.max(
              estado.larguras[i],
              Math.min(60, formatarTextoSeguro(valor, tipo).length() + 2)
            );
          }
        }
      });

      if (!estado.inicializado) {
        sheet.createRow(0);
      }

      for (int i = 0; i < estado.colunas.size(); i++) {
        sheet.setColumnWidth(i, Math.min(60, estado.larguras[i]) * 256);
      }

      if (!estado.colunas.isEmpty()) {
        sheet.setAutoFilter(
          new org.apache.poi.ss.util.CellRangeAddress(
            0,
            Math.max(0, estado.indiceLinha - 1),
            0,
            estado.colunas.size() - 1
          )
        );
      }

      workbook.write(destino);
      destino.flush();
      workbook.dispose();
      return quantidade;
    }
  }

  private void inicializarColunas(
    EstadoColunas estado,
    List<LinkedHashMap<String, Object>> lote
  ) {
    estado.colunas = colunas(lote);
    estado.tipos = inferirTipos(lote, estado.colunas);
    estado.inicializado = true;
  }

  private void preencherCelulaSeguro(
    Cell cell,
    Object valor,
    TipoColuna tipo,
    CellStyle estilo,
    CellStyle estiloTexto
  ) {
    try {
      preencherCelula(cell, valor, tipo, estilo);
    } catch (IllegalArgumentException ex) {
      // Uma página posterior pode conter texto em uma coluna inicialmente
      // numérica ou de data; preservar o valor é melhor que abortar o arquivo.
      cell.setCellStyle(estiloTexto);
      cell.setCellValue(neutralizarFormula(texto(valor)));
    }
  }

  private String formatarTextoSeguro(Object valor, TipoColuna tipo) {
    try {
      return formatarTexto(valor, tipo);
    } catch (IllegalArgumentException ex) {
      return neutralizarFormula(texto(valor));
    }
  }

  private byte[] gerarCsv(
    List<LinkedHashMap<String, Object>> registros,
    char delimitador
  ) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    // O BOM melhora a abertura de conteúdo UTF-8 no Excel usado no escritório.
    escreverBom(out);

    try (
      Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
      CSVPrinter printer = new CSVPrinter(
        writer,
        CSVFormat.DEFAULT.builder()
          .setDelimiter(delimitador)
          .setRecordSeparator("\r\n")
          .build()
      )
    ) {
      List<String> colunas = colunas(registros);
      Map<String, TipoColuna> tipos = inferirTipos(registros, colunas);
      if (!colunas.isEmpty()) {
        for (String coluna : colunas) printer.print(neutralizarFormula(coluna));
        printer.println();
      }

      for (Map<String, Object> registro : registros) {
        for (String coluna : colunas) {
          printer.print(formatarTexto(registro.get(coluna), tipos.get(coluna)));
        }
        printer.println();
      }
    }

    return out.toByteArray();
  }

  private byte[] gerarTxt(List<LinkedHashMap<String, Object>> registros)
    throws IOException {
    // TXT e CSV compartilham o contrato delimitado por ponto e vírgula. O
    // CSVPrinter também protege campos que já contêm o próprio delimitador.
    return gerarCsv(registros, ';');
  }

  private byte[] gerarXlsx(List<LinkedHashMap<String, Object>> registros)
    throws IOException {
    // A janela de 100 linhas reduz memória durante relatórios extensos.
    try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
      workbook.setCompressTempFiles(true);
      Sheet sheet = workbook.createSheet("Relatório");
      sheet.createFreezePane(0, 1);

      List<String> colunas = colunas(registros);
      Map<String, TipoColuna> tipos = inferirTipos(registros, colunas);
      CellStyle cabecalho = workbook.createCellStyle();
      Font fonte = workbook.createFont();
      fonte.setBold(true);
      cabecalho.setFont(fonte);
      cabecalho.setFillForegroundColor(
        IndexedColors.GREY_25_PERCENT.getIndex()
      );
      cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      DataFormat formato = workbook.createDataFormat();
      CellStyle texto = workbook.createCellStyle();
      texto.setDataFormat(formato.getFormat("@"));
      CellStyle inteiro = workbook.createCellStyle();
      inteiro.setDataFormat(formato.getFormat("#,##0"));
      CellStyle decimal = workbook.createCellStyle();
      decimal.setDataFormat(formato.getFormat("#,##0.00########"));
      CellStyle data = workbook.createCellStyle();
      data.setDataFormat(formato.getFormat("dd/mm/yyyy"));
      Map<TipoColuna, CellStyle> estilos = Map.of(
        TipoColuna.TEXTO,
        texto,
        TipoColuna.INTEIRO,
        inteiro,
        TipoColuna.DECIMAL,
        decimal,
        TipoColuna.DATA,
        data,
        TipoColuna.BOOLEANO,
        texto
      );

      int[] larguras = new int[colunas.size()];

      Row header = sheet.createRow(0);
      for (int i = 0; i < colunas.size(); i++) {
        Cell cell = header.createCell(i);
        cell.setCellValue(colunas.get(i));
        cell.setCellStyle(cabecalho);
        larguras[i] = Math.max(12, colunas.get(i).length() + 3);
        sheet.setDefaultColumnStyle(i, estilos.get(tipos.get(colunas.get(i))));
      }

      int indiceLinha = 1;
      for (Map<String, Object> registro : registros) {
        Row row = sheet.createRow(indiceLinha++);
        for (int i = 0; i < colunas.size(); i++) {
          String coluna = colunas.get(i);
          Object valor = registro.get(coluna);
          TipoColuna tipo = tipos.get(coluna);
          preencherCelula(row.createCell(i), valor, tipo, estilos.get(tipo));
          larguras[i] = Math.max(
            larguras[i],
            Math.min(60, formatarTexto(valor, tipo).length() + 2)
          );
        }
      }

      for (int i = 0; i < colunas.size(); i++) {
        sheet.setColumnWidth(i, Math.min(60, larguras[i]) * 256);
      }

      if (!colunas.isEmpty()) {
        sheet.setAutoFilter(
          new org.apache.poi.ss.util.CellRangeAddress(
            0,
            Math.max(0, indiceLinha - 1),
            0,
            colunas.size() - 1
          )
        );
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      workbook.dispose();
      return out.toByteArray();
    }
  }

  private void preencherCelula(
    Cell cell,
    Object valor,
    TipoColuna tipo,
    CellStyle estilo
  ) {
    cell.setCellStyle(estilo);
    if (valor == null || texto(valor).isBlank()) return;

    switch (tipo) {
      case DATA -> cell.setCellValue(converterData(valor));
      case INTEIRO, DECIMAL -> cell.setCellValue(
        converterNumero(valor).doubleValue()
      );
      case BOOLEANO -> {
        cell.setCellType(CellType.BOOLEAN);
        cell.setCellValue((Boolean) valor);
      }
      case TEXTO -> cell.setCellValue(texto(valor));
    }
  }

  private Map<String, TipoColuna> inferirTipos(
    List<LinkedHashMap<String, Object>> registros,
    List<String> colunas
  ) {
    Map<String, TipoColuna> tipos = new LinkedHashMap<>();
    for (String coluna : colunas) {
      tipos.put(coluna, inferirTipo(registros, coluna));
    }
    return tipos;
  }

  private TipoColuna inferirTipo(
    List<LinkedHashMap<String, Object>> registros,
    String coluna
  ) {
    String nome = normalizarNomeColuna(coluna);
    boolean nomeDeData = nome.matches(".*(^|_)(DATA|DT|DTH)($|_).*");
    boolean nomeTextual = nome.matches(
      ".*(^|_)(COD|CODIGO|ID|CPF|CNPJ|CNES|CEP|CID|TUSS|GUIA|CONTRATO|" +
        "MATRICULA|CARTEIRA|PROTOCOLO|TELEFONE|COMPETENCIA|PERIODO|" +
        "REGISTRO|DOCUMENTO|DOC|SEQ|SEQUENCIA|TIPO|NUMERO|NRO|CHAVE|UF)($|_).*"
    );
    boolean nomeNumerico = nome.matches(
      ".*(^|_)(VALOR|QTD|QUANTIDADE|IDADE|TOTAL|PRECO|PERCENTUAL|TAXA)($|_).*"
    );

    if (nomeTextual && !nomeDeData) return TipoColuna.TEXTO;

    boolean possuiValor = false;
    boolean todosDatas = true;
    boolean todosNumeros = true;
    boolean todosBooleanos = true;
    boolean possuiNumeroNativo = false;
    boolean possuiParteDecimal = false;
    boolean numerosTextuaisSeguros = true;

    for (Map<String, Object> registro : registros) {
      Object valor = registro.get(coluna);
      if (valor == null || texto(valor).isBlank()) continue;

      possuiValor = true;
      todosDatas &= tentarConverterData(valor) != null;

      BigDecimal numero = tentarConverterNumero(valor);
      todosNumeros &= numero != null;
      if (numero != null && numero.stripTrailingZeros().scale() > 0) {
        possuiParteDecimal = true;
      }
      possuiNumeroNativo |= valor instanceof Number;
      if (valor instanceof CharSequence && numero != null) {
        String digitos = numero.abs().toBigInteger().toString();
        String original = valor.toString().trim().replaceFirst("^[+-]", "");
        boolean zeroAEsquerda = original.matches("0\\d+(?:[.,]\\d+)?");
        numerosTextuaisSeguros &= !zeroAEsquerda && digitos.length() <= 15;
      }
      todosBooleanos &= valor instanceof Boolean;
    }

    if (!possuiValor) return nomeDeData ? TipoColuna.DATA : TipoColuna.TEXTO;
    if (todosDatas && (nomeDeData || !todosNumeros)) return TipoColuna.DATA;
    if (todosBooleanos) return TipoColuna.BOOLEANO;
    if (
      todosNumeros &&
      (possuiNumeroNativo ||
        nomeNumerico ||
        possuiParteDecimal ||
        numerosTextuaisSeguros)
    ) {
      return possuiParteDecimal ? TipoColuna.DECIMAL : TipoColuna.INTEIRO;
    }
    return TipoColuna.TEXTO;
  }

  private String formatarTexto(Object valor, TipoColuna tipo) {
    if (valor == null || texto(valor).isBlank()) return "";

    return switch (tipo) {
      case DATA -> DATA_BRASILEIRA.format(converterData(valor));
      case INTEIRO -> converterNumero(valor).setScale(0).toPlainString();
      case DECIMAL -> formatarDecimal(converterNumero(valor));
      case BOOLEANO -> texto(valor);
      case TEXTO -> neutralizarFormula(texto(valor));
    };
  }

  private String neutralizarFormula(String valor) {
    if (valor.isEmpty()) return valor;
    String semEspacosIniciais = valor.stripLeading();
    if (semEspacosIniciais.isEmpty()) return valor;
    char primeiro = semEspacosIniciais.charAt(0);
    // CSV e TXT não distinguem texto de fórmula; o apóstrofo impede execução no Excel.
    return primeiro == '=' || primeiro == '+' || primeiro == '-' || primeiro == '@'
      ? "'" + valor
      : valor;
  }

  private String formatarDecimal(BigDecimal numero) {
    int escala = Math.max(2, Math.max(0, numero.stripTrailingZeros().scale()));
    return numero.setScale(escala).toPlainString().replace('.', ',');
  }

  private LocalDate converterData(Object valor) {
    LocalDate data = tentarConverterData(valor);
    if (data == null) {
      throw new IllegalArgumentException("Valor de data inválido na exportação.");
    }
    return data;
  }

  private LocalDate tentarConverterData(Object valor) {
    if (valor instanceof LocalDate data) return data;
    if (valor instanceof LocalDateTime dataHora) return dataHora.toLocalDate();
    if (valor instanceof OffsetDateTime dataHora) return dataHora.toLocalDate();
    if (valor instanceof java.sql.Date data) return data.toLocalDate();
    if (valor instanceof java.util.Date data)
      return data.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    if (!(valor instanceof CharSequence)) return null;

    String texto = valor.toString().trim();
    if (texto.isEmpty()) return null;

    List<DateTimeFormatter> formatos = List.of(
      DATA_BRASILEIRA,
      DateTimeFormatter.ISO_LOCAL_DATE,
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    );
    for (DateTimeFormatter formato : formatos) {
      try {
        return formato.parseBest(
          texto,
          LocalDateTime::from,
          LocalDate::from
        ) instanceof LocalDateTime dataHora
          ? dataHora.toLocalDate()
          : LocalDate.parse(texto, formato);
      } catch (DateTimeParseException ignored) {
        // Tenta o próximo formato aceito pelo contrato de exportação.
      }
    }

    try {
      return OffsetDateTime.parse(texto).toLocalDate();
    } catch (DateTimeParseException ignored) {
      try {
        return LocalDateTime.parse(texto).toLocalDate();
      } catch (DateTimeParseException ignoredAgain) {
        return null;
      }
    }
  }

  private BigDecimal converterNumero(Object valor) {
    BigDecimal numero = tentarConverterNumero(valor);
    if (numero == null) {
      throw new IllegalArgumentException("Valor numérico inválido na exportação.");
    }
    return numero;
  }

  private BigDecimal tentarConverterNumero(Object valor) {
    if (valor instanceof BigDecimal numero) return numero;
    if (valor instanceof Number numero) {
      try {
        return new BigDecimal(numero.toString());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    if (!(valor instanceof CharSequence)) return null;

    String original = valor.toString().trim();
    String normalizado;
    if (original.matches("[-+]?\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?")) {
      normalizado = original.replace(".", "").replace(',', '.');
    } else if (
      original.matches("[-+]?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?")
    ) {
      normalizado = original.replace(",", "");
    } else if (original.matches("[-+]?\\d+(?:[.,]\\d+)?")) {
      normalizado = original.replace(',', '.');
    } else {
      return null;
    }
    try {
      return new BigDecimal(normalizado);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String normalizarNomeColuna(String coluna) {
    String palavrasSeparadas = texto(coluna).replaceAll(
      "([a-z0-9])([A-Z])",
      "$1_$2"
    );
    return Normalizer.normalize(palavrasSeparadas, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9]+", "_")
      .replaceAll("^_+|_+$", "");
  }

  private List<String> colunas(List<LinkedHashMap<String, Object>> registros) {
    if (registros.isEmpty()) return List.of();

    LinkedHashSet<String> colunas = new LinkedHashSet<>();
    registros.forEach(registro ->
      registro
        .keySet()
        .stream()
        .filter(coluna -> !ehColunaTecnicaPaginacao(coluna))
        .forEach(colunas::add)
    );
    return new ArrayList<>(colunas);
  }

  private boolean ehColunaTecnicaPaginacao(String coluna) {
    return "RNUM".equalsIgnoreCase(texto(coluna).trim());
  }

  private String texto(Object valor) {
    return valor == null ? "" : String.valueOf(valor);
  }

  private void escreverBom(OutputStream out) throws IOException {
    out.write(0xEF);
    out.write(0xBB);
    out.write(0xBF);
  }
}
