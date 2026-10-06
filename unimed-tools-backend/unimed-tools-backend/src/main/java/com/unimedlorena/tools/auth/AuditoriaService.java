package com.unimedlorena.tools.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Persiste somente metadados sanitizados dos eventos de segurança. */
@Service
public class AuditoriaService {

  private final AuthRepository repository;
  private final ObjectMapper objectMapper;

  public AuditoriaService(AuthRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public void registrar(
    Long executorId,
    Long alvoId,
    String evento,
    String resultado,
    String ip,
    String userAgent,
    Map<String, ?> detalhes
  ) {
    try {
      repository.auditar(
        executorId,
        alvoId,
        evento,
        resultado,
        limitar(ip, 45),
        limitar(userAgent, 500),
        objectMapper.writeValueAsString(detalhes == null ? Map.of() : detalhes)
      );
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Não foi possível serializar o evento de auditoria.", ex);
    }
  }

  public static String limitar(String valor, int tamanho) {
    if (valor == null || valor.isBlank()) return null;
    return valor.length() <= tamanho ? valor : valor.substring(0, tamanho);
  }
}
