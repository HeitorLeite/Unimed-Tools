/*
 * Responsabilidade: Corrige o grupo do prestador nas APIs da ferramenta Comercial.
 */
package com.unimedlorena.tools.service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class GrupoPrestadorComercialNormalizer {

  private static final Set<String> APIS_COMERCIAIS = Set.of(
    "0090-beneficiario-empresa",
    "0090-receita-empresa-com-grupo",
    "0090-despesa-empresas",
    "0090-faixa-etaria"
  );
  private static final Set<String> COLUNAS_GRUPO = Set.of("GRUPOPRESTADOR");
  private static final Set<String> COLUNAS_NOME = Set.of("NOMEPRESTADOR");
  private static final Set<String> COLUNAS_TIPO = Set.of("TIPOPRESTADOR");
  private static final Set<String> FLEXOES_MEDICO = Set.of(
    "MEDICA", "MEDICO", "MEDICAS", "MEDICOS"
  );
  private static final Set<String> FLEXOES_COOPERADO = Set.of(
    "COOPERADA", "COOPERADO", "COOPERADAS", "COOPERADOS"
  );
  private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
  private static final Pattern PONTUACAO = Pattern.compile("[^A-Z0-9]+");
  private static final Pattern ESPACOS = Pattern.compile("\\s+");

  private static final List<RegraNome> REGRAS_NOME = List.of(
    new RegraNome("RECURSO PROPRIO", List.of("UNIMED", "LORENA"), true),
    new RegraNome(
      "SESSOES MULTI",
      List.of("FISIOCLINICA", "MBA GUARANY", "NUTRISAUDE", "VTALLIS"),
      false
    ),
    new RegraNome("CLINICA DE IMAGEM", List.of("CAVALCA"), false),
    new RegraNome(
      "CLINICA MEDICA",
      List.of(
        "CLINICA KARINE",
        "FUNDACAO JOAO PAULO II",
        "L2A",
        "PRONTOCOR",
        "SOCIEDADE DE OFTALMOLOGIA"
      ),
      false
    ),
    new RegraNome("REEMBOLSO", List.of("PRESTADOR", "REEMBOLSO"), true)
  );

  /**
   * Altera somente GRUPO_PRESTADOR e devolve a quantidade de linhas corrigidas.
   */
  public int normalizar(
    String apiNome,
    List<LinkedHashMap<String, Object>> registros
  ) {
    if (!ehApiComercial(apiNome) || registros == null || registros.isEmpty()) return 0;

    int alterados = 0;
    for (LinkedHashMap<String, Object> registro : registros) {
      String colunaGrupo = localizarColuna(registro, COLUNAS_GRUPO);
      if (colunaGrupo == null || !grupoElegivel(texto(registro.get(colunaGrupo)))) continue;

      String resolvido = resolver(registro);
      if (resolvido == null) continue;
      registro.put(colunaGrupo, resolvido);
      alterados++;
    }
    return alterados;
  }

  public String normalizarTexto(String valor) {
    if (valor == null || valor.isBlank()) return "";
    String semAcentos = ACENTOS.matcher(
      Normalizer.normalize(valor, Normalizer.Form.NFD)
    ).replaceAll("");
    return ESPACOS.matcher(PONTUACAO.matcher(
      semAcentos.toUpperCase(Locale.ROOT)
    ).replaceAll(" ").trim()).replaceAll(" ");
  }

  private String resolver(Map<String, Object> registro) {
    if ("OPME".equals(normalizarTexto(texto(valor(registro, COLUNAS_TIPO))))) {
      return "OPME";
    }

    String nomePrestador = normalizarTexto(texto(valor(registro, COLUNAS_NOME)));
    for (RegraNome regra : REGRAS_NOME) {
      if (regra.corresponde(nomePrestador)) return regra.destino;
    }
    return null;
  }

  private boolean ehApiComercial(String apiNome) {
    return apiNome != null && APIS_COMERCIAIS.contains(
      apiNome.strip().toLowerCase(Locale.ROOT)
    );
  }

  private boolean grupoElegivel(String grupo) {
    String[] palavras = normalizarTexto(grupo).split(" ");
    return palavras.length == 3 &&
      FLEXOES_MEDICO.contains(palavras[0]) &&
      "NAO".equals(palavras[1]) &&
      FLEXOES_COOPERADO.contains(palavras[2]);
  }

  private Object valor(Map<String, Object> registro, Set<String> aliases) {
    String coluna = localizarColuna(registro, aliases);
    return coluna == null ? null : registro.get(coluna);
  }

  private String localizarColuna(Map<String, Object> registro, Set<String> aliases) {
    if (registro == null) return null;
    for (String coluna : registro.keySet()) {
      if (aliases.contains(normalizarColuna(coluna))) return coluna;
    }
    return null;
  }

  private String normalizarColuna(String coluna) {
    return coluna == null
      ? ""
      : coluna.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }

  private String texto(Object valor) {
    return valor == null ? "" : String.valueOf(valor);
  }

  private record RegraNome(
    String destino,
    List<String> identificadores,
    boolean exigirTodos
  ) {
    boolean corresponde(String nomePrestador) {
      if (nomePrestador.isBlank()) return false;
      if (exigirTodos) {
        return identificadores.stream().allMatch(
          identificador -> contemFrase(nomePrestador, identificador)
        );
      }
      return identificadores.stream().anyMatch(
        identificador -> contemFrase(nomePrestador, identificador)
      );
    }

    private static boolean contemFrase(String texto, String frase) {
      return (" " + texto + " ").contains(" " + frase + " ");
    }
  }
}
