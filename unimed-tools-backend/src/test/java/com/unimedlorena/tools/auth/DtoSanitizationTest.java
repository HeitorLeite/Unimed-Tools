package com.unimedlorena.tools.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.unimedlorena.tools.dto.AuthDtos;
import com.unimedlorena.tools.dto.UsuarioDtos;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DtoSanitizationTest {

  @Test
  void naoExpoeCredenciaisNosDtosDeEntrada() {
    String login = new AuthDtos.LoginRequest("pessoa.teste", "SenhaSecreta!123").toString();
    String mfa = new AuthDtos.MfaRequest("token-muito-secreto", "123456").toString();
    String troca = new AuthDtos.TrocaSenhaRequest("SenhaAntiga!123", "SenhaNova!456").toString();
    String cadastro = new UsuarioDtos.CriacaoRequest(
      "Pessoa Teste",
      "pessoa.teste",
      "pessoa@example.invalid",
      "SenhaTemporaria!123",
      "USUARIO"
    ).toString();
    String permissoes = new UsuarioDtos.AtualizacaoPermissoesRequest(
      Set.of("XML_ACESSAR")
    ).toString();
    String atualizacao = new UsuarioDtos.AtualizacaoDadosRequest(
      "Pessoa Atualizada",
      "atualizada@example.invalid",
      "ADMINISTRADOR"
    ).toString();
    String redefinicao = new UsuarioDtos.RedefinicaoSenhaRequest(
      "SenhaTemporaria!456"
    ).toString();

    assertThat(login).doesNotContain("pessoa.teste", "SenhaSecreta!123");
    assertThat(mfa).doesNotContain("token-muito-secreto", "123456");
    assertThat(troca).doesNotContain("SenhaAntiga!123", "SenhaNova!456");
    assertThat(cadastro).doesNotContain(
      "Pessoa Teste",
      "pessoa.teste",
      "pessoa@example.invalid",
      "SenhaTemporaria!123"
    );
    assertThat(permissoes).doesNotContain("XML_ACESSAR", "123456");
    assertThat(atualizacao).doesNotContain(
      "Pessoa Atualizada",
      "atualizada@example.invalid",
      "234567"
    );
    assertThat(redefinicao).doesNotContain("SenhaTemporaria!456");
  }

  @Test
  void naoExpoeDadosPessoaisNemSegredoNosDtosDeSaida() {
    AuthDtos.UsuarioResponse usuario = new AuthDtos.UsuarioResponse(
      10,
      "Pessoa Teste",
      "pessoa.teste",
      "pessoa@example.invalid",
      "ADMINISTRADOR",
      false,
      Set.of("USUARIOS_CRIAR")
    );
    String fluxo = new AuthDtos.AuthFlowResponse(
      "MFA_CONFIGURACAO",
      "token-muito-secreto",
      "SEGREDOTOTP",
      "otpauth://segredo",
      usuario
    ).toString();
    String respostaUsuario = usuario.toString();

    assertThat(fluxo).doesNotContain(
      "token-muito-secreto",
      "SEGREDOTOTP",
      "otpauth://segredo",
      "Pessoa Teste",
      "pessoa@example.invalid"
    );
    assertThat(respostaUsuario).doesNotContain(
      "Pessoa Teste",
      "pessoa.teste",
      "pessoa@example.invalid",
      "USUARIOS_CRIAR"
    );
  }
}
