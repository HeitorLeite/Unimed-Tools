package com.unimedlorena.tools.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Contratos HTTP do fluxo de login, sessão e troca de senha. */
public final class AuthDtos {

  private AuthDtos() {}

  public record LoginRequest(
    @NotBlank @Size(max = 80) String login,
    @NotBlank @Size(max = 128) String senha
  ) {
    @Override
    public String toString() {
      return "LoginRequest[credenciais=<protegidas>]";
    }
  }

  public record TrocaSenhaRequest(
    @NotBlank @Size(max = 128) String senhaAtual,
    @NotBlank @Size(max = 128) String novaSenha
  ) {
    @Override
    public String toString() {
      return "TrocaSenhaRequest[senhas=<protegidas>]";
    }
  }

  public record UsuarioResponse(
    long id,
    String nome,
    String login,
    String email,
    String perfil,
    boolean deveTrocarSenha,
    Set<String> permissoes
  ) {
    @Override
    public String toString() {
      return "UsuarioResponse[perfil=" + perfil + ", deveTrocarSenha=" + deveTrocarSenha + "]";
    }
  }

  public record AuthFlowResponse(
    String status,
    UsuarioResponse usuario
  ) {
    @Override
    public String toString() {
      return "AuthFlowResponse[status=" + status + ", usuario=<protegido>]";
    }
  }
}
