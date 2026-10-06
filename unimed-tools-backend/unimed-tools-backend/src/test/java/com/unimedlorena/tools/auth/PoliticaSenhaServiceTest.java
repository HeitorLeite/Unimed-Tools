package com.unimedlorena.tools.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unimedlorena.tools.exception.ApiException;
import org.junit.jupiter.api.Test;

class PoliticaSenhaServiceTest {

  private final PoliticaSenhaService service = new PoliticaSenhaService();

  @Test
  void deveAceitarSenhaLongaNaoRelacionadaAConta() {
    assertDoesNotThrow(() -> service.validar("Frase longa e exclusiva! 2026", "heitor", "heitor@example.test"));
    assertDoesNotThrow(() -> service.validar("Caju#804", "heitor", "heitor@example.test"));
  }

  @Test
  void deveRejeitarSenhaCurtaComumOuComLogin() {
    assertThrows(ApiException.class, () -> service.validar("curta", "heitor", null));
    assertThrows(ApiException.class, () -> service.validar("senha123", "heitor", null));
    assertThrows(ApiException.class, () -> service.validar("senha123456", "heitor", null));
    assertThrows(ApiException.class, () -> service.validar("chave-heitor-segura-2026", "heitor", null));
  }
}
