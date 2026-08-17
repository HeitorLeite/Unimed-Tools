package com.unimedlorena.tools.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unimedlorena.tools.auth.SessaoService;
import com.unimedlorena.tools.controller.RelatorioController;
import com.unimedlorena.tools.service.ExportacaoLoteRelatorioService;
import com.unimedlorena.tools.service.ExportacaoRelatorioService;
import com.unimedlorena.tools.service.RelatorioPersonalizadoService;
import com.unimedlorena.tools.service.SguRelatorioService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RelatorioController.class)
@Import({ SecurityConfig.class, SessaoAuthenticationFilter.class })
class RelatorioAuthorizationTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private SessaoService sessaoService;

  @MockBean
  private SguRelatorioService sgu;

  @MockBean
  private ExportacaoRelatorioService exportacao;

  @MockBean
  private ExportacaoLoteRelatorioService exportacaoLote;

  @MockBean
  private RelatorioPersonalizadoService relatorioPersonalizado;

  @BeforeEach
  void prepararRespostasSgu() {
    when(sgu.criarOuAtualizar(any())).thenReturn(Map.of());
    when(sgu.apagar(anyString())).thenReturn(Map.of());
  }

  @Test
  void devePermitirImportarSqlComAcessoAoModulo() throws Exception {
    mvc.perform(
        post("/api/relatorios/sgu/criar")
          .with(user("operacional").authorities(() -> "RELATORIOS_ACESSAR"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isOk());
  }

  @Test
  void devePermitirExcluirApiComAcessoAoModulo() throws Exception {
    mvc.perform(
        delete("/api/relatorios/sgu/relatorio-teste")
          .with(user("operacional").authorities(() -> "RELATORIOS_ACESSAR"))
          .with(csrf())
      )
      .andExpect(status().isOk());
  }

  @Test
  void deveNegarImportacaoSemAcessoAoModulo() throws Exception {
    mvc.perform(
        post("/api/relatorios/sgu/criar")
          .with(user("operacional").authorities(() -> "APLICACAO_ACESSAR"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
  }
}
