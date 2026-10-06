package com.unimedlorena.tools.exception;

import org.springframework.http.HttpStatus;

/** Falha esperada cujo código e mensagem podem ser enviados ao cliente. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String codigo;

  public ApiException(HttpStatus status, String codigo, String message) {
    super(message);
    this.status = status;
    this.codigo = codigo;
  }

  public HttpStatus status() {
    return status;
  }

  public String codigo() {
    return codigo;
  }
}
