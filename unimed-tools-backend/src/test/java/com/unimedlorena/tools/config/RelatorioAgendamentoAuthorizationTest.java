package com.unimedlorena.tools.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unimedlorena.tools.auth.SessaoService;
import com.unimedlorena.tools.controller.RelatorioAgendamentoController;
import com.unimedlorena.tools.dto.RelatorioAgendamentoDtos;
import com.unimedlorena.tools.service.RelatorioAgendamentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RelatorioAgendamentoController.class)
@Import({ SecurityConfig.class, SessaoAuthenticationFilter.class })
class RelatorioAgendamentoAuthorizationTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private SessaoService sessaoService;

  @MockBean
  private RelatorioAgendamentoService service;

  @Test
  void devePermitirConsultarConfiguracaoComAcessoAosRelatorios() throws Exception {
    when(service.configuracao()).thenReturn(
      new RelatorioAgendamentoDtos.ConfiguracaoResponse(true, 30, 90, "teste")
    );

    mvc.perform(
      get("/api/relatorios/agendamentos/configuracao")
        .with(user("operacional").authorities(() -> "RELATORIOS_ACESSAR"))
    ).andExpect(status().isOk());
  }

  @Test
  void deveNegarAgendamentosSemPermissaoDoModulo() throws Exception {
    mvc.perform(
      get("/api/relatorios/agendamentos/configuracao")
        .with(user("operacional").authorities(() -> "APLICACAO_ACESSAR"))
    ).andExpect(status().isForbidden());
  }
}
