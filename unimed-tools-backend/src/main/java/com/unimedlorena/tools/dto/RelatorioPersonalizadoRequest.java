/*
 * Responsabilidade: Define filtros, colunas, transformações e paginação do relatório personalizado.
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
  List<String> colunasMeses,
  String rankingDimensao,
  String rankingMetrica,
  String rankingDirecao,
  Integer rankingLimite,
  List<String> ordemResultado,
  Integer pagina,
  Integer tamanhoPagina,
  String nomeArquivo
) {
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
        null,
        null,
        null,
        List.of(),
        pagina,
        tamanhoPagina,
        nomeArquivo);
  }

  public RelatorioPersonalizadoRequest(
      List<String> colunas,
      Map<String, Object> filtros,
      Boolean distinct,
      Integer pagina,
      Integer tamanhoPagina,
      String nomeArquivo) {
    this(colunas, filtros, distinct, null, null, pagina, tamanhoPagina, nomeArquivo);
  }
}
