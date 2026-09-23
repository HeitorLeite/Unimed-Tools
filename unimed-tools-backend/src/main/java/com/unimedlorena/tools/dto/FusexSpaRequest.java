package com.unimedlorena.tools.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FusexSpaRequest(
    @NotBlank @Size(max = 20000) String guias,
    Boolean confirmado) {}
