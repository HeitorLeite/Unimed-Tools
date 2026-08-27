package com.unimedlorena.tools.controller;

import com.unimedlorena.tools.auth.AuthService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.RelatorioAgendamentoDtos;
import com.unimedlorena.tools.service.RelatorioAgendamentoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Expõe somente os agendamentos do usuário autenticado e downloads reservados. */
@RestController
@RequestMapping("/api/relatorios/agendamentos")
public class RelatorioAgendamentoController {

  private final RelatorioAgendamentoService service;

  public RelatorioAgendamentoController(RelatorioAgendamentoService service) {
    this.service = service;
  }

  @GetMapping("/configuracao")
  public RelatorioAgendamentoDtos.ConfiguracaoResponse configuracao() {
    return service.configuracao();
  }

  @GetMapping
  public List<RelatorioAgendamentoDtos.ResumoResponse> listar(
    @AuthenticationPrincipal UsuarioPrincipal principal
  ) {
    return service.listar(principal);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RelatorioAgendamentoDtos.ResumoResponse criar(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @Valid @RequestBody RelatorioAgendamentoDtos.CriacaoRequest request,
    HttpServletRequest httpRequest
  ) {
    return service.criar(principal, request, info(httpRequest));
  }

  @GetMapping("/pendentes")
  public List<String> listarPendentes(
    @AuthenticationPrincipal UsuarioPrincipal principal
  ) {
    return service.listarPendentes(principal);
  }

  @PostMapping("/{id}/reservar")
  public RelatorioAgendamentoDtos.ResumoResponse reservar(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @PathVariable String id
  ) {
    return service.reservar(principal, id);
  }

  @PostMapping("/{id}/arquivo")
  public ResponseEntity<StreamingResponseBody> arquivo(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @PathVariable String id
  ) {
    RelatorioAgendamentoService.ArquivoPreparado arquivo = service.prepararArquivo(
      principal,
      id
    );
    StreamingResponseBody corpo = arquivo.escritor()::escrever;
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(arquivo.contentType()))
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
          .filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .body(corpo);
  }

  @PostMapping("/{id}/concluir")
  public RelatorioAgendamentoDtos.OperacaoResponse concluir(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @PathVariable String id,
    HttpServletRequest httpRequest
  ) {
    return service.concluir(principal, id, info(httpRequest));
  }

  @PostMapping("/{id}/falhar")
  public RelatorioAgendamentoDtos.OperacaoResponse falhar(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @PathVariable String id,
    @Valid @RequestBody RelatorioAgendamentoDtos.FalhaRequest request,
    HttpServletRequest httpRequest
  ) {
    return service.falhar(principal, id, request, info(httpRequest));
  }

  @PutMapping("/{id}/destino")
  public RelatorioAgendamentoDtos.OperacaoResponse alterarDestino(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @PathVariable String id,
    @Valid @RequestBody RelatorioAgendamentoDtos.DestinoRequest request,
    HttpServletRequest httpRequest
  ) {
    return service.alterarDestino(principal, id, request, info(httpRequest));
  }

  @DeleteMapping("/{id}")
  public RelatorioAgendamentoDtos.OperacaoResponse cancelar(
    @AuthenticationPrincipal UsuarioPrincipal principal,
    @PathVariable String id,
    HttpServletRequest httpRequest
  ) {
    return service.cancelar(principal, id, info(httpRequest));
  }

  private AuthService.RequestInfo info(HttpServletRequest request) {
    return new AuthService.RequestInfo(
      request.getRemoteAddr(),
      request.getHeader("User-Agent")
    );
  }
}
