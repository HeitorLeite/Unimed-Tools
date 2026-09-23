/*
 * Responsabilidade: Define filtros, colunas, análises e paginação do relatório personalizado.
 */
package com.unimedlorena.tools.dto;

import java.util.List;
import java.util.Map;

public record RelatorioPersonalizadoRequest(
  List<String> colunas,
  Map<String, Object> filtros,
  Boolean distinct,
  String ordenarPor,
  String direcaoOrdenacao,
  Boolean separarMeses,
  List<String> metricasPorMes,
  Ranking ranking,
  List<String> ordemResultado,
  Integer pagina,
  Integer tamanhoPagina,
  String nomeArquivo
) {
  public record Ranking(
    String modo,
    Integer quantidade,
    String dimensao,
    String metrica
  ) {}

  /**
   * Compatibilidade com chamadas anteriores que ainda não enviam ordenação
   * nem opções analíticas.
   */
  public RelatorioPersonalizadoRequest(
      List<String> colunas,
      Map<String, Object> filtros,
      Boolean distinct,
      Integer pagina,
      Integer tamanhoPagina,
      String nomeArquivo) {
    this(
      colunas,
      filtros,
      distinct,
      null,
      null,
      false,
      List.of(),
      null,
      List.of(),
      pagina,
      tamanhoPagina,
      nomeArquivo
    );
  }

  /**
   * Compatibilidade com o contrato que já suportava ordenação.
   */
  public RelatorioPersonalizadoRequest(
      List<String> colunas,
      Map<String, Object> filtros,
      Boolean distinct,
      String ordenarPor,
      String direcaoOrdenacao,
      Integer pagina,
      Integer tamanhoPagina,
      String nomeArquivo) {
    this(
      colunas,
      filtros,
      distinct,
      ordenarPor,
      direcaoOrdenacao,
      false,
      List.of(),
      null,
      List.of(),
      pagina,
      tamanhoPagina,
      nomeArquivo
    );
  }
}
