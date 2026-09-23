package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.unimedlorena.tools.auth.*;
import com.unimedlorena.tools.dto.FusexSpaRequest;
import com.unimedlorena.tools.exception.ApiException;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FusexSpaServiceTest {
  @Test
  void normalizaSemPerderPrecisaoERejeitaEntradasInvalidas() {
    assertThat(FusexSpaService.normalizar("001, 2\r\n1\n9007199254740993"))
        .containsExactly("1", "2", "9007199254740993");
    for (String texto : List.of("", ",\n", "1.5", "1e3", "-1", "1; UPDATE X", "1 2", "9223372036854775808", "1".repeat(20001))) {
      assertThatThrownBy(() -> FusexSpaService.normalizar(texto)).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> FusexSpaService.normalizar(IntStream.rangeClosed(1, 1001)
        .mapToObj(Integer::toString).collect(Collectors.joining(",")))).hasMessageContaining("1.000");
  }

  @Test
  void exigeConfirmacaoEBloqueiaSemSimularAfetados() {
    var auditoria = mock(AuditoriaService.class);
    var service = new FusexSpaService(auditoria);
    var principal = new UsuarioPrincipal(1, "Teste", "teste", "", "OPERACIONAL", false, Set.of("FUSEX_SPA_VALORIZAR"));
    assertThat(service.validar(new FusexSpaRequest("1,1,2", false)).execucaoDisponivel()).isFalse();
    assertThatThrownBy(() -> service.executar(new FusexSpaRequest("1", false), principal)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(auditoria);
    assertThatThrownBy(() -> service.executar(new FusexSpaRequest("1,2", true), principal))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.status()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
          assertThat(ex.codigo()).isEqualTo("SGU_DML_NAO_SUPORTADO");
        });
    verify(auditoria).registrar(1L, null, "FUSEX_SPA_VALORIZAR", "BLOQUEADO", null, null,
        Map.of("quantidadeGuias", 2, "motivo", "SGU_DML_NAO_SUPORTADO"));
  }
}
