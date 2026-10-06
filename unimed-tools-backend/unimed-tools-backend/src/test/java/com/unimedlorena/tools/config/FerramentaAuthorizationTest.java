package com.unimedlorena.tools.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unimedlorena.tools.auth.SessaoService;
import com.unimedlorena.tools.controller.FerramentaController;
import com.unimedlorena.tools.dto.FerramentaDtos;
import com.unimedlorena.tools.service.FerramentaConfiguravelService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FerramentaController.class)
@Import({ SecurityConfig.class, SessaoAuthenticationFilter.class })
class FerramentaAuthorizationTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private SessaoService sessaoService;

  @MockBean
  private FerramentaConfiguravelService service;

  @Test
  void devePermitirLerConfiguracoesNativasQuandoAutenticado() throws Exception {
    mvc.perform(get("/api/ferramentas/nativas").with(user("operacional")))
      .andExpect(status().isOk());
  }

  @Test
  void deveNegarEdicaoNativaSemPermissaoAdministrativa() throws Exception {
    mvc.perform(
        put("/api/ferramentas/nativas/comercial")
          .with(user("operacional").authorities(() -> "RELATORIOS_ACESSAR"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "nome": "Comercial",
              "descricao": "Descrição",
              "ativo": true
            }
            """)
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void devePermitirEdicaoNativaComPermissaoAdministrativa() throws Exception {
    when(service.salvarNativa(anyString(), any(), any())).thenReturn(
      new FerramentaDtos.NativaResponse(
        "comercial",
        "Comercial",
        "Descrição",
        true,
        LocalDateTime.now()
      )
    );

    mvc.perform(
        put("/api/ferramentas/nativas/comercial")
          .with(user("admin").authorities(() -> "FERRAMENTAS_ADMINISTRAR"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "nome": "Comercial",
              "descricao": "Descrição",
              "ativo": true
            }
            """)
      )
      .andExpect(status().isOk());
  }
}
