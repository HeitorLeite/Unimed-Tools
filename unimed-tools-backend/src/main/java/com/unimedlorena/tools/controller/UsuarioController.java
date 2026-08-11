package com.unimedlorena.tools.controller;

import com.unimedlorena.tools.auth.AuthService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.auth.UsuarioService;
import com.unimedlorena.tools.dto.UsuarioDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

  private final UsuarioService usuarioService;

  public UsuarioController(UsuarioService usuarioService) {
    this.usuarioService = usuarioService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UsuarioDtos.CriacaoResponse criar(
    @AuthenticationPrincipal UsuarioPrincipal administrador,
    @Valid @RequestBody UsuarioDtos.CriacaoRequest request,
    HttpServletRequest httpRequest
  ) {
    return usuarioService.criar(
      administrador,
      request,
      new AuthService.RequestInfo(httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"))
    );
  }

  @GetMapping
  public List<UsuarioDtos.ResumoResponse> listar(
    @AuthenticationPrincipal UsuarioPrincipal administrador
  ) {
    return usuarioService.listar(administrador);
  }

  @GetMapping("/permissoes-disponiveis")
  public List<UsuarioDtos.PermissaoResponse> listarPermissoes(
    @AuthenticationPrincipal UsuarioPrincipal administrador
  ) {
    return usuarioService.listarPermissoes(administrador);
  }

  @PutMapping("/{usuarioId}")
  public UsuarioDtos.ResumoResponse atualizar(
    @AuthenticationPrincipal UsuarioPrincipal administrador,
    @PathVariable long usuarioId,
    @Valid @RequestBody UsuarioDtos.AtualizacaoDadosRequest request,
    HttpServletRequest httpRequest
  ) {
    return usuarioService.atualizar(
      administrador,
      usuarioId,
      request,
      requestInfo(httpRequest)
    );
  }

  @DeleteMapping("/{usuarioId}")
  public UsuarioDtos.OperacaoResponse excluir(
    @AuthenticationPrincipal UsuarioPrincipal administrador,
    @PathVariable long usuarioId,
    @Valid @RequestBody UsuarioDtos.ExclusaoRequest request,
    HttpServletRequest httpRequest
  ) {
    return usuarioService.excluir(
      administrador,
      usuarioId,
      request,
      requestInfo(httpRequest)
    );
  }

  @PutMapping("/{usuarioId}/permissoes")
  public UsuarioDtos.OperacaoResponse atualizarPermissoes(
    @AuthenticationPrincipal UsuarioPrincipal administrador,
    @PathVariable long usuarioId,
    @Valid @RequestBody UsuarioDtos.AtualizacaoPermissoesRequest request,
    HttpServletRequest httpRequest
  ) {
    return usuarioService.atualizarPermissoes(
      administrador,
      usuarioId,
      request,
      requestInfo(httpRequest)
    );
  }

  @PostMapping("/{usuarioId}/resetar-senha")
  public UsuarioDtos.OperacaoResponse redefinirSenha(
    @AuthenticationPrincipal UsuarioPrincipal administrador,
    @PathVariable long usuarioId,
    @Valid @RequestBody UsuarioDtos.RedefinicaoSenhaRequest request,
    HttpServletRequest httpRequest
  ) {
    return usuarioService.redefinirSenha(
      administrador,
      usuarioId,
      request,
      requestInfo(httpRequest)
    );
  }

  private AuthService.RequestInfo requestInfo(HttpServletRequest request) {
    return new AuthService.RequestInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
  }
}
