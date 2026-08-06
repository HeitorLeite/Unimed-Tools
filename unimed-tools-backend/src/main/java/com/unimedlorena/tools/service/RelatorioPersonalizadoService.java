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
        "Despesas por item de guia",
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

      Map<String, Object> parametros = new LinkedHashMap<>(
          normalizada.filtros());
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
      List<LinkedHashMap<String, Object>> registros = exportacao.carregarRegistros(API_NOME, normalizada.filtros());
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
        normalizada.filtros().keySet());

    Map<String, Object> definicao = new LinkedHashMap<>();
    definicao.put("nome", API_NOME);
    definicao.put("consultaSQL", gerada.consultaSql());
    definicao.put("ordenacao", gerada.ordenacao());
    definicao.put("filtros", gerada.filtros());

    // ins_atu_query_api atualiza a definição existente com o mesmo nome.
    sgu.criarOuAtualizar(definicao);
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

    return new RequisicaoNormalizada(colunas, filtros, pagina, tamanho);
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

    for (String informado : origem.keySet()) {
      if (sqlBuilder.filtro(informado) == null) {
        throw new IllegalArgumentException(
            "Filtro não permitido: " + informado + ".");
      }
    }

    for (RelatorioPersonalizadoSqlBuilder.Filtro filtro : sqlBuilder.filtros()) {
      Object bruto = origem.get(filtro.id());
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

    return switch (filtro.tipoTela()) {
      case "competencia" -> validarCompetencia(texto, filtro.rotulo());
      case "number" -> validarInteiro(texto, filtro.rotulo());
      case "decimal" -> validarDecimal(texto, filtro.rotulo());
      case "date" -> validarData(texto, filtro.rotulo());
      default -> texto;
    };
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
    int inicio = (Integer) filtros.get("competencia-inicio");
    int fim = (Integer) filtros.get("competencia-fim");
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
    if (filtros.containsKey("data-guia-inicio") &&
        filtros.containsKey("data-guia-fim")) {
      LocalDate inicio = LocalDate.parse(
          String.valueOf(filtros.get("data-guia-inicio")));
      LocalDate fim = LocalDate.parse(
          String.valueOf(filtros.get("data-guia-fim")));
      if (fim.isBefore(inicio)) {
        throw new IllegalArgumentException(
            "A data final da guia não pode ser anterior à inicial.");
      }
    }
  }

  private void validarIntervaloValores(Map<String, Object> filtros) {
    if (filtros.containsKey("valor-minimo") &&
        filtros.containsKey("valor-maximo")) {
      BigDecimal minimo = (BigDecimal) filtros.get("valor-minimo");
      BigDecimal maximo = (BigDecimal) filtros.get("valor-maximo");
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
      int pagina,
      int tamanhoPagina) {
  }
}