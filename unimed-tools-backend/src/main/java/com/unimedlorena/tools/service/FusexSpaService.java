package com.unimedlorena.tools.service;

import com.unimedlorena.tools.auth.AuditoriaService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.FusexSpaRequest;
import com.unimedlorena.tools.exception.ApiException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Valida a operação; falha fechada até existir contrato transacional SGU aprovado. */
@Service
public class FusexSpaService {
  public static final String LIMITACAO = "Execução indisponível: a integração atual não possui " +
      "um contrato validado para atualizar guias e confirmar a transação no SGU. " +
      "Nenhuma guia foi alterada. Solicite à TI a homologação de uma operação transacional.";
  private final AuditoriaService auditoria;

  public FusexSpaService(AuditoriaService auditoria) {
    this.auditoria = auditoria;
  }

  public record Validacao(List<String> guias, int quantidadeGuias, boolean execucaoDisponivel,
      String mensagem) {}

  public Validacao validar(FusexSpaRequest request) {
    List<String> guias = normalizar(request == null ? null : request.guias());
    return new Validacao(guias, guias.size(), false, LIMITACAO);
  }

  public void executar(FusexSpaRequest request, UsuarioPrincipal principal) {
    Validacao validacao = validar(request);
    if (!Boolean.TRUE.equals(request.confirmado())) {
      throw new IllegalArgumentException("Confirme explicitamente a valorização das guias.");
    }
    auditoria.registrar(principal.id(), null, "FUSEX_SPA_VALORIZAR", "BLOQUEADO",
        null, null, Map.of("quantidadeGuias", validacao.quantidadeGuias(),
            "motivo", "SGU_DML_NAO_SUPORTADO"));
    // O endpoint de relatórios não comprova DML/commit. Não publicar UPDATE como SELECT,
    // não chamar procedure inventada nem usar o MariaDB de identidade para acessar Oracle.
    throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "SGU_DML_NAO_SUPORTADO", LIMITACAO);
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
        throw new IllegalArgumentException("Use apenas IDs inteiros separados por vírgula ou quebra de linha.");
      }
      try {
        guias.add(Long.toString(Long.parseLong(id)));
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Um ID excede o limite de inteiro de 64 bits.");
      }
      // Lote limitado ao teto tradicional de 1.000 expressões IN do Oracle.
      if (guias.size() > 1000) throw new IllegalArgumentException("Informe no máximo 1.000 guias por operação.");
    }
    if (guias.isEmpty()) throw new IllegalArgumentException("Informe pelo menos um ID de guia.");
    return List.copyOf(guias);
  }
}
