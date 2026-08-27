package com.unimedlorena.tools.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contratos públicos do agendamento; filtros persistidos nunca são devolvidos na listagem. */
public final class RelatorioAgendamentoDtos {

  private RelatorioAgendamentoDtos() {}

  public record CriacaoRequest(
    @NotBlank @Size(max = 20) String tipoRelatorio,
    @NotBlank @Size(max = 150) String tituloRelatorio,
    @Size(max = 150) String apiNome,
    Map<String, Object> filtros,
    List<String> colunasPersonalizadas,
    Boolean distinct,
    String ordenarPor,
    String direcaoOrdenacao,
    @NotNull @Size(min = 1, max = 200) List<String> colunasExportacao,
    Boolean incluirCabecalho,
    @NotBlank @Size(max = 20) String formato,
    @NotBlank @Size(max = 180) String nomeArquivo,
    @NotBlank @Size(max = 36) String diretorioReferencia,
    @NotBlank @Size(max = 255) String diretorioNome,
    @NotNull @Future Instant agendadoPara,
    @Size(max = 20) String recorrencia,
    @Size(max = 7) List<Integer> diasSemana,
    Integer diaMes,
    @Size(max = 50) String fusoHorario
  ) {}

  public record DestinoRequest(
    @NotBlank @Size(max = 36) String diretorioReferencia,
    @NotBlank @Size(max = 255) String diretorioNome
  ) {}

  public record FalhaRequest(@NotBlank @Size(max = 40) String codigo) {}

  public record ConfiguracaoResponse(
    boolean disponivel,
    int intervaloVerificacaoSegundos,
    int retencaoDias,
    String limitacaoExecucao
  ) {}

  public record ResumoResponse(
    String id,
    long usuarioId,
    String usuarioNome,
    String tipoRelatorio,
    String tituloRelatorio,
    String formato,
    String nomeArquivo,
    String nomeArquivoExecucao,
    String diretorioReferencia,
    String diretorioNome,
    boolean incluirCabecalho,
    Instant agendadoPara,
    String recorrencia,
    List<Integer> diasSemana,
    Integer diaMes,
    String fusoHorario,
    String status,
    String erroCodigo,
    String erroMensagem,
    int tentativas,
    int execucoesConcluidas,
    Instant criadoEm,
    Instant concluidoEm
  ) {}

  public record OperacaoResponse(String message, ResumoResponse agendamento) {}
}
