package com.unimedlorena.tools.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Contratos HTTP do fluxo de login, MFA, sessão e troca de senha. */
public final class AuthDtos {

  private AuthDtos() {}

  // toString() é sobrescrito para que logs em DEBUG não revelem credenciais.

  public record LoginRequest(
    @NotBlank @Size(max = 80) String login,
    @NotBlank @Size(max = 128) String senha
  ) {
    @Override
    public String toString() {
      return "LoginRequest[credenciais=<protegidas>]";
    }
  }

  public record MfaRequest(
    @NotBlank @Size(max = 128) String desafioToken,
    @NotBlank @Size(min = 6, max = 6) String codigo
  ) {
    @Override
    public String toString() {
      return "MfaRequest[desafioToken=<protegido>, codigo=<protegido>]";
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
    String desafioToken,
    String segredoMfa,
    String uriMfa,
    UsuarioResponse usuario
  ) {
    @Override
    public String toString() {
      return "AuthFlowResponse[status=" + status + ", dadosSensiveis=<protegidos>]";
    }
  }
}
