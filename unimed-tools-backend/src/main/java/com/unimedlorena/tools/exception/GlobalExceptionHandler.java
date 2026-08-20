package com.unimedlorena.tools.exception;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Devolve erros sanitizados e mantém o diagnóstico interno correlacionável. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, String>> handleApi(ApiException ex) {
    return ResponseEntity.status(ex.status()).body(
      Map.of("codigo", ex.codigo(), "message", ex.getMessage())
    );
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
      .findFirst()
      .map(error -> "Revise o campo " + error.getField() + ".")
      .orElse("Revise os dados enviados.");
    return ResponseEntity.badRequest().body(Map.of("codigo", "DADOS_INVALIDOS", "message", message));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(
    IllegalArgumentException ex
  ) {
    return ResponseEntity.badRequest().body(
      Map.of("codigo", "SOLICITACAO_INVALIDA", "message", ex.getMessage())
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handle(Exception ex) {
    String correlationId = UUID.randomUUID().toString();
    logger.error("Falha não tratada. correlationId={}", correlationId, ex);
    return ResponseEntity.internalServerError().body(
      Map.of(
        "codigo", "ERRO_INTERNO",
        "message", "Não foi possível concluir a operação.",
        "correlationId", correlationId
      )
    );
  }
}
