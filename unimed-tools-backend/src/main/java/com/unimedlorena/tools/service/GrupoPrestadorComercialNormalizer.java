/*
 * Responsabilidade: Corrige o grupo do prestador e sintetiza sessões multi
 * nas APIs da ferramenta Comercial.
 */
package com.unimedlorena.tools.service;

import java.math.BigDecimal;
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

  private static final String API_DESPESA = "0090-despesa-empresas";

  private static final Set<String> APIS_COMERCIAIS = Set.of(
    "0090-beneficiario-empresa",
    "0090-receita-empresa-com-grupo",
    API_DESPESA,
    "0090-faixa-etaria"
  );
  private static final Set<String> COLUNAS_GRUPO = Set.of("GRUPOPRESTADOR");
  private static final Set<String> COLUNAS_NOME = Set.of("NOMEPRESTADOR");
  private static final Set<String> COLUNAS_TIPO = Set.of("TIPOPRESTADOR");
  private static final Set<String> COLUNAS_CODIGO_ITEM = Set.of(
    "CODIGOITEM", "CODITEM", "ITEMCOD", "CODIGOPROCEDIMENTO", "CODPROCEDIMENTO",
    "CODIGOTUSS", "CODTUSS", "CODIGOSERVICO", "CODSERVICO", "CODPROCED"
  );
  private static final Set<String> COLUNAS_DESCRICAO_ITEM = Set.of(
    "DESCRICAOITEM", "DESCITEM", "DESCRICAOPROCEDIMENTO", "DESCPROCEDIMENTO",
    "NOMEPROCEDIMENTO", "PROCEDIMENTO", "DESCRICAOSERVICO"
  );
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

  private static final Map<String, String> DESCRICOES_SESSOES = criarDescricoesSessoes();

  /**
   * Corrige GRUPO_PRESTADOR e, exclusivamente em Despesas, sintetiza
   * DESCRICAO_ITEM quando a linha pertence a SESSOES MULTI.
   *
   * @return quantidade de linhas que tiveram ao menos uma alteração.
   */
  public int normalizar(
    String apiNome,
    List<LinkedHashMap<String, Object>> registros
  ) {
    if (!ehApiComercial(apiNome) || registros == null || registros.isEmpty()) return 0;

    int alterados = 0;
    for (LinkedHashMap<String, Object> registro : registros) {
      boolean alterado = false;
      String colunaGrupo = localizarColuna(registro, COLUNAS_GRUPO);

      if (colunaGrupo != null && grupoElegivel(texto(registro.get(colunaGrupo)))) {
        String resolvido = resolver(registro);
        if (resolvido != null) {
          registro.put(colunaGrupo, resolvido);
          alterado = true;
        }
      }

      if (normalizarDescricaoSessao(apiNome, registro)) alterado = true;
      if (alterado) alterados++;
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

  private boolean normalizarDescricaoSessao(
    String apiNome,
    LinkedHashMap<String, Object> registro
  ) {
    if (!API_DESPESA.equalsIgnoreCase(apiNome == null ? "" : apiNome.trim())) return false;

    String colunaGrupo = localizarColuna(registro, COLUNAS_GRUPO);
    if (colunaGrupo == null ||
        !"SESSOES MULTI".equals(normalizarTexto(texto(registro.get(colunaGrupo))))) {
      return false;
    }

    String colunaCodigo = localizarColuna(registro, COLUNAS_CODIGO_ITEM);
    String colunaDescricao = localizarColuna(registro, COLUNAS_DESCRICAO_ITEM);
    if (colunaCodigo == null || colunaDescricao == null) return false;

    String codigo = normalizarCodigoProcedimento(registro.get(colunaCodigo));
    String descricao = DESCRICOES_SESSOES.get(codigo);
    if (descricao == null || descricao.equals(texto(registro.get(colunaDescricao)))) return false;

    registro.put(colunaDescricao, descricao);
    return true;
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

  private String normalizarCodigoProcedimento(Object valor) {
    if (valor == null) return "";
    if (valor instanceof Number numero) {
      try {
        return new BigDecimal(numero.toString()).stripTrailingZeros().toPlainString()
          .replaceFirst("\\.0+$", "");
      } catch (NumberFormatException ignored) {
        return "";
      }
    }

    String texto = String.valueOf(valor).trim();
    if (texto.matches("\\d+(?:\\.0+)?")) {
      int ponto = texto.indexOf('.');
      return ponto < 0 ? texto : texto.substring(0, ponto);
    }
    return texto.replaceAll("[^0-9]", "");
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

  private static Map<String, String> criarDescricoesSessoes() {
    Map<String, String> mapa = new LinkedHashMap<>();

    adicionar(mapa, "Sessao de Fisioterapia",
      "20103069","20103077","20103093","20103107","20103131","20103182","20103190",
      "20103204","20103212","20103220","20103247","20103255","20103263","20103271",
      "20103280","20103298","20103310","20103328","20103344","20103360","20103379",
      "20103387","20103395","20103409","20103417","20103425","20103433","20103441",
      "20103450","20103468","20103476","20103484","20103492","20103506","20103514",
      "20103522","20103530","20103611","20103620","20103646","20103662","20103670",
      "20103689","20103697","20103700","20103727",
      "50000144","50000160","50000195","50000209","50000217","50000233","50000349",
      "50000365","50000381","50000390","50000403","50000411","50000454","50000713",
      "50000730","50000748","50000756","50000764","50000772","50000780","50000799",
      "50000802","50000810","50000829","50001000","50001019","50001043","50001051",
      "50001078"
    );

    adicionar(mapa, "Sessao de Terapia Ocupacional",
      "50000055","50000071","50000080","50000101","50000128","50000136"
    );
    adicionar(mapa, "Sessao de Psicologia/Psicoterapia",
      "50000470","50000489","50001183","50001191","50001221","50001230"
    );
    adicionar(mapa, "Sessao de Fonoaudiologia",
      "50000586","50000608","50000616","50000632","50000640"
    );
    adicionar(mapa, "Sessao de Psicomotricidade", "50000020","50000012");
    adicionar(mapa, "Sessao de Nutricao", "50000560");
    adicionar(mapa, "Sessoa de Musicoterapia", "50001213");

    return Map.copyOf(mapa);
  }

  private static void adicionar(
    Map<String, String> mapa,
    String descricao,
    String... codigos
  ) {
    for (String codigo : codigos) mapa.put(codigo, descricao);
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
