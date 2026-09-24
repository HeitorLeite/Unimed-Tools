package com.unimedlorena.tools.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthRepositoryTest {

  @Test
  void deveUsarInsertValuesCompativelComMariaDbDoXampp() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    AuthRepository repository = new AuthRepository(jdbc);
    when(jdbc.queryForObject(
      "SELECT id FROM permissao WHERE codigo = ? AND ativo = TRUE",
      Long.class,
      "XML_ACESSAR"
    )).thenReturn(6L);

    repository.substituirPermissoesUsuario(3L, Set.of("XML_ACESSAR"), 2L);

    verify(jdbc).update("DELETE FROM usuario_permissao WHERE usuario_id = ?", 3L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Object[]>> lotes = ArgumentCaptor.forClass(List.class);
    verify(jdbc).batchUpdate(contains("VALUES (?, ?, ?)"), lotes.capture());
    assertThat(lotes.getValue()).hasSize(1);
    assertThat(lotes.getValue().getFirst()).containsExactly(3L, 6L, 2L);
  }
}
