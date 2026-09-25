/*
 * Responsabilidade: Transforma páginas retornadas pelo SGU nos formatos CSV, TXT e XLSX.
 */
package com.unimedlorena.tools.service;

import com.unimedlorena.tools.dto.RelatorioExportacaoRequest;
import com.unimedlorena.tools.exception.ApiException;
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
import org.springframework.beans.factory.annotation.Autowired;
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
    DECIMAL_FIXO,
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
  private final EspecialidadeRelatorioResolver especialidades;
  private final GrupoPrestadorComercialNormalizer gruposPrestador;
  private final int tamanhoLote;
  private final int maximoPaginas;

  @Autowired
  public ExportacaoRelatorioService(
    SguRelatorioService sgu,
    EspecialidadeRelatorioResolver especialidades,
    GrupoPrestadorComercialNormalizer gruposPrestador,
    @Value("${sgu.api.export.page-size:5000}") int tamanhoLote,
    @Value("${sgu.api.export.max-pages:0}") int maximoPaginas
  ) {
    this.sgu = sgu;
    this.especialidades = especialidades;
    this.gruposPrestador = gruposPrestador;
    this.tamanhoLote = Math.max(1, tamanhoLote);
    this.maximoPaginas = Math.max(0, maximoPaginas);
  }

  public ExportacaoRelatorioService(
    SguRelatorioService sgu,
    int tamanhoLote,
    int maximoPaginas
  ) {
    this(
      sgu,
      new EspecialidadeRelatorioResolver(),
      new GrupoPrestadorComercialNormalizer(),
      tamanhoLote,
      maximoPaginas
    );
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
  public int exportarPara(
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
    if (apiPossuiEspecialidade(apiNome)) {
      Arquivo arquivo = gerarArquivo(
        descricao.extensao(),
        carregarRegistros(apiNome, filtros)
      );
      destino.write(arquivo.conteudo());
      destino.flush();
      return arquivo.quantidadeRegistros();
    }
    int quantidade = switch (descricao.extensao()) {
      case "csv" -> escreverCsvPaginado(apiNome, filtros, destino, ';');
      case "txt" -> escreverCsvPaginado(apiNome, filtros, destino, ';');
      case "xlsx" -> escreverXlsxPaginado(apiNome, filtros, destino);
      default -> throw new IllegalStateException("Formato de exportação não suportado.");
    };
    if (quantidade == 0) throw semRegistros();

    log.info(
      "Exportação paginada concluída. api={}, formato={}, registros={}, duracaoMs={}",
      apiNome,
      descricao.extensao(),
      quantidade,
      (System.nanoTime() - inicio) / 1_000_000
    );
    return quantidade;
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
    return especialidades.normalizar(todos);
  }

  /**
   * Aplica os tratamentos centrais antes de devolver a prévia. O grupo do
   * prestador é corrigido por linha; especialidades exigem reunir todas as
   * páginas para resolver guias que atravessam páginas do SGU.
   */
  public Map<String, Object> executarPaginaNormalizada(
    String apiNome,
    Map<String, Object> parametros
  ) {
    Map<String, Object> recebidos = parametros == null
      ? new LinkedHashMap<>()
      : new LinkedHashMap<>(parametros);
    Map<String, Object> resposta = sgu.executar(apiNome, recebidos);
    List<LinkedHashMap<String, Object>> pagina = extrairRegistros(resposta.get("content"));
    int gruposCorrigidos = gruposPrestador.normalizar(apiNome, pagina);
    if (!especialidades.aplicavel(pagina)) {
      if (gruposCorrigidos == 0) return resposta;
      Map<String, Object> normalizada = new LinkedHashMap<>(resposta);
      normalizada.put("content", pagina);
      return normalizada;
    }

    int numeroPagina = inteiroPositivo(recebidos.get("page"), 1);
    int tamanhoPagina = inteiroPositivo(recebidos.get("size"), tamanhoLote);
    recebidos.remove("page");
    recebidos.remove("size");
    List<LinkedHashMap<String, Object>> todos = carregarRegistros(apiNome, recebidos);
    int inicio = Math.min(todos.size(), (numeroPagina - 1) * tamanhoPagina);
    int fim = Math.min(todos.size(), inicio + tamanhoPagina);
    int totalPaginas = todos.isEmpty() ? 0 : (todos.size() + tamanhoPagina - 1) / tamanhoPagina;

    Map<String, Object> normalizada = new LinkedHashMap<>(resposta);
    normalizada.put("content", new ArrayList<>(todos.subList(inicio, fim)));
    normalizada.put("numberOfElements", todos.size());
    normalizada.put("totalElements", todos.size());
    normalizada.put("totalPage", totalPaginas);
    normalizada.put("totalPages", totalPaginas);
    normalizada.put("number", numeroPagina - 1);
    normalizada.put("last", numeroPagina >= Math.max(1, totalPaginas));
    return normalizada;
  }

  private int inteiroPositivo(Object valor, int padrao) {
    if (valor == null) return padrao;
    try {
      return Math.max(1, Integer.parseInt(String.valueOf(valor)));
    } catch (NumberFormatException ex) {
      return padrao;
    }
  }

  private boolean apiPossuiEspecialidade(String apiNome) {
    Map<String, Object> resposta = sgu.listar(apiNome);
    Object conteudo = resposta == null ? null : resposta.get("content");
    if (!(conteudo instanceof List<?> itens)) return false;
    return itens.stream().filter(Map.class::isInstance).map(Map.class::cast)
      .filter(item -> apiNome.equalsIgnoreCase(texto(valorIgnorandoCaixa(item, "nome")).trim()))
      .map(item -> texto(valorIgnorandoCaixa(item, "consultaSQL")))
      .map(sql -> sql.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""))
      .anyMatch(sql -> sql.contains("NOMEESPECIALIDADE"));
  }

  private int percorrerPaginas(
    String apiNome,
    Map<String, Object> filtros,
    ConsumidorPagina consumidor
  ) throws IOException {
    List<LinkedHashMap<String, Object>> loteAnterior = null;
    boolean ultimaPaginaEncontrada = false;
    int quantidade = 0;
    Long totalEsperado = null;

    for (
      int pagina = 1;
      maximoPaginas == 0 || pagina <= maximoPaginas;
      pagina++
    ) {
      Map<String, Object> parametros = new LinkedHashMap<>();
      if (filtros != null) parametros.putAll(filtros);
      parametros.put("page", pagina);
      parametros.put("size", tamanhoLote);

      Map<String, Object> resposta = consultarPagina(apiNome, parametros, pagina);
      if (resposta == null || !(resposta.get("content") instanceof List<?>)) {
        throw respostaInvalida();
      }
      // O SGU usa strings e chama o total geral de numberOfElements.
      // Outros contratos paginados usam totalElements.
      Long total = numeroMetadado(resposta.get(resposta.containsKey("totalPage")
        ? "numberOfElements" : "totalElements"));
      Long paginas = numeroMetadado(resposta.get("totalPage"));
      if (total != null) {
        if (totalEsperado != null && !totalEsperado.equals(total)) throw respostaInvalida();
        totalEsperado = total;
      }
      boolean possuiRnum = false;
      int indice = 0;
      for (Object item : (List<?>) resposta.get("content")) {
        if (item instanceof Map<?, ?> mapa) {
          Long rnum = numeroMetadado(valorIgnorandoCaixa(mapa, "rnum"));
          if (rnum != null) {
            possuiRnum = true;
            if (rnum != (long) quantidade + indice + 1) throw respostaInvalida();
          }
        }
        indice++;
      }
      List<LinkedHashMap<String, Object>> lote = extrairRegistros(
        resposta.get("content")
      );
      gruposPrestador.normalizar(apiNome, lote);

      if (lote.isEmpty()) {
        if (Boolean.FALSE.equals(resposta.get("last")) ||
            (totalEsperado != null && quantidade != totalEsperado)) throw respostaInvalida();
        ultimaPaginaEncontrada = true;
        break;
      }

      // Protege contra endpoints que ignoram page/size e repetem eternamente.
      if (pagina > 1 && !possuiRnum && totalEsperado == null && lote.equals(loteAnterior)) {
        throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "PAGINA_REPETIDA",
          "A API repetiu a mesma página durante a exportação. " +
            "Verifique a paginação do endpoint."
        );
      }

      loteAnterior = lote;

      boolean possuiProximaPagina = resposta.get("last") instanceof Boolean ultima
        ? !ultima : paginas != null ? pagina < paginas : lote.size() >= tamanhoLote;
      if (pagina == 1 && possuiProximaPagina) {
        validarOrdenacaoPaginada(apiNome);
      }

      long inicioEscrita = System.nanoTime();
      consumidor.aceitar(lote);
      log.info("Página processada. pagina={}, registros={}, escritaMs={}",
        pagina, lote.size(), (System.nanoTime() - inicioEscrita) / 1_000_000);
      quantidade += lote.size();

      if (pagina == 1 || pagina % 10 == 0) {
        log.info(
          "Exportação em andamento. api={}, pagina={}, registros={}",
          apiNome,
          pagina,
          quantidade
        );
      }

      if (!possuiProximaPagina) {
        ultimaPaginaEncontrada = true;
        break;
      }
    }

    if (maximoPaginas > 0 && !ultimaPaginaEncontrada) {
      throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "LIMITE_EXPORTACAO",
        "O relatório atingiu o limite de páginas configurado no backend."
      );
    }

    if (totalEsperado != null && quantidade != totalEsperado) throw respostaInvalida();
    return quantidade;
  }

  private Long numeroMetadado(Object valor) {
    if (valor == null) return null;
    try {
      long numero = Long.parseLong(valor.toString());
      if (numero < 0) throw respostaInvalida();
      return numero;
    } catch (NumberFormatException ex) {
      throw respostaInvalida();
    }
  }

  private Map<String, Object> consultarPagina(
    String apiNome, Map<String, Object> parametros, int pagina
  ) throws IOException {
    for (int tentativa = 1; tentativa <= 2; tentativa++) {
      long inicio = System.nanoTime();
      try {
        Map<String, Object> resposta = sgu.executar(apiNome, parametros);
        log.info("Página consultada no SGU. pagina={}, tentativa={}, consultaMs={}",
          pagina, tentativa, (System.nanoTime() - inicio) / 1_000_000);
        return resposta;
      } catch (ApiException ex) {
        boolean temporaria =
          (ex.status().value() == 504 && "SGU_TIMEOUT".equals(ex.codigo())) ||
          ((ex.status().value() == 502 || ex.status().value() == 503) &&
            "SGU_INDISPONIVEL".equals(ex.codigo()));
        log.warn("Falha ao consultar página. pagina={}, tentativa={}, status={}, consultaMs={}",
          pagina, tentativa, ex.status().value(), (System.nanoTime() - inicio) / 1_000_000);
        if (!temporaria || tentativa == 2) throw ex;
        // Só consultas de exportação são repetidas, antes de entregar a página
        // ao escritor. Páginas anteriores não são consultadas nem gravadas de novo.
        aguardarNovaTentativa();
      }
    }
    throw new IllegalStateException("Consulta de página não concluída.");
  }

  private void validarOrdenacaoPaginada(String apiNome) {
    Map<String, Object> resposta = sgu.listar(apiNome);
    if (resposta == null || !(resposta.get("content") instanceof List<?> itens)) {
      throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "ORDENACAO_NAO_CONFIRMADA",
        "Não foi possível confirmar a ordenação da API " + apiNome +
          ". A exportação paginada foi interrompida para evitar linhas repetidas ou omitidas."
      );
    }

    Map<?, ?> definicao = itens.stream()
      .filter(Map.class::isInstance)
      .map(Map.class::cast)
      .filter(item -> apiNome.equalsIgnoreCase(texto(valorIgnorandoCaixa(item, "nome")).trim()))
      .findFirst()
      .orElse(null);

    String ordenacao = definicao == null
      ? ""
      : texto(valorIgnorandoCaixa(definicao, "ordenacao")).trim();

    if (ordenacao.isBlank()) {
      throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "ORDENACAO_AUSENTE",
        "A API " + apiNome + " não possui ordenação estável. " +
          "Relatórios com várias páginas não podem ser exportados sem ordenação, " +
          "pois o SGU pode repetir linhas de uma página em outra e omitir registros. " +
          "Abra a API na Central de Relatórios, salve uma ordenação determinística e gere novamente."
      );
    }
  }

  private Object valorIgnorandoCaixa(Map<?, ?> mapa, String chave) {
    for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
      if (chave.equalsIgnoreCase(String.valueOf(entrada.getKey()))) {
        return entrada.getValue();
      }
    }
    return null;
  }

  void aguardarNovaTentativa() throws IOException {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new java.io.InterruptedIOException("Exportação interrompida antes da nova tentativa.");
    }
  }

  /**
   * Converte uma lista já carregada no formato solicitado.
   */
  public Arquivo gerarArquivo(
    String formato,
    List<LinkedHashMap<String, Object>> registros
  ) throws IOException {
    return gerarArquivo(formato, registros, java.util.Set.of());
  }

  /** Metadados do Assistencial prevalecem sobre inferência por amostra. */
  public Arquivo gerarArquivo(String formato, List<LinkedHashMap<String, Object>> registros,
      java.util.Set<String> decimais) throws IOException {
    String tipo = formato == null ? "xlsx" : formato.toLowerCase(Locale.ROOT);

    List<LinkedHashMap<String, Object>> dados =
      registros == null ? List.of() : registros;
    if (dados.isEmpty() || colunas(dados).isEmpty()) throw semRegistros();

    return switch (tipo) {
      case "csv" -> new Arquivo(
        gerarCsv(dados, ';', decimais),
        "text/csv; charset=UTF-8",
        "csv",
        dados.size()
      );
      case "txt" -> new Arquivo(
        gerarCsv(dados, ';', decimais),
        "text/plain; charset=UTF-8",
        "txt",
        dados.size()
      );
      case "xlsx" -> new Arquivo(
        gerarXlsx(dados, decimais),
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
    if (!(content instanceof List<?> lista)) throw respostaInvalida();

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
        if (registro.isEmpty()) throw respostaInvalida();
        registros.add(registro);
      } else throw respostaInvalida();
    }
    return registros;
  }

  private ApiException respostaInvalida() {
    return new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "RESPOSTA_SGU_INVALIDA",
      "O SGU devolveu uma resposta inválida. Nenhum arquivo foi gerado; tente novamente ou informe a TI.");
  }

  private ApiException semRegistros() {
    return new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "RELATORIO_VAZIO",
      "Nenhum registro foi encontrado para os filtros informados. Revise os filtros e gere novamente.");
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
        validarColunas(estado, lote);

        for (Map<String, Object> registro : lote) {
          for (String coluna : estado.colunas) {
            printer.print(
              formatarTextoSeguro(registro.get(coluna), estado.tipos.get(coluna))
            );
          }
          printer.println();
        }

        // Descarrega a página no destino temporário, sem acumular o CSV em memória.
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
    SXSSFWorkbook workbook = new SXSSFWorkbook(100);
    try (workbook) {
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
      CellStyle decimalFixo = workbook.createCellStyle();
      decimalFixo.setDataFormat(formato.getFormat("#,##0.00"));
      CellStyle data = workbook.createCellStyle();
      data.setDataFormat(formato.getFormat("dd/mm/yyyy"));
      Map<TipoColuna, CellStyle> estilos = Map.of(
        TipoColuna.TEXTO,
        texto,
        TipoColuna.INTEIRO,
        inteiro,
        TipoColuna.DECIMAL,
        decimal,
        TipoColuna.DECIMAL_FIXO,
        decimalFixo,
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
        validarColunas(estado, lote);

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
            TipoColuna tipo = tipoDoValor(valor, estado.tipos.get(coluna));
            preencherCelulaSeguro(
              row.createCell(i),
              valor,
              tipo,
              estilos.get(tipo),
              texto
            );
            if (estado.larguras[i] < 60) estado.larguras[i] = Math.max(
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

      long inicioEmpacotamento = System.nanoTime();
      workbook.write(destino);
      destino.flush();
      log.info("XLSX empacotado. registros={}, empacotamentoMs={}",
        quantidade, (System.nanoTime() - inicioEmpacotamento) / 1_000_000);
      return quantidade;
    } finally {
      workbook.dispose();
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

  private void validarColunas(EstadoColunas estado, List<LinkedHashMap<String, Object>> lote) {
    if (!estado.colunas.containsAll(colunas(lote))) throw respostaInvalida();
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
      cell.setCellValue(texto(valor));
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
    char delimitador,
    java.util.Set<String> decimais
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
      Map<String, TipoColuna> tipos = inferirTipos(registros, colunas, decimais);
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
    return gerarCsv(registros, ';', java.util.Set.of());
  }

  private byte[] gerarXlsx(List<LinkedHashMap<String, Object>> registros, java.util.Set<String> decimais)
    throws IOException {
    // A janela de 100 linhas reduz memória durante relatórios extensos.
    SXSSFWorkbook workbook = new SXSSFWorkbook(100);
    try (workbook) {
      workbook.setCompressTempFiles(true);
      Sheet sheet = workbook.createSheet("Relatório");
      sheet.createFreezePane(0, 1);

      List<String> colunas = colunas(registros);
      Map<String, TipoColuna> tipos = inferirTipos(registros, colunas, decimais);
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
      CellStyle decimalFixo = workbook.createCellStyle();
      decimalFixo.setDataFormat(formato.getFormat("#,##0.00"));
      CellStyle data = workbook.createCellStyle();
      data.setDataFormat(formato.getFormat("dd/mm/yyyy"));
      Map<TipoColuna, CellStyle> estilos = Map.of(
        TipoColuna.TEXTO,
        texto,
        TipoColuna.INTEIRO,
        inteiro,
        TipoColuna.DECIMAL,
        decimal,
        TipoColuna.DECIMAL_FIXO,
        decimalFixo,
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
          TipoColuna tipo = tipoDoValor(valor, tipos.get(coluna));
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

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } finally {
      workbook.dispose();
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
      case DECIMAL_FIXO -> cell.setCellValue(
        converterDecimalFixo(valor).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue()
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
    return inferirTipos(registros, colunas, java.util.Set.of());
  }

  private Map<String, TipoColuna> inferirTipos(List<LinkedHashMap<String, Object>> registros,
      List<String> colunas, java.util.Set<String> decimais) {
    Map<String, TipoColuna> tipos = new LinkedHashMap<>();
    for (String coluna : colunas) {
      tipos.put(coluna, decimais.contains(coluna) ? TipoColuna.DECIMAL_FIXO : inferirTipo(registros, coluna));
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

  private TipoColuna tipoDoValor(Object valor, TipoColuna tipo) {
    if (tipo != TipoColuna.INTEIRO && tipo != TipoColuna.DECIMAL) return tipo;
    BigDecimal numero = tentarConverterNumero(valor);
    if (numero == null) return tipo;
    // O Excel armazena no máximo 15 algarismos significativos. Identificadores
    // tardios com zeros e números extensos precisam continuar como texto.
    if (numero.precision() > 15 || (valor instanceof CharSequence &&
        valor.toString().trim().matches("[+-]?0\\d+(?:[.,]\\d+)?"))) return TipoColuna.TEXTO;
    return tipo == TipoColuna.INTEIRO && numero.stripTrailingZeros().scale() > 0
      ? TipoColuna.DECIMAL : tipo;
  }

  private String formatarTexto(Object valor, TipoColuna tipo) {
    if (valor == null || texto(valor).isBlank()) return "";

    return switch (tipoDoValor(valor, tipo)) {
      case DATA -> DATA_BRASILEIRA.format(converterData(valor));
      case INTEIRO -> converterNumero(valor).setScale(0).toPlainString();
      case DECIMAL -> formatarDecimal(converterNumero(valor));
      case DECIMAL_FIXO -> converterDecimalFixo(valor).setScale(2, java.math.RoundingMode.HALF_UP)
        .toPlainString().replace('.', ',');
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

  private BigDecimal converterDecimalFixo(Object valor) {
    if (valor instanceof CharSequence) {
      String texto = valor.toString().trim();
      if (texto.matches("[-+]?\\d+(?:,\\d+)?")
          || texto.matches("[-+]?\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?")) {
        if (texto.contains(",")) return new BigDecimal(texto.replace(".", "").replace(',', '.'));
      }
      if (texto.matches("[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")) {
        return new BigDecimal(texto);
      }
    }
    return converterNumero(valor);
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
    if (original.matches("[-+]?\\d{1,3}(?:\\.\\d{3})+,\\d+")) {
      normalizado = original.replace(".", "").replace(',', '.');
    } else if (
      original.matches("[-+]?\\d{1,3}(?:,\\d{3})+\\.\\d+")
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
    return "RNUM".equalsIgnoreCase(texto(coluna).trim()) ||
      OrdenacaoRelatorio.COLUNA_TECNICA.equalsIgnoreCase(texto(coluna).trim());
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
