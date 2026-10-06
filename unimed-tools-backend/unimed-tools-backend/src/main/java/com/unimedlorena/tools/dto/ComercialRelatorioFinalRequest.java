package com.unimedlorena.tools.dto;

import java.util.List;
import java.util.Map;

/**
 * Parâmetros necessários para gerar o relatório executivo do Comercial.
 *
 * <p>Os filtros são produzidos pelo mesmo fluxo já usado na tela Comercial.
 * O backend altera apenas a competência para percorrer a janela móvel de
 * doze meses; nenhum SQL ou segredo é recebido do navegador.</p>
 */
public record ComercialRelatorioFinalRequest(
  String empresa,
  String competencia,
  Map<String, List<Map<String, Object>>> filtrosPorApi
) {}
