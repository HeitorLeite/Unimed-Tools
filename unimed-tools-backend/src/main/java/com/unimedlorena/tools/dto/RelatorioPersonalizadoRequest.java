/*
 * Responsabilidade: Define filtros, colunas, analises e paginacao do relatorio personalizado.
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
  List<String> metricasMes,
  String rankingTipo,
  Integer rankingLimite,
  String rankingDimensao,
  String rankingMetrica,
  Integer pagina,
  Integer tamanhoPagina,
  String nomeArquivo
) {
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
        null,
        null,
        null,
        pagina,
        tamanhoPagina,
        nomeArquivo);
  }

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
        pagina,
        tamanhoPagina,
        nomeArquivo);
  }
}
