package com.unimedlorena.tools.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

  @Test
  void deveValidarCodigoAtualEImpedirReutilizacao() throws Exception {
    TotpService service = new TotpService();
    String segredo = service.gerarSegredo();
    var generator = new TimeBasedOneTimePasswordGenerator();
    var key = new SecretKeySpec(new Base32().decode(segredo), generator.getAlgorithm());
    String codigo = generator.generateOneTimePasswordString(key, Instant.now());

    Long passo = service.validar(segredo, codigo, null);

    assertNotNull(passo);
    assertNull(service.validar(segredo, codigo, passo));
    assertTrue(service.gerarUri("admin", segredo).startsWith("otpauth://totp/"));
  }

  @Test
  void deveRejeitarCodigoMalformado() {
    TotpService service = new TotpService();
    assertNull(service.validar(service.gerarSegredo(), "12345a", null));
  }
}
