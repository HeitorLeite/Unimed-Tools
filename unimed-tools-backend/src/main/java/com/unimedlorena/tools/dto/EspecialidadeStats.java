/*
 * Responsabilidade: Representa as métricas calculadas durante o preenchimento de especialidades.
 */
package com.unimedlorena.tools.dto;

public record EspecialidadeStats(
  int total,
  int preenchidas,
  int jaOk,
  int semInfo,
  String aba
) {}
