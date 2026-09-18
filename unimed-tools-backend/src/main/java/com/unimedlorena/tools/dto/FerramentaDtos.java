package com.unimedlorena.tools.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class FerramentaDtos {

  private FerramentaDtos() {}

  public record SalvarRequest(
    @NotBlank @Size(max = 80) String slug,
    @NotBlank @Size(max = 120) String nome,
    @NotBlank @Size(max = 500) String descricao,
    @NotBlank @Size(max = 160) String apiNome,
    List<@Size(max = 100) String> filtros,
    List<@Size(max = 120) String> colunasPreview
  ) {}

  public record Response(
    long id,
    String slug,
    String nome,
    String descricao,
    String apiNome,
    List<String> filtros,
    List<String> colunasPreview,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm
  ) {}
}
