/*
 * Responsabilidade: Define filtros, colunas e paginação do relatório personalizado.
 */
package com.unimedlorena.tools.dto;

import java.util.List;
import java.util.Map;

public record RelatorioPersonalizadoRequest(
  List<String> colunas,
  Map<String, Object> filtros,
  Integer pagina,
  Integer tamanhoPagina,
  String nomeArquivo
) {}
