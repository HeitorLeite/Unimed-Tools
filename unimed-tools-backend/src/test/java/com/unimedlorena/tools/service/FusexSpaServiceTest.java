package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.unimedlorena.tools.auth.AuditoriaService;
import com.unimedlorena.tools.auth.UsuarioPrincipal;
import com.unimedlorena.tools.dto.FusexSpaRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FusexSpaServiceTest {
  @Test
  void normalizaSemPerderPrecisaoERejeitaEntradasInvalidas() {
    assertThat(FusexSpaService.normalizar("001, 2\r\n1\n9007199254740993"))
        .containsExactly("1", "2", "9007199254740993");
    for (String texto : List.of(
        "", ",\n", "1.5", "1e3", "-1", "1; UPDATE X", "1 2",
        "9223372036854775808", "1".repeat(20001))) {
      assertThatThrownBy(() -> FusexSpaService.normalizar(texto))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> FusexSpaService.normalizar(
        IntStream.rangeClosed(1, 1001)
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(","))))
        .hasMessageContaining("1.000");
  }

  @Test
  void exigeConfirmacaoEExecutaApiReservada() {
    var auditoria = mock(AuditoriaService.class);
    var sgu = mock(SguRelatorioService.class);
    var service = new FusexSpaService(auditoria, sgu, FusexSpaService.API_NOME);
    var principal = new UsuarioPrincipal(
        1, "Teste", "teste", "", "OPERACIONAL", false, Set.of("FUSEX_SPA_VALORIZAR"));

    assertThat(service.validar(new FusexSpaRequest("1,1,2", false)).execucaoDisponivel())
        .isTrue();

    assertThatThrownBy(() -> service.executar(new FusexSpaRequest("1", false), principal))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(sgu, auditoria);

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of("success", true));
    when(sgu.executar(anyString(), anyMap())).thenReturn(Map.of("success", true));

    service.executar(new FusexSpaRequest("1,2", true), principal);

    @SuppressWarnings("rawtypes")
    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(sgu).criarOuAtualizar(captor.capture());
    Map<?, ?> definicao = captor.getValue();
    assertThat(definicao.get("nome")).isEqualTo(FusexSpaService.API_NOME);
    assertThat(String.valueOf(definicao.get("consultaSQL")))
        .contains("i.guia_cod_id IN (1, 2)")
        .contains("COMMIT;");
    assertThat(definicao.get("filtros")).isEqualTo(List.of());

    verify(sgu).executar(FusexSpaService.API_NOME, Map.of());
    verify(auditoria).registrar(
        1L, null, "FUSEX_SPA_VALORIZAR", "SUCESSO", null, null,
        Map.of("quantidadeGuias", 2, "apiNome", FusexSpaService.API_NOME));
  }

  @Test
  void auditaFalhaSemRegistrarIds() {
    var auditoria = mock(AuditoriaService.class);
    var sgu = mock(SguRelatorioService.class);
    var service = new FusexSpaService(auditoria, sgu, FusexSpaService.API_NOME);
    var principal = new UsuarioPrincipal(
        1, "Teste", "teste", "", "OPERACIONAL", false, Set.of("FUSEX_SPA_VALORIZAR"));

    when(sgu.criarOuAtualizar(anyMap())).thenReturn(Map.of("success", true));
    when(sgu.executar(anyString(), anyMap())).thenThrow(new IllegalStateException("falha"));

    assertThatThrownBy(() -> service.executar(new FusexSpaRequest("10,20", true), principal))
        .isInstanceOf(IllegalStateException.class);

    verify(auditoria).registrar(
        1L, null, "FUSEX_SPA_VALORIZAR", "FALHA", null, null,
        Map.of(
            "quantidadeGuias", 2,
            "apiNome", FusexSpaService.API_NOME,
            "etapa", "EXECUCAO"));
  }
}
