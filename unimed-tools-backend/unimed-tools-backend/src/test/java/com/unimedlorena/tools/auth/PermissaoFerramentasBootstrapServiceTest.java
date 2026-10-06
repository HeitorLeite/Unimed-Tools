package com.unimedlorena.tools.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class PermissaoFerramentasBootstrapServiceTest {

  @Test
  void naoDeveReaplicarMigracaoLegadaQuandoPermissoesJaExistem() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var service = new PermissaoFerramentasBootstrapService(jdbc);

    service.run(mock(ApplicationArguments.class));

    verify(jdbc, never()).update(
      contains("INSERT IGNORE INTO usuario_permissao"),
      any(),
      any()
    );
  }

  @Test
  void deveMigrarSomentePermissaoOperacionalCriadaNaInicializacao() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(
      contains("INSERT INTO permissao"),
      eq("COMERCIAL_ACESSAR"),
      eq("COMERCIAL"),
      anyString(),
      eq("COMERCIAL_ACESSAR")
    )).thenReturn(1);
    var service = new PermissaoFerramentasBootstrapService(jdbc);

    service.run(mock(ApplicationArguments.class));

    verify(jdbc).update(
      contains("INSERT IGNORE INTO usuario_permissao"),
      eq("COMERCIAL_ACESSAR"),
      eq("RELATORIOS_ACESSAR")
    );
    verify(jdbc, never()).update(
      contains("INSERT IGNORE INTO usuario_permissao"),
      eq("ASSISTENCIAL_ACESSAR"),
      eq("RELATORIOS_ACESSAR")
    );
  }
}
