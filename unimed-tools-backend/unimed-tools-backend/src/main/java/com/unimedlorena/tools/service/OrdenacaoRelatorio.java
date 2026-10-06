package com.unimedlorena.tools.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mantém desempates completos sem exceder o campo curto de ordenação do SGU. */
final class OrdenacaoRelatorio {
  static final String COLUNA_TECNICA = "UT_EXPORT_ORD";

  private OrdenacaoRelatorio() {}

  static Map<String, Object> preparar(Map<String, Object> definicao) {
    String ordem = String.valueOf(definicao.getOrDefault("ordenacao", "")).trim();
    if (ordem.length() <= 100) return definicao;
    // A forma longa automática contém apenas aliases projetados. Expressões
    // arbitrárias/posições não são reinterpretadas como constantes analíticas.
    String criterio = "[A-Za-z][A-Za-z0-9_$#]*(?:\\s+(?i:ASC|DESC))?(?:\\s+(?i:NULLS)\\s+(?i:FIRST|LAST))?";
    if (!ordem.matches(criterio + "(?:\\s*,\\s*" + criterio + ")*")) {
      throw new IllegalArgumentException("A ordenação extensa deve usar aliases das colunas do relatório.");
    }
    String sql = String.valueOf(definicao.getOrDefault("consultaSQL", "")).strip().replaceFirst(";\\s*$", "");
    if (sql.isBlank() || sql.toUpperCase(java.util.Locale.ROOT).contains(COLUNA_TECNICA)) {
      throw new IllegalArgumentException("Revise a consulta e sua coluna técnica de ordenação antes de salvar.");
    }
    Map<String, Object> preparada = new LinkedHashMap<>(definicao);
    preparada.put("consultaSQL", "SELECT UT_DADOS.*, ROW_NUMBER() OVER (ORDER BY " + ordem +
      ") AS " + COLUNA_TECNICA + " FROM (\n" + sql + "\n) UT_DADOS");
    preparada.put("ordenacao", COLUNA_TECNICA);
    return preparada;
  }
}
