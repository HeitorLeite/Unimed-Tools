package com.unimedlorena.tools.dto;

import java.util.Map;

public record HospitalRelatorioRequest(
  Map<String, Object> filtros,
  Integer pagina,
  Integer tamanhoPagina,
  String nomeArquivo
) {}
