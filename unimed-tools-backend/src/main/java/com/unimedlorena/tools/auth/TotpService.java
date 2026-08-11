package com.unimedlorena.tools.auth;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

/** Gera e valida TOTP RFC 6238 com janela máxima de um passo de 30 segundos. */
@Service
public class TotpService {

  private static final int SECRET_BYTES = 20;
  private static final long STEP_SECONDS = 30;

  private final TimeBasedOneTimePasswordGenerator generator;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Base32 base32 = new Base32();

  public TotpService() {
    this.generator = new TimeBasedOneTimePasswordGenerator();
  }

  public String gerarSegredo() {
    byte[] bytes = new byte[SECRET_BYTES];
    secureRandom.nextBytes(bytes);
    return base32.encodeToString(bytes).replace("=", "");
  }

  public String gerarUri(String login, String segredo) {
    String issuer = "Unimed Tools";
    String label = issuer + ":" + login;
    return (
      "otpauth://totp/" +
      encode(label) +
      "?secret=" +
      encode(segredo) +
      "&issuer=" +
      encode(issuer) +
      "&algorithm=SHA1&digits=6&period=30"
    );
  }

  public Long validar(String segredo, String codigo, Long ultimoPassoAceito) {
    if (codigo == null || !codigo.matches("\\d{6}")) return null;
    byte[] secretBytes = base32.decode(segredo);
    SecretKeySpec key = new SecretKeySpec(secretBytes, generator.getAlgorithm());
    long passoAtual = Instant.now().getEpochSecond() / STEP_SECONDS;

    for (long passo = passoAtual - 1; passo <= passoAtual + 1; passo++) {
      if (ultimoPassoAceito != null && passo <= ultimoPassoAceito) continue;
      String esperado;
      try {
        esperado = generator.generateOneTimePasswordString(
          key,
          Instant.ofEpochSecond(passo * STEP_SECONDS)
        );
      } catch (GeneralSecurityException ex) {
        throw new IllegalStateException("Não foi possível validar o código MFA.", ex);
      }
      if (
        MessageDigest.isEqual(
          esperado.getBytes(StandardCharsets.US_ASCII),
          codigo.getBytes(StandardCharsets.US_ASCII)
        )
      ) return passo;
    }
    return null;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
