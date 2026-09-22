/*
 * Responsabilidade: Gera, publica e executa com exclusividade a API reservada do relatório personalizado.
 */
package com.unimedlorena.tools.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
      boolean numerica,
      boolean ranqueavel) {
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
            sqlBuilder.campoNumerico(campo.id()),
            sqlBuilder.campoRanking(campo.id())))
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

      if (normalizada.requerTransformacao()) {
        List<LinkedHashMap<String, Object>> registros = exportacao.carregarRegistros(
            API_NOME,
            parametrosSgu(normalizada.filtros()));
        List<LinkedHashMap<String, Object>> processados = processarRegistros(
            registros,
            normalizada);
        return respostaPaginada(processados, normalizada);
      }

      Map<String, Object> parametros = parametrosSgu(normalizada.filtros());
      parametros.put("page", normalizada.pagina());
      parametros.put("size", normalizada.tamanhoPagina());

      Map<String, Object> resposta = sgu.executar(API_NOME, parametros);
      Map<String, Object> projetada = new LinkedHashMap<>(resposta);
      projetada.put(
          "content",
          projetarRegistros(resposta.get("content"), normalizada.colunas()));
      projetada.put("colunas", normalizada.colunas());
      projetada.put("rotulosColunas", rotulosResultado(normalizada));
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
      List<LinkedHashMap<String, Object>> processados = processarRegistros(
          registros,
          normalizada);
      return exportacao.gerarArquivo(formato, processados);
    } finally {
      API_LOCK.unlock();
    }
  }

  private void publicarApi(RequisicaoNormalizada normalizada) {
    String ordenarNoSgu = normalizada.requerTransformacao()
        ? null
        : normalizada.ordenarPor();
    String direcaoNoSgu = ordenarNoSgu == null
        ? null
        : normalizada.direcaoOrdenacao();

    RelatorioPersonalizadoSqlBuilder.ApiGerada geradaOriginal = sqlBuilder.gerar(
        normalizada.colunasConsulta(),
        normalizada.filtros().keySet(),
        normalizada.distinct(),
        ordenarNoSgu,
        direcaoNoSgu);

    RelatorioPersonalizadoSqlBuilder.ApiGerada gerada =
        new RelatorioPersonalizadoSqlBuilder.ApiGerada(
            compactarSql(geradaOriginal.consultaSql()),
            compactarSql(geradaOriginal.ordenacao()),
            geradaOriginal.filtros());

    validarDefinicaoSgu(gerada);

    if (gerada.equals(ultimaApiPublicada)) {
      return;
    }

    Map<String, Object> definicao = new LinkedHashMap<>();
    definicao.put("nome", API_NOME);
    definicao.put("consultaSQL", gerada.consultaSql());
    definicao.put("ordenacao", gerada.ordenacao());
    definicao.put("filtros", gerada.filtros());

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
    boolean separarMeses = Boolean.TRUE.equals(request.separarMeses());
    List<String> colunasMeses = normalizarColunasMeses(
        request.colunasMeses(),
        colunas,
        separarMeses);

    Ranking ranking = normalizarRanking(request, colunas);

    LinkedHashSet<String> consulta = new LinkedHashSet<>(colunas);
    if (separarMeses) {
      consulta.add("PERIODO");
    }

    List<String> colunasPrevistas = colunasResultadoPrevistas(
        colunas,
        filtros,
        separarMeses,
        colunasMeses);
    String ordenarPor = normalizarOrdenarPor(
        request.ordenarPor(),
        colunasPrevistas);
    String direcaoOrdenacao = normalizarDirecaoOrdenacao(
        request.direcaoOrdenacao(),
        ordenarPor);
    List<String> ordemResultado = normalizarOrdemResultado(
        request.ordemResultado(),
        colunasPrevistas);

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

    return new RequisicaoNormalizada(
        colunas,
        List.copyOf(consulta),
        filtros,
        Boolean.TRUE.equals(request.distinct()),
        ordenarPor,
        direcaoOrdenacao,
        separarMeses,
        colunasMeses,
        ranking.dimensao(),
        ranking.metrica(),
        ranking.direcao(),
        ranking.limite(),
        ordemResultado,
        pagina,
        tamanho);
  }

  private String normalizarOrdenarPor(
      String recebido,
      List<String> colunasResultado) {
    if (recebido == null || recebido.isBlank()) {
      return null;
    }
    String coluna = recebido.trim().toUpperCase(Locale.ROOT);
    if (!colunasResultado.contains(coluna)) {
      throw new IllegalArgumentException(
          "A coluna de ordenação deve estar entre as colunas exibidas.");
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

  private List<String> normalizarColunasMeses(
      List<String> solicitadas,
      List<String> selecionadas,
      boolean separarMeses) {
    if (!separarMeses) {
      return List.of();
    }

    LinkedHashSet<String> unicas = new LinkedHashSet<>();
    if (solicitadas != null) {
      for (String coluna : solicitadas) {
        String id = coluna == null ? "" : coluna.trim().toUpperCase(Locale.ROOT);
        if (!selecionadas.contains(id) || !sqlBuilder.campoNumerico(id)) {
          throw new IllegalArgumentException(
              "Só é possível separar por mês uma coluna numérica selecionada.");
        }
        unicas.add(id);
      }
    }

    if (unicas.isEmpty()) {
      throw new IllegalArgumentException(
          "Selecione pelo menos uma coluna numérica para separar por mês.");
    }
    return List.copyOf(unicas);
  }

  private Ranking normalizarRanking(
      RelatorioPersonalizadoRequest request,
      List<String> colunas) {
    String dimensao = textoMaiusculo(request.rankingDimensao());
    String metrica = textoMaiusculo(request.rankingMetrica());
    String direcao = textoMaiusculo(request.rankingDirecao());
    Integer limiteRecebido = request.rankingLimite();

    boolean informado = dimensao != null || metrica != null ||
        direcao != null || limiteRecebido != null;
    if (!informado) {
      return new Ranking(null, null, null, null);
    }

    if (dimensao == null || metrica == null) {
      throw new IllegalArgumentException(
          "Escolha a dimensão e a métrica do ranking.");
    }
    if (!colunas.contains(dimensao) || sqlBuilder.campoNumerico(dimensao)) {
      throw new IllegalArgumentException(
          "A dimensão do ranking deve ser uma coluna descritiva selecionada.");
    }
    if (!colunas.contains(metrica) || !sqlBuilder.campoRanking(metrica)) {
      throw new IllegalArgumentException(
          "A métrica do ranking deve ser uma coluna de gasto selecionada.");
    }

    String direcaoNormalizada = direcao == null ? "MAIORES" : direcao;
    if (!Set.of("MAIORES", "MENORES").contains(direcaoNormalizada)) {
      throw new IllegalArgumentException(
          "O ranking deve usar MAIORES ou MENORES.");
    }
    int limite = limitar(limiteRecebido, 1, 1000, 10, "Quantidade do ranking");
    return new Ranking(dimensao, metrica, direcaoNormalizada, limite);
  }

  private String textoMaiusculo(String valor) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    return valor.trim().toUpperCase(Locale.ROOT);
  }

  private List<String> colunasResultadoPrevistas(
      List<String> colunas,
      Map<String, Object> filtros,
      boolean separarMeses,
      List<String> colunasMeses) {
    if (!separarMeses) {
      return List.copyOf(colunas);
    }

    List<Integer> meses = competencias(filtros);
    List<String> resultado = new ArrayList<>();
    for (String coluna : colunas) {
      if ("PERIODO".equals(coluna)) {
        continue;
      }
      if (colunasMeses.contains(coluna)) {
        for (Integer mes : meses) {
          resultado.add(aliasMes(coluna, mes));
        }
      } else {
        resultado.add(coluna);
      }
    }
    return List.copyOf(resultado);
  }

  private List<String> normalizarOrdemResultado(
      List<String> recebida,
      List<String> prevista) {
    if (recebida == null || recebida.isEmpty()) {
      return prevista;
    }

    LinkedHashSet<String> normalizada = new LinkedHashSet<>();
    for (String coluna : recebida) {
      String id = coluna == null ? "" : coluna.trim().toUpperCase(Locale.ROOT);
      if (!prevista.contains(id)) {
        throw new IllegalArgumentException(
            "A ordem do resultado contém uma coluna que não está disponível: " + id + ".");
      }
      normalizada.add(id);
    }
    for (String coluna : prevista) {
      normalizada.add(coluna);
    }
    return List.copyOf(normalizada);
  }

  private boolean requerTransformacao(RequisicaoNormalizada normalizada) {
    return normalizada.separarMeses() || normalizada.rankingDimensao() != null;
  }

  private List<LinkedHashMap<String, Object>> processarRegistros(
      Object conteudo,
      RequisicaoNormalizada normalizada) {
    List<LinkedHashMap<String, Object>> registros = projetarRegistros(
        conteudo,
        normalizada.colunasConsulta());

    if (normalizada.rankingDimensao() != null) {
      registros = aplicarRanking(registros, normalizada);
    }
    if (normalizada.separarMeses()) {
      registros = separarMeses(registros, normalizada);
    } else {
      registros = projetarRegistros(registros, normalizada.colunas());
    }

    registros = reordenarRegistros(registros, normalizada.ordemResultado());
    ordenarRegistros(registros, normalizada);
    return registros;
  }

  private List<LinkedHashMap<String, Object>> aplicarRanking(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    Map<String, BigDecimal> totais = new LinkedHashMap<>();
    for (Map<String, Object> registro : registros) {
      String chave = textoChave(registro.get(normalizada.rankingDimensao()));
      if (chave == null) {
        continue;
      }
      totais.merge(
          chave,
          numero(registro.get(normalizada.rankingMetrica())),
          BigDecimal::add);
    }

    List<Map.Entry<String, BigDecimal>> ordenados = new ArrayList<>(totais.entrySet());
    ordenados.sort((a, b) -> {
      int comparacao = a.getValue().compareTo(b.getValue());
      if ("MAIORES".equals(normalizada.rankingDirecao())) {
        comparacao = -comparacao;
      }
      return comparacao != 0
          ? comparacao
          : a.getKey().compareToIgnoreCase(b.getKey());
    });

    LinkedHashSet<String> permitidos = new LinkedHashSet<>();
    ordenados.stream()
        .limit(normalizada.rankingLimite())
        .map(Map.Entry::getKey)
        .forEach(permitidos::add);

    return registros.stream()
        .filter(registro -> {
          String chave = textoChave(registro.get(normalizada.rankingDimensao()));
          return chave != null && permitidos.contains(chave);
        })
        .map(LinkedHashMap::new)
        .toList();
  }

  private List<LinkedHashMap<String, Object>> separarMeses(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    List<String> dimensoes = normalizada.colunas().stream()
        .filter(coluna -> !"PERIODO".equals(coluna))
        .filter(coluna -> !sqlBuilder.campoNumerico(coluna))
        .toList();
    List<String> metricasTotais = normalizada.colunas().stream()
        .filter(sqlBuilder::campoNumerico)
        .filter(coluna -> !normalizada.colunasMeses().contains(coluna))
        .toList();
    List<Integer> meses = competencias(normalizada.filtros());

    Map<String, LinkedHashMap<String, Object>> grupos = new LinkedHashMap<>();
    for (LinkedHashMap<String, Object> registro : registros) {
      String chave = chaveGrupo(registro, dimensoes);
      LinkedHashMap<String, Object> saida = grupos.computeIfAbsent(
          chave,
          ignored -> criarLinhaMensal(
              registro,
              dimensoes,
              metricasTotais,
              normalizada.colunasMeses(),
              meses));

      for (String metrica : metricasTotais) {
        acumular(saida, metrica, registro.get(metrica), metrica);
      }

      Integer competencia = competenciaRegistro(registro.get("PERIODO"));
      if (competencia == null || !meses.contains(competencia)) {
        continue;
      }
      for (String metrica : normalizada.colunasMeses()) {
        acumular(
            saida,
            aliasMes(metrica, competencia),
            registro.get(metrica),
            metrica);
      }
    }

    return new ArrayList<>(grupos.values());
  }

  private LinkedHashMap<String, Object> criarLinhaMensal(
      Map<String, Object> origem,
      List<String> dimensoes,
      List<String> metricasTotais,
      List<String> metricasMeses,
      List<Integer> meses) {
    LinkedHashMap<String, Object> linha = new LinkedHashMap<>();
    dimensoes.forEach(coluna -> linha.put(coluna, origem.get(coluna)));
    metricasTotais.forEach(coluna -> linha.put(coluna, BigDecimal.ZERO));
    for (String metrica : metricasMeses) {
      for (Integer mes : meses) {
        linha.put(aliasMes(metrica, mes), BigDecimal.ZERO);
      }
    }
    return linha;
  }

  private void acumular(
      Map<String, Object> destino,
      String colunaDestino,
      Object valor,
      String metricaBase) {
    BigDecimal numero = numero(valor);
    if ("IDADE".equals(metricaBase) || "SINISTRALIDADE".equals(metricaBase)) {
      BigDecimal atual = numero(destino.get(colunaDestino));
      destino.put(colunaDestino, atual.max(numero));
      return;
    }
    destino.put(
        colunaDestino,
        numero(destino.get(colunaDestino)).add(numero));
  }

  private List<Integer> competencias(Map<String, Object> filtros) {
    int inicio = (Integer) filtros.get("competencia_inicio");
    int fim = (Integer) filtros.get("competencia_fim");
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

  private Integer competenciaRegistro(Object valor) {
    if (valor == null) {
      return null;
    }
    String texto = String.valueOf(valor).trim();
    if (texto.matches("\\d{6}")) {
      return Integer.valueOf(texto);
    }
    return null;
  }

  private String aliasMes(String coluna, int competencia) {
    return coluna + "_" + competencia;
  }

  private String chaveGrupo(
      Map<String, Object> registro,
      List<String> dimensoes) {
    StringBuilder chave = new StringBuilder();
    for (String dimensao : dimensoes) {
      String valor = String.valueOf(registro.get(dimensao));
      chave.append(valor.length()).append(':').append(valor).append('|');
    }
    return chave.toString();
  }

  private String textoChave(Object valor) {
    if (valor == null) {
      return null;
    }
    String texto = String.valueOf(valor).trim();
    return texto.isBlank() ? null : texto;
  }

  private BigDecimal numero(Object valor) {
    if (valor == null) {
      return BigDecimal.ZERO;
    }
    if (valor instanceof BigDecimal decimal) {
      return decimal;
    }
    if (valor instanceof Number numero) {
      return new BigDecimal(numero.toString());
    }

    String texto = String.valueOf(valor).trim();
    if (texto.isBlank()) {
      return BigDecimal.ZERO;
    }
    if (texto.contains(",") && texto.contains(".")) {
      texto = texto.replace(".", "").replace(',', '.');
    } else if (texto.contains(",")) {
      texto = texto.replace(',', '.');
    }
    try {
      return new BigDecimal(texto);
    } catch (NumberFormatException ex) {
      return BigDecimal.ZERO;
    }
  }

  private List<LinkedHashMap<String, Object>> reordenarRegistros(
      List<LinkedHashMap<String, Object>> registros,
      List<String> ordem) {
    List<LinkedHashMap<String, Object>> resultado = new ArrayList<>();
    for (Map<String, Object> registro : registros) {
      LinkedHashMap<String, Object> linha = new LinkedHashMap<>();
      ordem.forEach(coluna -> linha.put(coluna, registro.get(coluna)));
      resultado.add(linha);
    }
    return resultado;
  }

  private void ordenarRegistros(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    if (normalizada.ordenarPor() == null || registros.size() < 2) {
      return;
    }
    String coluna = normalizada.ordenarPor();
    boolean numerica = colunaNumericaResultado(coluna);
    registros.sort((a, b) -> {
      int comparacao = numerica
          ? numero(a.get(coluna)).compareTo(numero(b.get(coluna)))
          : String.valueOf(a.getOrDefault(coluna, ""))
              .compareToIgnoreCase(String.valueOf(b.getOrDefault(coluna, "")));
      return "DESC".equals(normalizada.direcaoOrdenacao())
          ? -comparacao
          : comparacao;
    });
  }

  private boolean colunaNumericaResultado(String coluna) {
    if (sqlBuilder.campoNumerico(coluna)) {
      return true;
    }
    int separador = coluna.lastIndexOf('_');
    if (separador <= 0 || separador == coluna.length() - 1) {
      return false;
    }
    String sufixo = coluna.substring(separador + 1);
    return sufixo.matches("\\d{6}") &&
        sqlBuilder.campoNumerico(coluna.substring(0, separador));
  }

  private Map<String, Object> respostaPaginada(
      List<LinkedHashMap<String, Object>> registros,
      RequisicaoNormalizada normalizada) {
    int total = registros.size();
    int inicio = Math.min((normalizada.pagina() - 1) * normalizada.tamanhoPagina(), total);
    int fim = Math.min(inicio + normalizada.tamanhoPagina(), total);
    List<LinkedHashMap<String, Object>> pagina = registros.subList(inicio, fim);

    Map<String, Object> resposta = new LinkedHashMap<>();
    resposta.put("content", pagina);
    resposta.put("colunas", normalizada.ordemResultado());
    resposta.put("rotulosColunas", rotulosResultado(normalizada));
    resposta.put("totalElements", total);
    resposta.put("numberOfElements", pagina.size());
    resposta.put(
        "totalPages",
        total == 0 ? 0 : (int) Math.ceil((double) total / normalizada.tamanhoPagina()));
    resposta.put("number", normalizada.pagina() - 1);
    resposta.put("last", fim >= total);
    return resposta;
  }

  private Map<String, String> rotulosResultado(RequisicaoNormalizada normalizada) {
    Map<String, String> rotulos = new LinkedHashMap<>();
    for (String coluna : normalizada.ordemResultado()) {
      int separador = coluna.lastIndexOf('_');
      if (separador > 0) {
        String sufixo = coluna.substring(separador + 1);
        String base = coluna.substring(0, separador);
        if (sufixo.matches("\\d{6}") && sqlBuilder.campo(base) != null) {
          int competencia = Integer.parseInt(sufixo);
          rotulos.put(
              coluna,
              sqlBuilder.campo(base).rotulo() + " " + rotuloMes(competencia));
          continue;
        }
      }
      RelatorioPersonalizadoSqlBuilder.Campo campo = sqlBuilder.campo(coluna);
      rotulos.put(coluna, campo == null ? coluna : campo.rotulo());
    }
    return rotulos;
  }

  private String rotuloMes(int competencia) {
    String[] meses = {
        "JAN", "FEV", "MAR", "ABR", "MAI", "JUN",
        "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"
    };
    int ano = competencia / 100;
    int mes = competencia % 100;
    return meses[mes - 1] + "/" + ano;
  }

  private void validarDefinicaoSgu(
      RelatorioPersonalizadoSqlBuilder.ApiGerada gerada) {
    /*
     * VARCHAR2 em PL/SQL tem limite de 32.767 bytes em variáveis locais. A
     * procedure do SGU não expõe seus tamanhos internos, então recusamos uma
     * definição próxima desse teto antes que ela vire um ORA-06502 remoto.
     */
    if (gerada.consultaSql().length() > 32_000) {
      throw new IllegalArgumentException(
          "A consulta gerada ficou grande demais para a rotina do SGU. " +
              "Reduza a quantidade de colunas ou filtros desta configuração.");
    }
    if (gerada.ordenacao().length() > 200) {
      throw new IllegalArgumentException(
          "A ordenação gerada ficou grande demais para a rotina do SGU. " +
              "Escolha uma única coluna para ordenar.");
    }
    for (Map<String, Object> filtro : gerada.filtros()) {
      String conteudo = String.valueOf(filtro.getOrDefault("conteudoFiltro", ""));
      if (conteudo.length() > 1000) {
        throw new IllegalArgumentException(
            "Um dos filtros gerados excedeu o tamanho seguro aceito pelo SGU.");
      }
    }
  }

  private String compactarSql(String sql) {
    if (sql == null || sql.isBlank()) {
      return "";
    }

    StringBuilder saida = new StringBuilder(sql.length());
    boolean emTexto = false;
    boolean espacoPendente = false;
    for (int i = 0; i < sql.length(); i++) {
      char atual = sql.charAt(i);

      if (atual == '\'') {
        saida.append(atual);
        if (emTexto && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
          saida.append(sql.charAt(++i));
          continue;
        }
        emTexto = !emTexto;
        espacoPendente = false;
        continue;
      }

      if (!emTexto && Character.isWhitespace(atual)) {
        espacoPendente = true;
        continue;
      }

      if (espacoPendente && saida.length() > 0) {
        char anterior = saida.charAt(saida.length() - 1);
        if (anterior != '(' && atual != ')' && atual != ',') {
          saida.append(' ');
        }
      }
      espacoPendente = false;
      saida.append(atual);
    }
    return saida.toString().trim();
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

  private record Ranking(
      String dimensao,
      String metrica,
      String direcao,
      Integer limite) {
  }

  private record RequisicaoNormalizada(
      List<String> colunas,
      List<String> colunasConsulta,
      Map<String, Object> filtros,
      boolean distinct,
      String ordenarPor,
      String direcaoOrdenacao,
      boolean separarMeses,
      List<String> colunasMeses,
      String rankingDimensao,
      String rankingMetrica,
      String rankingDirecao,
      Integer rankingLimite,
      List<String> ordemResultado,
      int pagina,
      int tamanhoPagina) {

    boolean requerTransformacao() {
      return separarMeses || rankingDimensao != null;
    }
  }
}
