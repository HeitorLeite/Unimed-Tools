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
import com.unimedlorena.tools.service.HospitalRelatorioService;
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

  @Test
  void preservaSessaoOpacaNoRetornoAssincronoSemLiberarRequisicoesAnonimas() throws Exception {
    var principal = new com.unimedlorena.tools.auth.UsuarioPrincipal(1, "Teste", "teste", "", "OPERACIONAL", false,
      java.util.Set.of("RELATORIOS_ACESSAR"));
    when(sessaoService.autenticar(any())).thenReturn(java.util.Optional.of(principal));
    when(exportacao.descreverArquivo("csv")).thenReturn(new ExportacaoRelatorioService.DescricaoArquivo("text/csv", "csv"));
    when(exportacao.exportarPara(anyString(), anyString(), any(), any())).thenAnswer(inv -> {
      ((java.io.OutputStream) inv.getArgument(3)).write("ID\r\n001\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return 1;
    });
    var inicio = mvc.perform(post("/api/relatorios/sgu/exportar/teste").param("formato", "csv").with(csrf()))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(inicio))
      .andExpect(status().isOk())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("ID\r\n001\r\n"));
    when(sessaoService.autenticar(any())).thenReturn(java.util.Optional.empty());
    mvc.perform(post("/api/relatorios/sgu/exportar/teste").with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/relatorios/sgu/exportar/teste").with(csrf())
      .with(user("sem-permissao").authorities(() -> "APLICACAO_ACESSAR"))).andExpect(status().isForbidden());
    mvc.perform(post("/api/relatorios/sgu/exportar/teste")
      .with(user("sem-csrf").authorities(() -> "RELATORIOS_ACESSAR"))).andExpect(status().isForbidden());
  }

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

  @MockBean
  private HospitalRelatorioService hospitalRelatorio;

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
