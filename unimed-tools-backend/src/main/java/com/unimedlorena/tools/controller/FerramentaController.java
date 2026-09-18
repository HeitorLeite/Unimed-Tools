package com.unimedlorena.tools.controller;

import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.FerramentaDtos;
import com.unimedlorena.tools.service.FerramentaConfiguravelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ferramentas")
public class FerramentaController {

  private final FerramentaConfiguravelService service;

  public FerramentaController(FerramentaConfiguravelService service) {
    this.service = service;
  }

  @GetMapping
  public List<FerramentaDtos.Response> listar() {
    return service.listar();
  }

  @PostMapping
  public FerramentaDtos.Response criar(
    @Valid @RequestBody FerramentaDtos.SalvarRequest request,
    @AuthenticationPrincipal UsuarioPrincipal principal
  ) {
    return service.criar(request, principal);
  }

  @PutMapping("/{id}")
  public FerramentaDtos.Response atualizar(
    @PathVariable long id,
    @Valid @RequestBody FerramentaDtos.SalvarRequest request,
    @AuthenticationPrincipal UsuarioPrincipal principal
  ) {
    return service.atualizar(id, request, principal);
  }

  @DeleteMapping("/{id}")
  public Map<String, String> excluir(
    @PathVariable long id,
    @AuthenticationPrincipal UsuarioPrincipal principal
  ) {
    service.excluir(id, principal);
    return Map.of("mensagem", "Ferramenta removida.");
  }
}
