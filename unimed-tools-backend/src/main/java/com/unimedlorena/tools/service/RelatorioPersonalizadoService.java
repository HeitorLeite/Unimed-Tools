/*
 * Responsabilidade: Gera, publica e executa com exclusividade a API reservada do relatório personalizado.
 */
package com.unimedlorena.tools.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;

import com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest;

@Service
public class RelatorioPersonalizadoService {

  public static final String API_NOME = "0090-relatorio-personalizado";

  public record Coluna(
      String id,
      String rotulo,
      String grupo,
      boolean selecionadaPorPadrao,
      boolean sensivel,
      String tipo) {
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
            sqlBuilder.tipoColuna(campo.id())))
        .toList();

    List<Filtro> filtros = sqlBuilder
        .filtros()
        .stream()
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
        API_NOME,
        "Despesas, receita e sinistralidade",
        colunas,
        filtros,
        new Limites(colunas.size(), MAXIMO_MESES, MAXIMO_LINHAS_PAGINA));
  }

  public boolean ehApiReservada(String nome) {
    return nome != null && API_NOME.equalsIgnoreCase(nome.trim());
  }

  public Map<String, Object> executar(RelatorioPersonalizadoRequest request) {
    RequisicaoNormalizada normalizada = normalizar(request, true);

    API_LOCK.lock();
    try {
      publicarApi(normalizada);

      Map<String, Object> parametros = parametrosSgu(normalizada.filtros());

      if (normalizada.requerPosProcessamento()) {
        List<LinkedHashMap<String, Object>> registros = exportacao.carregarRegistros(
            API_NOME,
            parametros);
        List<LinkedHashMap<String, Object>> projetados = projetarRegistros(
            registros,
            normalizada.colunasConsulta());
        ResultadoProcessado resultado = processar(projetados, normalizada);
        return paginar(resultado, normalizada.pagina(), normalizada.tamanhoPagina());
      }

      parametros.put("page", normalizada.pagina());
      parametros.put("size", normalizada.tamanhoPagina());

      Map<String, Object> resposta = sgu.executar(API_NOME, parametros);
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

      List<LinkedHashMap<String, Object>> registros = exportacao.carregarRegistros(
          API_NOME,
          parametrosSgu(normalizada.filtros()));
      List<LinkedHashMap<String, Object>> projetados = projetarRegistros(
          registros,
          normalizada.requerPosProcessamento()
              ? normalizada.colunasConsulta()
              : normalizada.colunas());

      if (normalizada.requerPosProcessamento()) {
        ResultadoProcessado resultado = processar(projetados, normalizada);
        return exportacao.gerarArquivo(formato, resultado.registros());
      }

      return exportacao.gerarArquivo(formato, projetados);
    } finally {
      API_LOCK.unlock();
    }
  }

  private void publicarApi(RequisicaoNormalizada normalizada) {
    RelatorioPersonalizadoSqlBuilder.ApiGerada gerada = sqlBuilder.gerar(
        normalizada.colunasConsulta(),
        normalizada.filtros().keySet(),
        normalizada.distinct(),
        normalizada.requerPosProcessamento() ? null : normalizada.ordenarPor(),
        normalizada.requerPosProcessamento() ? null : normalizada.direcaoOrdenacao());

    if (gerada.equals(ultimaApiPublicada)) {
      return;
    }

    String consultaCompacta = compactarSqlParaSgu(gerada.consultaSql());
    if (consultaCompacta.length() > 32_000) {
      throw new IllegalArgumentException(
          "A combinação escolhida gerou uma consulta grande demais para a rotina do SGU. " +
              "Reduza colunas ou filtros e tente novamente.");
    }

    String ordenacao = gerada.ordenacao() == null ? "" : gerada.ordenacao().trim();
    if (ordenacao.length() > 240) {
      throw new IllegalArgumentException(
          "A ordenação gerada excedeu o limite seguro do SGU.");
    }

    Map<String, Object> definicao = new LinkedHashMap<>();
    definicao.put("nome", API_NOME);
    definicao.put("consultaSQL", consultaCompacta);
    definicao.put("ordenacao", ordenacao);
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
    List<String> metricasMes = normalizarMetricasMes(
        request.metricasMes(),
        colunas,
        separarMeses);

    String rankingTipo = normalizarRankingTipo(request.rankingTipo());
    Integer rankingLimite = rankingTipo == null
        ? null
        : limitar(request.rankingLimite(), 1, 1_000, 10, "Quantidade do ranking");
    String rankingDimensao = rankingTipo == null
        ? null
        : normalizarColunaAnalise(
            request.rankingDimensao(),
            colunas,
            false,
            "dimensão do ranking");
    String rankingMetrica = rankingTipo == null
        ? null
        : normalizarColunaAnalise(
            request.rankingMetrica(),
            colunas,
            true,
            "métrica do ranking");

    LinkedHashSet<String> consulta = new LinkedHashSet<>(colunas);
    if (separarMeses) {
      consulta.add("PERIODO");
      consulta.addAll(metricasMes);
    }
    if (rankingDimensao != null) {
      consulta.add(rankingDimensao);
      consulta.add(rankingMetrica);
    }

    boolean posProcessamento = separarMeses || rankingTipo != null;
    String ordenarPor = posProcessamento
        ? normalizarOrdenacaoPosProcessamento(request.ordenarPor())
        : normalizarOrdenarPor(request.ordenarPor(), colunas);
    String direcaoOrdenacao = normalizarDirecaoOrdenacao(
        request.direcaoOrdenacao(),
        ordenarPor);

    return new RequisicaoNormalizada(
        colunas,
        List.copyOf(consulta),
        filtros,
        distinct,
        ordenarPor,
        direcaoOrdenacao,
        separarMeses,
        metricasMes,
        rankingTipo,
        rankingLimite,
        rankingDimensao,
        rankingMetrica,
        pagina,
        tamanho);
  }

  private List<String> normalizarMetricasMes(
      List<String> recebidas,
      List<String> colunas,
      boolean separarMeses) {
    if (!separarMeses) {
      return List.of();
    }

    LinkedHashSet<String> metricas = new LinkedHashSet<>();
    if (recebidas != null) {
      for (String recebida : recebidas) {
        String id = recebida == null ? "" : recebida.trim().toUpperCase(Locale.ROOT);
        if (!colunas.contains(id) || !sqlBuilder.ehColunaNumerica(id)) {
          throw new IllegalArgumentException(
              "As colunas separadas por mês devem ser numéricas e estar selecionadas.");
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

  private String normalizarRankingTipo(String recebido) {
    if (recebido == null || recebido.isBlank()) {
      return null;
    }
    String tipo = recebido.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("MAIORES", "MENORES").contains(tipo)) {
      throw new IllegalArgumentException(
          "O ranking deve usar maiores ou menores gastadores.");
    }
    return tipo;
  }

  private String normalizarColunaAnalise(
      String recebida,
      List<String> colunas,
      boolean numerica,
      String descricao) {
    if (recebida == null || recebida.isBlank()) {
      throw new IllegalArgumentException("Selecione a " + descricao + ".");
    }
    String id = recebida.trim().toUpperCase(Locale.ROOT);
    if (!colunas.contains(id)) {
      throw new IllegalArgumentException(
          "A " + descricao + " deve estar entre as colunas selecionadas.");
    }
    if (numerica != sqlBuilder.ehColunaNumerica(id)) {
      throw new IllegalArgumentException(
          numerica
              ? "A métrica do ranking deve ser uma coluna numérica."
              : "A dimensão do ranking não pode ser uma métrica numérica.");
    }
    return id;
  }

  private String normalizarOrdenacaoPosProcessamento(String recebida) {
    if (recebida == null || recebida.isBlank()) {
      return null;
    }
    String coluna = recebida.trim();
    if (coluna.length() > 160) {
      throw new IllegalArgumentException("A coluna de ordenação informada é inválida.");
    }
    return coluna;
  }

  private String normalizarOrdenarPor(
      String recebido,
      List<String> colunasSelecionadas) {
    if (recebido == null || recebido.isBlank()) {
      return null;
    }
    String coluna = recebido.trim().toUpperCase(Locale.ROOT);
    if (!colunasSelecionadas.contains(coluna)) {
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
      case "codigo_empresa", "nome_empresa" -> normalizarCodigosEmpresa(texto);
      case "id_guia" -> normalizarIdsGuia(texto);
      case "cpf" -> texto.replaceAll("[^0-9]", "");
      case "cid" -> texto.toUpperCase(Locale.ROOT);
      case "grupo_beneficiario" -> normalizarGrupoBeneficiario(texto);
      case "nome_beneficiario", "nome_prestador",
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

  private ResultadoProcessado processar(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    List<LinkedHashMap<String, Object>> atuais = registros;

    if (normalizada.rankingTipo() != null) {
      atuais = aplicarRanking(atuais, normalizada);
    }

    ResultadoProcessado resultado = normalizada.separarMeses()
        ? pivotarMeses(atuais, normalizada)
        : new ResultadoProcessado(
            projetarRegistros(atuais, normalizada.colunas()),
            normalizada.colunas());

    if (normalizada.ordenarPor() != null) {
      ordenarResultado(
          resultado.registros(),
          normalizada.ordenarPor(),
          normalizada.direcaoOrdenacao());
    }

    return resultado;
  }

  private List<LinkedHashMap<String, Object>> aplicarRanking(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    Map<String, BigDecimal> totais = new LinkedHashMap<>();
    for (Map<String, Object> registro : registros) {
      String chave = chaveRanking(registro.get(normalizada.rankingDimensao()));
      BigDecimal valor = decimal(registro.get(normalizada.rankingMetrica()));
      totais.merge(chave, valor, BigDecimal::add);
    }

    Comparator<Map.Entry<String, BigDecimal>> comparador =
        Map.Entry.comparingByValue();
    if ("MAIORES".equals(normalizada.rankingTipo())) {
      comparador = comparador.reversed();
    }

    Set<String> permitidos = totais.entrySet().stream()
        .sorted(comparador)
        .limit(normalizada.rankingLimite())
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

    return registros.stream()
        .filter(registro -> permitidos.contains(
            chaveRanking(registro.get(normalizada.rankingDimensao()))))
        .map(LinkedHashMap::new)
        .toList();
  }

  private ResultadoProcessado pivotarMeses(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    List<String> dimensoes = normalizada.colunas().stream()
        .filter(coluna -> !"PERIODO".equals(coluna))
        .filter(coluna -> !normalizada.metricasMes().contains(coluna))
        .toList();

    List<Integer> meses = competencias(
        (Integer) normalizada.filtros().get("competencia_inicio"),
        (Integer) normalizada.filtros().get("competencia_fim"));

    List<String> colunasSaida = new ArrayList<>(dimensoes);
    for (String metrica : normalizada.metricasMes()) {
      for (Integer mes : meses) {
        colunasSaida.add(rotuloMetricaMes(metrica, mes));
      }
    }

    Map<List<Object>, LinkedHashMap<String, Object>> agrupados = new LinkedHashMap<>();
    for (LinkedHashMap<String, Object> registro : registros) {
      List<Object> chave = dimensoes.stream().map(registro::get).toList();
      LinkedHashMap<String, Object> linha = agrupados.computeIfAbsent(chave, ignorado -> {
        LinkedHashMap<String, Object> nova = new LinkedHashMap<>();
        for (String dimensao : dimensoes) {
          nova.put(dimensao, registro.get(dimensao));
        }
        for (String metrica : normalizada.metricasMes()) {
          for (Integer mes : meses) {
            nova.put(rotuloMetricaMes(metrica, mes), BigDecimal.ZERO);
          }
        }
        return nova;
      });

      Integer competencia = competencia(registro.get("PERIODO"));
      if (competencia == null || !meses.contains(competencia)) {
        continue;
      }
      for (String metrica : normalizada.metricasMes()) {
        String coluna = rotuloMetricaMes(metrica, competencia);
        BigDecimal atual = decimal(linha.get(coluna));
        linha.put(coluna, atual.add(decimal(registro.get(metrica))));
      }
    }

    return new ResultadoProcessado(
        new ArrayList<>(agrupados.values()),
        List.copyOf(colunasSaida));
  }

  private Map<String, Object> paginar(
      ResultadoProcessado resultado,
      int pagina,
      int tamanhoPagina) {
    int total = resultado.registros().size();
    int inicio = Math.min(total, (pagina - 1) * tamanhoPagina);
    int fim = Math.min(total, inicio + tamanhoPagina);
    int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / tamanhoPagina);

    Map<String, Object> resposta = new LinkedHashMap<>();
    resposta.put("content", resultado.registros().subList(inicio, fim));
    resposta.put("colunas", resultado.colunas());
    resposta.put("totalElements", total);
    resposta.put("numberOfElements", fim - inicio);
    resposta.put("totalPages", totalPaginas);
    resposta.put("number", Math.max(0, pagina - 1));
    resposta.put("last", pagina >= Math.max(1, totalPaginas));
    return resposta;
  }

  private void ordenarResultado(
      List<LinkedHashMap<String, Object>> registros,
      String coluna,
      String direcao) {
    if (registros.isEmpty() || !registros.getFirst().containsKey(coluna)) {
      return;
    }

    Comparator<LinkedHashMap<String, Object>> comparador =
        (a, b) -> compararValores(a.get(coluna), b.get(coluna));
    if ("DESC".equals(direcao)) {
      comparador = comparador.reversed();
    }
    registros.sort(comparador);
  }

  private int compararValores(Object a, Object b) {
    if (a == b) return 0;
    if (a == null) return 1;
    if (b == null) return -1;

    BigDecimal numeroA = decimalOuNull(a);
    BigDecimal numeroB = decimalOuNull(b);
    if (numeroA != null && numeroB != null) {
      return numeroA.compareTo(numeroB);
    }

    return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
  }

  private BigDecimal decimal(Object valor) {
    BigDecimal convertido = decimalOuNull(valor);
    return convertido == null ? BigDecimal.ZERO : convertido;
  }

  private BigDecimal decimalOuNull(Object valor) {
    if (valor == null) return null;
    if (valor instanceof BigDecimal decimal) return decimal;
    if (valor instanceof Number numero) {
      return new BigDecimal(numero.toString());
    }

    String texto = String.valueOf(valor).trim();
    if (texto.isBlank()) return null;
    try {
      String normalizado = texto.contains(",")
          ? texto.replace(".", "").replace(',', '.')
          : texto;
      return new BigDecimal(normalizado);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Integer competencia(Object valor) {
    if (valor == null) return null;
    String texto = String.valueOf(valor).replaceAll("[^0-9]", "");
    if (texto.length() < 6) return null;
    try {
      return Integer.valueOf(texto.substring(0, 6));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private List<Integer> competencias(int inicio, int fim) {
    List<Integer> meses = new ArrayList<>();
    int ano = inicio / 100;
    int mes = inicio % 100;
    while (ano * 100 + mes <= fim) {
      meses.add(ano * 100 + mes);
      mes++;
      if (mes == 13) {
        mes = 1;
        ano++;
      }
    }
    return meses;
  }

  private String rotuloMetricaMes(String metrica, int competencia) {
    String[] nomes = {
        "", "JAN", "FEV", "MAR", "ABR", "MAI", "JUN",
        "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"
    };
    int mes = competencia % 100;
    int ano = competencia / 100;
    RelatorioPersonalizadoSqlBuilder.Campo campo = sqlBuilder.campo(metrica);
    String rotulo = campo == null ? metrica : campo.rotulo();
    return rotulo + " " + nomes[mes] + "/" + ano;
  }

  private String chaveRanking(Object valor) {
    return valor == null ? "<SEM VALOR>" : String.valueOf(valor);
  }

  private String compactarSqlParaSgu(String sql) {
    StringBuilder saida = new StringBuilder(sql.length());
    boolean literal = false;
    boolean comentarioLinha = false;
    boolean comentarioBloco = false;
    boolean espacoPendente = false;

    for (int i = 0; i < sql.length(); i++) {
      char atual = sql.charAt(i);
      char proximo = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

      if (comentarioLinha) {
        if (atual == '\n' || atual == '\r') {
          comentarioLinha = false;
          espacoPendente = true;
        }
        continue;
      }
      if (comentarioBloco) {
        if (atual == '*' && proximo == '/') {
          comentarioBloco = false;
          i++;
          espacoPendente = true;
        }
        continue;
      }

      if (!literal && atual == '-' && proximo == '-') {
        comentarioLinha = true;
        i++;
        continue;
      }
      if (!literal && atual == '/' && proximo == '*') {
        comentarioBloco = true;
        i++;
        continue;
      }

      if (atual == '\'') {
        if (literal && proximo == '\'') {
          if (espacoPendente && saida.length() > 0) {
            saida.append(' ');
            espacoPendente = false;
          }
          saida.append(atual).append(proximo);
          i++;
          continue;
        }
        if (espacoPendente && saida.length() > 0) {
          saida.append(' ');
          espacoPendente = false;
        }
        literal = !literal;
        saida.append(atual);
        continue;
      }

      if (!literal && Character.isWhitespace(atual)) {
        espacoPendente = true;
        continue;
      }

      if (espacoPendente && saida.length() > 0) {
        saida.append(' ');
        espacoPendente = false;
      }
      saida.append(atual);
    }

    return saida.toString().trim();
  }

  private record ResultadoProcessado(
      List<LinkedHashMap<String, Object>> registros,
      List<String> colunas) {
  }

  private record RequisicaoNormalizada(
      List<String> colunas,
      List<String> colunasConsulta,
      Map<String, Object> filtros,
      boolean distinct,
      String ordenarPor,
      String direcaoOrdenacao,
      boolean separarMeses,
      List<String> metricasMes,
      String rankingTipo,
      Integer rankingLimite,
      String rankingDimensao,
      String rankingMetrica,
      int pagina,
      int tamanhoPagina) {

    boolean requerPosProcessamento() {
      return separarMeses || rankingTipo != null;
    }
  }
}
