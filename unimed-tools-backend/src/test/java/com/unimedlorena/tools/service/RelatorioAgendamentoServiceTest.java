package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimedlorena.tools.auth.AuditoriaService;
import com.unimedlorena.tools.auth.AuthService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.RelatorioAgendamentoDtos;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RelatorioAgendamentoServiceTest {

  @Test
  void deveVincularAoCriadorECriptografarFiltros() {
    RelatorioAgendamentoRepository repository = mock(RelatorioAgendamentoRepository.class);
    AuditoriaService auditoria = mock(AuditoriaService.class);
    String chave = Base64.getEncoder().encodeToString(new byte[32]);
    var service = new RelatorioAgendamentoService(
      repository,
      new CriptografiaAgendamentoService(chave),
      new ObjectMapper(),
      mock(ExportacaoRelatorioService.class),
      mock(RelatorioPersonalizadoService.class),
      auditoria
    );
    UsuarioPrincipal principal = new UsuarioPrincipal(
      42,
      "Usuário sintético",
      "usuario.teste",
      null,
      "USUARIO",
      false,
      Set.of("RELATORIOS_ACESSAR")
    );
    var request = new RelatorioAgendamentoDtos.CriacaoRequest(
      "MANUAL",
      "Relatório sintético",
      "0090-relatorio-teste",
      Map.of("competencia", 202608),
      null,
      false,
      null,
      null,
      List.of("COLUNA_A"),
      true,
      "csv",
      "arquivo_teste",
      "00000000-0000-0000-0000-000000000001",
      "Relatorios",
      Instant.now().plusSeconds(3600),
      "SEMANAL",
      List.of(1, 3, 5),
      null,
      "America/Sao_Paulo"
    );

    service.criar(principal, request, new AuthService.RequestInfo("127.0.0.1", "teste"));

    ArgumentCaptor<RelatorioAgendamentoRepository.AgendamentoRow> captor =
      ArgumentCaptor.forClass(RelatorioAgendamentoRepository.AgendamentoRow.class);
    verify(repository).criar(captor.capture());
    assertThat(captor.getValue().usuarioId()).isEqualTo(42);
    assertThat(captor.getValue().configuracaoCriptografada())
      .doesNotContain("competencia", "202608");
    assertThat(captor.getValue().recorrencia()).isEqualTo("SEMANAL");
    assertThat(captor.getValue().diasSemana()).isEqualTo("1,3,5");
    verify(auditoria).registrar(any(), isNull(), any(), any(), any(), any(), any());
  }

  @Test
  void deveReagendarRecorrenteDepoisDaConclusao() {
    RelatorioAgendamentoRepository repository = mock(RelatorioAgendamentoRepository.class);
    AuditoriaService auditoria = mock(AuditoriaService.class);
    var service = new RelatorioAgendamentoService(
      repository,
      new CriptografiaAgendamentoService(Base64.getEncoder().encodeToString(new byte[32])),
      new ObjectMapper(),
      mock(ExportacaoRelatorioService.class),
      mock(RelatorioPersonalizadoService.class),
      auditoria
    );
    UsuarioPrincipal principal = new UsuarioPrincipal(
      42,
      "Usuário sintético",
      "usuario.teste",
      null,
      "USUARIO",
      false,
      Set.of("RELATORIOS_ACESSAR")
    );
    String id = "00000000-0000-0000-0000-000000000001";
    long primeiraExecucao = Instant.now().minusSeconds(3600).toEpochMilli();
    var row = new RelatorioAgendamentoRepository.AgendamentoRow(
      id,
      42,
      "Usuário sintético",
      "MANUAL",
      "Relatório",
      "api-teste",
      "protegida",
      "csv",
      "relatorio",
      "00000000-0000-0000-0000-000000000002",
      "Relatorios",
      true,
      primeiraExecucao,
      "DIARIA",
      null,
      null,
      "America/Sao_Paulo",
      "EM_EXECUCAO",
      1,
      0,
      null,
      null,
      null,
      primeiraExecucao,
      primeiraExecucao,
      null,
      null
    );
    when(repository.buscar(id, 42, false)).thenReturn(Optional.of(row));
    when(repository.concluirRecorrente(eq(id), eq(42L), anyLong(), anyLong())).thenReturn(true);
    ArgumentCaptor<Long> proxima = ArgumentCaptor.forClass(Long.class);

    service.concluir(
      principal,
      id,
      new AuthService.RequestInfo("127.0.0.1", "teste")
    );

    verify(repository).concluirRecorrente(eq(id), eq(42L), proxima.capture(), anyLong());
    assertThat(proxima.getValue()).isGreaterThan(Instant.now().toEpochMilli());
    assertThat(proxima.getValue()).isLessThan(Instant.now().plusSeconds(25 * 3600).toEpochMilli());
  }
}
