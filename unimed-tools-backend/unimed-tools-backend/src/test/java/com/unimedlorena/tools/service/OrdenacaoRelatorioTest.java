package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;

class OrdenacaoRelatorioTest {
  @Test void preservaTodosOsDesempatesSemExcederBufferDoSgu() {
    String ordem = IntStream.rangeClosed(1, 54).mapToObj(i -> "COLUNA_" + i).collect(Collectors.joining(","));
    String sql = "SELECT COLUNA_1 FROM ORIGEM WHERE 1=1 /*FILTROS*/";
    var original = Map.<String,Object>of("nome", "teste", "consultaSQL", sql, "ordenacao", ordem, "filtros", List.of());
    var preparada = OrdenacaoRelatorio.preparar(original);
    assertThat(preparada.get("ordenacao")).isEqualTo("UT_EXPORT_ORD");
    assertThat(preparada.get("consultaSQL").toString()).contains("ROW_NUMBER() OVER (ORDER BY " + ordem + ")", sql);
    assertThat(preparada.get("filtros")).isSameAs(original.get("filtros"));
    assertThat(original.get("consultaSQL")).isEqualTo(sql);
    assertThat(OrdenacaoRelatorio.preparar(preparada)).isEqualTo(preparada);
  }

  @Test void preservaOrdenacaoManualCurta() {
    var original = Map.<String,Object>of("ordenacao", "DATA DESC,ID");
    assertThat(OrdenacaoRelatorio.preparar(original)).isSameAs(original);
  }
}
