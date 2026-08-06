/*
 * Responsabilidade: Converte falhas não tratadas em respostas JSON compreensíveis pelos clientes HTTP.
 */
package com.unimedlorena.tools.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mantém uma resposta de erro JSON uniforme para exceções não tratadas pelos
 * controllers. Isso permite que o frontend apresente a mensagem retornada pelo
 * backend mesmo quando a resposta esperada seria um arquivo binário.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Registra o diagnóstico completo no servidor e retorna somente o tipo e a
   * mensagem da exceção ao cliente. Dados de arquivos e credenciais não devem
   * ser incluídos manualmente nessa resposta.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handle(Exception ex) {
    ex.printStackTrace();
    return ResponseEntity.internalServerError().body(
      Map.of("message", ex.getClass().getSimpleName() + ": " + ex.getMessage())
    );
  }
}
