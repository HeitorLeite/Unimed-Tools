package com.unimedlorena.tools.service;

import com.unimedlorena.tools.auth.AuditoriaService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.FusexSpaRequest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Publica e executa a API reservada de valorização Fusex-SPA.
 *
 * A definição do SGU é mutável; o lock impede que duas solicitações troquem os
 * IDs entre a publicação do SQL e a chamada de execução.
 */
@Service
public class FusexSpaService {
  public static final String API_NOME = "0090-valorizar-guias-fusex-spa";
  private static final ReentrantLock API_LOCK = new ReentrantLock(true);

  private final AuditoriaService auditoria;
  private final SguRelatorioService sgu;
  private final String apiNome;

  public FusexSpaService(
      AuditoriaService auditoria,
      SguRelatorioService sgu,
      @Value("${relatorios.fusex-spa.api-nome:0090-valorizar-guias-fusex-spa}") String apiNome) {
    this.auditoria = auditoria;
    this.sgu = sgu;
    this.apiNome = apiNome == null || apiNome.isBlank() ? API_NOME : apiNome.trim();
  }

  public record Validacao(
      List<String> guias,
      int quantidadeGuias,
      boolean execucaoDisponivel,
      String mensagem) {}

  public Validacao validar(FusexSpaRequest request) {
    List<String> guias = normalizar(request == null ? null : request.guias());
    return new Validacao(
        guias,
        guias.size(),
        true,
        "Execução de teste habilitada. Ao confirmar, o backend publicará a API reservada no SGU "
            + "com UPDATE + COMMIT e chamará o mesmo endpoint usado pelos demais relatórios.");
  }

  public void executar(FusexSpaRequest request, UsuarioPrincipal principal) {
    Validacao validacao = validar(request);
    if (!Boolean.TRUE.equals(request.confirmado())) {
      throw new IllegalArgumentException("Confirme explicitamente a valorização das guias.");
    }

    Map<String, Object> definicao = new LinkedHashMap<>();
    definicao.put("nome", apiNome);
    definicao.put("consultaSQL", montarSql(validacao.guias()));
    definicao.put("ordenacao", "");
    definicao.put("filtros", List.of(filtroTecnico()));

    API_LOCK.lock();
    String etapa = "PUBLICACAO";
    try {
      sgu.criarOuAtualizar(definicao);
      etapa = "EXECUCAO";
      // Operação não idempotente: não há retry automático neste fluxo.
      sgu.executar(apiNome, Map.of());
      auditoria.registrar(
          principal.id(),
          null,
          "FUSEX_SPA_VALORIZAR",
          "SUCESSO",
          null,
          null,
          Map.of("quantidadeGuias", validacao.quantidadeGuias(), "apiNome", apiNome));
    } catch (RuntimeException ex) {
      auditoria.registrar(
          principal.id(),
          null,
          "FUSEX_SPA_VALORIZAR",
          "FALHA",
          null,
          null,
          Map.of(
              "quantidadeGuias", validacao.quantidadeGuias(),
              "apiNome", apiNome,
              "etapa", etapa));
      throw ex;
    } finally {
      API_LOCK.unlock();
    }
  }

  private static Map<String, Object> filtroTecnico() {
    /*
     * O ins_atu_query_api rejeita uma lista vazia de filtros. Este filtro é
     * opcional e nunca é enviado na execução; ele existe somente para cumprir
     * o contrato estrutural do cadastro sem alterar o UPDATE solicitado.
     */
    Map<String, Object> filtro = new LinkedHashMap<>();
    filtro.put("nomeFiltro", "controle");
    filtro.put("conteudoFiltro", "AND 1 = :controle");
    filtro.put("tipoDadoFiltro", "NUMBER");
    filtro.put("mascaraFiltro", "");
    filtro.put("obrigatorioFiltro", "N");
    return filtro;
  }

  static String montarSql(List<String> guias) {
    /*
     * Os valores chegam aqui somente depois de regex numérica + Long.parseLong
     * e são reserializados na forma canônica. Assim, nenhum fragmento SQL
     * arbitrário vindo do navegador pode entrar na lista IN.
     */
    String ids = String.join(", ", guias);
    return """
        UPDATE dbaunimed.guia_item i
        SET
            i.guite_val_fat_honor = i.guite_val_inform_honor
        WHERE
            i.guia_cod_id IN (%s)
            AND i.guite_val_inform_honor <> i.guite_val_fat_honor;

        COMMIT;
        """.formatted(ids).trim();
  }

  static List<String> normalizar(String texto) {
    if (texto == null || texto.isBlank() || texto.length() > 20000) {
      throw new IllegalArgumentException("Informe os IDs das guias (até 20.000 caracteres).");
    }

    LinkedHashSet<String> guias = new LinkedHashSet<>();
    for (String parte : texto.split("[,\\r\\n]", -1)) {
      String id = parte.trim();
      if (id.isEmpty()) continue;

      if (!id.matches("[0-9]+")) {
        throw new IllegalArgumentException(
            "Use apenas IDs inteiros separados por vírgula ou quebra de linha.");
      }

      try {
        guias.add(Long.toString(Long.parseLong(id)));
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Um ID excede o limite de inteiro de 64 bits.");
      }

      if (guias.size() > 1000) {
        throw new IllegalArgumentException("Informe no máximo 1.000 guias por operação.");
      }
    }

    if (guias.isEmpty()) {
      throw new IllegalArgumentException("Informe pelo menos um ID de guia.");
    }
    return List.copyOf(guias);
  }
}
