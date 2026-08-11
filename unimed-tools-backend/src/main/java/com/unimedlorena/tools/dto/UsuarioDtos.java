package com.unimedlorena.tools.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Contratos do cadastro de usuário executado por administrador. */
public final class UsuarioDtos {

  private UsuarioDtos() {}

  public record CriacaoRequest(
    @NotBlank @Size(min = 3, max = 150) String nome,
    @NotBlank
    @Pattern(regexp = "[a-z0-9._-]{3,80}")
    String login,
    @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 128) String senhaTemporaria,
    @NotBlank
    @Pattern(regexp = "ADMINISTRADOR|USUARIO")
    String perfilCodigo,
    @NotBlank @Pattern(regexp = "\\d{6}") String codigoMfaAdministrador
  ) {
    @Override
    public String toString() {
      return "CriacaoRequest[perfilCodigo=" + perfilCodigo + ", credenciais=<protegidas>]";
    }
  }

  public record CriacaoResponse(
    long id,
    String nome,
    String login,
    String email,
    String perfil,
    String status,
    boolean deveTrocarSenha
  ) {
    @Override
    public String toString() {
      return "CriacaoResponse[perfil=" + perfil + ", status=" + status + "]";
    }
  }

  public record ResumoResponse(
    long id,
    String nome,
    String login,
    String email,
    String perfil,
    String status,
    boolean deveTrocarSenha,
    Set<String> permissoes
  ) {
    @Override
    public String toString() {
      return "ResumoResponse[id=" + id + ", perfil=" + perfil + ", status=" + status + "]";
    }
  }

  public record PermissaoResponse(String codigo, String modulo, String descricao) {}

  public record AtualizacaoPermissoesRequest(
    @NotNull @Size(max = 10)
    Set<@Pattern(regexp = "[A-Z0-9_]{3,100}") String> permissoes,
    @NotBlank @Pattern(regexp = "\\d{6}") String codigoMfaAdministrador
  ) {
    @Override
    public String toString() {
      return "AtualizacaoPermissoesRequest[quantidade=" +
        (permissoes == null ? 0 : permissoes.size()) + ", credenciais=<protegidas>]";
    }
  }

  public record AtualizacaoDadosRequest(
    @NotBlank @Size(min = 3, max = 150) String nome,
    @Email @Size(max = 254) String email,
    @NotBlank
    @Pattern(regexp = "ADMINISTRADOR|USUARIO")
    String perfilCodigo,
    @NotBlank @Pattern(regexp = "\\d{6}") String codigoMfaAdministrador
  ) {
    @Override
    public String toString() {
      return "AtualizacaoDadosRequest[perfilCodigo=" + perfilCodigo + ", credenciais=<protegidas>]";
    }
  }

  public record ExclusaoRequest(
    @NotBlank @Pattern(regexp = "\\d{6}") String codigoMfaAdministrador
  ) {
    @Override
    public String toString() {
      return "ExclusaoRequest[credenciais=<protegidas>]";
    }
  }

  public record RedefinicaoSenhaRequest(
    @NotBlank @Size(min = 8, max = 128) String senhaTemporaria,
    @NotBlank @Pattern(regexp = "\\d{6}") String codigoMfaAdministrador
  ) {
    @Override
    public String toString() {
      return "RedefinicaoSenhaRequest[credenciais=<protegidas>]";
    }
  }

  public record OperacaoResponse(String mensagem) {}
}
