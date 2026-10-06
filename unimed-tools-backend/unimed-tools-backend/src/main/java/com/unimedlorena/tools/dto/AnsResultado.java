/*
 * Responsabilidade: Representa o arquivo filtrado e as estatísticas retornadas pelo serviço ANS.
 */
package com.unimedlorena.tools.dto;

import java.util.Map;

public record AnsResultado(
  int lidas,
  int removidas,
  int mantidas,
  Map<String, Integer> chavesPorTipo
) {}
