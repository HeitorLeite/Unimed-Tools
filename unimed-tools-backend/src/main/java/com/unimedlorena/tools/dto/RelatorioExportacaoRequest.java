/*
 * Responsabilidade: Define o corpo de uma exportação individual de relatório.
 */
package com.unimedlorena.tools.dto;

import java.util.Map;

public record RelatorioExportacaoRequest(
  Map<String, Object> filtros,
  String nomeArquivo
) {}
