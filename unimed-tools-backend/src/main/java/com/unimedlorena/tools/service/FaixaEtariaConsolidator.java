package com.unimedlorena.tools.service;

import com.unimedlorena.tools.exception.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Acumula apenas as dez faixas, sem guardar as páginas ou somar subtotais de contratos. */
final class FaixaEtariaConsolidator {
  static final String API = "0090-faixa-etaria";
  private static final List<String> FAIXAS = List.of(
    "0 a 18", "19 a 23", "24 a 28", "29 a 33", "34 a 38",
    "39 a 43", "44 a 48", "49 a 53", "54 a 58", "59 a 999"
  );
  private final long[][] totais = new long[10][6];
  private boolean possuiFaixa;
  private boolean possuiAgregado;

  static boolean aplicavel(String api) {
    return API.equalsIgnoreCase(api == null ? "" : api.trim());
  }

  void aceitar(List<LinkedHashMap<String, Object>> pagina) {
    for (Map<String, Object> linha : pagina) {
      Object rotulo = valor(linha, "FAIXA_ETARIA");
      if (rotulo == null) throw invalido();
      String faixa = rotulo.toString().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
      if (faixa.startsWith("contrato ") || faixa.equals("total geral")) continue;
      int indice = FAIXAS.indexOf(faixa);
      if (indice < 0) throw invalido();
      possuiFaixa = true;
      String[] campos = {"DEP_MASC", "DEP_FEM", "TIT_MASC", "TIT_FEM", "AGR_MASC", "AGR_FEM"};
      for (int i = 0; i < campos.length; i++) {
        try {
          long quantidade = numero(linha, campos[i]);
          totais[indice][i] = Math.addExact(totais[indice][i], quantidade);
          if (i >= 4 && quantidade > 0) possuiAgregado = true;
        } catch (ArithmeticException ex) {
          throw invalido();
        }
      }
    }
  }

  List<LinkedHashMap<String, Object>> resultado() {
    if (!possuiFaixa) return List.of();
    List<LinkedHashMap<String, Object>> linhas = new ArrayList<>();
    long[] soma = new long[6];
    try {
      for (int i = 0; i < FAIXAS.size(); i++) {
        linhas.add(linha(FAIXAS.get(i), totais[i]));
        for (int j = 0; j < soma.length; j++) soma[j] = Math.addExact(soma[j], totais[i][j]);
      }
      linhas.add(linha("TOTAL GERAL", soma));
    } catch (ArithmeticException ex) {
      throw invalido();
    }
    return linhas;
  }

  private LinkedHashMap<String, Object> linha(String faixa, long[] v) {
    LinkedHashMap<String, Object> linha = new LinkedHashMap<>();
    linha.put("FAIXA_ETARIA", faixa);
    long dep = Math.addExact(v[0], v[1]);
    long tit = Math.addExact(v[2], v[3]);
    long agr = Math.addExact(v[4], v[5]);
    linha.put("DEP", dep);
    linha.put("TIT", tit);
    if (possuiAgregado) linha.put("AGR", agr);
    linha.put("FEM", Math.addExact(Math.addExact(v[1], v[3]), v[5]));
    linha.put("MASC", Math.addExact(Math.addExact(v[0], v[2]), v[4]));
    linha.put("TOTAL", Math.addExact(Math.addExact(dep, tit), agr));
    return linha;
  }

  private long numero(Map<String, Object> linha, String campo) {
    Object valor = valor(linha, campo);
    if (valor == null || valor.toString().isBlank()) throw invalido();
    try {
      long numero = new BigDecimal(valor.toString().trim()).longValueExact();
      if (numero < 0) throw invalido();
      return numero;
    } catch (NumberFormatException | ArithmeticException ex) {
      throw invalido();
    }
  }

  private Object valor(Map<String, Object> linha, String campo) {
    return linha.entrySet().stream().filter(e -> campo.equalsIgnoreCase(e.getKey()))
      .map(e -> e.getValue() == null ? "" : e.getValue()).findFirst().orElse(null);
  }

  private ApiException invalido() {
    return new ApiException(HttpStatus.BAD_GATEWAY, "FAIXA_ETARIA_INVALIDA",
      "O SGU devolveu faixas ou contagens inválidas. Não foi possível consolidar a faixa etária.");
  }
}
