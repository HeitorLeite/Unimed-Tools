package com.unimedlorena.tools.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  @Test
  void deveTratarArgumentoInvalidoSemResponderErroInterno() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    ResponseEntity<Map<String, String>> resposta = handler.handleIllegalArgument(
      new IllegalArgumentException("Definição SQL inválida.")
    );

    assertThat(resposta.getStatusCode().value()).isEqualTo(400);
    assertThat(resposta.getBody()).containsEntry("codigo", "SOLICITACAO_INVALIDA");
    assertThat(resposta.getBody()).containsEntry("message", "Definição SQL inválida.");
  }
}
