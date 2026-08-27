package com.unimedlorena.tools.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Protege filtros e opções agendadas com uma chave exclusiva e contexto autenticado. */
@Service
public class CriptografiaAgendamentoService {

  private static final byte VERSION = 1;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public CriptografiaAgendamentoService(
    @Value("${relatorios.agendamento.encryption-key:}") String encryptionKey
  ) {
    String valor = encryptionKey == null ? "" : encryptionKey.trim();
    if (valor.isBlank()) {
      this.key = null;
      return;
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(valor);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException(
        "REPORT_SCHEDULE_ENCRYPTION_KEY deve estar em Base64.",
        ex
      );
    }
    if (decoded.length != 32) {
      throw new IllegalStateException(
        "REPORT_SCHEDULE_ENCRYPTION_KEY deve conter exatamente 32 bytes em Base64."
      );
    }
    this.key = new SecretKeySpec(decoded, "AES");
  }

  public boolean configurada() {
    return key != null;
  }

  public String criptografar(String valor, String contexto) {
    validarConfigurada();
    try {
      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
      byte[] encrypted = cipher.doFinal(valor.getBytes(StandardCharsets.UTF_8));
      byte[] output = new byte[1 + iv.length + encrypted.length];
      output[0] = VERSION;
      System.arraycopy(iv, 0, output, 1, iv.length);
      System.arraycopy(encrypted, 0, output, 1 + iv.length, encrypted.length);
      return Base64.getEncoder().encodeToString(output);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Não foi possível proteger o agendamento.", ex);
    }
  }

  public String descriptografar(String valor, String contexto) {
    validarConfigurada();
    try {
      byte[] input = Base64.getDecoder().decode(valor);
      if (input.length <= 1 + IV_BYTES || input[0] != VERSION) {
        throw new IllegalArgumentException("Formato de agendamento inválido.");
      }
      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(input, 1, iv, 0, IV_BYTES);
      byte[] encrypted = new byte[input.length - 1 - IV_BYTES];
      System.arraycopy(input, 1 + IV_BYTES, encrypted, 0, encrypted.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw new IllegalStateException("Não foi possível ler o agendamento protegido.", ex);
    }
  }

  private void validarConfigurada() {
    if (key == null) {
      throw new IllegalStateException(
        "O agendamento está indisponível até REPORT_SCHEDULE_ENCRYPTION_KEY ser configurada."
      );
    }
  }
}
