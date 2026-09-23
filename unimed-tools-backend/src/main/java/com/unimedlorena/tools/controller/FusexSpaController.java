package com.unimedlorena.tools.controller;

import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.FusexSpaRequest;
import com.unimedlorena.tools.service.FusexSpaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fusex-spa")
public class FusexSpaController {
  private final FusexSpaService service;

  public FusexSpaController(FusexSpaService service) {
    this.service = service;
  }

  @PostMapping("/validar")
  public FusexSpaService.Validacao validar(@Valid @RequestBody FusexSpaRequest request) {
    return service.validar(request);
  }

  @PostMapping("/executar")
  public void executar(@Valid @RequestBody FusexSpaRequest request,
      @AuthenticationPrincipal UsuarioPrincipal principal) {
    service.executar(request, principal);
  }
}
