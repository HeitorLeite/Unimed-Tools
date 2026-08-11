package com.unimedlorena.tools.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CriptografiaMfaServiceTest {

  @Test
  void deveCriptografarComNonceUnicoEDescriptografar() {
    CriptografiaMfaService service = new CriptografiaMfaService(chave());

    String primeiro = service.criptografar("SEGREDOTOTP");
    String segundo = service.criptografar("SEGREDOTOTP");

    assertNotEquals(primeiro, segundo);
    assertEquals("SEGREDOTOTP", service.descriptografar(primeiro));
    assertEquals("SEGREDOTOTP", service.descriptografar(segundo));
  }

  @Test
  void deveRejeitarConteudoAdulterado() {
    CriptografiaMfaService service = new CriptografiaMfaService(chave());
    byte[] payload = Base64.getDecoder().decode(service.criptografar("SEGREDOTOTP"));
    payload[payload.length - 1] ^= 1;

    assertThrows(
      IllegalStateException.class,
      () -> service.descriptografar(Base64.getEncoder().encodeToString(payload))
    );
  }

  @Test
  void deveExigirChaveDe256Bits() {
    assertThrows(IllegalStateException.class, () -> new CriptografiaMfaService(""));
  }

  private String chave() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }
}
