package com.unimedlorena.tools.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimedlorena.tools.auth.AuditoriaService;
import com.unimedlorena.tools.auth.AuthService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.RelatorioAgendamentoDtos;
import com.unimedlorena.tools.dto.RelatorioExportacaoRequest;
import com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest;
import com.unimedlorena.tools.exception.ApiException;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Coordena propriedade, criptografia, reserva e retenção dos agendamentos. */
@Service
public class RelatorioAgendamentoService {

  private static final int RETENCAO_DIAS = 90;
  private static final int INTERVALO_VERIFICACAO_SEGUNDOS = 30;
  private static final Duration DURACAO_RESERVA = Duration.ofHours(2);
  private static final Set<String> TIPOS = Set.of("MANUAL", "PERSONALIZADO");
  private static final Set<String> FORMATOS = Set.of("csv", "txt", "xlsx");
  private static final Map<String, String> MENSAGENS_FALHA = Map.of(
    "PASTA_INACESSIVEL",
    "A pasta não está acessível. Selecione outro diretório e tente novamente.",
    "PERMISSAO_REVOGADA",
    "A permissão da pasta foi revogada. Selecione o diretório novamente.",
    "ARQUIVO_JA_EXISTE",
    "Já existe um arquivo com esse nome na pasta. Selecione outro diretório ou altere o nome.",
    "EXPORTACAO_FALHOU",
    "Não foi possível gerar ou transferir o relatório. Tente novamente.",
    "GRAVACAO_FALHOU",
    "Não foi possível gravar o arquivo na pasta escolhida. Selecione outro diretório."
  );

  private final RelatorioAgendamentoRepository repository;
  private final CriptografiaAgendamentoService criptografia;
  private final ObjectMapper objectMapper;
  private final ExportacaoRelatorioService exportacao;
  private final RelatorioPersonalizadoService personalizado;
  private final AuditoriaService auditoria;

  public RelatorioAgendamentoService(
    RelatorioAgendamentoRepository repository,
    CriptografiaAgendamentoService criptografia,
    ObjectMapper objectMapper,
    ExportacaoRelatorioService exportacao,
    RelatorioPersonalizadoService personalizado,
    AuditoriaService auditoria
  ) {
    this.repository = repository;
    this.criptografia = criptografia;
    this.objectMapper = objectMapper;
    this.exportacao = exportacao;
    this.personalizado = personalizado;
    this.auditoria = auditoria;
  }

  public RelatorioAgendamentoDtos.ConfiguracaoResponse configuracao() {
    return new RelatorioAgendamentoDtos.ConfiguracaoResponse(
      criptografia.configurada(),
      INTERVALO_VERIFICACAO_SEGUNDOS,
      RETENCAO_DIAS,
      "O navegador precisa estar aberto; execuções vencidas são retomadas ao reabrir a aplicação."
    );
  }

  public List<RelatorioAgendamentoDtos.ResumoResponse> listar(
    UsuarioPrincipal principal
  ) {
    boolean administrador = ehAdministrador(principal);
    return repository
      .listar(principal.id(), administrador)
      .stream()
      .map(row -> resumo(row, administrador, row.usuarioId() == principal.id()))
      .toList();
  }

  public RelatorioAgendamentoDtos.ResumoResponse criar(
    UsuarioPrincipal principal,
    RelatorioAgendamentoDtos.CriacaoRequest request,
    AuthService.RequestInfo info
  ) {
    exigirCriptografia();
    String id = UUID.randomUUID().toString();
    String tipo = normalizarEnum(request.tipoRelatorio(), TIPOS, "Tipo de relatório");
    String formato = normalizarEnum(request.formato(), FORMATOS, "Formato").toLowerCase(Locale.ROOT);
    String titulo = textoSeguro(request.tituloRelatorio(), 150, "Título do relatório");
    String apiNome = normalizarApi(tipo, request.apiNome());
    String nomeArquivo = normalizarNomeArquivo(request.nomeArquivo());
    String diretorioReferencia = normalizarReferencia(request.diretorioReferencia());
    String diretorioNome = normalizarDiretorioNome(request.diretorioNome());
    List<String> colunasExportacao = normalizarColunas(request.colunasExportacao());
    boolean incluirCabecalho = !Boolean.FALSE.equals(request.incluirCabecalho());
    validarFiltros(request.filtros());
    RecorrenciaAgendamento.Plano recorrencia = RecorrenciaAgendamento.normalizar(
      request.recorrencia(),
      request.diasSemana(),
      request.diaMes(),
      request.fusoHorario(),
      request.agendadoPara()
    );

    RelatorioPersonalizadoRequest personalizadoRequest = null;
    if ("PERSONALIZADO".equals(tipo)) {
      personalizadoRequest = new RelatorioPersonalizadoRequest(
        request.colunasPersonalizadas(),
        request.filtros(),
        request.distinct(),
        request.ordenarPor(),
        request.direcaoOrdenacao(),
        1,
        100,
        nomeArquivo
      );
      personalizado.validarAgendamento(personalizadoRequest);
    }

    ConfiguracaoPersistida configuracao = new ConfiguracaoPersistida(
      tipo,
      apiNome,
      request.filtros() == null ? Map.of() : new LinkedHashMap<>(request.filtros()),
      personalizadoRequest,
      colunasExportacao
    );
    String contexto = contexto(id, principal.id());
    String protegida = criptografia.criptografar(serializar(configuracao), contexto);
    long agora = Instant.now().toEpochMilli();

    RelatorioAgendamentoRepository.AgendamentoRow row =
      new RelatorioAgendamentoRepository.AgendamentoRow(
        id,
        principal.id(),
        principal.nome(),
        tipo,
        titulo,
        apiNome,
        protegida,
        formato,
        nomeArquivo,
        diretorioReferencia,
        diretorioNome,
        incluirCabecalho,
        request.agendadoPara().toEpochMilli(),
        recorrencia.tipo(),
        recorrencia.diasSemanaCsv(),
        recorrencia.diaMes(),
        recorrencia.fusoHorario(),
        "PENDENTE",
        0,
        0,
        null,
        null,
        null,
        agora,
        agora,
        null,
        null
      );
    repository.criar(row);
    auditar(principal, info, id, "RELATORIO_AGENDADO", "SUCESSO", tipo, formato);
    return resumo(row, false);
  }

  public List<String> listarPendentes(UsuarioPrincipal principal) {
    repository.liberarReservasExpiradas(Instant.now().toEpochMilli());
    return repository.listarPendentes(principal.id(), Instant.now().toEpochMilli());
  }

  public RelatorioAgendamentoDtos.ResumoResponse reservar(
    UsuarioPrincipal principal,
    String id
  ) {
    exigirCriptografia();
    validarId(id);
    long agora = Instant.now().toEpochMilli();
    boolean reservado = repository.reservar(
      id,
      principal.id(),
      agora,
      agora + DURACAO_RESERVA.toMillis()
    );
    if (!reservado) {
      throw new ApiException(
        HttpStatus.CONFLICT,
        "AGENDAMENTO_INDISPONIVEL",
        "O agendamento não está pendente, ainda não venceu ou já foi reservado por outra aba."
      );
    }
    return resumo(buscarProprio(principal, id), false);
  }

  public ArquivoPreparado prepararArquivo(UsuarioPrincipal principal, String id) {
    exigirCriptografia();
    RelatorioAgendamentoRepository.AgendamentoRow row = buscarProprio(principal, id);
    if (!"EM_EXECUCAO".equals(row.status())) {
      throw new ApiException(
        HttpStatus.CONFLICT,
        "AGENDAMENTO_NAO_RESERVADO",
        "Reserve o agendamento antes de gerar o arquivo."
      );
    }
    ConfiguracaoPersistida configuracao = desserializar(
      criptografia.descriptografar(
        row.configuracaoCriptografada(),
        contexto(row.id(), row.usuarioId())
      )
    );
    ExportacaoRelatorioService.DescricaoArquivo descricao = exportacao.descreverArquivo(
      row.formato()
    );
    ExportacaoRelatorioService.OpcoesSaida opcoes =
      new ExportacaoRelatorioService.OpcoesSaida(
        configuracao.colunasExportacao(),
        row.incluirCabecalho()
      );

    EscritorArquivo escritor = destino -> {
      if ("PERSONALIZADO".equals(configuracao.tipoRelatorio())) {
        personalizado.exportarPara(
          row.formato(),
          configuracao.personalizado(),
          destino,
          opcoes
        );
      } else {
        exportacao.exportarPara(
          configuracao.apiNome(),
          row.formato(),
          new RelatorioExportacaoRequest(configuracao.filtros(), row.nomeArquivo()),
          destino,
          opcoes
        );
      }
    };

    return new ArquivoPreparado(
      nomeArquivoExecucao(row) + "." + descricao.extensao(),
      descricao.contentType(),
      escritor
    );
  }

  public RelatorioAgendamentoDtos.OperacaoResponse concluir(
    UsuarioPrincipal principal,
    String id,
    AuthService.RequestInfo info
  ) {
    long agora = Instant.now().toEpochMilli();
    RelatorioAgendamentoRepository.AgendamentoRow atual = buscarProprio(principal, id);
    Instant proxima = RecorrenciaAgendamento.proxima(
      atual.recorrencia(),
      atual.diasSemana(),
      atual.diaMes(),
      atual.fusoHorario(),
      Instant.ofEpochMilli(atual.agendadoParaEpochMs()),
      Instant.ofEpochMilli(agora)
    );
    boolean concluido = proxima == null
      ? repository.concluirUnico(
          validarId(id),
          principal.id(),
          agora,
          agora + Duration.ofDays(RETENCAO_DIAS).toMillis()
        )
      : repository.concluirRecorrente(
          validarId(id),
          principal.id(),
          proxima.toEpochMilli(),
          agora
        );
    if (!concluido) operacaoInvalida();
    RelatorioAgendamentoRepository.AgendamentoRow row = buscarProprio(principal, id);
    auditar(principal, info, id, "RELATORIO_AGENDADO_CONCLUIDO", "SUCESSO", row.tipoRelatorio(), row.formato());
    return new RelatorioAgendamentoDtos.OperacaoResponse(
      proxima == null
        ? "Arquivo salvo com sucesso na pasta autorizada."
        : "Arquivo salvo com sucesso. A próxima execução recorrente já foi programada.",
      resumo(row, false)
    );
  }

  public RelatorioAgendamentoDtos.OperacaoResponse falhar(
    UsuarioPrincipal principal,
    String id,
    RelatorioAgendamentoDtos.FalhaRequest request,
    AuthService.RequestInfo info
  ) {
    String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
    String mensagem = MENSAGENS_FALHA.get(codigo);
    if (mensagem == null) {
      throw new IllegalArgumentException("Código de falha do agendamento inválido.");
    }
    long agora = Instant.now().toEpochMilli();
    boolean alterado = repository.falhar(
      validarId(id),
      principal.id(),
      codigo,
      mensagem,
      agora,
      agora + Duration.ofDays(RETENCAO_DIAS).toMillis()
    );
    if (!alterado) operacaoInvalida();
    RelatorioAgendamentoRepository.AgendamentoRow row = buscarProprio(principal, id);
    auditar(principal, info, id, "RELATORIO_AGENDADO_FALHOU", "FALHA", row.tipoRelatorio(), row.formato());
    return new RelatorioAgendamentoDtos.OperacaoResponse(mensagem, resumo(row, false));
  }

  public RelatorioAgendamentoDtos.OperacaoResponse alterarDestino(
    UsuarioPrincipal principal,
    String id,
    RelatorioAgendamentoDtos.DestinoRequest request,
    AuthService.RequestInfo info
  ) {
    String referencia = normalizarReferencia(request.diretorioReferencia());
    String nome = normalizarDiretorioNome(request.diretorioNome());
    boolean alterado = repository.alterarDestino(
      validarId(id),
      principal.id(),
      referencia,
      nome,
      Instant.now().toEpochMilli()
    );
    if (!alterado) {
      throw new ApiException(
        HttpStatus.CONFLICT,
        "DESTINO_NAO_ALTERADO",
        "Somente um agendamento com falha pode receber outra pasta."
      );
    }
    RelatorioAgendamentoRepository.AgendamentoRow row = buscarProprio(principal, id);
    auditar(principal, info, id, "RELATORIO_AGENDADO_DESTINO_ALTERADO", "SUCESSO", row.tipoRelatorio(), row.formato());
    return new RelatorioAgendamentoDtos.OperacaoResponse(
      "Pasta alterada. O relatório será tentado novamente.",
      resumo(row, false)
    );
  }

  public RelatorioAgendamentoDtos.OperacaoResponse cancelar(
    UsuarioPrincipal principal,
    String id,
    AuthService.RequestInfo info
  ) {
    long agora = Instant.now().toEpochMilli();
    boolean alterado = repository.cancelar(
      validarId(id),
      principal.id(),
      ehAdministrador(principal),
      agora,
      agora + Duration.ofDays(RETENCAO_DIAS).toMillis()
    );
    if (!alterado) operacaoInvalida();
    RelatorioAgendamentoRepository.AgendamentoRow row = repository
      .buscar(id, principal.id(), ehAdministrador(principal))
      .orElseThrow();
    auditar(principal, info, id, "RELATORIO_AGENDADO_CANCELADO", "SUCESSO", row.tipoRelatorio(), row.formato());
    return new RelatorioAgendamentoDtos.OperacaoResponse(
      "Agendamento cancelado.",
      resumo(row, ehAdministrador(principal))
    );
  }

  @Scheduled(cron = "0 15 2 * * *", zone = "UTC")
  public void limparHistoricoExpirado() {
    repository.excluirRetencaoExpirada(Instant.now().toEpochMilli());
  }

  private RelatorioAgendamentoRepository.AgendamentoRow buscarProprio(
    UsuarioPrincipal principal,
    String id
  ) {
    return repository
      .buscar(validarId(id), principal.id(), false)
      .orElseThrow(() -> new ApiException(
        HttpStatus.NOT_FOUND,
        "AGENDAMENTO_NAO_ENCONTRADO",
        "Agendamento não encontrado."
      ));
  }

  private String normalizarApi(String tipo, String apiNome) {
    if ("PERSONALIZADO".equals(tipo)) return RelatorioPersonalizadoService.API_NOME;
    String nome = apiNome == null ? "" : apiNome.trim();
    if (!nome.matches("[A-Za-z0-9._-]{1,150}")) {
      throw new IllegalArgumentException("O nome da API do relatório é inválido.");
    }
    if (personalizado.ehApiReservada(nome)) {
      throw new IllegalArgumentException("A API reservada só pode ser usada no modo personalizado.");
    }
    return nome;
  }

  private List<String> normalizarColunas(List<String> recebidas) {
    if (recebidas == null || recebidas.isEmpty() || recebidas.size() > 200) {
      throw new IllegalArgumentException("Selecione de uma a 200 colunas para o arquivo.");
    }
    LinkedHashSet<String> colunas = new LinkedHashSet<>();
    for (String recebida : recebidas) {
      String coluna = textoSeguro(recebida, 128, "Coluna");
      if ("RNUM".equalsIgnoreCase(coluna) || !colunas.add(coluna)) {
        throw new IllegalArgumentException("A seleção de colunas contém um item inválido ou repetido.");
      }
    }
    return List.copyOf(colunas);
  }

  private void validarFiltros(Map<String, Object> filtros) {
    if (filtros == null) return;
    if (filtros.size() > 50) {
      throw new IllegalArgumentException("A quantidade de filtros é inválida.");
    }
    filtros.forEach((nome, valor) -> {
      if (nome == null || nome.isBlank() || nome.length() > 100) {
        throw new IllegalArgumentException("Existe um filtro com nome inválido.");
      }
      if (valor instanceof Map<?, ?> || valor instanceof List<?>) {
        throw new IllegalArgumentException("Filtros compostos não são permitidos no agendamento.");
      }
      if (valor != null && String.valueOf(valor).length() > 240) {
        throw new IllegalArgumentException("Um filtro excede o tamanho permitido.");
      }
    });
  }

  private String normalizarNomeArquivo(String recebido) {
    String semExtensao = recebido == null
      ? ""
      : recebido.trim().replaceFirst("(?i)\\.(csv|txt|xlsx)$", "");
    String nome = semExtensao
      .replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_")
      .replaceAll("[. ]+$", "")
      .trim();
    if (nome.isBlank() || nome.length() > 170) {
      throw new IllegalArgumentException("Informe um nome de arquivo válido com até 170 caracteres.");
    }
    return nome;
  }

  private String normalizarReferencia(String recebida) {
    try {
      return UUID.fromString(recebida.trim()).toString();
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("A referência da pasta é inválida.", ex);
    }
  }

  private String normalizarDiretorioNome(String recebido) {
    String nome = textoSeguro(recebido, 255, "Nome da pasta");
    if (nome.matches(".*[\\/\\p{Cntrl}].*")) {
      throw new IllegalArgumentException("O nome da pasta é inválido.");
    }
    return nome;
  }

  private String textoSeguro(String recebido, int maximo, String campo) {
    String texto = recebido == null ? "" : recebido.trim();
    if (texto.isBlank() || texto.length() > maximo || texto.matches(".*\\p{Cntrl}.*")) {
      throw new IllegalArgumentException(campo + " é inválido.");
    }
    return texto;
  }

  private String normalizarEnum(String recebido, Set<String> permitidos, String campo) {
    String valor = recebido == null ? "" : recebido.trim().toUpperCase(Locale.ROOT);
    if (!permitidos.contains(valor) && !permitidos.contains(valor.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException(campo + " é inválido.");
    }
    return valor;
  }

  private String validarId(String id) {
    try {
      return UUID.fromString(id).toString();
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Identificador de agendamento inválido.", ex);
    }
  }

  private void exigirCriptografia() {
    if (!criptografia.configurada()) {
      throw new ApiException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "AGENDAMENTO_NAO_CONFIGURADO",
        "Configure REPORT_SCHEDULE_ENCRYPTION_KEY no backend para habilitar agendamentos."
      );
    }
  }

  private void operacaoInvalida() {
    throw new ApiException(
      HttpStatus.CONFLICT,
      "ESTADO_AGENDAMENTO_INVALIDO",
      "O agendamento não está em um estado compatível com esta operação."
    );
  }

  private boolean ehAdministrador(UsuarioPrincipal principal) {
    return "ADMINISTRADOR".equals(principal.perfil());
  }

  private String serializar(ConfiguracaoPersistida configuracao) {
    try {
      String json = objectMapper.writeValueAsString(configuracao);
      if (json.length() > 100_000) {
        throw new IllegalArgumentException("A configuração do agendamento excede o limite permitido.");
      }
      return json;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("A configuração do agendamento é inválida.", ex);
    }
  }

  private ConfiguracaoPersistida desserializar(String json) {
    try {
      return objectMapper.readValue(json, ConfiguracaoPersistida.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("A configuração protegida do agendamento está inválida.", ex);
    }
  }

  private String contexto(String id, long usuarioId) {
    return "relatorio-agendamento:" + id + ":" + usuarioId;
  }

  private RelatorioAgendamentoDtos.ResumoResponse resumo(
    RelatorioAgendamentoRepository.AgendamentoRow row,
    boolean incluirUsuario
  ) {
    return resumo(row, incluirUsuario, true);
  }

  private RelatorioAgendamentoDtos.ResumoResponse resumo(
    RelatorioAgendamentoRepository.AgendamentoRow row,
    boolean incluirUsuario,
    boolean incluirReferenciaDiretorio
  ) {
    return new RelatorioAgendamentoDtos.ResumoResponse(
      row.id(),
      row.usuarioId(),
      incluirUsuario ? row.usuarioNome() : null,
      row.tipoRelatorio(),
      row.tituloRelatorio(),
      row.formato(),
      row.nomeArquivo(),
      nomeArquivoExecucao(row),
      incluirReferenciaDiretorio ? row.diretorioReferencia() : null,
      row.diretorioNome(),
      row.incluirCabecalho(),
      Instant.ofEpochMilli(row.agendadoParaEpochMs()),
      row.recorrencia(),
      diasSemana(row.diasSemana()),
      row.diaMes(),
      row.fusoHorario(),
      row.status(),
      row.erroCodigo(),
      row.erroMensagem(),
      row.tentativas(),
      row.execucoesConcluidas(),
      Instant.ofEpochMilli(row.criadoEmEpochMs()),
      row.concluidoEmEpochMs() == null ? null : Instant.ofEpochMilli(row.concluidoEmEpochMs())
    );
  }

  private String nomeArquivoExecucao(RelatorioAgendamentoRepository.AgendamentoRow row) {
    return RecorrenciaAgendamento.nomeExecucao(
      row.nomeArquivo(),
      row.recorrencia(),
      row.fusoHorario(),
      Instant.ofEpochMilli(row.agendadoParaEpochMs())
    );
  }

  private List<Integer> diasSemana(String dias) {
    if (dias == null || dias.isBlank()) return List.of();
    return Arrays.stream(dias.split(",")).map(Integer::valueOf).toList();
  }

  private void auditar(
    UsuarioPrincipal principal,
    AuthService.RequestInfo info,
    String id,
    String evento,
    String resultado,
    String tipo,
    String formato
  ) {
    auditoria.registrar(
      principal.id(),
      null,
      evento,
      resultado,
      info.ip(),
      info.userAgent(),
      Map.of("agendamentoId", id, "tipo", tipo, "formato", formato)
    );
  }

  private record ConfiguracaoPersistida(
    String tipoRelatorio,
    String apiNome,
    Map<String, Object> filtros,
    RelatorioPersonalizadoRequest personalizado,
    List<String> colunasExportacao
  ) {}

  @FunctionalInterface
  public interface EscritorArquivo {
    void escrever(OutputStream destino) throws IOException;
  }

  public record ArquivoPreparado(
    String nomeArquivo,
    String contentType,
    EscritorArquivo escritor
  ) {}
}
