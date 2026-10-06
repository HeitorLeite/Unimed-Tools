package com.unimedlorena.tools.auth;

import com.unimedlorena.tools.exception.ApiException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Aplica comprimento e uma denylist local mínima sem registrar a senha. */
@Service
public class PoliticaSenhaService {

  private static final Set<String> COMUNS = Set.of(
    "12345678",
    "123456789012",
    "password",
    "password1234",
    "senha123",
    "senha123456",
    "unimed12",
    "unimed123456",
    "administrador",
    "qwerty12",
    "qwerty123456"
  );

  public void validar(String senha, String login, String email) {
    if (senha == null || senha.length() < 8 || senha.length() > 128) {
      falha("A senha deve possuir entre 8 e 128 caracteres.");
    }
    String normalizada = senha.toLowerCase(Locale.ROOT);
    if (COMUNS.contains(normalizada)) {
      falha("Escolha uma senha menos comum.");
    }
    if (login != null && normalizada.contains(login.toLowerCase(Locale.ROOT))) {
      falha("A senha não pode conter o login.");
    }
    if (email != null && !email.isBlank()) {
      String parteLocal = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
      if (parteLocal.length() >= 3 && normalizada.contains(parteLocal)) {
        falha("A senha não pode conter o e-mail.");
      }
    }
  }

  private void falha(String mensagem) {
    throw new ApiException(HttpStatus.BAD_REQUEST, "SENHA_FRACA", mensagem);
  }
}
