/*
 * Responsabilidade: Gera, publica e executa com exclusividade a API reservada do relatório personalizado.
 */
package com.unimedlorena.tools.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest;

@Service
public class RelatorioPersonalizadoService {

  public static final String API_NOME = "0090-relatorio-personalizado";

  @Value("${relatorios.personalizado.api-nome:0090-relatorio-personalizado}")
  private String apiNome = API_NOME;

  public record Coluna(
      String id,
      String rotulo,
      String grupo,
      boolean selecionadaPorPadrao,
      boolean sensivel,
      String tipo,
      boolean separavelPorMes,
      boolean disponivelParaRanking) {
  }

  public record Opcao(String valor, String rotulo) {
  }

  public record Filtro(
      String id,
      String rotulo,
      String grupo,
      String tipo,
      String placeholder,
      boolean obrigatorio,
      List<Opcao> opcoes) {
  }

  public record Limites(
      int maximoColunas,
      int maximoMeses,
      int maximoLinhasPagina) {
  }

  public record Configuracao(
      String apiNome,
      String fonte,
      List<Coluna> colunas,
      List<Filtro> filtros,
      Limites limites) {
  }

  private static final int MAXIMO_MESES = 12;
  private static final int MAXIMO_LINHAS_PAGINA = 100;

  /*
   * A API no SGU é um recurso mutável compartilhado. O lock cobre publicação
   * e execução para impedir que outra solicitação troque o SQL no intervalo.
   */
  private static final ReentrantLock API_LOCK = new ReentrantLock(true);

  private final SguRelatorioService sgu;
  private final ExportacaoRelatorioService exportacao;
  private final RelatorioPersonalizadoSqlBuilder sqlBuilder;
  private RelatorioPersonalizadoSqlBuilder.ApiGerada ultimaApiPublicada;

  public RelatorioPersonalizadoService(
      SguRelatorioService sgu,
      ExportacaoRelatorioService exportacao,
      RelatorioPersonalizadoSqlBuilder sqlBuilder) {
    this.sgu = sgu;
    this.exportacao = exportacao;
    this.sqlBuilder = sqlBuilder;
  }

  public Configuracao configuracao() {
    List<Coluna> colunas = sqlBuilder
        .campos()
        .stream()
        .map(campo -> new Coluna(
            campo.id(),
            campo.rotulo(),
            campo.grupo(),
            campo.selecionadaPorPadrao(),
            campo.sensivel(),
            sqlBuilder.tipoCampo(campo.id()),
            sqlBuilder.campoSeparavelPorMes(campo.id()),
            sqlBuilder.campoRanking(campo.id())))
        .toList();

    List<Filtro> filtros = sqlBuilder
        .filtros()
        .stream()
        // O catálogo de empresas substitui a busca textual livre por nome.
        .filter(filtro -> !"nome_empresa".equals(filtro.id()))
        .map(filtro -> new Filtro(
            filtro.id(),
            filtro.rotulo(),
            filtro.grupo(),
            filtro.tipoTela(),
            filtro.placeholder(),
            filtro.obrigatorio(),
            List.of()))
        .toList();

    return new Configuracao(
        apiNome,
        "Despesas, receita e sinistralidade",
        colunas,
        filtros,
        new Limites(colunas.size(), MAXIMO_MESES, MAXIMO_LINHAS_PAGINA));
  }

  public boolean ehApiReservada(String nome) {
    if (nome == null) return false;
    String valor = nome.trim();
    return API_NOME.equalsIgnoreCase(valor) || apiNome.equalsIgnoreCase(valor);
  }

  public String apiNome() {
    return apiNome;
  }

  public Map<String, Object> executar(RelatorioPersonalizadoRequest request) {
    RequisicaoNormalizada normalizada = normalizar(request, true);

    API_LOCK.lock();
    try {
      publicarApi(normalizada);

      if (normalizada.analiseAvancada()) {
        List<LinkedHashMap<String, Object>> registros = carregarETransformar(normalizada);
        return paginarAnalise(registros, normalizada);
      }

      Map<String, Object> parametros = parametrosSgu(normalizada.filtros());
      parametros.put("page", normalizada.pagina());
      parametros.put("size", normalizada.tamanhoPagina());

      Map<String, Object> resposta = sgu.executar(apiNome, parametros);
      Map<String, Object> projetada = new LinkedHashMap<>(resposta);
      projetada.put(
          "content",
          projetarRegistros(resposta.get("content"), normalizada.colunas()));
      projetada.put("colunas", normalizada.colunas());
      return projetada;
    } finally {
      API_LOCK.unlock();
    }
  }

  public ExportacaoRelatorioService.Arquivo exportar(
      String formato,
      RelatorioPersonalizadoRequest request) throws IOException {
    RequisicaoNormalizada normalizada = normalizar(request, false);

    API_LOCK.lock();
    try {
      publicarApi(normalizada);

      List<LinkedHashMap<String, Object>> registros;
      if (normalizada.analiseAvancada()) {
        registros = carregarETransformar(normalizada);
      } else {
        /*
         * O lock permanece durante todas as páginas. Sem isso, outra consulta
         * poderia substituir a definição no meio da exportação.
         */
        registros = projetarRegistros(
            exportacao.carregarRegistros(
                API_NOME,
                parametrosSgu(normalizada.filtros())),
            normalizada.colunas());
      }
      return exportacao.gerarArquivo(formato, registros);
    } finally {
      API_LOCK.unlock();
    }
  }

  private void publicarApi(RequisicaoNormalizada normalizada) {
    boolean transformarNoBackend = normalizada.analiseAvancada();
    RelatorioPersonalizadoSqlBuilder.ApiGerada gerada = sqlBuilder.gerar(
        colunasConsulta(normalizada),
        normalizada.filtros().keySet(),
        transformarNoBackend ? false : normalizada.distinct(),
        transformarNoBackend ? null : normalizada.ordenarPor(),
        transformarNoBackend ? null : normalizada.direcaoOrdenacao());

    /*
     * Paginação e repetições com a mesma estrutura não precisam republicar a
     * API reservada. O acesso ocorre dentro de API_LOCK; se colunas ou filtros
     * ativos mudarem, ApiGerada também muda e a publicação é refeita.
     */
    if (gerada.equals(ultimaApiPublicada)) {
      return;
    }

    Map<String, Object> definicao = new LinkedHashMap<>();
    definicao.put("nome", apiNome);
    /*
     * O PL/SQL ins_atu_query_api possui buffers internos menores que um CLOB.
     * A consulta gerada é compactada antes da publicação para não desperdiçar
     * esse buffer com indentação e quebras de linha.
     */
    definicao.put("consultaSQL", compactarSql(gerada.consultaSql()));
    definicao.put("ordenacao", gerada.ordenacao());
    definicao.put("filtros", gerada.filtros());

    // ins_atu_query_api atualiza a definição existente com o mesmo nome.
    sgu.criarOuAtualizar(definicao);
    ultimaApiPublicada = gerada;
  }

  private RequisicaoNormalizada normalizar(
      RelatorioPersonalizadoRequest request,
      boolean paginado) {
    if (request == null) {
      throw new IllegalArgumentException(
          "Informe a configuração do relatório personalizado.");
    }

    List<String> colunas = normalizarColunas(request.colunas());
    Map<String, Object> filtros = normalizarFiltros(request.filtros());
    int pagina = paginado
        ? limitar(request.pagina(), 1, 10_000, 1, "Página")
        : 1;
    int tamanho = paginado
        ? limitar(
            request.tamanhoPagina(),
            1,
            MAXIMO_LINHAS_PAGINA,
            50,
            "Tamanho da página")
        : MAXIMO_LINHAS_PAGINA;
    boolean distinct = Boolean.TRUE.equals(request.distinct());
    boolean separarMeses = Boolean.TRUE.equals(request.separarMeses());
    List<String> metricasPorMes = normalizarMetricasPorMes(
        request.metricasPorMes(),
        colunas,
        separarMeses);
    RankingNormalizado ranking = normalizarRanking(request.ranking(), colunas);
    boolean analiseAvancada = separarMeses || ranking != null;

    String ordenarPor = normalizarOrdenarPor(
        request.ordenarPor(),
        colunas,
        analiseAvancada);
    String direcaoOrdenacao = normalizarDirecaoOrdenacao(
        request.direcaoOrdenacao(),
        ordenarPor);
    List<String> ordemResultado = request.ordemResultado() == null
        ? List.of()
        : request.ordemResultado().stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(valor -> !valor.isBlank())
            .map(valor -> valor.toUpperCase(Locale.ROOT))
            .distinct()
            .toList();

    return new RequisicaoNormalizada(
        colunas,
        filtros,
        distinct,
        ordenarPor,
        direcaoOrdenacao,
        separarMeses,
        metricasPorMes,
        ranking,
        ordemResultado,
        pagina,
        tamanho);
  }

  private List<String> normalizarMetricasPorMes(
      List<String> recebidas,
      List<String> colunasSelecionadas,
      boolean separarMeses) {
    if (!separarMeses) {
      return List.of();
    }

    LinkedHashSet<String> metricas = new LinkedHashSet<>();
    if (recebidas != null) {
      for (String recebida : recebidas) {
        String id = recebida == null
            ? ""
            : recebida.trim().toUpperCase(Locale.ROOT);
        if (!colunasSelecionadas.contains(id)) {
          throw new IllegalArgumentException(
              "As colunas separadas por mês precisam estar selecionadas no relatório.");
        }
        if (!sqlBuilder.campoSeparavelPorMes(id)) {
          throw new IllegalArgumentException(
              "A coluna “" + id + "” não pode ser separada por mês.");
        }
        metricas.add(id);
      }
    }

    if (metricas.isEmpty()) {
      throw new IllegalArgumentException(
          "Selecione pelo menos uma coluna numérica para separar por mês.");
    }
    return List.copyOf(metricas);
  }

  private RankingNormalizado normalizarRanking(
      RelatorioPersonalizadoRequest.Ranking recebido,
      List<String> colunasSelecionadas) {
    if (recebido == null) {
      return null;
    }

    String modo = recebido.modo() == null
        ? ""
        : recebido.modo().trim().toUpperCase(Locale.ROOT);
    if (!Set.of("MAIORES", "MENORES").contains(modo)) {
      throw new IllegalArgumentException(
          "O ranking deve usar MAIORES ou MENORES.");
    }

    int quantidade = limitar(
        recebido.quantidade(),
        1,
        1000,
        10,
        "Quantidade do ranking");

    String dimensao = normalizarIdColunaAnalitica(
        recebido.dimensao(),
        "dimensão do ranking",
        colunasSelecionadas);
    String metrica = normalizarIdColunaAnalitica(
        recebido.metrica(),
        "métrica do ranking",
        colunasSelecionadas);

    if (!sqlBuilder.campoRanking(metrica)) {
      throw new IllegalArgumentException(
          "A métrica do ranking deve ser uma coluna do grupo Valores.");
    }
    if (dimensao.equals(metrica)) {
      throw new IllegalArgumentException(
          "A dimensão e a métrica do ranking precisam ser diferentes.");
    }

    return new RankingNormalizado(modo, quantidade, dimensao, metrica);
  }

  private String normalizarIdColunaAnalitica(
      String recebido,
      String descricao,
      List<String> colunasSelecionadas) {
    String id = recebido == null
        ? ""
        : recebido.trim().toUpperCase(Locale.ROOT);
    if (!colunasSelecionadas.contains(id)) {
      throw new IllegalArgumentException(
          "A " + descricao + " deve estar entre as colunas selecionadas.");
    }
    return id;
  }

  private String normalizarOrdenarPor(
      String recebido,
      List<String> colunasSelecionadas,
      boolean analiseAvancada) {
    if (recebido == null || recebido.isBlank()) {
      return null;
    }
    String coluna = recebido.trim().toUpperCase(Locale.ROOT);
    if (!analiseAvancada && !colunasSelecionadas.contains(coluna)) {
      throw new IllegalArgumentException(
          "A coluna de ordenação deve estar entre as colunas selecionadas.");
    }
    return coluna;
  }

  private String normalizarDirecaoOrdenacao(
      String recebida,
      String ordenarPor) {
    if (ordenarPor == null) {
      return null;
    }
    String direcao = recebida == null
        ? "ASC"
        : recebida.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("ASC", "DESC").contains(direcao)) {
      throw new IllegalArgumentException(
          "Direção de ordenação deve ser ASC ou DESC.");
    }
    return direcao;
  }

  private List<String> normalizarColunas(List<String> solicitadas) {
    if (solicitadas == null || solicitadas.isEmpty()) {
      throw new IllegalArgumentException("Selecione pelo menos uma coluna.");
    }

    LinkedHashSet<String> unicas = new LinkedHashSet<>();
    for (String coluna : solicitadas) {
      String id = coluna == null
          ? ""
          : coluna.trim().toUpperCase(Locale.ROOT);
      if (sqlBuilder.campo(id) == null) {
        throw new IllegalArgumentException("Coluna não permitida: " + id + ".");
      }
      unicas.add(id);
    }

    if (unicas.size() > sqlBuilder.campos().size()) {
      throw new IllegalArgumentException("A quantidade de colunas é inválida.");
    }
    return List.copyOf(unicas);
  }

  private Map<String, Object> normalizarFiltros(Map<String, Object> recebidos) {
    Map<String, Object> filtros = new LinkedHashMap<>();
    Map<String, Object> origem = recebidos == null ? Map.of() : recebidos;
    Map<String, Object> origemNormalizada = new LinkedHashMap<>();

    for (Map.Entry<String, Object> informado : origem.entrySet()) {
      RelatorioPersonalizadoSqlBuilder.Filtro filtro = sqlBuilder.filtro(informado.getKey());
      if (filtro == null) {
        throw new IllegalArgumentException(
            "Filtro não permitido: " + informado.getKey() + ".");
      }
      if (origemNormalizada.containsKey(filtro.id())) {
        throw new IllegalArgumentException(
            "O filtro “" + filtro.rotulo() + "” foi informado mais de uma vez.");
      }
      origemNormalizada.put(filtro.id(), informado.getValue());
    }

    for (RelatorioPersonalizadoSqlBuilder.Filtro filtro : sqlBuilder.filtros()) {
      Object bruto = origemNormalizada.get(filtro.id());
      String texto = bruto == null ? "" : String.valueOf(bruto).trim();
      if (texto.isBlank()) {
        if (filtro.obrigatorio()) {
          throw new IllegalArgumentException(
              "Preencha o filtro obrigatório “" + filtro.rotulo() + "”.");
        }
        continue;
      }
      filtros.put(filtro.id(), normalizarValor(filtro, texto));
    }

    validarIntervaloCompetencias(filtros);
    validarIntervaloDatas(filtros);
    validarIntervaloValores(filtros);
    return filtros;
  }

  private Object normalizarValor(
      RelatorioPersonalizadoSqlBuilder.Filtro filtro,
      String texto) {
    if (texto.length() > 240) {
      throw new IllegalArgumentException(
          "O filtro “" + filtro.rotulo() + "” excede o tamanho permitido.");
    }

    Object valor = switch (filtro.tipoTela()) {
      case "competencia" -> validarCompetencia(texto, filtro.rotulo());
      case "number" -> validarInteiro(texto, filtro.rotulo());
      case "decimal" -> validarDecimal(texto, filtro.rotulo());
      case "date" -> validarData(texto, filtro.rotulo());
      default -> texto;
    };
    return normalizarValorParaAlias(filtro.id(), valor);
  }

  /**
   * As transformações ficam no backend para que o SGU receba filtros formados
   * somente por alias, operador e bind, sem funções no conteudoFiltro.
   */
  private Object normalizarValorParaAlias(String id, Object valor) {
    if (!(valor instanceof String texto)) {
      return valor;
    }

    return switch (id) {
      case "codigo_beneficiario" -> texto.replace(".", "");
      case "codigo_empresa" -> normalizarCodigosEmpresa(texto);
      case "id_guia" -> normalizarIdsGuia(texto);
      case "cpf" -> texto.replaceAll("[^0-9]", "");
      case "cid" -> texto.toUpperCase(Locale.ROOT);
      case "grupo_beneficiario" -> normalizarGrupoBeneficiario(texto);
      case "nome_beneficiario", "nome_empresa", "nome_prestador",
          "grupo_prestador", "descricao_item", "tipo_procedimento" ->
          "%" + texto.toUpperCase(Locale.ROOT) + "%";
      default -> texto;
    };
  }

  private String normalizarCodigosEmpresa(String texto) {
    String[] partes = texto.split(",", -1);
    LinkedHashSet<String> codigos = new LinkedHashSet<>();

    for (String parte : partes) {
      String codigo = parte.trim();
      if (!codigo.matches("\\d+")) {
        throw new IllegalArgumentException(
            "Código da empresa deve conter um ou mais números separados por vírgula.");
      }
      try {
        codigos.add(Long.valueOf(codigo).toString());
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
            "Código da empresa contém um número inválido.",
            ex);
      }
    }

    String listaNormalizada = "," + String.join(",", codigos) + ",";
    if (listaNormalizada.length() > 240) {
      throw new IllegalArgumentException(
          "O filtro “Código da empresa” excede o tamanho permitido.");
    }
    return listaNormalizada;
  }

  private String normalizarIdsGuia(String texto) {
    String[] partes = texto.split(",", -1);
    LinkedHashSet<String> ids = new LinkedHashSet<>();

    for (String parte : partes) {
      String id = parte.trim();
      if (!id.matches("\\d+")) {
        throw new IllegalArgumentException(
            "ID da guia deve conter um ou mais números separados por vírgula.");
      }
      try {
        ids.add(Long.valueOf(id).toString());
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
            "ID da guia contém um número inválido.",
            ex);
      }
    }

    String listaNormalizada = "," + String.join(",", ids) + ",";
    if (listaNormalizada.length() > 240) {
      throw new IllegalArgumentException(
          "O filtro “ID da guia” excede o tamanho permitido.");
    }
    return listaNormalizada;
  }

  private String normalizarGrupoBeneficiario(String texto) {
    if (!texto.matches("\\d+")) {
      return "%|N:%" + texto.toUpperCase(Locale.ROOT) + "%";
    }

    try {
      return "%|C:" + Long.parseLong(texto) + "|%";
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "Grupo do beneficiário deve conter um código válido ou parte do nome.",
          ex);
    }
  }

  private Integer validarCompetencia(String texto, String rotulo) {
    if (!texto.matches("\\d{6}")) {
      throw new IllegalArgumentException(
          rotulo + " deve usar o formato AAAAMM.");
    }
    int valor = Integer.parseInt(texto);
    int mes = valor % 100;
    if (mes < 1 || mes > 12) {
      throw new IllegalArgumentException(rotulo + " possui um mês inválido.");
    }
    return valor;
  }

  private Long validarInteiro(String texto, String rotulo) {
    if (!texto.matches("\\d+")) {
      throw new IllegalArgumentException(
          rotulo + " deve conter somente números.");
    }
    try {
      return Long.valueOf(texto);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          rotulo + " deve conter um número válido.",
          ex);
    }
  }

  private BigDecimal validarDecimal(String texto, String rotulo) {
    String normalizado = texto.contains(",")
        ? texto.replace(".", "").replace(',', '.')
        : texto;
    if (!normalizado.matches("\\d+(\\.\\d{1,2})?")) {
      throw new IllegalArgumentException(
          rotulo +
              " deve conter um valor não negativo com até duas casas decimais.");
    }
    try {
      return new BigDecimal(normalizado);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          rotulo + " deve conter um valor válido.",
          ex);
    }
  }

  private String validarData(String texto, String rotulo) {
    try {
      return LocalDate.parse(texto).toString();
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(
          rotulo + " deve conter uma data válida.",
          ex);
    }
  }

  private void validarIntervaloCompetencias(Map<String, Object> filtros) {
    int inicio = (Integer) filtros.get("competencia_inicio");
    int fim = (Integer) filtros.get("competencia_fim");
    int indiceInicio = (inicio / 100) * 12 + (inicio % 100);
    int indiceFim = (fim / 100) * 12 + (fim % 100);
    if (indiceFim < indiceInicio) {
      throw new IllegalArgumentException(
          "A competência final não pode ser anterior à inicial.");
    }
    if (indiceFim - indiceInicio + 1 > MAXIMO_MESES) {
      throw new IllegalArgumentException(
          "O intervalo deve possuir no máximo " + MAXIMO_MESES + " meses.");
    }
  }

  private void validarIntervaloDatas(Map<String, Object> filtros) {
    if (filtros.containsKey("data_guia_inicio") &&
        filtros.containsKey("data_guia_fim")) {
      LocalDate inicio = LocalDate.parse(
          String.valueOf(filtros.get("data_guia_inicio")));
      LocalDate fim = LocalDate.parse(
          String.valueOf(filtros.get("data_guia_fim")));
      if (fim.isBefore(inicio)) {
        throw new IllegalArgumentException(
            "A data final da guia não pode ser anterior à inicial.");
      }
    }
  }

  private void validarIntervaloValores(Map<String, Object> filtros) {
    if (filtros.containsKey("valor_minimo") &&
        filtros.containsKey("valor_maximo")) {
      BigDecimal minimo = (BigDecimal) filtros.get("valor_minimo");
      BigDecimal maximo = (BigDecimal) filtros.get("valor_maximo");
      if (maximo.compareTo(minimo) < 0) {
        throw new IllegalArgumentException(
            "O valor máximo não pode ser menor que o mínimo.");
      }
    }
  }

  private List<LinkedHashMap<String, Object>> projetarRegistros(
      Object conteudo,
      List<String> colunas) {
    if (!(conteudo instanceof List<?> lista))
      return List.of();
    List<LinkedHashMap<String, Object>> projetados = new ArrayList<>();

    for (Object item : lista) {
      if (!(item instanceof Map<?, ?> registro))
        continue;
      LinkedHashMap<String, Object> normalizado = new LinkedHashMap<>();
      Map<String, Object> porChave = new LinkedHashMap<>();
      registro.forEach((chave, valor) -> porChave.put(String.valueOf(chave).toUpperCase(Locale.ROOT), valor));
      colunas.forEach(coluna -> normalizado.put(coluna, porChave.get(coluna)));
      projetados.add(normalizado);
    }
    return projetados;
  }

  private List<String> colunasConsulta(RequisicaoNormalizada normalizada) {
    LinkedHashSet<String> colunas = new LinkedHashSet<>(normalizada.colunas());
    if (normalizada.separarMeses()) {
      // A competência é necessária para montar as colunas mensais, mas não é
      // exibida como coluna normal quando o modo mensal está ativo.
      colunas.add("PERIODO");
    }
    return List.copyOf(colunas);
  }

  private List<LinkedHashMap<String, Object>> carregarETransformar(
      RequisicaoNormalizada normalizada) {
    List<LinkedHashMap<String, Object>> registros = projetarRegistros(
        exportacao.carregarRegistros(
            apiNome,
            parametrosSgu(normalizada.filtros())),
        colunasConsulta(normalizada));

    if (normalizada.ranking() != null) {
      registros = aplicarRanking(registros, normalizada.ranking());
    }
    if (normalizada.separarMeses()) {
      registros = pivotarMeses(registros, normalizada);
    } else {
      registros = projetarRegistros(registros, normalizada.colunas());
    }
    if (normalizada.distinct()) {
      registros = new ArrayList<>(new LinkedHashSet<>(registros));
    }

    ordenarAnalise(registros, normalizada);
    return reordenarMapas(registros, colunasResultado(normalizada));
  }

  private List<LinkedHashMap<String, Object>> reordenarMapas(
      List<LinkedHashMap<String, Object>> registros,
      List<String> colunas) {
    List<LinkedHashMap<String, Object>> ordenados = new ArrayList<>();
    for (LinkedHashMap<String, Object> registro : registros) {
      LinkedHashMap<String, Object> novo = new LinkedHashMap<>();
      colunas.forEach(coluna -> novo.put(coluna, registro.get(coluna)));
      ordenados.add(novo);
    }
    return ordenados;
  }

  private List<LinkedHashMap<String, Object>> aplicarRanking(
      List<LinkedHashMap<String, Object>> registros,
      RankingNormalizado ranking) {
    Map<String, BigDecimal> totais = new LinkedHashMap<>();
    for (LinkedHashMap<String, Object> registro : registros) {
      String chave = Objects.toString(registro.get(ranking.dimensao()), "");
      totais.merge(
          chave,
          numero(registro.get(ranking.metrica())),
          BigDecimal::add);
    }

    Comparator<Map.Entry<String, BigDecimal>> comparador =
        Map.Entry.comparingByValue();
    if ("MAIORES".equals(ranking.modo())) {
      comparador = comparador.reversed();
    }

    Set<String> selecionados = totais.entrySet().stream()
        .sorted(comparador.thenComparing(Map.Entry::getKey))
        .limit(ranking.quantidade())
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    return registros.stream()
        .filter(registro -> selecionados.contains(
            Objects.toString(registro.get(ranking.dimensao()), "")))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  private List<LinkedHashMap<String, Object>> pivotarMeses(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    List<String> dimensoes = normalizada.colunas().stream()
        .filter(coluna -> !"PERIODO".equals(coluna))
        .filter(coluna -> !normalizada.metricasPorMes().contains(coluna))
        .toList();
    List<Integer> competencias = competencias(normalizada.filtros());
    List<String> colunasMensais = colunasMensais(
        normalizada.metricasPorMes(),
        competencias);

    Map<List<String>, LinkedHashMap<String, Object>> agrupados =
        new LinkedHashMap<>();

    for (LinkedHashMap<String, Object> registro : registros) {
      List<String> chave = dimensoes.stream()
          .map(coluna -> Objects.toString(registro.get(coluna), ""))
          .toList();

      LinkedHashMap<String, Object> destino = agrupados.computeIfAbsent(
          chave,
          ignorada -> {
            LinkedHashMap<String, Object> novo = new LinkedHashMap<>();
            dimensoes.forEach(coluna -> novo.put(coluna, registro.get(coluna)));
            colunasMensais.forEach(coluna -> novo.put(coluna, BigDecimal.ZERO));
            return novo;
          });

      Integer competencia = competencia(registro.get("PERIODO"));
      if (competencia == null || !competencias.contains(competencia)) {
        continue;
      }

      for (String metrica : normalizada.metricasPorMes()) {
        String colunaMensal = colunaMensal(metrica, competencia);
        BigDecimal atual = numero(destino.get(colunaMensal));
        destino.put(
            colunaMensal,
            atual.add(numero(registro.get(metrica))));
      }
    }

    return new ArrayList<>(agrupados.values());
  }

  private List<Integer> competencias(Map<String, Object> filtros) {
    int inicio = (Integer) filtros.get("competencia_inicio");
    int fim = (Integer) filtros.get("competencia_fim");
    YearMonth atual = YearMonth.of(inicio / 100, inicio % 100);
    YearMonth ultimo = YearMonth.of(fim / 100, fim % 100);
    List<Integer> competencias = new ArrayList<>();

    while (!atual.isAfter(ultimo)) {
      competencias.add(atual.getYear() * 100 + atual.getMonthValue());
      atual = atual.plusMonths(1);
    }
    return competencias;
  }

  private List<String> colunasResultado(RequisicaoNormalizada normalizada) {
    List<String> padrao = new ArrayList<>();
    if (!normalizada.separarMeses()) {
      padrao.addAll(normalizada.colunas());
    } else {
      normalizada.colunas().stream()
          .filter(coluna -> !"PERIODO".equals(coluna))
          .filter(coluna -> !normalizada.metricasPorMes().contains(coluna))
          .forEach(padrao::add);
      padrao.addAll(colunasMensais(
          normalizada.metricasPorMes(),
          competencias(normalizada.filtros())));
    }

    if (normalizada.ordemResultado().isEmpty()) {
      return List.copyOf(padrao);
    }

    LinkedHashSet<String> ordenadas = new LinkedHashSet<>();
    normalizada.ordemResultado().stream()
        .filter(padrao::contains)
        .forEach(ordenadas::add);
    padrao.forEach(ordenadas::add);
    return List.copyOf(ordenadas);
  }

  private List<String> colunasMensais(
      List<String> metricas,
      List<Integer> competencias) {
    List<String> colunas = new ArrayList<>();
    for (String metrica : metricas) {
      for (Integer competencia : competencias) {
        colunas.add(colunaMensal(metrica, competencia));
      }
    }
    return colunas;
  }

  private String colunaMensal(String metrica, int competencia) {
    return metrica + "__" + competencia;
  }

  private Integer competencia(Object valor) {
    if (valor == null) {
      return null;
    }
    String texto = String.valueOf(valor).trim();
    if (texto.matches("\\d{6}")) {
      return Integer.valueOf(texto);
    }
    if (texto.matches("\\d{2}/\\d{2}/\\d{4}")) {
      String[] partes = texto.split("/");
      return Integer.valueOf(partes[2] + partes[1]);
    }
    return null;
  }

  private BigDecimal numero(Object valor) {
    if (valor == null) {
      return BigDecimal.ZERO;
    }
    if (valor instanceof BigDecimal decimal) {
      return decimal;
    }
    if (valor instanceof Number numero) {
      try {
        return new BigDecimal(numero.toString());
      } catch (NumberFormatException ex) {
        return BigDecimal.ZERO;
      }
    }

    String texto = String.valueOf(valor).trim();
    if (texto.isBlank()) {
      return BigDecimal.ZERO;
    }
    if (texto.matches("[-+]?\\d{1,3}(?:\\.\\d{3})+(?:,\\d+)?")) {
      texto = texto.replace(".", "").replace(',', '.');
    } else if (texto.matches("[-+]?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?")) {
      texto = texto.replace(",", "");
    } else {
      texto = texto.replace(',', '.');
    }
    try {
      return new BigDecimal(texto);
    } catch (NumberFormatException ex) {
      return BigDecimal.ZERO;
    }
  }

  private void ordenarAnalise(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    if (normalizada.ordenarPor() == null || registros.isEmpty()) {
      return;
    }

    List<String> colunas = colunasResultado(normalizada);
    String coluna = normalizada.ordenarPor().toUpperCase(Locale.ROOT);
    if (!colunas.contains(coluna)) {
      throw new IllegalArgumentException(
          "A coluna de ordenação não está presente no resultado atual.");
    }

    Comparator<LinkedHashMap<String, Object>> comparador = (a, b) ->
        compararValores(a.get(coluna), b.get(coluna));
    if ("DESC".equals(normalizada.direcaoOrdenacao())) {
      comparador = comparador.reversed();
    }
    registros.sort(comparador);
  }

  private int compararValores(Object a, Object b) {
    if (a == b) return 0;
    if (a == null) return 1;
    if (b == null) return -1;

    BigDecimal numeroA = numeroOuNull(a);
    BigDecimal numeroB = numeroOuNull(b);
    if (numeroA != null && numeroB != null) {
      return numeroA.compareTo(numeroB);
    }
    return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
  }

  private BigDecimal numeroOuNull(Object valor) {
    if (valor == null) return null;
    if (valor instanceof Number) return numero(valor);
    String texto = String.valueOf(valor).trim();
    if (!texto.matches("[-+]?\\d+(?:[.,]\\d+)?")
        && !texto.matches("[-+]?\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d+)?")) {
      return null;
    }
    return numero(valor);
  }

  private Map<String, Object> paginarAnalise(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    int total = registros.size();
    int inicio = Math.min(
        total,
        (normalizada.pagina() - 1) * normalizada.tamanhoPagina());
    int fim = Math.min(total, inicio + normalizada.tamanhoPagina());
    List<LinkedHashMap<String, Object>> pagina =
        new ArrayList<>(registros.subList(inicio, fim));
    int totalPaginas = total == 0
        ? 0
        : (int) Math.ceil((double) total / normalizada.tamanhoPagina());

    Map<String, Object> resposta = new LinkedHashMap<>();
    resposta.put("content", pagina);
    resposta.put("colunas", colunasResultado(normalizada));
    resposta.put("totalElements", total);
    resposta.put("numberOfElements", pagina.size());
    resposta.put("totalPages", totalPaginas);
    resposta.put("number", normalizada.pagina() - 1);
    resposta.put("last", normalizada.pagina() >= Math.max(1, totalPaginas));
    return resposta;
  }

  private String compactarSql(String sql) {
    return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
  }

  /**
   * O SGU rejeita nomes de filtros com underscore. A conversão fica restrita
   * à borda da integração para não alterar o contrato interno do frontend.
   */
  private Map<String, Object> parametrosSgu(Map<String, Object> filtros) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    filtros.forEach((id, valor) -> parametros.put(sqlBuilder.nomeFiltroSgu(id), valor));
    return parametros;
  }

  private int limitar(
      Integer valor,
      int minimo,
      int maximo,
      int padrao,
      String campo) {
    int numero = valor == null ? padrao : valor;
    if (numero < minimo || numero > maximo) {
      throw new IllegalArgumentException(
          campo + " deve ficar entre " + minimo + " e " + maximo + ".");
    }
    return numero;
  }

  private record RankingNormalizado(
      String modo,
      int quantidade,
      String dimensao,
      String metrica) {
  }

  private record RequisicaoNormalizada(
      List<String> colunas,
      Map<String, Object> filtros,
      boolean distinct,
      String ordenarPor,
      String direcaoOrdenacao,
      boolean separarMeses,
      List<String> metricasPorMes,
      RankingNormalizado ranking,
      List<String> ordemResultado,
      int pagina,
      int tamanhoPagina) {

    boolean analiseAvancada() {
      return separarMeses || ranking != null;
    }
  }
}
