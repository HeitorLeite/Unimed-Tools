/*
 * Responsabilidade: Normaliza a especialidade de todos os itens de uma guia.
 */
package com.unimedlorena.tools.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EspecialidadeRelatorioResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(
    EspecialidadeRelatorioResolver.class
  );

  private static final Set<String> COLUNAS_ESPECIALIDADE = Set.of("NOMEESPECIALIDADE");
  private static final Set<String> COLUNAS_BENEFICIARIO = Set.of(
    "CODBENEFICIARIO", "CODIGOBENEFICIARIO"
  );
  private static final Set<String> COLUNAS_GUIA = Set.of(
    "NUMGUIA", "NROGUIA", "NUMEROGUIA"
  );
  private static final Set<String> COLUNAS_DATA = Set.of("DATAGUIA");
  private static final Set<String> COLUNAS_DESCRICAO = Set.of(
    "DESCRICAOITEM", "DESCITEM"
  );
  private static final Set<String> COLUNAS_CID = Set.of("CID", "CODCID", "CODIGOCID");
  private static final Set<String> GENERICAS = Set.of("CLINICO", "CLINICA MEDICA");
  private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
  private static final Pattern PONTUACAO = Pattern.compile("[^A-Z0-9]+");
  private static final Pattern ESPACOS = Pattern.compile("\\s+");
  private static final Pattern SEPARADORES_CID = Pattern.compile("[.\\s]+");

  private static final Map<String, String> POR_NOME = criarMapaNomes();
  private static final List<RegraDescricao> POR_DESCRICAO_PRINCIPAL = criarRegrasDescricaoPrincipal();
  private static final List<RegraDescricao> POR_DESCRICAO_RESIDUAL = criarRegrasDescricaoResidual();
  private static final Map<String, String> POR_CID_SEGURO = Map.ofEntries(
    Map.entry("K804", "GASTROENTEROLOGIA"),
    Map.entry("K573", "GASTROENTEROLOGIA"),
    Map.entry("K40", "CIRURGIA GERAL"),
    Map.entry("N390", "UROLOGIA"),
    Map.entry("K801", "CIRURGIA GERAL"),
    Map.entry("S829", "ORTOPEDIA E TRAUMATOLOGIA"),
    Map.entry("O809", "OBSTETRICIA"),
    Map.entry("S729", "ORTOPEDIA E TRAUMATOLOGIA")
  );

  public boolean aplicavel(List<? extends Map<String, Object>> registros) {
    return registros != null && registros.stream().anyMatch(registro ->
      localizarColuna(registro, COLUNAS_ESPECIALIDADE) != null
    );
  }

  /**
   * Resolve cada guia em tempo linear e altera somente NOME_ESPECIALIDADE.
   * Quando a origem omite parte da chave mínima, a linha recebe uma chave única
   * para impedir que guias distintas sejam misturadas por acidente.
   */
  public List<LinkedHashMap<String, Object>> normalizar(
    List<LinkedHashMap<String, Object>> registros
  ) {
    if (!aplicavel(registros)) return registros;

    Map<String, Guia> guias = new LinkedHashMap<>();
    AtomicInteger linha = new AtomicInteger();
    for (LinkedHashMap<String, Object> registro : registros) {
      String colunaEspecialidade = localizarColuna(registro, COLUNAS_ESPECIALIDADE);
      if (colunaEspecialidade == null) continue;

      String chave = chaveGuia(registro, linha.getAndIncrement());
      Guia guia = guias.computeIfAbsent(chave, ignorada -> new Guia());
      guia.itens.add(new Item(registro, colunaEspecialidade));
      guia.nomes.add(texto(valor(registro, COLUNAS_ESPECIALIDADE)));
      guia.descricoes.add(texto(valor(registro, COLUNAS_DESCRICAO)));
      guia.cids.add(texto(valor(registro, COLUNAS_CID)));
    }

    Set<String> cidsNaoMapeados = new TreeSet<>();
    for (Guia guia : guias.values()) {
      String especialidade = resolverGuia(guia, cidsNaoMapeados);
      guia.itens.forEach(item -> item.registro.put(item.colunaEspecialidade, especialidade));
    }
    if (!cidsNaoMapeados.isEmpty()) {
      LOGGER.warn(
        "Normalização residual encontrou {} CID(s) ainda não mapeados: {}",
        cidsNaoMapeados.size(),
        String.join(", ", cidsNaoMapeados)
      );
    }
    return registros;
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

  private String resolverGuia(Guia guia, Set<String> cidsNaoMapeados) {
    String principal = resolverPrincipal(guia);
    if (!"CLINICO".equals(principal)) return principal;

    String porCid = resolverCidResidual(guia, cidsNaoMapeados);
    if (porCid != null) return porCid;

    String porDescricao = resolverDescricao(guia, POR_DESCRICAO_RESIDUAL);
    return porDescricao == null ? "CLINICO" : porDescricao;
  }

  private String resolverPrincipal(Guia guia) {
    String generica = null;
    for (String nome : guia.nomes) {
      String encontrada = POR_NOME.get(normalizarTexto(nome));
      if (encontrada == null) continue;
      if (!GENERICAS.contains(encontrada)) return encontrada;
      if (generica == null) generica = encontrada;
    }
    if (generica != null) return generica;

    String porDescricao = resolverDescricao(guia, POR_DESCRICAO_PRINCIPAL);
    return porDescricao == null ? "CLINICO" : porDescricao;
  }

  private String resolverDescricao(Guia guia, List<RegraDescricao> regras) {
    for (RegraDescricao regra : regras) {
      boolean corresponde = guia.descricoes.stream()
        .map(this::normalizarTexto)
        .anyMatch(regra::corresponde);
      if (corresponde) return regra.especialidade;
    }
    return null;
  }

  private String resolverCidResidual(Guia guia, Set<String> cidsNaoMapeados) {
    String especialidade = null;
    for (String cid : guia.cids) {
      String normalizado = normalizarCid(cid);
      if (normalizado.isBlank()) continue;
      String encontrada = POR_CID_SEGURO.get(normalizado);
      if (encontrada != null) {
        if (especialidade == null) especialidade = encontrada;
      } else {
        cidsNaoMapeados.add(normalizado);
      }
    }
    return especialidade;
  }

  private String normalizarCid(String cid) {
    return cid == null
      ? ""
      : SEPARADORES_CID.matcher(cid.toUpperCase(Locale.ROOT)).replaceAll("");
  }

  private String chaveGuia(Map<String, Object> registro, int indice) {
    String beneficiario = chave(valor(registro, COLUNAS_BENEFICIARIO));
    String guia = chave(valor(registro, COLUNAS_GUIA));
    String data = chave(valor(registro, COLUNAS_DATA));
    if (beneficiario.isBlank() || guia.isBlank() || data.isBlank()) {
      return "LINHA_SEM_CHAVE|" + indice;
    }
    return beneficiario + "|" + guia + "|" + data;
  }

  private String chave(Object valor) {
    return texto(valor).strip().toUpperCase(Locale.ROOT);
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

  private static Map<String, String> criarMapaNomes() {
    Map<String, String> mapa = new LinkedHashMap<>();
    aliases(mapa, "ALERGIA E IMUNOLOGIA", "ALERGIA E IMUNOLOGIA");
    aliases(mapa, "ANATOMIA PATOLOGICA", "ANATOMIA PATOLOGICA");
    aliases(mapa, "ANESTESIOLOGIA", "ANESTESIOLOGIA");
    aliases(mapa, "CARDIOLOGIA", "CARDIOLOGIA", "CLINICA DE CARDIOLOGIA",
      "ELETROFISIOLOGIA CARDIACA/ABLA", "CIRURGIA CARDIOVASCULAR");
    aliases(mapa, "CIRURGIA CRANIO-MAXILO-FACIAL", "CIRURGIA CRANIO-MAXILO-FACIAL");
    aliases(mapa, "CIRURGIA GERAL", "CIRURGIA GERAL");
    aliases(mapa, "CIRURGIA PEDIATRICA", "CIRURGIA PEDIATRICA");
    aliases(mapa, "CIRURGIA PLASTICA", "CIRURGIA PLASTICA", "MICROCIRUGIA REPARADORA");
    aliases(mapa, "CIRURGIA VASCULAR", "CIRURGIA VASCULAR", "CONSULTA EM ANGIOLOGIA");
    aliases(mapa, "CLINICA MEDICA", "CLINICA MEDICA", "CONSULTA CLINICA MEDICA");
    aliases(mapa, "CLINICO", "CLINICO", "MED. INTERNA OU CLINICA MEDICA", "MEDICO",
      "INTERNACOES/ACOMP. CLINICO", "CHECK UP", "HOSPITAL GERAL",
      "ADMINISTRACAO EM SAUDE", "MEDICINA DE EMERGENCIA", "PRONTO SOCORRO",
      "PRONTO-SOCORRO ADULTO");
    aliases(mapa, "DERMATOLOGIA", "DERMATOLOGIA");
    aliases(mapa, "ENDOCRINOLOGIA", "ENDOCRINOLOGIA");
    aliases(mapa, "ENFERMAGEM", "ENFERMAGEM");
    aliases(mapa, "FISIOTERAPIA", "FISIOTERAPIA", "RPG");
    aliases(mapa, "FONOAUDIOLOGIA", "FONOAUDIOLOGIA");
    aliases(mapa, "GASTROENTEROLOGIA", "GASTROENTEROLOGIA", "CONSULTA EM GASTROENTEROLOGIA",
      "ENDOSCOPIA", "MUCOSECTOMIA", "CIRURGIA DO APARELHO DIGESTIVO",
      "COLOPROCTOLOGIA", "CONSULTA PROCTOLOGIA");
    aliases(mapa, "GENETICA MEDICA", "CONSULTA EM GENETICA MEDICA");
    aliases(mapa, "GERIATRIA", "GERIATRIA");
    aliases(mapa, "GINECOLOGIA", "CONSULTA EM GINECOLOGIA", "RECANALIZACAO TUBARIA");
    aliases(mapa, "OBSTETRICIA", "GINECOLOGIA E OBSTETRICIA", "OBSTETRICIA");
    aliases(mapa, "HEMATOLOGIA", "HEMATOLOGIA", "TRANSPLANTE DE MEDULA OSSEA AU");
    aliases(mapa, "HOME CARE", "HOME CARE");
    aliases(mapa, "HOMEOPATIA", "HOMEOPATIA");
    aliases(mapa, "INFECTOLOGIA", "INFECTOLOGIA");
    aliases(mapa, "MASTOLOGIA", "MASTOLOGIA");
    aliases(mapa, "MEDICINA DE FAMILIA E COMUNIDADE", "MEDICINA DE FAMILIA/COMUNIDADE");
    aliases(mapa, "MEDICINA DO TRABALHO", "MEDICINA DO TRABALHO", "CONSULTA MEDICINA DO TRABALHO");
    aliases(mapa, "MEDICINA INTENSIVA", "MEDICINA INTENSIVA", "TERAPIA INTENSIVA");
    aliases(mapa, "NEFROLOGIA", "NEFROLOGIA", "DIALISE");
    aliases(mapa, "NEUROCIRURGIA", "NEUROCIRURGIA", "CONSULTA EM NEUROCIRURGIA",
      "TRATAMENTO CIRURGICO DA EPILEP");
    aliases(mapa, "NEUROLOGIA", "NEUROLOGIA", "REPOSICAO DE FARMACOS EM BOMBA");
    aliases(mapa, "NEUROLOGIA PEDIATRICA", "NEUROLOGIA PEDIATRICA");
    aliases(mapa, "NUTRICAO", "NUTRICIONISTA", "CONSULTA COM NUTRICIONISTA", "NUTROLOGIA");
    aliases(mapa, "ODONTOLOGIA", "ODONTOLOGIA", "CIRURGIA ODONT. AMBULATORIAL", "PERIODONTIA");
    aliases(mapa, "OFTALMOLOGIA", "OFTALMOLOGIA", "CLINICA DE OFTALMOLOGIA",
      "CONSULTA EM OFTALMOLOGIA", "REMOCAO DE PIGMENTOS DA LENTE");
    aliases(mapa, "ONCOLOGIA", "CANCEROLOGIA", "ONCOLOGIA CLINICA", "ONCOLOGIA CIRURGICA",
      "CIRURGIA ONCOLOGICA", "RADIOTERAPIA");
    aliases(mapa, "ORTOPEDIA E TRAUMATOLOGIA", "ORTOPEDIA E TRAUMATOLOGIA", "CIRURGIA DO TRAUMA");
    aliases(mapa, "OTORRINOLARINGOLOGIA", "OTORRINOLARINGOLOGIA", "CIRURGIA DE IMPLANTE COCLEAR",
      "IMPEDANCIOMETRIA", "BRONCOESOFAGOLOGIA", "CIRURGIA DE CABECA E PESCOCO");
    aliases(mapa, "PEDIATRIA", "PEDIATRIA", "CONSULTA EM PEDIATRIA", "PRONTO ATENDIMENTO PEDIATRICO");
    aliases(mapa, "PNEUMOLOGIA", "PNEUMOLOGIA", "CONSULTA EM PNEUMOLOGIA");
    aliases(mapa, "PSICOLOGIA", "PSICOLOGIA", "ATENDIMENTO PSICOSSOCIAL");
    aliases(mapa, "PSIQUIATRIA", "PSIQUIATRIA", "CONSULTA EM PSIQUIATRIA");
    aliases(mapa, "RADIOLOGIA E DIAGNOSTICO POR IMAGEM", "RADIOLOGIA E DIAGNOSTICO POR IMAGEM",
      "RADIOLOGIA E DIAG. POR IMAGEM", "CLINICA DE IMAGEM", "DENSITOMETRIA OSSEA",
      "ULTRASSONOGRAFIA", "RESSONANCIA MAGNETICA", "MEDICINA NUCLEAR");
    aliases(mapa, "REUMATOLOGIA", "REUMATOLOGIA", "CONSULTA EM REUMATOLOGIA");
    aliases(mapa, "TERAPIA OCUPACIONAL", "TERAPIA OCUPACIONAL");
    aliases(mapa, "TELECONSULTA", "TELECONSULTA");
    aliases(mapa, "UROLOGIA", "UROLOGIA", "CONSULTA EM UROLOGIA",
      "VASO-VASOSTOMIA MICROCIRURGIA", "HIPERTERMIA PROSTATICA");
    return Map.copyOf(mapa);
  }

  private static void aliases(Map<String, String> mapa, String destino, String... origens) {
    for (String origem : origens) mapa.put(normalizarEstatico(origem), destino);
  }

  private static List<RegraDescricao> criarRegrasDescricaoPrincipal() {
    return List.of(
      regra("OBSTETRICIA", "OBSTETRIC", "PARTO", "CESAREA", "CESARIANA", "GESTACAO",
        "GESTANTE", "PRE-NATAL", "PUERPERIO"),
      regra("NEUROLOGIA PEDIATRICA", "NEUROLOGIA PEDIATRICA"),
      regra("CIRURGIA PEDIATRICA", "CIRURGIA PEDIATRICA"),
      regra("NEUROCIRURGIA", "NEUROCIRURGIA", "TRATAMENTO CIRURGICO DA EPILEPSIA",
        "TRATAMENTO CIRURGICO DA EPILEP"),
      regra("OTORRINOLARINGOLOGIA", "COCLEAR", "IMPLANTE COCLEAR", "IMPEDANCIOMETRIA",
        "AUDIOMETRIA", "NASOFIBROLARINGOSCOPIA", "LARINGOSCOPIA", "BRONCOESOFAGOLOGIA"),
      regra("OFTALMOLOGIA", "CATARATA", "FACOEMULSIFICACAO", "VITRECTOMIA",
        "TOMOGRAFIA DE COERENCIA OPTICA", "LENTE INTRAOCULAR", "REMOCAO DE PIGMENTOS DA LENTE"),
      regra("CARDIOLOGIA", "ELETROFISIOLOGIA", "ABLACAO CARDIACA", "HOLTER",
        "ECOCARDIOGRAMA", "TESTE ERGOMETRICO", "CIRURGIA CARDIOVASCULAR"),
      regra("GINECOLOGIA", "RECANALIZACAO TUBARIA", "COLPOSCOPIA", "HISTEROSCOPIA"),
      regra("NEUROLOGIA", "NEUROLOGIA", "ELETROENCEFALOGRAMA", "REPOSICAO DE FARMACOS EM BOMBA"),
      regra("PEDIATRIA", "PEDIATRIA", "CONSULTA PEDIATRICA", "PUERICULTURA"),
      regra("ORTOPEDIA E TRAUMATOLOGIA", "CIRURGIA DO TRAUMA", "ORTOPEDIA", "ORTOPEDICO",
        "TRAUMATOLOGIA", "TRAUMATOLOGICO"),
      regra("CIRURGIA PLASTICA", "MICROCIRUGIA REPARADORA", "CIRURGIA PLASTICA"),
      regra("CIRURGIA VASCULAR", "ANGIOLOGIA", "CIRURGIA VASCULAR"),
      regra("GASTROENTEROLOGIA", "ENDOSCOPIA", "COLONOSCOPIA", "CPRE", "MUCOSECTOMIA",
        "PROCTOLOGIA", "COLOPROCTOLOGIA", "CIRURGIA DO APARELHO DIGESTIVO"),
      regra("UROLOGIA", "UROLOGIA", "VASO-VASOSTOMIA", "HIPERTERMIA PROSTATICA",
        "PROSTATECTOMIA", "LITOTRIPSIA", "URETEROSCOPIA"),
      regra("NEFROLOGIA", "DIALISE", "HEMODIALISE", "DIALISE PERITONEAL"),
      regra("HEMATOLOGIA", "TRANSPLANTE DE MEDULA", "MIELOGRAMA"),
      regra("ONCOLOGIA", "ONCOLOGIA", "CANCEROLOGIA", "RADIOTERAPIA", "QUIMIOTERAPIA"),
      regra("RADIOLOGIA E DIAGNOSTICO POR IMAGEM", "RESSONANCIA MAGNETICA",
        "ULTRASSONOGRAFIA", "DENSITOMETRIA OSSEA", "TOMOGRAFIA COMPUTADORIZADA", "MEDICINA NUCLEAR"),
      regra("FISIOTERAPIA", "FISIOTERAPIA", "RPG"),
      regra("PSICOLOGIA", "PSICOLOGIA", "PSICOTERAPIA", "PSICOPEDAGOGIA", "ATENDIMENTO PSICOSSOCIAL"),
      regra("NUTRICAO", "NUTRICIONISTA", "CONSULTA NUTRICIONAL", "NUTROLOGIA"),
      regra("ODONTOLOGIA", "ODONTOLOGIA", "PERIODONTIA", "CIRURGIA ODONTOLOGICA")
    );
  }

  /**
   * Segunda camada aplicada exclusivamente quando a resolução já existente
   * terminou em CLINICO. A ordem evita que exames específicos sejam absorvidos
   * pela regra genérica de diagnóstico por imagem.
   */
  private static List<RegraDescricao> criarRegrasDescricaoResidual() {
    return List.of(
      regraResidual("TELECONSULTA", "TELECONSULTA", "TELECONSULTA ELETIVA"),
      regraResidual("OBSTETRICIA", "OBSTETRICIA", "OBSTETRICA", "OBSTETRICO", "PARTO",
        "CESAREA", "CESARIANA", "GESTACAO", "GESTANTE", "PRE NATAL", "PUERPERIO",
        "TRANSLUCENCIA NUCAL", "FETAL"),
      regraResidual("CIRURGIA PEDIATRICA", "CIRURGIA PEDIATRICA"),
      regraResidual("NEUROCIRURGIA", "NEUROCIRURGIA", "TRATAMENTO CIRURGICO DA EPILEPSIA"),
      regraResidual("OFTALMOLOGIA", "OFTALMO", "OFTALMOLOGIA", "OFTALMOSCOPIA", "RETINA",
        "RETINOGRAFIA", "CORNEA", "TONOMETRIA", "PAQUIMETRIA", "CERATOSCOPIA", "MONOCULAR",
        "BINOCULAR", "FUNDOSCOPIA", "CAMPIMETRIA", "PTERIGIO", "CONJUNTIVAL"),
      regraResidual("OTORRINOLARINGOLOGIA", "OTORRINO", "COCLEAR", "AUDIOMETRIA",
        "IMPEDANCIOMETRIA", "IMITANCIOMETRIA", "NASOFIBRO", "LARINGOSCOPIA", "CERUMEN",
        "MASTOIDE", "ORELHA"),
      regraResidual("CARDIOLOGIA", "ANGIOTOMOGRAFIA", "ANGIOTOMOGRAFIA CORONARIANA", "ECG",
        "ELETROCARDIOGRAMA", "ECODOPPLERCARDIOGRAMA", "ECOCARDIOGRAMA", "HOLTER",
        "MAPA 24 HORAS", "TESTE ERGOMETRICO", "CORONARIANA", "VASOS CERVICAIS ARTERIAIS",
        "CAROTIDAS"),
      regraResidual("GINECOLOGIA", "TRANSVAGINAL", "UTERO", "OVARIO", "OVARIOS",
        "ENDOMETRIOSE", "COLPOSCOPIA", "HISTEROSCOPIA", "CERVICO VAGINAL", "CERVICOVAGINAL"),
      regraResidual("UROLOGIA", "APARELHO URINARIO", "PROSTATA", "PROSTATICO", "PSA",
        "URETER", "URETERES", "LITOTRIPSIA", "URODINAMICA"),
      regraResidual("GASTROENTEROLOGIA", "COLONOSCOPIA", "ENDOSCOPIA DIGESTIVA", "CPRE",
        "MUCOSECTOMIA", "PROCTOLOGIA", "COLOPROCTOLOGIA", "RETOSSIGMOIDOSCOPIA", "GASTROSCOPIA"),
      regraResidual("HEMATOLOGIA", "TRANSPLANTE DE MEDULA", "MIELOGRAMA"),
      regraResidual("CIRURGIA VASCULAR", "DOPPLER COLORIDO VENOSO", "ANGIOLOGIA", "VARIZES"),
      regraResidual("PNEUMOLOGIA", "PNEUMOLOGIA"),
      regraResidual("DERMATOLOGIA", "DERMATOLOGIA"),
      regraResidual("CIRURGIA PLASTICA", "MICROCIRUGIA REPARADORA", "RETALHOS CUTANEOS"),
      regraResidual("PEDIATRIA", "PEDIATRIA", "CONSULTA PEDIATRICA", "PUERICULTURA"),
      regraResidual("FISIOTERAPIA", "FISIOTERAPIA", "RECUPERACAO FUNCIONAL POS OPERATORIA",
        "RECUPERACAO FUNCIONAL POSOPERATORIA"),
      regraResidual("ORTOPEDIA E TRAUMATOLOGIA", "ORTOPEDIA", "ORTOPEDICO", "OSTEOMIOARTICULAR",
        "ARTICULAR", "ARTICULACAO", "TORNOZELO", "PUNHO", "JOELHO", "QUIRODACTILO",
        "PODODACTILO", "ESCANOMETRIA", "COLUNA LOMBO SACRA", "COLUNA CERVICAL", "COLUNA TOTAL"),
      regraResidual("MASTOLOGIA", "MAMOGRAFIA", "US MAMAS", "ULTRASSONOGRAFIA DE MAMAS"),
      regraResidual("ANATOMIA PATOLOGICA", "PROCEDIMENTO DIAGNOSTICO EM PECA CIRURGICA OU ANATOMICA",
        "PROCEDIMENTO DIAGNOSTICO EM PECA ANATOMICA",
        "PROCEDIMENTO DIAGNOSTICO EM GRUPOS DE LINFONODOS", "CELL BLOCK", "COLORACAO ESPECIAL",
        "PROCEDIMENTO DIAGNOSTICO CITOPATOLOGICO"),
      regraResidual("CIRURGIA GERAL", "COLECISTECTOMIA", "HERNIORRAFIA", "HERNIA",
        "APENDICECTOMIA", "LAPAROTOMIA", "CIRURGIA GERAL", "TAXA DE SALA CIRURGICA",
        "TAXA COMPACTA DE SALA DE PEQUENAS CIRURGIAS", "LAPAROSCOPIA PARA CIRURGIA"),
      regraResidual("RADIOLOGIA E DIAGNOSTICO POR IMAGEM", "TC", "RM", "RX", "US",
        "TOMOGRAFIA COMPUTADORIZADA", "RESSONANCIA MAGNETICA", "RADIOGRAFIA",
        "DOPPLER COLORIDO DE ORGAO OU ESTRUTURA ISOLADA")
    );
  }

  private static RegraDescricao regra(String especialidade, String... termos) {
    return new RegraDescricao(especialidade, List.of(termos).stream()
      .map(EspecialidadeRelatorioResolver::normalizarEstatico).toList(), false);
  }

  private static RegraDescricao regraResidual(String especialidade, String... termos) {
    return new RegraDescricao(especialidade, List.of(termos).stream()
      .map(EspecialidadeRelatorioResolver::normalizarEstatico).toList(), true);
  }

  private static String normalizarEstatico(String valor) {
    String semAcentos = ACENTOS.matcher(
      Normalizer.normalize(valor, Normalizer.Form.NFD)
    ).replaceAll("");
    return ESPACOS.matcher(PONTUACAO.matcher(
      semAcentos.toUpperCase(Locale.ROOT)
    ).replaceAll(" ").trim()).replaceAll(" ");
  }

  private static final class Guia {
    private final List<Item> itens = new ArrayList<>();
    private final List<String> nomes = new ArrayList<>();
    private final List<String> descricoes = new ArrayList<>();
    private final List<String> cids = new ArrayList<>();
  }

  private record Item(
    LinkedHashMap<String, Object> registro,
    String colunaEspecialidade
  ) {}

  private record RegraDescricao(
    String especialidade,
    List<String> termos,
    boolean exigirLimitesDePalavra
  ) {
    boolean corresponde(String descricao) {
      if (descricao.isBlank()) return false;
      if (!exigirLimitesDePalavra) return termos.stream().anyMatch(descricao::contains);
      String delimitada = " " + descricao + " ";
      return termos.stream().anyMatch(termo -> delimitada.contains(" " + termo + " "));
    }
  }
}
