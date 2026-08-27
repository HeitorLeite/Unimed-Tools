/*
 * Responsabilidade: Inicializa o contexto Spring Boot do backend Unimed Tools.
 */
package com.unimedlorena.tools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UnimedToolsApplication {

  public static void main(String[] args) {
    SpringApplication.run(UnimedToolsApplication.class, args);
  }
}
