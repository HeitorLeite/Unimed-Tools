/*
 * Responsabilidade: Verifica contratos críticos da integração SGU sem acessar o serviço externo.
 */
package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unimedlorena.tools.exception.ApiException;
import org.springframework.http.HttpStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SguRelatorioServiceTest {

  @Test
  void deveSanitizarFalhasTemporariasSemRepetirPublicacao() {
    for (HttpStatus status : java.util.List.of(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)) {
      RestClient.Builder builder = RestClient.builder();
      var server = MockRestServiceServer.bindTo(builder).build();
      var service = new SguRelatorioService(builder, new ObjectMapper(),
        "https://sgu.example.com", "segredo-teste", "apikey", "/api", "/api");
      server.expect(requestTo("https://sgu.example.com/api/ins_atu_query_api"))
        .andRespond(withStatus(status).contentType(MediaType.TEXT_HTML)
          .body("<html>504 Gateway Time-out detalhe-interno</html>"));
      assertThatThrownBy(() -> service.criarOuAtualizar(Map.of("nome", "api-teste")))
        .isInstanceOfSatisfying(ApiException.class, ex -> {
          assertThat(ex.status()).isEqualTo(status);
          assertThat(ex.codigo()).isEqualTo(status == HttpStatus.GATEWAY_TIMEOUT ? "SGU_TIMEOUT" : "SGU_INDISPONIVEL");
          assertThat(ex.getCause()).isNull();
        })
        .hasMessageNotContaining("<html>").hasMessageNotContaining("detalhe-interno");
      server.verify();
    }
  }

  @Test
  void deveEnviarHeaderConfiguravelParaOsgu() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(
      builder
    ).build();

    SguRelatorioService service = new SguRelatorioService(
      builder,
      new ObjectMapper(),
      "https://sgu.example.com",
      "segredo-teste",
      "apikey,x-api-key",
      "/api/procedure",
      "/api/procedure"
    );

    server
      .expect(
        requestTo("https://sgu.example.com/api/procedure/lista_query_api")
      )
      .andExpect(header("x-api-key", "segredo-teste"))
      .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

    Map<String, Object> resposta = service.listar("relatorio");

    assertThat(resposta).containsEntry("ok", true);
    server.verify();
  }

  @Test
  void deveOcultarSqlRetornadoEmErroPeloSgu() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    SguRelatorioService service = new SguRelatorioService(
      builder,
      new ObjectMapper(),
      "https://sgu.example.com",
      "segredo-teste",
      "apikey",
      "/api/procedure",
      "/api/procedure"
    );

    server
      .expect(requestTo("https://sgu.example.com/api/procedure/ins_atu_query_api"))
      .andRespond(
        withBadRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"message\":\"Informação conteudoFiltro:and CASE segredo_sql inválida\"}")
      );

    assertThatThrownBy(() -> service.criarOuAtualizar(Map.of("nome", "api-teste")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("O SGU rejeitou a definição SQL da API de relatório.")
      .hasMessageNotContaining("CASE")
      .hasMessageNotContaining("segredo_sql")
      .hasMessageNotContaining("conteudoFiltro");
    server.verify();
  }

  @Test
  void devePreservarCodigoOracleSemExporSqlRetornadoPeloSgu() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    SguRelatorioService service = new SguRelatorioService(
      builder,
      new ObjectMapper(),
      "https://sgu.example.com",
      "segredo-teste",
      "apikey",
      "/api/procedure",
      "/api/procedure"
    );

    server
      .expect(requestTo("https://sgu.example.com/api/procedure/ins_atu_query_api"))
      .andRespond(
        withBadRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"message\":\"consultaSQL inválida: ORA-00918; SELECT segredo\"}")
      );

    assertThatThrownBy(() -> service.criarOuAtualizar(Map.of("nome", "api-teste")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("O SGU rejeitou a definição SQL da API de relatório. Código retornado: ORA-00918.")
      .hasMessageNotContaining("SELECT")
      .hasMessageNotContaining("segredo");
    server.verify();
  }
}
