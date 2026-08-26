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
      boolean sensivel) {
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
            campo.sensivel()))
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

      /*
       * O lock permanece durante todas as páginas. Sem isso, outra consulta
       * poderia substituir a definição no meio da exportação.
       */
      List<LinkedHashMap<String, Object>> registros = exportacao.carregarRegistros(
          API_NOME,
          parametrosSgu(normalizada.filtros()));
      List<LinkedHashMap<String, Object>> projetados = projetarRegistros(
          registros,
          normalizada.colunas());
      return exportacao.gerarArquivo(formato, projetados);
    } finally {
      API_LOCK.unlock();
    }
  }

  private void publicarApi(RequisicaoNormalizada normalizada) {
    RelatorioPersonalizadoSqlBuilder.ApiGerada gerada = sqlBuilder.gerar(
        normalizada.colunas(),
        normalizada.filtros().keySet(),
        normalizada.distinct(),
        normalizada.ordenarPor(),
        normalizada.direcaoOrdenacao());

    /*
     * Paginação e repetições com a mesma estrutura não precisam republicar a
     * API reservada. O acesso ocorre dentro de API_LOCK; se colunas ou filtros
     * ativos mudarem, ApiGerada também muda e a publicação é refeita.
     */
    if (gerada.equals(ultimaApiPublicada)) {
      return;
    }

    Map<String, Object> definicao = new LinkedHashMap<>();
    definicao.put("nome", API_NOME);
    definicao.put("consultaSQL", gerada.consultaSql());
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
    String ordenarPor = normalizarOrdenarPor(request.ordenarPor(), colunas);
    String direcaoOrdenacao = normalizarDirecaoOrdenacao(
        request.direcaoOrdenacao(),
        ordenarPor);

    return new RequisicaoNormalizada(
        colunas,
        filtros,
        distinct,
        ordenarPor,
        direcaoOrdenacao,
        pagina,
        tamanho);
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
      case "cpf" -> texto.replaceAll("[^0-9]", "");
      case "cid" -> texto.toUpperCase(Locale.ROOT);
      case "grupo_beneficiario" -> normalizarGrupoBeneficiario(texto);
      case "nome_beneficiario", "nome_empresa", "nome_prestador",
          "grupo_prestador", "descricao_item", "tipo_procedimento" ->
          "%" + texto.toUpperCase(Locale.ROOT) + "%";
      default -> texto;
    };
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

  private record RequisicaoNormalizada(
      List<String> colunas,
      Map<String, Object> filtros,
      boolean distinct,
      String ordenarPor,
      String direcaoOrdenacao,
      int pagina,
      int tamanhoPagina) {
  }
}
