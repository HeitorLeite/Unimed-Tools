package com.unimedlorena.tools.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Valida a regra recorrente e calcula ocorrências no fuso horário escolhido pelo usuário. */
final class RecorrenciaAgendamento {

  static final String FUSO_PADRAO = "America/Sao_Paulo";
  private static final Set<String> TIPOS = Set.of("UNICA", "DIARIA", "SEMANAL", "MENSAL");
  private static final DateTimeFormatter SUFIXO_ARQUIVO = DateTimeFormatter.ofPattern(
    "yyyyMMdd_HHmm"
  );

  private RecorrenciaAgendamento() {}

  static Plano normalizar(
    String recorrencia,
    List<Integer> diasSemana,
    Integer diaMes,
    String fusoHorario,
    Instant primeiraExecucao
  ) {
    String tipo = recorrencia == null || recorrencia.isBlank()
      ? "UNICA"
      : recorrencia.trim().toUpperCase(Locale.ROOT);
    if (!TIPOS.contains(tipo)) {
      throw new IllegalArgumentException("A recorrência do agendamento é inválida.");
    }

    ZoneId zona;
    try {
      zona = ZoneId.of(
        fusoHorario == null || fusoHorario.isBlank() ? FUSO_PADRAO : fusoHorario.trim()
      );
    } catch (DateTimeException ex) {
      throw new IllegalArgumentException("O fuso horário do agendamento é inválido.", ex);
    }
    if (zona.getId().length() > 50) {
      throw new IllegalArgumentException("O fuso horário do agendamento é inválido.");
    }

    List<Integer> dias = List.of();
    if ("SEMANAL".equals(tipo)) {
      LinkedHashSet<Integer> unicos = new LinkedHashSet<>();
      if (diasSemana != null) unicos.addAll(diasSemana);
      if (unicos.isEmpty() || unicos.stream().anyMatch(dia -> dia == null || dia < 1 || dia > 7)) {
        throw new IllegalArgumentException("Selecione pelo menos um dia válido para a recorrência semanal.");
      }
      dias = unicos.stream().sorted().toList();
    }

    Integer diaDoMes = null;
    if ("MENSAL".equals(tipo)) {
      diaDoMes = diaMes == null
        ? primeiraExecucao.atZone(zona).getDayOfMonth()
        : diaMes;
      if (diaDoMes < 1 || diaDoMes > 31) {
        throw new IllegalArgumentException("O dia da recorrência mensal deve estar entre 1 e 31.");
      }
    }
    return new Plano(tipo, dias, diaDoMes, zona.getId());
  }

  static Instant proxima(
    String recorrencia,
    String diasSemanaCsv,
    Integer diaMes,
    String fusoHorario,
    Instant horarioBase,
    Instant depoisDe
  ) {
    if ("UNICA".equals(recorrencia)) return null;
    ZoneId zona = ZoneId.of(fusoHorario);
    LocalTime horario = horarioBase.atZone(zona).toLocalTime();
    ZonedDateTime limite = depoisDe.atZone(zona);

    if ("DIARIA".equals(recorrencia)) {
      LocalDate data = limite.toLocalDate();
      ZonedDateTime candidata = combinar(data, horario, zona);
      if (!candidata.toInstant().isAfter(depoisDe)) candidata = combinar(data.plusDays(1), horario, zona);
      return candidata.toInstant();
    }

    if ("SEMANAL".equals(recorrencia)) {
      Set<Integer> dias = Set.copyOf(
        Arrays.stream(diasSemanaCsv.split(",")).map(Integer::valueOf).toList()
      );
      for (int deslocamento = 0; deslocamento <= 7; deslocamento++) {
        LocalDate data = limite.toLocalDate().plusDays(deslocamento);
        if (!dias.contains(data.getDayOfWeek().getValue())) continue;
        Instant candidata = combinar(data, horario, zona).toInstant();
        if (candidata.isAfter(depoisDe)) return candidata;
      }
      throw new IllegalStateException("Não foi possível calcular a próxima execução semanal.");
    }

    YearMonth mes = YearMonth.from(limite);
    for (int deslocamento = 0; deslocamento <= 12; deslocamento++) {
      YearMonth candidatoMes = mes.plusMonths(deslocamento);
      int dia = Math.min(diaMes, candidatoMes.lengthOfMonth());
      Instant candidata = combinar(candidatoMes.atDay(dia), horario, zona).toInstant();
      if (candidata.isAfter(depoisDe)) return candidata;
    }
    throw new IllegalStateException("Não foi possível calcular a próxima execução mensal.");
  }

  static String nomeExecucao(
    String nomeBase,
    String recorrencia,
    String fusoHorario,
    Instant agendadoPara
  ) {
    if ("UNICA".equals(recorrencia)) return nomeBase;
    return nomeBase + "_" + SUFIXO_ARQUIVO.format(agendadoPara.atZone(ZoneId.of(fusoHorario)));
  }

  private static ZonedDateTime combinar(LocalDate data, LocalTime horario, ZoneId zona) {
    // atZone resolve transições de horário de verão usando as regras oficiais do fuso.
    return LocalDateTime.of(data, horario).atZone(zona);
  }

  record Plano(String tipo, List<Integer> diasSemana, Integer diaMes, String fusoHorario) {
    String diasSemanaCsv() {
      return diasSemana.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
    }
  }
}
