package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CriptografiaAgendamentoServiceTest {

  @Test
  void deveCriptografarComContextoSemExporConfiguracao() {
    String chave = Base64.getEncoder().encodeToString(new byte[32]);
    var service = new CriptografiaAgendamentoService(chave);

    String protegido = service.criptografar("{\"filtro\":\"sintetico\"}", "agenda:1:10");

    assertThat(protegido).doesNotContain("sintetico", "filtro");
    assertThat(service.descriptografar(protegido, "agenda:1:10"))
      .isEqualTo("{\"filtro\":\"sintetico\"}");
    assertThatThrownBy(() -> service.descriptografar(protegido, "agenda:1:11"))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void devePermanecerIndisponivelSemChave() {
    var service = new CriptografiaAgendamentoService("");

    assertThat(service.configurada()).isFalse();
    assertThatThrownBy(() -> service.criptografar("teste", "contexto"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("indisponível");
  }
}
