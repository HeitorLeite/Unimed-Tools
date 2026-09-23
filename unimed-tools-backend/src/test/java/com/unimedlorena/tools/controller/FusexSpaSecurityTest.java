package com.unimedlorena.tools.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.unimedlorena.tools.auth.*;
import com.unimedlorena.tools.config.*;
import com.unimedlorena.tools.service.FusexSpaService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FusexSpaController.class)
@Import({SecurityConfig.class, SessaoAuthenticationFilter.class, FusexSpaService.class})
class FusexSpaSecurityTest {
  @Autowired MockMvc mvc;
  @MockBean SessaoService sessoes;
  @MockBean AuditoriaService auditoria;

  @Test
  void exigeSessaoPermissaoPropriaECsrf() throws Exception {
    when(sessoes.autenticar(any())).thenReturn(Optional.empty());
    for (String endpoint : List.of("validar", "executar")) {
      var url = "/api/fusex-spa/" + endpoint;
      mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"guias\":\"1\"}"))
          .andExpect(status().isUnauthorized());
      mvc.perform(post(url).with(user("teste").authorities(new SimpleGrantedAuthority("RELATORIOS_ACESSAR")))
          .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"guias\":\"1\"}"))
          .andExpect(status().isForbidden());
      mvc.perform(post(url).with(user("teste").authorities(new SimpleGrantedAuthority("FUSEX_SPA_VALORIZAR")))
          .contentType(MediaType.APPLICATION_JSON).content("{\"guias\":\"1\"}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(auditoria);
  }

  @Test
  void validacaoNaoExecutaEConfirmacaoRetornaBloqueioExplicito() throws Exception {
    var principal = new UsuarioPrincipal(1, "Teste", "teste", "", "OPERACIONAL", false, Set.of("FUSEX_SPA_VALORIZAR"));
    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("FUSEX_SPA_VALORIZAR")));
    mvc.perform(post("/api/fusex-spa/validar").with(authentication(auth)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"guias\":\"1,1,2\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.quantidadeGuias").value(2))
        .andExpect(jsonPath("$.execucaoDisponivel").value(false)).andExpect(jsonPath("$.itensAfetados").doesNotExist());
    mvc.perform(post("/api/fusex-spa/executar").with(authentication(auth)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"guias\":\"1\",\"confirmado\":false}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/fusex-spa/executar").with(authentication(auth)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"guias\":\"1\",\"confirmado\":true}"))
        .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.codigo").value("SGU_DML_NAO_SUPORTADO"));
  }
}
