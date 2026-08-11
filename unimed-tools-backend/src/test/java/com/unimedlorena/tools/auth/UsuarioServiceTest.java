package com.unimedlorena.tools.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unimedlorena.tools.auth.AuthRepository.UsuarioRow;
import com.unimedlorena.tools.dto.UsuarioDtos;
import com.unimedlorena.tools.exception.ApiException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class UsuarioServiceTest {

  private final AuthRepository repository = mock(AuthRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final PoliticaSenhaService politicaSenha = mock(PoliticaSenhaService.class);
  private final CriptografiaMfaService criptografia = mock(CriptografiaMfaService.class);
  private final TotpService totp = mock(TotpService.class);
  private final AuditoriaService auditoria = mock(AuditoriaService.class);
  private final UsuarioService service = new UsuarioService(
    repository,
    encoder,
    politicaSenha,
    criptografia,
    totp,
    auditoria
  );
  private final UsuarioPrincipal principalAdmin = new UsuarioPrincipal(
    1,
    "Admin",
    "admin",
    null,
    "ADMINISTRADOR",
    false,
    Set.of("USUARIOS_EDITAR")
  );
  private final AuthService.RequestInfo info = new AuthService.RequestInfo("127.0.0.1", "teste");

  @BeforeEach
  void configurarAdministrador() {
    when(repository.buscarUsuarioPorId(1)).thenReturn(Optional.of(usuario(1, "ADMINISTRADOR", "segredo")));
    when(criptografia.descriptografar("segredo")).thenReturn("TOTP");
    when(totp.validar(eq("TOTP"), any(), any())).thenReturn(100L);
    when(repository.atualizarPassoMfa(1, 100L)).thenReturn(true);
  }

  @Test
  void deveNegarGerenciamentoParaUsuarioOperacional() {
    UsuarioPrincipal operacional = new UsuarioPrincipal(
      2,
      "Operacional",
      "operacional",
      null,
      "USUARIO",
      false,
      Set.of()
    );

    assertThrows(ApiException.class, () -> service.listar(operacional));
    verify(repository, never()).listarUsuarios();
  }

  @Test
  void deveConcederSomentePermissoesOperacionaisEAdicionarAcessoBase() {
    when(repository.buscarUsuarioPorId(2)).thenReturn(Optional.of(usuario(2, "USUARIO", null)));
    when(repository.buscarPermissoesOperacionaisAtivas(Set.of("XML_ACESSAR")))
      .thenReturn(Set.of("XML_ACESSAR"));

    service.atualizarPermissoes(
      principalAdmin,
      2,
      new UsuarioDtos.AtualizacaoPermissoesRequest(Set.of("XML_ACESSAR"), "123456"),
      info
    );

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<String>> permissoes = ArgumentCaptor.forClass(Set.class);
    verify(repository).substituirPermissoesUsuario(eq(2L), permissoes.capture(), eq(1L));
    assertThat(permissoes.getValue()).containsExactlyInAnyOrder("APLICACAO_ACESSAR", "XML_ACESSAR");
  }

  @Test
  void deveRevogarSessoesAoRedefinirSenha() {
    when(repository.buscarUsuarioPorId(2)).thenReturn(Optional.of(usuario(2, "USUARIO", null)));
    when(encoder.encode("Caju#804")).thenReturn("hash");

    service.redefinirSenha(
      principalAdmin,
      2,
      new UsuarioDtos.RedefinicaoSenhaRequest("Caju#804", "123456"),
      info
    );

    verify(repository).redefinirSenha(eq(2L), eq("hash"), eq(1L), any());
    verify(repository).revogarSessoesDoUsuario(2, "SENHA_REDEFINIDA_ADMIN");
  }

  private UsuarioRow usuario(long id, String perfil, String segredoMfa) {
    return new UsuarioRow(
      id,
      perfil.equals("ADMINISTRADOR") ? "Admin" : "Operacional",
      perfil.equals("ADMINISTRADOR") ? "admin" : "operacional",
      null,
      "hash",
      perfil,
      true,
      "ATIVO",
      false,
      null,
      0,
      null,
      segredoMfa,
      segredoMfa != null,
      null
    );
  }
}
