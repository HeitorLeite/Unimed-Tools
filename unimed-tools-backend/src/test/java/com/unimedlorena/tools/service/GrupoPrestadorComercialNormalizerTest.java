package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GrupoPrestadorComercialNormalizerTest {

  private static final String API_COMERCIAL = "0090-despesa-empresas";
  private final GrupoPrestadorComercialNormalizer normalizer =
    new GrupoPrestadorComercialNormalizer();

  @ParameterizedTest
  @MethodSource("regrasPorNome")
  void deveAplicarMapaOrdenadoPorNomeDoPrestador(
    String nomePrestador,
    String esperado
  ) {
    var registros = new ArrayList<>(List.of(
      registro("MEDICA NAO COOPERADO", nomePrestador, "CLINICA")
    ));

    int alterados = normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(alterados).isEqualTo(1);
    assertThat(registros.getFirst())
      .containsEntry("grupo_prestador", esperado)
      .containsEntry("nome_prestador", nomePrestador)
      .containsEntry("tipo_prestador", "CLINICA");
  }

  static Stream<Arguments> regrasPorNome() {
    return Stream.of(
      Arguments.of("UNIMED DE LORENA COOP TRAB MEDICO", "RECURSO PROPRIO"),
      Arguments.of("CLINICA FISIOCLINICA LTDA", "SESSOES MULTI"),
      Arguments.of("MBA GUARANY LTDA", "SESSOES MULTI"),
      Arguments.of("NUTRISAUDE LTDA", "SESSOES MULTI"),
      Arguments.of("VTALLIS CLINICA", "SESSOES MULTI"),
      Arguments.of("CAVALCA DIAGNOSTICOS", "CLINICA DE IMAGEM"),
      Arguments.of("CLINICA KARINE LTDA", "CLINICA MEDICA"),
      Arguments.of("Fundação João Paulo II", "CLINICA MEDICA"),
      Arguments.of("L2A SERVICOS MEDICOS", "CLINICA MEDICA"),
      Arguments.of("PRONTOCOR LTDA", "CLINICA MEDICA"),
      Arguments.of("SOCIEDADE DE OFTALMOLOGIA LTDA", "CLINICA MEDICA"),
      Arguments.of("PRESTADOR DE REEMBOLSO", "REEMBOLSO")
    );
  }

  @Test
  void deveClassificarOpmeIndependentementeDoNome() {
    var registros = new ArrayList<>(List.of(
      registro("MEDICA NAO COOPERADO", "QUALQUER PRESTADOR", "OPME")
    ));

    normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(registros.getFirst().get("grupo_prestador")).isEqualTo("OPME");
  }

  @Test
  void opmeDeveTerPrioridadeSobreTodasAsRegrasDeNome() {
    var registros = new ArrayList<>(List.of(
      registro("MEDICA NAO COOPERADO", "FUNDACAO JOAO PAULO II", "OPME")
    ));

    normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(registros.getFirst())
      .containsEntry("grupo_prestador", "OPME")
      .containsEntry("nome_prestador", "FUNDACAO JOAO PAULO II")
      .containsEntry("tipo_prestador", "OPME");
  }

  @Test
  void deveAceitarAcentosPontuacaoEspacosEFlexaoDoGrupoReal() {
    var registros = new ArrayList<>(List.of(
      registro("  MÉDICOS  NÃO-COOPERADOS ", "UNIMED, DE LORENA", "CLINICA")
    ));

    normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(registros.getFirst().get("grupo_prestador"))
      .isEqualTo("RECURSO PROPRIO");
  }

  @Test
  void naoDeveAlterarGrupoJaClassificado() {
    var registros = new ArrayList<>(List.of(
      registro("MEDICOS COOPERADOS", "FUNDACAO JOAO PAULO II", "CLINICA")
    ));

    int alterados = normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(alterados).isZero();
    assertThat(registros.getFirst().get("grupo_prestador"))
      .isEqualTo("MEDICOS COOPERADOS");
  }

  @Test
  void deveManterGrupoElegivelQuandoPrestadorForDesconhecido() {
    var registros = new ArrayList<>(List.of(
      registro("MEDICA NAO COOPERADO", "PRESTADOR DESCONHECIDO", "CLINICA")
    ));

    int alterados = normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(alterados).isZero();
    assertThat(registros.getFirst().get("grupo_prestador"))
      .isEqualTo("MEDICA NAO COOPERADO");
  }

  @Test
  void l2aDeveExigirLimitesDePalavra() {
    var registros = new ArrayList<>(List.of(
      registro("MEDICA NAO COOPERADO", "PRESTADOR XL2ABC SERVICOS", "CLINICA")
    ));

    normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(registros.getFirst().get("grupo_prestador"))
      .isEqualTo("MEDICA NAO COOPERADO");
  }

  @Test
  void naoDeveAtuarForaDasApisDaFerramentaComercial() {
    var registros = new ArrayList<>(List.of(
      registro("MEDICA NAO COOPERADO", "CAVALCA", "CLINICA")
    ));

    normalizer.normalizar("api-assistencial", registros);

    assertThat(registros.getFirst().get("grupo_prestador"))
      .isEqualTo("MEDICA NAO COOPERADO");
  }

  @Test
  void devePreservarQuantidadeNomeTipoERegistrosNaoElegiveis() {
    var elegivel = registro("MEDICA NAO COOPERADO", "VTALLIS LTDA", "CLINICA");
    var classificado = registro("INTERCAMBIO", "VTALLIS LTDA", "CLINICA");
    var elegivelAntes = new LinkedHashMap<>(elegivel);
    var classificadoAntes = new LinkedHashMap<>(classificado);
    var registros = new ArrayList<>(List.of(elegivel, classificado));

    normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(registros).hasSize(2);
    assertThat(registros.get(0))
      .containsEntry("grupo_prestador", "SESSOES MULTI")
      .containsEntry("nome_prestador", "VTALLIS LTDA")
      .containsEntry("tipo_prestador", "CLINICA")
      .containsEntry("nome_especialidade", "CARDIOLOGIA");
    elegivelAntes.forEach((coluna, valor) -> {
      if (!"grupo_prestador".equals(coluna)) {
        assertThat(registros.get(0).get(coluna)).isEqualTo(valor);
      }
    });
    assertThat(registros.get(1)).isEqualTo(classificadoAntes);
  }

  private static LinkedHashMap<String, Object> registro(
    String grupo,
    String nome,
    String tipo
  ) {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("grupo_prestador", grupo);
    registro.put("nome_prestador", nome);
    registro.put("tipo_prestador", tipo);
    registro.put("nome_especialidade", "CARDIOLOGIA");
    registro.put("valor_total", "123,45");
    return registro;
  }
}
