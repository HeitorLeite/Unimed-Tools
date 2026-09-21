package com.unimedlorena.tools.service;

import com.unimedlorena.tools.dto.HospitalRelatorioRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Relatório reservado de autorizações hospitalares ainda não convertidas em guia.
 * A consulta e a allowlist de filtros permanecem exclusivamente no backend.
 */
@Service
public class HospitalRelatorioService {

  public static final String API_NOME = "0090-hospital-autorizacoes";

  public record Opcao(String valor, String rotulo) {}

  public record Filtro(
    String id,
    String rotulo,
    String tipo,
    String placeholder,
    List<Opcao> opcoes
  ) {}

  public record Configuracao(
    String apiNome,
    List<String> colunas,
    List<Filtro> filtros
  ) {}

  private static final List<String> COLUNAS = List.of(
    "CONVENIO",
    "NOME_BENEF",
    "DATA_AUTORIZACAO",
    "COD_GUIA",
    "PROCEDIMENTO",
    "DESCRICAO",
    "STATUS"
  );

  private static final List<Filtro> FILTROS = List.of(
    new Filtro("convenio", "Convênio", "text", "Digite parte do convênio", List.of()),
    new Filtro("nomebenef", "Nome do beneficiário", "text", "Digite parte do nome", List.of()),
    new Filtro("dataautorizacaoinicio", "Autorização a partir de", "date", "", List.of()),
    new Filtro("dataautorizacaofim", "Autorização até", "date", "", List.of()),
    new Filtro("codguia", "Código da guia", "text", "Número da guia", List.of()),
    new Filtro("procedimento", "Procedimento", "text", "Código do procedimento", List.of()),
    new Filtro("descricao", "Descrição", "text", "Digite parte da descrição", List.of()),
    new Filtro(
      "status",
      "Status",
      "select",
      "",
      List.of(
        new Opcao("1", "Negado"),
        new Opcao("2", "Aprovado"),
        new Opcao("3", "Em estudo")
      )
    )
  );

  private static final Set<String> IDS_FILTROS = Set.of(
    "convenio",
    "nomebenef",
    "dataautorizacaoinicio",
    "dataautorizacaofim",
    "codguia",
    "procedimento",
    "descricao",
    "status"
  );

  private static final String SQL = """
      SELECT
        CASE
          WHEN BF.UNI_COD_RESPON = 90 THEN P_UNI.PES_NOM_COMP
          ELSE P_UNI2.PES_NOM_COMP
        END AS CONVENIO,
        CASE
          WHEN BF.UNI_COD_RESPON = 90 THEN P.PES_NOM_COMP
          ELSE EXT.US8NOME
        END AS NOME_BENEF,
        GSOL.GSOL_DAT_AUTOR AS DATA_AUTORIZACAO,
        GSOL.GSOL_NRO_GUIA AS COD_GUIA,
        GSAI.GSOLI_ITEM_SERVICO AS PROCEDIMENTO,
        IT.ITEM_DES_PRINC AS DESCRICAO,
        CASE
          WHEN GSOL.GSOL_IND_SITUAC = 1 THEN 'NEGADO'
          WHEN GSOL.GSOL_IND_SITUAC = 2 THEN 'APROVADO'
          WHEN GSOL.GSOL_IND_SITUAC = 3 THEN 'EM ESTUDO'
        END AS STATUS
      FROM DBAUNIMED.GUIA_SOLIC_AUTRIZ GSOL
      LEFT JOIN DBAUNIMED.BNFRIO BF
        ON BF.UNI_COD_RESPON = GSOL.GSOL_COD_UNIMED_BNF
        AND BF.BNF_COD_CNTRAT_CART = GSOL.GSOL_COD_CNTRAT_CART_BNF
        AND BF.BNF_COD = GSOL.GSOL_COD_BNF
        AND BF.BNF_COD_DEPNTE = GSOL.GSOL_COD_DEPNTE_BNF
      LEFT JOIN DBAUNIMED.US8001 EXT
        ON EXT.US8UNIMED = GSOL.GSOL_COD_UNIMED_BNF
        AND EXT.US8CODCONT = GSOL.GSOL_COD_CNTRAT_CART_BNF
        AND EXT.US8CODUSU = GSOL.GSOL_COD_BNF
        AND EXT.US8CODDEP = GSOL.GSOL_COD_DEPNTE_BNF
      LEFT JOIN DBAUNIMED.PESSOA P
        ON P.PES_COD = BF.BNF_COD_PESSOA
      LEFT JOIN DBAUNIMED.GUIA_SOLIC_AUTRIZ_ITEM GSAI
        ON GSAI.GSOL_COD_SOLICITACAO = GSOL.GSOL_COD_SOLICITACAO
        AND GSAI.GSOLI_NRO_SENHA = GSOL.GSOL_NRO_SENHA
      LEFT JOIN DBAUNIMED.UNIMED U
        ON U.UNI_COD = BF.UNI_COD_RESPON
      LEFT JOIN DBAUNIMED.PESSOA P_UNI
        ON P_UNI.PES_COD = U.UNI_PES_COD
      LEFT JOIN DBAUNIMED.UNIMED U2
        ON U2.UNI_COD = EXT.US8UNIMED
      LEFT JOIN DBAUNIMED.PESSOA P_UNI2
        ON P_UNI2.PES_COD = U2.UNI_PES_COD
      LEFT JOIN DBAUNIMED.ITEM IT
        ON IT.ITEM_COD = GSAI.GSOLI_ITEM_SERVICO
      WHERE GSOL.GSOL_DAT_AUTOR IS NOT NULL
        AND GSOL.GSOL_IND_SITUAC IN (1, 2, 3)
        AND GSOL.GSOL_TP_SOLICITACAO IN (0, 1)
        AND NOT EXISTS (
          SELECT 1
          FROM DBAUNIMED.GUIA G
          WHERE G.GUIA_COD = GSOL.GSOL_NRO_GUIA
        )
      """.strip();

  private final SguRelatorioService sgu;
  private final ExportacaoRelatorioService exportacao;

  public HospitalRelatorioService(
    SguRelatorioService sgu,
    ExportacaoRelatorioService exportacao
  ) {
    this.sgu = sgu;
    this.exportacao = exportacao;
  }

  public Configuracao configuracao() {
    return new Configuracao(API_NOME, COLUNAS, FILTROS);
  }

  public boolean ehApiReservada(String nome) {
    return nome != null && API_NOME.equalsIgnoreCase(nome.trim());
  }

  public Map<String, Object> executar(HospitalRelatorioRequest request) {
    publicar();
    Map<String, Object> parametros = normalizarFiltros(request == null ? null : request.filtros());
    parametros.put("page", limitar(request == null ? null : request.pagina(), 1, 10_000, 1));
    parametros.put("size", limitar(request == null ? null : request.tamanhoPagina(), 1, 100, 25));
    return sgu.executar(API_NOME, parametros);
  }

  public ExportacaoRelatorioService.Arquivo exportar(
    String formato,
    HospitalRelatorioRequest request
  ) throws IOException {
    publicar();
    var registros = exportacao.carregarRegistros(
      API_NOME,
      normalizarFiltros(request == null ? null : request.filtros())
    );
    return exportacao.gerarArquivo(formato, registros);
  }

  private void publicar() {
    Map<String, Object> definicao = new LinkedHashMap<>();
    definicao.put("nome", API_NOME);
    definicao.put("consultaSQL", SQL);
    definicao.put("ordenacao", "DATA_AUTORIZACAO DESC");
    definicao.put("filtros", definicoesFiltros());
    sgu.criarOuAtualizar(definicao);
  }

  private List<Map<String, Object>> definicoesFiltros() {
    return List.of(
      filtro("convenio", "and UPPER(CASE WHEN BF.UNI_COD_RESPON = 90 THEN P_UNI.PES_NOM_COMP ELSE P_UNI2.PES_NOM_COMP END) LIKE :convenio", "VARCHAR(160)"),
      filtro("nomebenef", "and UPPER(CASE WHEN BF.UNI_COD_RESPON = 90 THEN P.PES_NOM_COMP ELSE EXT.US8NOME END) LIKE :nomebenef", "VARCHAR(160)"),
      filtro("dataautorizacaoinicio", "and TRUNC(GSOL.GSOL_DAT_AUTOR) >= TO_DATE(:dataautorizacaoinicio, 'YYYY-MM-DD')", "VARCHAR(10)"),
      filtro("dataautorizacaofim", "and TRUNC(GSOL.GSOL_DAT_AUTOR) <= TO_DATE(:dataautorizacaofim, 'YYYY-MM-DD')", "VARCHAR(10)"),
      filtro("codguia", "and TO_CHAR(GSOL.GSOL_NRO_GUIA) = :codguia", "VARCHAR(40)"),
      filtro("procedimento", "and TO_CHAR(GSAI.GSOLI_ITEM_SERVICO) = :procedimento", "VARCHAR(40)"),
      filtro("descricao", "and UPPER(IT.ITEM_DES_PRINC) LIKE :descricao", "VARCHAR(200)"),
      filtro("status", "and GSOL.GSOL_IND_SITUAC = :status", "NUMBER")
    );
  }

  private Map<String, Object> filtro(String nome, String conteudo, String tipo) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("nomeFiltro", nome);
    item.put("conteudoFiltro", conteudo);
    item.put("tipoDadoFiltro", tipo);
    item.put("mascaraFiltro", "");
    item.put("obrigatorioFiltro", "N");
    return item;
  }

  private Map<String, Object> normalizarFiltros(Map<String, Object> recebidos) {
    Map<String, Object> normalizados = new LinkedHashMap<>();
    if (recebidos == null) return normalizados;

    for (Map.Entry<String, Object> entry : recebidos.entrySet()) {
      String id = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
      if (!IDS_FILTROS.contains(id)) {
        throw new IllegalArgumentException("Filtro não permitido: " + id + ".");
      }
      String valor = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
      if (valor.isBlank()) continue;
      if (valor.length() > 240) throw new IllegalArgumentException("Filtro muito longo: " + id + ".");

      if (Set.of("convenio", "nomebenef", "descricao").contains(id)) {
        normalizados.put(id, "%" + valor.toUpperCase(Locale.ROOT) + "%");
      } else if ("status".equals(id)) {
        if (!Set.of("1", "2", "3").contains(valor)) throw new IllegalArgumentException("Status inválido.");
        normalizados.put(id, Integer.parseInt(valor));
      } else if (Set.of("dataautorizacaoinicio", "dataautorizacaofim").contains(id)) {
        if (!valor.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Data de autorização inválida.");
        normalizados.put(id, valor);
      } else {
        normalizados.put(id, valor);
      }
    }
    return normalizados;
  }

  private int limitar(Integer valor, int minimo, int maximo, int padrao) {
    int definido = valor == null ? padrao : valor;
    return Math.max(minimo, Math.min(maximo, definido));
  }
}
