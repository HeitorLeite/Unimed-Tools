/*
 * Responsabilidade: Monta consultas de despesas apenas com fragmentos SQL previamente aprovados.
 */
package com.unimedlorena.tools.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class RelatorioPersonalizadoSqlBuilder {

  public record Campo(
      String id,
      String rotulo,
      String grupo,
      boolean selecionadaPorPadrao,
      boolean sensivel,
      String expressaoSql) {
  }

  public record Filtro(
      String id,
      String rotulo,
      String grupo,
      String tipoTela,
      String placeholder,
      boolean obrigatorio,
      String conteudoSgu,
      String tipoSgu,
      String mascaraSgu) {
  }

  public record ApiGerada(
      String consultaSql,
      String ordenacao,
      List<Map<String, Object>> filtros) {
  }

  private static final String CODIGO_BENEFICIARIO = """
      CASE
        WHEN BF.UNI_COD_RESPON = 90 THEN
          LPAD(BF.UNI_COD_RESPON, 3, '0') || '.' ||
          LPAD(BF.BNF_COD_CNTRAT_CART, 4, '0') || '.' ||
          LPAD(BF.BNF_COD, 6, '0') || '.' ||
          LPAD(BF.BNF_COD_DEPNTE, 2, '0')
        ELSE
          LPAD(EXT.US8UNIMED, 3, '0') || '.' ||
          LPAD(EXT.US8CODCONT, 4, '0') || '.' ||
          LPAD(EXT.US8CODUSU, 6, '0') || '.' ||
          LPAD(EXT.US8CODDEP, 2, '0')
      END
      """.strip();

  private static final String NOME_BENEFICIARIO = """
      CASE
        WHEN BF.UNI_COD_RESPON = 90 THEN P.PES_NOM_COMP
        ELSE EXT.US8NOME
      END
      """.strip();

  private static final String NOME_EMPRESA = """
      CASE
        WHEN BF.UNI_COD_RESPON <> 90 THEN PES_INTERCAMBIO.PES_NOM_COMP
        WHEN PES_EMPRESA.PES_NOM_COMP IS NULL THEN 'PESSOA FISICA'
        ELSE PES_EMPRESA.PES_NOM_COMP
      END
      """.strip();

  private static final String NUMERO_CONTRATO = """
      CASE
        WHEN BF.UNI_COD_RESPON <> 90 THEN EXT.US8CODCONT
        ELSE BF.BNF_COD_CNTRAT_CART
      END
      """.strip();

  private static final String CODIGO_CONTRATO = """
      CASE
        WHEN BF.UNI_COD_RESPON <> 90 THEN
          LPAD(EXT.US8UNIMED, 3, '0') || '.' ||
          LPAD(EXT.US8CODCONT, 4, '0')
        ELSE
          LPAD(BF.UNI_COD_RESPON, 3, '0') || '.' ||
          LPAD(BF.BNF_COD_CNTRAT_CART, 4, '0')
      END
      """.strip();

  private static final String CODIGO_EMPRESA = """
      CASE
        WHEN BF.UNI_COD_RESPON <> 90 THEN EXT.US8UNIMED
        ELSE EC.EMPCN_COD_PESSOA
      END
      """.strip();

  private static final String NOME_PRESTADOR = """
      CASE
        WHEN G.GUIA_IND_REEMB = 'S' OR G.GUIA_COD_GFR_ID IS NOT NULL
          THEN 'PRESTADOR REEMBOLSO'
        WHEN P_EXEC.PES_NOM_COMP LIKE '%PRESTADOR EXTERNO%'
          OR G.GUIA_COD_UNIMED_EXECUT <> 90
          THEN P_UNIMED_EXEC.PES_NOM_COMP
        ELSE P_EXEC.PES_NOM_COMP
      END
      """.strip();

  private static final String GRUPO_PRESTADOR = """
      CASE
        WHEN PR_EXEC.TIPPR_COD = 1 THEN 'MEDICO COOPERADO'
        WHEN G.GUIA_COD_UNIMED_EXECUT <> 90 THEN 'INTERCAMBIO'
        WHEN PR_EXEC.PREST_COD_PESSOA IN
          (2230911, 2004412, 2216783, 2217542, 2221506, 2221521, 2226685, 2210728)
          THEN 'RECURSO PROPRIO'
        WHEN PR_EXEC.PREST_COD_PESSOA IN
          (2004309, 2045009, 2212799, 2216257, 2223310, 2226549, 2049412, 2166642)
          THEN 'CLINICA DE IMAGEM'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2010269 THEN 'CLINICA DE CARDIOLOGIA'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2201343 THEN 'CLINICA DE HEMODINAMICA'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2218839 THEN 'CLINICA DE TERAPIA OCUPACIONAL'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2004423 THEN 'CLINICA DE UROLOGIA'
        WHEN PR_EXEC.PREST_COD_PESSOA IN
          (2221905, 2221906, 2222015, 2222634, 2223502, 2223504, 2223626,
           2224063, 2224407, 2224799, 2227063, 2227413, 2227686, 2228628,
           2228864, 2234432, 2234672, 2236778)
          THEN 'CLINICA MEDICA'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2201623 THEN 'OPME'
        WHEN PR_EXEC.PREST_COD_PESSOA IN (2208413, 2209830) THEN 'CONTROLE INTERNO'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2208655 THEN 'MODULO CORACAO'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2208981 THEN 'REEMBOLSO'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2216377 THEN 'REMOCAO UTI'
        WHEN PR_EXEC.PREST_COD_PESSOA IN (2004310, 2004371, 2004409, 2007364, 2216433)
          THEN 'LABORATORIO'
        WHEN PR_EXEC.PREST_COD_PESSOA IN (2213244, 2040366, 2004372, 2224566, 2215914, 2225181)
          THEN 'SESSOES MULTI'
        WHEN PR_EXEC.PREST_COD_PESSOA IN (2230911, 2231855) THEN 'MEDICINA OCUPACIONAL'
        WHEN PR_EXEC.PREST_COD_PESSOA = 2223647 THEN 'ONCOLOGIA'
        ELSE 'MEDICOS NAO COOPERADOS'
      END
      """.strip();

  private static final String CODIGO_ESPECIALIDADE = """
      CASE
        WHEN G.GUIA_IND_REEMB = 'S' THEN REE.REEMB_COD_ESPEC_MEDICA
        WHEN G.GUIA_COD_GFR_ID IS NOT NULL THEN ESP_FORA_REDE.ESPMD_COD
        ELSE SOL.CD_ESPECIALIDADE
      END
      """.strip();

  private static final String NOME_ESPECIALIDADE = """
      CASE
        WHEN GI.ITEM_COD = 1010103 THEN 'CLINICO'
        WHEN GI.ITEM_COD = 5000034 THEN 'FISIOTERAPIA'
        WHEN GI.ITEM_COD = 5000047 THEN 'PSICOLOGIA'
        WHEN GI.ITEM_COD = 5000056 THEN 'NUTRICIONISTA'
        WHEN G.GUIA_COD_GFR_ID IS NOT NULL THEN ESP_FORA_REDE.ESPMD_DES
        WHEN G.GUIA_IND_REEMB = 'S' THEN ESP_REEMB.ESPMD_DES
        WHEN SOL.NM_ESPECIALIDADE IS NULL THEN ESP_SOL.ESPMD_DES
        ELSE SOL.NM_ESPECIALIDADE
      END
      """.strip();

  private static final String DESCRICAO_ITEM = """
      CASE
        WHEN G.GUIA_COD_GFR_ID IS NOT NULL THEN GFRI.GFRI_DES_ITEM
        WHEN G.GUIA_IND_REEMB = 'S' THEN REEI.REMBI_DES_ITEM
        ELSE GI.GUITE_ITEM_DES
      END
      """.strip();

  private static final String VALOR_TOTAL = """
      NVL(GI.GUITE_VAL_PAG_HONOR, 0) +
      NVL(GI.GUITE_VAL_PAG_FILME, 0) +
      NVL(GI.GUITE_VAL_PAG_CO, 0)
      """.strip();

  private static final String TIPO_PROCEDIMENTO = """
      CASE
        WHEN IT.TPITE_COD = 1 AND IT.TPITV_COD = 1 THEN '92'
        WHEN IT.TPITE_COD = 1 AND IT.TPITV_COD = 2 THEN '96'
        WHEN IT.TPITE_COD = 1 AND IT.TPITV_COD = 3 THEN '99'
        WHEN IT.TPITE_COD = 2 AND IT.TPITV_COD = 1 THEN 'TAXA/ALUGUEL'
        WHEN IT.TPITE_COD = 2 AND IT.TPITV_COD = 2 THEN 'GASES'
        WHEN IT.TPITE_COD = 2 AND IT.TPITV_COD = 3 THEN 'DIARIA DE UTI'
        WHEN IT.TPITE_COD = 2 AND IT.TPITV_COD = 4 THEN 'DIARIA DE ACOMPANHANTE'
        WHEN IT.TPITE_COD = 2 AND IT.TPITV_COD = 5 THEN 'DIARIA'
        WHEN IT.TPITE_COD = 3 AND IT.TPITV_COD = 1 THEN 'MATERIAL'
        WHEN IT.TPITE_COD = 3 AND IT.TPITV_COD = 2 THEN 'MATERIAL ESPECIAL'
        WHEN IT.TPITE_COD = 3 AND IT.TPITV_COD = 3 THEN 'MEDICAMENTO'
        WHEN IT.TPITE_COD = 3 AND IT.TPITV_COD = 4 THEN 'MEDICAMENTO ESPECIAL'
        WHEN IT.TPITE_COD = 3 AND IT.TPITV_COD = 5 THEN 'ORTESES E PROTESES'
        WHEN IT.TPITE_COD = 4 AND IT.TPITV_COD = 1 THEN 'PACOTE'
        WHEN IT.TPITE_COD = 5 AND IT.TPITV_COD = 1 THEN 'PROCEDIMENTO'
        WHEN IT.TPITE_COD = 6 AND IT.TPITV_COD = 1 THEN 'INATIVO'
      END
      """.strip();

  private static final String REGIAO_BENEFICIARIO = """
      CASE
        WHEN NVL(PE.CEP_COD, PE_TIT.CEP_COD) LIKE '12%' THEN 'Vale do Paraiba'
        WHEN NVL(NVL(PE.END_COD_UF, PE_TIT.END_COD_UF), CIDADE.UF_COD)
          IN ('AC', 'AM', 'AP', 'PA', 'RO', 'RR', 'TO') THEN 'Norte'
        WHEN NVL(NVL(PE.END_COD_UF, PE_TIT.END_COD_UF), CIDADE.UF_COD)
          IN ('AL', 'BA', 'CE', 'MA', 'PB', 'PE', 'PI', 'RN', 'SE') THEN 'Nordeste'
        WHEN NVL(NVL(PE.END_COD_UF, PE_TIT.END_COD_UF), CIDADE.UF_COD)
          IN ('DF', 'GO', 'MS', 'MT') THEN 'Centro-Oeste'
        WHEN NVL(NVL(PE.END_COD_UF, PE_TIT.END_COD_UF), CIDADE.UF_COD)
          IN ('ES', 'MG', 'RJ', 'SP') THEN 'Sudeste'
        WHEN NVL(NVL(PE.END_COD_UF, PE_TIT.END_COD_UF), CIDADE.UF_COD)
          IN ('PR', 'RS', 'SC') THEN 'Sul'
      END
      """.strip();

  private static final String CTE_SQL = """
      WITH SOLA_UNICA AS (
        SELECT *
        FROM (
          SELECT SOLA.*,
            ROW_NUMBER() OVER (
              PARTITION BY SOLA.GSOL_COD_UNIMED_BNF,
                SOLA.GSOL_COD_CNTRAT_CART_BNF,
                SOLA.GSOL_COD_BNF,
                SOLA.GSOL_COD_DEPNTE_BNF,
                SOLA.GSOL_NRO_SENHA
              ORDER BY SOLA.GSOL_COD_SOLICITACAO DESC
            ) RN
          FROM DBAUNIMED.GUIA_SOLIC_AUTRIZ SOLA
        )
        WHERE RN = 1
      ),
      SOL_UNICA AS (
        SELECT *
        FROM (
          SELECT SOL.*,
            ROW_NUMBER() OVER (
              PARTITION BY SOL.NR_GUIA
              ORDER BY SOL.CD_SOLICITACAO DESC
            ) RN
          FROM DATACENTER.AUTSC2_SOLICITACOES SOL
        )
        WHERE RN = 1
      ),
      ESP_FORA_REDE_UNICA AS (
        SELECT *
        FROM (
          SELECT ESP.*,
            ROW_NUMBER() OVER (
              PARTITION BY ESP.ESPMD_COD_NRO_CBO
              ORDER BY ESP.ESPMD_COD
            ) RN
          FROM DBAUNIMED.ESPEC_MEDICA ESP
        )
        WHERE RN = 1
      )
      """.strip();

  private static final String FROM_SQL = """
      FROM DBAUNIMED.GUIA G
      LEFT JOIN DBAUNIMED.BNFRIO BF
        ON G.GUIA_COD_UNIMED_BNFRIO = BF.UNI_COD_RESPON
        AND G.GUIA_COD_CNTRAT_CART_BNFRIO = BF.BNF_COD_CNTRAT_CART
        AND G.GUIA_COD_BNFRIO = BF.BNF_COD
        AND G.GUIA_COD_DEPNTE_BNFRIO = BF.BNF_COD_DEPNTE
      LEFT JOIN DBAUNIMED.PESSOA P ON P.PES_COD = BF.BNF_COD_PESSOA
      LEFT JOIN DBAUNIMED.PESSOA_DOC PESDOC
        ON PESDOC.PES_COD = P.PES_COD AND PESDOC.TPDOC_COD = 2
      LEFT JOIN DBAUNIMED.CNTRAT_VENDA CNT ON CNT.CV_NRO = BF.CV_NRO
      LEFT JOIN DBAUNIMED.EMP_CONTRT EC ON EC.EMPCN_COD = CNT.EMPCN_COD
      LEFT JOIN DBAUNIMED.PESSOA PES_EMPRESA ON PES_EMPRESA.PES_COD = EC.EMPCN_COD_PESSOA
      LEFT JOIN DBAUNIMED.PLANO PN ON PN.PLANO_NRO_REG_ANS = CNT.PLANO_NRO_REG_ANS
      LEFT JOIN DBAUNIMED.PREST PR_GUIA ON PR_GUIA.PREST_COD = G.GUIA_COD_PREST
      LEFT JOIN DBAUNIMED.GRUPO_PREST GP ON GP.GRPRE_COD = PR_GUIA.PREST_COD_GRUPO
      LEFT JOIN SOLA_UNICA SOLA
        ON SOLA.GSOL_NRO_SENHA = G.GUIA_NRO_SENHA_SOLIC
        AND SOLA.GSOL_COD_UNIMED_BNF = G.GUIA_COD_UNIMED_BNFRIO
        AND SOLA.GSOL_COD_CNTRAT_CART_BNF = G.GUIA_COD_CNTRAT_CART_BNFRIO
        AND SOLA.GSOL_COD_BNF = G.GUIA_COD_BNFRIO
        AND SOLA.GSOL_COD_DEPNTE_BNF = G.GUIA_COD_DEPNTE_BNFRIO
        AND G.GUIA_IND_REEMB = 'N'
        AND G.GUIA_COD_GFR_ID IS NULL
      LEFT JOIN SOL_UNICA SOL
        ON SOL.NR_GUIA = G.GUIA_COD
        AND G.GUIA_IND_REEMB = 'N'
        AND G.GUIA_COD_GFR_ID IS NULL
      LEFT JOIN DBAUNIMED.GUIA_ITEM GI ON GI.GUIA_COD_ID = G.GUIA_COD_ID
      LEFT JOIN DBAUNIMED.GUIA_CID GC ON GC.GUIA_COD_ID = G.GUIA_COD_ID
      LEFT JOIN DBAUNIMED.ITEM IT ON IT.ITEM_COD = GI.ITEM_COD
      LEFT JOIN DBAUNIMED.MVTO_REC_SERVIC MVTOSER
        ON MVTOSER.GUIA_COD_ID = G.GUIA_COD_ID
        AND MVTOSER.GUITE_NRO_SEQ = GI.GUITE_NRO_SEQ
      INNER JOIN DBAUNIMED.MVTO_REC MVTOR ON MVTOR.MR_COD = MVTOSER.MR_COD
      LEFT JOIN DBAUNIMED.FATURA_REC FAT ON FAT.FR_NRO = MVTOR.MR_FR_NRO
      LEFT JOIN DBAUNIMED.PESSOA_END PE
        ON PE.PES_COD = P.PES_COD
        AND (PE.END_DAT_EXCL IS NULL OR TRUNC(PE.END_DAT_EXCL) = DATE '0001-01-01')
      LEFT JOIN DBAUNIMED.PESSOA_END PE_TIT ON PE_TIT.PES_COD = PE.END_COD_PES_VINC
      LEFT JOIN DBAUNIMED.CEP CEP ON CEP.CEP_COD = NVL(PE.CEP_COD, PE_TIT.CEP_COD)
      LEFT JOIN DBAUNIMED.CIDADE CIDADE ON CIDADE.CIDAD_COD = CEP.CIDAD_COD
      LEFT JOIN DBAUNIMED.PREST PR_EXEC ON PR_EXEC.PREST_COD = GI.GUITE_COD_EXECUT
      LEFT JOIN DBAUNIMED.PESSOA P_EXEC ON P_EXEC.PES_COD = PR_EXEC.PREST_COD_PESSOA
      LEFT JOIN DBAUNIMED.UNIMED U_EXEC ON U_EXEC.UNI_COD = G.GUIA_COD_UNIMED_EXECUT
      LEFT JOIN DBAUNIMED.PESSOA P_UNIMED_EXEC ON P_UNIMED_EXEC.PES_COD = U_EXEC.UNI_PES_COD
      LEFT JOIN DBAUNIMED.ESPEC_MEDICA ESP_SOL ON ESP_SOL.ESPMD_COD = SOL.CD_ESPECIALIDADE
      LEFT JOIN DBAUNIMED.US8001 EXT
        ON EXT.US8UNIMED = G.US8UNIMED
        AND EXT.US8CODCONT = G.US8CODCONT
        AND EXT.US8CODUSU = G.US8CODUSU
        AND EXT.US8CODDEP = G.US8CODDEP
      LEFT JOIN DBAUNIMED.UNIMED U_INTERCAMBIO ON U_INTERCAMBIO.UNI_COD = EXT.US8UNIMED
      LEFT JOIN DBAUNIMED.PESSOA PES_INTERCAMBIO
        ON PES_INTERCAMBIO.PES_COD = U_INTERCAMBIO.UNI_PES_COD
      LEFT JOIN DBAUNIMED.REEMB REE ON REE.REEMB_NRO_SEQ = G.GUIA_NRO_SEQ_REEMB
      LEFT JOIN DBAUNIMED.REEMB_ITEM REEI
        ON REEI.REEMB_NRO_SEQ = REE.REEMB_NRO_SEQ
        AND REEI.REMBI_NRO_SEQ = GI.GUITE_NRO_SEQ
      LEFT JOIN DBAUNIMED.GUIA_FORA_REDE GFR ON GFR.GFR_COD_ID = G.GUIA_COD_GFR_ID
      LEFT JOIN DBAUNIMED.GUIA_FORA_REDE_ITEM GFRI
        ON GFRI.GFR_COD_ID = GFR.GFR_COD_ID
        AND GFRI.GFRI_NRO_SEQ = GI.GUITE_NRO_SEQ
      LEFT JOIN ESP_FORA_REDE_UNICA ESP_FORA_REDE
        ON ESP_FORA_REDE.ESPMD_COD_NRO_CBO = GFRI.GFRI_COD_CBO
      LEFT JOIN DBAUNIMED.ESPEC_MEDICA ESP_REEMB
        ON ESP_REEMB.ESPMD_COD = REE.REEMB_COD_ESPEC_MEDICA
      WHERE 1 = 1
        AND GI.GUITE_IND_STATUS = 'I'
        /*FILTROS*/
      """.strip();

  private static final Map<String, Campo> CAMPOS = criarCampos();
  private static final Map<String, Filtro> FILTROS = criarFiltros();

  public List<Campo> campos() {
    return List.copyOf(CAMPOS.values());
  }

  public List<Filtro> filtros() {
    return List.copyOf(FILTROS.values());
  }

  public Campo campo(String id) {
    return CAMPOS.get(id);
  }

  public Filtro filtro(String id) {
    return FILTROS.get(normalizarIdFiltro(id));
  }

  /**
   * Converte o identificador interno para o formato exigido pelo SGU.
   * Internamente usamos underscore para manter o contrato já consumido pelo
   * frontend; somente a integração externa recebe nomes com hífen.
   */
  public String nomeFiltroSgu(String id) {
    Filtro filtro = filtro(id);
    if (filtro == null) {
      throw new IllegalArgumentException("Filtro não permitido: " + id + ".");
    }
    return filtro.id().replace('_', '-');
  }

  public ApiGerada gerar(List<String> colunas, Set<String> filtrosAtivos) {
    if (colunas == null || colunas.isEmpty()) {
      throw new IllegalArgumentException("Selecione pelo menos uma coluna.");
    }

    List<String> expressoes = new ArrayList<>();
    for (String id : colunas) {
      Campo campo = CAMPOS.get(id);
      if (campo == null) {
        throw new IllegalArgumentException("Coluna não permitida: " + id + ".");
      }
      expressoes.add(campo.expressaoSql() + " AS " + campo.id());
    }

    Set<String> ativos = new LinkedHashSet<>();
    if (filtrosAtivos != null) {
      for (String id : filtrosAtivos) {
        Filtro filtro = filtro(id);
        if (filtro == null) {
          throw new IllegalArgumentException("Filtro não permitido: " + id + ".");
        }
        ativos.add(filtro.id());
      }
    }

    String consulta = CTE_SQL + "\nSELECT\n  " +
        String.join(",\n  ", expressoes) + "\n" + FROM_SQL;

    List<Map<String, Object>> definicoesFiltro = new ArrayList<>();
    for (Filtro filtro : FILTROS.values()) {
      if (!ativos.contains(filtro.id()))
        continue;

      String nomeFiltroSgu = nomeFiltroSgu(filtro.id());
      Map<String, Object> definicao = new LinkedHashMap<>();
      definicao.put("nomeFiltro", nomeFiltroSgu);
      /*
       * O nome público exigido pelo SGU usa hífen, mas o bind do Oracle deve
       * permanecer com underscore. Um hífen no identificador do bind é sintaxe
       * inválida em PL/SQL e seria interpretado como uma subtração.
       */
      definicao.put("conteudoFiltro", filtro.conteudoSgu());
      definicao.put("tipoDadoFiltro", filtro.tipoSgu());
      definicao.put("mascaraFiltro", filtro.mascaraSgu());
      definicao.put("obrigatorioFiltro", filtro.obrigatorio() ? "S" : "N");
      definicoesFiltro.add(definicao);
    }

    return new ApiGerada(
        consulta,
        "G.GUIA_NRO_COMPET, G.GUIA_COD_ID, GI.GUITE_NRO_SEQ",
        List.copyOf(definicoesFiltro));
  }

  private static Map<String, Campo> criarCampos() {
    LinkedHashMap<String, Campo> campos = new LinkedHashMap<>();

    adicionar(campos, "COD_BENEFICIARIO", "Código do beneficiário", "Beneficiário", true, true, CODIGO_BENEFICIARIO);
    adicionar(campos, "NOME_BENEFICIARIO", "Nome do beneficiário", "Beneficiário", true, true, NOME_BENEFICIARIO);
    adicionar(campos, "PARENTESCO", "Parentesco", "Beneficiário", false, false, parentesco());
    adicionar(campos, "CPF", "CPF", "Beneficiário", false, true,
        "CASE WHEN BF.UNI_COD_RESPON = 90 THEN PESDOC.DOC_NRO END");
    adicionar(campos, "COD_TITULAR", "Código do titular", "Beneficiário", false, true, codigoTitular());
    adicionar(campos, "NOME_TITULAR", "Nome do titular", "Beneficiário", false, true, nomeTitular());
    adicionar(campos, "CPF_TITULAR", "CPF do titular", "Beneficiário", false, true, cpfTitular());
    adicionar(campos, "IDADE", "Idade", "Beneficiário", false, true,
        "TRUNC(MONTHS_BETWEEN(SYSDATE, P.PES_DAT_NASC) / 12)");
    adicionar(campos, "UF", "UF", "Beneficiário", false, true,
        "NVL(NVL(PE.END_COD_UF, PE_TIT.END_COD_UF), CIDADE.UF_COD)");
    adicionar(campos, "MUNICIPIO", "Município", "Beneficiário", false, true,
        "NVL(NVL(PE.END_DES_CIDAD, PE_TIT.END_DES_CIDAD), CIDADE.CIDAD_DES)");
    adicionar(campos, "CEP", "CEP", "Beneficiário", false, true, "NVL(PE.CEP_COD, PE_TIT.CEP_COD)");
    adicionar(campos, "CODIGO_CONTRATO", "Código do contrato", "Contrato e empresa", true, false, CODIGO_CONTRATO);
    adicionar(campos, "NUMERO_CONTRATO", "Número do contrato", "Contrato e empresa", false, false, NUMERO_CONTRATO);
    adicionar(campos, "NOME_CONTRATO", "Nome do contrato", "Contrato e empresa", true, false, NOME_EMPRESA);
    adicionar(campos, "TIPO_PESSOA", "Tipo de pessoa", "Contrato e empresa", false, false,
        "CASE WHEN BF.UNI_COD_RESPON <> 90 THEN 'J' WHEN PES_EMPRESA.PES_IND IS NULL THEN 'F' ELSE PES_EMPRESA.PES_IND END");
    adicionar(campos, "NOME_EMPRESA", "Nome da empresa", "Contrato e empresa", false, false, NOME_EMPRESA);
    adicionar(campos, "CODIGO_EMPRESA", "Código da empresa", "Contrato e empresa", false, false, CODIGO_EMPRESA);
    adicionar(campos, "TIPO_CONVENIO", "Tipo do convênio", "Contrato e empresa", false, false, tipoConvenio());
    adicionar(campos, "CODIGO_PLANO", "Código do plano", "Contrato e empresa", false, false,
        "CASE WHEN BF.UNI_COD_RESPON <> 90 THEN 'ICO-A' ELSE PN.PLANO_NRO_REG_ANS END");
    adicionar(campos, "CODIGO_PESSOA_PRESTADOR", "Código da pessoa do prestador", "Prestador", false, false,
        "PR_EXEC.PREST_COD_PESSOA");
    adicionar(campos, "NOME_PRESTADOR", "Nome do prestador", "Prestador", true, false, NOME_PRESTADOR);
    adicionar(campos, "GRUPO_PRESTADOR", "Grupo do prestador", "Prestador", false, false, GRUPO_PRESTADOR);
    adicionar(campos, "CODIGO_ESPECIALIDADE", "Código da especialidade", "Prestador", false, true,
        CODIGO_ESPECIALIDADE);
    adicionar(campos, "NOME_ESPECIALIDADE", "Nome da especialidade", "Prestador", true, true, NOME_ESPECIALIDADE);
    adicionar(campos, "CODIGO_SOLICITANTE", "Código do solicitante", "Prestador", false, false,
        "SOL.CD_PREST_PROF_SOLIC");
    adicionar(campos, "NOME_SOLICITANTE", "Nome do solicitante", "Prestador", false, false,
        "CASE WHEN G.GUIA_COD_GFR_ID IS NOT NULL OR G.GUIA_IND_REEMB = 'S' THEN 'MEDICO NAO COOPERADO' WHEN SOL.NM_PREST_PROF_SOLIC IS NULL THEN SOL.NM_PRESTADOR ELSE SOL.NM_PREST_PROF_SOLIC END");
    adicionar(campos, "LOCAL_ATENDIMENTO", "Local de atendimento", "Prestador", false, false,
        "CASE WHEN PR_GUIA.PREST_COD_UNI = 90 THEN 'LOCAL' ELSE 'INTERCAMBIO' END");
    adicionar(campos, "TIPO_PRESTADOR", "Tipo do prestador", "Prestador", false, false, "GP.GRPRE_DES");
    adicionar(campos, "NUMERO_GUIA", "Número da guia", "Guia", true, true,
        "CASE WHEN REGEXP_LIKE(G.GUIA_COD, '^[0-9]+$') THEN G.GUIA_COD ELSE TO_CHAR(G.GUIA_COD_ID) END");
    adicionar(campos, "DATA_GUIA", "Data da guia", "Guia", true, false,
        "TO_CHAR(TRUNC(G.GUIA_DTH_EMIS), 'DD/MM/YYYY')");
    adicionar(campos, "DATA_INTERNACAO", "Data da internação/atendimento", "Guia", false, true, dataInternacao());
    adicionar(campos, "DATA_ALTA", "Data da alta", "Guia", false, true,
        "TO_CHAR(TRUNC(G.GUIA_DTH_ALTA), 'DD/MM/YYYY')");
    adicionar(campos, "PERIODO", "Competência", "Guia", true, false, "G.GUIA_NRO_COMPET");
    adicionar(campos, "DATA_PAGAMENTO", "Data do pagamento", "Guia", false, false,
        "TO_CHAR(TRUNC(ADD_MONTHS(TO_DATE(G.GUIA_NRO_COMPET, 'YYYYMM'), 1), 'MM') + 24, 'DD/MM/YYYY')");
    adicionar(campos, "TIPO_GUIA", "Código do tipo da guia", "Guia", false, false, "G.GUIA_TIP");
    adicionar(campos, "DESCRICAO_TIPO_GUIA", "Tipo da guia", "Guia", true, false, descricaoTipoGuia());
    adicionar(campos, "TIPO_INTERNACAO", "Tipo de internação", "Guia", false, true, tipoInternacao());
    adicionar(campos, "CID", "CID", "Guia", false, true, "GC.GUCID_COD_CID");
    adicionar(campos, "COD_TUSS", "Código TUSS", "Procedimento", true, true, "TO_CHAR(IT.ITEM_COD) || IT.ITEM_COD_DIG");
    adicionar(campos, "DESCRICAO_ITEM", "Descrição do item", "Procedimento", true, true, DESCRICAO_ITEM);
    adicionar(campos, "GRUPO_ITEM", "Grupo do item", "Procedimento", false, false,
        "SUBSTR(TO_CHAR(GI.ITEM_COD), 1, 4)");
    adicionar(campos, "QUANTIDADE", "Quantidade", "Procedimento", true, false, "TRUNC(GI.GUITE_QTD_ITEM)");
    adicionar(campos, "TIPO_PROCEDIMENTO", "Tipo do procedimento", "Procedimento", false, false, TIPO_PROCEDIMENTO);
    adicionar(campos, "VALOR_FATOR", "Valor do fator/coparticipação", "Valores", false, true,
        "CASE WHEN BF.UNI_COD_RESPON = 90 AND FAT.FR_DAT_CANCEL = DATE '0001-01-01' THEN NVL(MVTOSER.MRS_VAL_FINAL, 0) ELSE 0 END");
    adicionar(campos, "VALOR_PG_PROCEDIMENTO", "Valor pago do procedimento", "Valores", true, true,
        "NVL(GI.GUITE_VAL_PAG_HONOR, 0) + NVL(GI.GUITE_VAL_PAG_CO, 0)");
    adicionar(campos, "VALOR_PG_FILME", "Valor pago de filme", "Valores", false, true,
        "NVL(GI.GUITE_VAL_PAG_FILME, 0)");
    adicionar(campos, "VALOR_PG_CO", "Valor pago de custo operacional", "Valores", false, true,
        "NVL(GI.GUITE_VAL_PAG_CO, 0)");
    adicionar(campos, "VALOR_TOTAL", "Valor total", "Valores", true, true, VALOR_TOTAL);
    adicionar(campos, "VALOR_TOTAL_21", "Valor total com 21%", "Valores", false, true, "(" + VALOR_TOTAL + ") * 1.21");
    adicionar(campos, "VALOR_RECEBER", "Valor a receber", "Valores", false, true, valorReceber());

    return Collections.unmodifiableMap(campos);
  }

  private static Map<String, Filtro> criarFiltros() {
    LinkedHashMap<String, Filtro> filtros = new LinkedHashMap<>();
    adicionar(filtros, "competencia_inicio", "Competência inicial", "Período", "competencia", "Ex.: 202601", true,
        "and G.GUIA_NRO_COMPET >= :competencia_inicio /* :competencia-inicio */", "NUMBER", "");
    adicionar(filtros, "competencia_fim", "Competência final", "Período", "competencia", "Ex.: 202612", true,
        "and G.GUIA_NRO_COMPET <= :competencia_fim /* :competencia-fim */", "NUMBER", "");
    adicionar(filtros, "data_guia_inicio", "Data da guia inicial", "Período", "date", "", false,
        "and TRUNC(G.GUIA_DTH_EMIS) >= TO_DATE(:data_guia_inicio, 'YYYY-MM-DD')", "VARCHAR(10)", "");
    adicionar(filtros, "data_guia_fim", "Data da guia final", "Período", "date", "", false,
        "and TRUNC(G.GUIA_DTH_EMIS) <= TO_DATE(:data_guia_fim, 'YYYY-MM-DD')", "VARCHAR(10)", "");
    adicionar(filtros, "codigo_beneficiario", "Código do beneficiário", "Beneficiário", "text", "000.0000.000000.00",
        false, "and REPLACE(" + CODIGO_BENEFICIARIO + ", '.', '') = REPLACE(:codigo_beneficiario, '.', '')",
        "VARCHAR(30)", "");
    adicionar(filtros, "nome_beneficiario", "Nome do beneficiário", "Beneficiário", "text", "Digite parte do nome",
        false, "and UPPER(" + NOME_BENEFICIARIO + ") LIKE '%' || UPPER(:nome_beneficiario) || '%'", "VARCHAR(120)", "");
    adicionar(filtros, "cpf", "CPF", "Beneficiário", "text", "Somente números", false,
        "and REGEXP_REPLACE(PESDOC.DOC_NRO, '[^0-9]', '') = REGEXP_REPLACE(:cpf, '[^0-9]', '')", "VARCHAR(14)", "");
    adicionar(filtros, "numero_contrato", "Número do contrato", "Contrato e empresa", "number", "", false,
        "and (" + NUMERO_CONTRATO + ") = :numero_contrato", "NUMBER", "");
    adicionar(filtros, "codigo_empresa", "Código da empresa", "Contrato e empresa", "number", "", false,
        "and (" + CODIGO_EMPRESA + ") = :codigo_empresa", "NUMBER", "");
    adicionar(filtros, "nome_empresa", "Nome da empresa", "Contrato e empresa", "text", "Digite parte do nome", false,
        "and UPPER(" + NOME_EMPRESA + ") LIKE '%' || UPPER(:nome_empresa) || '%'", "VARCHAR(120)", "");
    adicionar(filtros, "numero_guia", "Número da guia", "Guia", "text", "Número ou código da guia", false,
        "and G.GUIA_COD = :numero_guia", "VARCHAR(40)", "");
    adicionar(filtros, "tipo_guia", "Tipo da guia", "Guia", "number", "Código do tipo", false,
        "and G.GUIA_TIP = :tipo_guia", "NUMBER", "");
    adicionar(filtros, "cid", "CID", "Guia", "text", "Ex.: J45", false, "and UPPER(GC.GUCID_COD_CID) = UPPER(:cid)",
        "VARCHAR(20)", "");
    adicionar(filtros, "codigo_prestador", "Código do prestador", "Prestador", "number", "", false,
        "and PR_EXEC.PREST_COD_PESSOA = :codigo_prestador", "NUMBER", "");
    adicionar(filtros, "nome_prestador", "Nome do prestador", "Prestador", "text", "Digite parte do nome", false,
        "and UPPER(" + NOME_PRESTADOR + ") LIKE '%' || UPPER(:nome_prestador) || '%'", "VARCHAR(120)", "");
    adicionar(filtros, "grupo_prestador", "Grupo do prestador", "Prestador", "text", "Digite parte do grupo", false,
        "and UPPER(" + GRUPO_PRESTADOR + ") LIKE '%' || UPPER(:grupo_prestador) || '%'", "VARCHAR(120)", "");
    adicionar(filtros, "codigo_especialidade", "Código da especialidade", "Prestador", "number", "", false,
        "and " + CODIGO_ESPECIALIDADE + " = :codigo_especialidade", "NUMBER", "");
    adicionar(filtros, "codigo_tuss", "Código TUSS", "Procedimento", "text", "Código completo", false,
        "and TO_CHAR(IT.ITEM_COD) || IT.ITEM_COD_DIG = :codigo_tuss", "VARCHAR(20)", "");
    adicionar(filtros, "descricao_item", "Descrição do item", "Procedimento", "text", "Digite parte da descrição",
        false, "and UPPER(" + DESCRICAO_ITEM + ") LIKE '%' || UPPER(:descricao_item) || '%'", "VARCHAR(160)", "");
    adicionar(filtros, "grupo_item", "Grupo do item", "Procedimento", "text", "Primeiros quatro dígitos", false,
        "and SUBSTR(TO_CHAR(GI.ITEM_COD), 1, 4) = :grupo_item", "VARCHAR(20)", "");
    adicionar(filtros, "tipo_procedimento", "Tipo do procedimento", "Procedimento", "text", "Ex.: PROCEDIMENTO", false,
        "and UPPER(" + TIPO_PROCEDIMENTO + ") LIKE '%' || UPPER(:tipo_procedimento) || '%'", "VARCHAR(60)", "");
    adicionar(filtros, "valor_minimo", "Valor total mínimo", "Valores", "decimal", "0,00", false,
        "and (" + VALOR_TOTAL + ") >= :valor_minimo", "NUMBER", "");
    adicionar(filtros, "valor_maximo", "Valor total máximo", "Valores", "decimal", "0,00", false,
        "and (" + VALOR_TOTAL + ") <= :valor_maximo", "NUMBER", "");
    return Collections.unmodifiableMap(filtros);
  }

  private static String normalizarIdFiltro(String id) {
    return id == null
        ? null
        : id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
  }

  private static void adicionar(
      Map<String, Campo> campos,
      String id,
      String rotulo,
      String grupo,
      boolean padrao,
      boolean sensivel,
      String expressao) {
    campos.put(id, new Campo(id, rotulo, grupo, padrao, sensivel, expressao));
  }

  private static void adicionar(
      Map<String, Filtro> filtros,
      String id,
      String rotulo,
      String grupo,
      String tipoTela,
      String placeholder,
      boolean obrigatorio,
      String conteudoSgu,
      String tipoSgu,
      String mascaraSgu) {
    filtros.put(id,
        new Filtro(id, rotulo, grupo, tipoTela, placeholder, obrigatorio, conteudoSgu, tipoSgu, mascaraSgu));
  }

  private static String parentesco() {
    return """
        CASE
          WHEN BF.BNF_IND_GRAU_DEPCIA = 0 THEN 'TITULAR'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 1 THEN 'ESPOSA'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 2 THEN 'COMPANHEIRO(A)'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 9 THEN 'ESPOSO(A)'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 10 THEN 'FILHOS'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 30 THEN 'FILHAS'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 40 THEN 'EX CONJUGE'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 50 THEN 'PAI'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 51 THEN 'MAE'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 52 THEN 'SOGRO'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 53 THEN 'SOGRA'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 60 THEN 'OUTROS'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 61 THEN 'AGREGADO FILHO PENSIO/SERVIDOR'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 63 THEN 'CUNHADO(A)'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 64 THEN 'BISNETO(A)'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 65 THEN 'GENRO/NORA'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 70 THEN 'FILHOS ADOTIVOS'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 75 THEN 'FILHAS ADOTIVAS'
          WHEN BF.BNF_IND_GRAU_DEPCIA = 81 THEN 'MENOR SOBRE GUARDA'
          WHEN BF.BNF_IND_GRAU_DEPCIA IN (90, 99) THEN 'AGREGADO'
        END
        """.strip();
  }

  private static String codigoTitular() {
    return """
        CASE
          WHEN BF.UNI_COD_RESPON <> 90 OR BF.BNF_FLG_TITLAR_CAD = 'S' THEN NULL
          ELSE LPAD(BF.UNI_COD_RESPON, 3, '0') || '.' ||
            LPAD(BF.BNF_COD_CNTRAT_CART, 4, '0') || '.' ||
            LPAD(BF.BNF_COD, 6, '0') || '.00'
        END
        """.strip();
  }

  private static String nomeTitular() {
    return """
        CASE
          WHEN BF.UNI_COD_RESPON <> 90 OR BF.BNF_FLG_TITLAR_CAD = 'S' THEN NULL
          ELSE (
            SELECT PT.PES_NOM_COMP
            FROM DBAUNIMED.BNFRIO BT
            JOIN DBAUNIMED.PESSOA PT ON PT.PES_COD = BT.BNF_COD_PESSOA
            WHERE BT.BNF_FLG_TITLAR_CAD = 'S'
              AND BT.UNI_COD_RESPON = BF.UNI_COD_RESPON
              AND BT.BNF_COD_CNTRAT_CART = BF.BNF_COD_CNTRAT_CART
              AND BT.BNF_COD = BF.BNF_COD
              AND ROWNUM = 1
          )
        END
        """.strip();
  }

  private static String cpfTitular() {
    return """
        CASE
          WHEN BF.UNI_COD_RESPON <> 90 OR BF.BNF_FLG_TITLAR_CAD = 'S' THEN NULL
          ELSE (
            SELECT PDT.DOC_NRO
            FROM DBAUNIMED.BNFRIO BT
            JOIN DBAUNIMED.PESSOA_DOC PDT
              ON PDT.PES_COD = BT.BNF_COD_PESSOA AND PDT.TPDOC_COD = 2
            WHERE BT.BNF_FLG_TITLAR_CAD = 'S'
              AND BT.UNI_COD_RESPON = BF.UNI_COD_RESPON
              AND BT.BNF_COD_CNTRAT_CART = BF.BNF_COD_CNTRAT_CART
              AND BT.BNF_COD = BF.BNF_COD
              AND ROWNUM = 1
          )
        END
        """.strip();
  }

  private static String tipoConvenio() {
    return """
        CASE
          WHEN BF.UNI_COD_RESPON <> 90 THEN 'INT-CO'
          WHEN PN.PLANO_IND_FORM_PRECO = 1 AND PN.PLANO_IND_CNTRTC = 2 THEN 'ENT-PP'
          WHEN PN.PLANO_IND_FORM_PRECO = 1 THEN 'PP'
          WHEN PN.PLANO_IND_FORM_PRECO = 2 THEN 'CO'
          WHEN PN.PLANO_IND_FORM_PRECO = 3 THEN 'GRATUITO'
          ELSE 'NAO INFORMADO'
        END
        """.strip();
  }

  private static String dataInternacao() {
    return """
        CASE
          WHEN G.GUIA_TIP IN (3, 4) THEN (
            SELECT TO_CHAR(TRUNC(MIN(G2.GUIA_DTH_ATEND)), 'DD/MM/YYYY')
            FROM DBAUNIMED.GUIA G2
            WHERE G2.GUIA_COD = G.GUIA_COD
          )
        END
        """.strip();
  }

  private static String descricaoTipoGuia() {
    return """
        CASE
          WHEN G.GUIA_TIP = 1 THEN 'CONSULTA'
          WHEN G.GUIA_TIP IN (2, 5, 6) AND G.GUIA_TIP_INTNCA IS NOT NULL THEN 'INTERNACAO'
          WHEN G.GUIA_TIP IN (2, 5, 6) AND TO_CHAR(IT.ITEM_COD) || IT.ITEM_COD_DIG = '10101039' THEN 'PA'
          WHEN G.GUIA_TIP IN (3, 4) THEN 'INTERNACAO'
          ELSE 'EXAME'
        END
        """.strip();
  }

  private static String tipoInternacao() {
    return """
        CASE
          WHEN G.GUIA_TIP_INTNCA = 1 THEN 'CLINICA'
          WHEN G.GUIA_TIP_INTNCA = 2 THEN 'CIRURGICA'
          WHEN G.GUIA_TIP_INTNCA = 3 THEN 'OBSTETRICA'
          WHEN G.GUIA_TIP_INTNCA = 4 THEN 'PEDIATRICA'
          WHEN G.GUIA_TIP_INTNCA = 5 THEN 'PSIQUIATRICA'
        END
        """.strip();
  }

  private static String valorReceber() {
    return """
        CASE
          WHEN PN.PLANO_IND_FORM_PRECO = 2 THEN
            NVL(GI.GUITE_VAL_FAT_HONOR, 0) +
            NVL(GI.GUITE_VAL_FAT_FILME, 0) +
            NVL(GI.GUITE_VAL_FAT_CO, 0)
          ELSE
            NVL(GI.GUITE_VAL_PAG_HONOR, 0) +
            NVL(GI.GUITE_VAL_PAG_FILME, 0) +
            NVL(GI.GUITE_VAL_PAG_CO, 0)
        END
        """.strip();
  }
}