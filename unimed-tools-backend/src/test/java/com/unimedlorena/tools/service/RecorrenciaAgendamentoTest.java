package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecorrenciaAgendamentoTest {

  @Test
  void deveNormalizarDiasSemanaisSemRepeticao() {
    var plano = RecorrenciaAgendamento.normalizar(
      "semanal",
      List.of(5, 1, 5, 3),
      null,
      "America/Sao_Paulo",
      Instant.parse("2026-08-28T11:00:00Z")
    );

    assertThat(plano.tipo()).isEqualTo("SEMANAL");
    assertThat(plano.diasSemana()).containsExactly(1, 3, 5);
    assertThat(plano.diasSemanaCsv()).isEqualTo("1,3,5");
  }

  @Test
  void deveCalcularProximoDiaSemGerarOcorrenciasRetroativas() {
    Instant proxima = RecorrenciaAgendamento.proxima(
      "DIARIA",
      null,
      null,
      "America/Sao_Paulo",
      Instant.parse("2026-08-20T11:00:00Z"),
      Instant.parse("2026-08-27T12:00:00Z")
    );

    assertThat(proxima).isEqualTo(Instant.parse("2026-08-28T11:00:00Z"));
  }

  @Test
  void deveRespeitarVariosDiasDaSemana() {
    Instant proxima = RecorrenciaAgendamento.proxima(
      "SEMANAL",
      "2,4",
      null,
      "America/Sao_Paulo",
      Instant.parse("2026-08-20T11:00:00Z"),
      Instant.parse("2026-08-27T12:00:00Z")
    );

    assertThat(proxima).isEqualTo(Instant.parse("2026-09-01T11:00:00Z"));
  }

  @Test
  void deveUsarUltimoDiaDoMesQuandoDia31NaoExistir() {
    Instant proxima = RecorrenciaAgendamento.proxima(
      "MENSAL",
      null,
      31,
      "America/Sao_Paulo",
      Instant.parse("2026-01-31T11:00:00Z"),
      Instant.parse("2026-01-31T12:00:00Z")
    );

    assertThat(proxima).isEqualTo(Instant.parse("2026-02-28T11:00:00Z"));
  }

  @Test
  void deveAdicionarDataAoNomeDeArquivoRecorrente() {
    assertThat(
      RecorrenciaAgendamento.nomeExecucao(
        "despesa",
        "DIARIA",
        "America/Sao_Paulo",
        Instant.parse("2026-08-28T11:00:00Z")
      )
    ).isEqualTo("despesa_20260828_0800");
  }
}
