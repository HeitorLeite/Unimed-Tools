package com.unimedlorena.tools.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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

  @ParameterizedTest
  @MethodSource("codigosSessoesMulti")
  void deveSintetizarDescricaoDeTodosOsCodigosDeSessoesMulti(
    String codigos,
    String descricaoEsperada
  ) {
    for (String codigo : codigos.split(",")) {
      var registro = registro("SESSOES MULTI", "PRESTADOR", "CLINICA");
      registro.put("codigo_item", codigo);
      registro.put("descricao_item", "Descrição original " + codigo);
      var registros = new ArrayList<>(List.of(registro));

      int alterados = normalizer.normalizar(API_COMERCIAL, registros);

      assertThat(alterados)
        .as("código %s", codigo)
        .isEqualTo(1);
      assertThat(registros.getFirst())
        .as("código %s", codigo)
        .containsEntry("descricao_item", descricaoEsperada)
        .containsEntry("grupo_prestador", "SESSOES MULTI");
    }
  }

  static Stream<Arguments> codigosSessoesMulti() {
    return Stream.of(
      Arguments.of(
        "20103069,20103077,20103093,20103107,20103131,20103182,20103190,20103204," +
        "20103212,20103220,20103247,20103255,20103263,20103271,20103280,20103298," +
        "20103310,20103328,20103344,20103360,20103379,20103387,20103395,20103409," +
        "20103417,20103425,20103433,20103441,20103450,20103468,20103476,20103484," +
        "20103492,20103506,20103514,20103522,20103530,20103611,20103620,20103646," +
        "20103662,20103670,20103689,20103697,20103700,20103727,50000144,50000160," +
        "50000195,50000209,50000217,50000233,50000349,50000365,50000381,50000390," +
        "50000403,50000411,50000454,50000713,50000730,50000748,50000756,50000764," +
        "50000772,50000780,50000799,50000802,50000810,50000829,50001000,50001019," +
        "50001043,50001051,50001078",
        "Sessao de Fisioterapia"
      ),
      Arguments.of(
        "50000055,50000071,50000080,50000101,50000128,50000136",
        "Sessao de Terapia Ocupacional"
      ),
      Arguments.of(
        "50000470,50000489,50001183,50001191,50001221,50001230",
        "Sessao de Psicologia/Psicoterapia"
      ),
      Arguments.of(
        "50000586,50000608,50000616,50000632,50000640",
        "Sessao de Fonoaudiologia"
      ),
      Arguments.of("50000020,50000012", "Sessao de Psicomotricidade"),
      Arguments.of("50000560", "Sessao de Nutricao"),
      Arguments.of("50001213", "Sessoa de Musicoterapia")
    );
  }

  @Test
  void deveSintetizarCodigoNumericoDeSessaoMulti() {
    var registro = registro("SESSOES MULTI", "PRESTADOR", "CLINICA");
    registro.put("codigo_item", 50000470L);
    registro.put("descricao_item", "Descrição original");
    var registros = new ArrayList<>(List.of(registro));

    normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(registros.getFirst().get("descricao_item"))
      .isEqualTo("Sessao de Psicologia/Psicoterapia");
  }

  @Test
  void naoDeveSintetizarDescricaoForaDeSessoesMultiOuComCodigoDesconhecido() {
    var fora = registro("INTERCAMBIO", "PRESTADOR", "CLINICA");
    fora.put("codigo_item", "50000470");
    fora.put("descricao_item", "Descrição original");

    var desconhecido = registro("SESSOES MULTI", "PRESTADOR", "CLINICA");
    desconhecido.put("codigo_item", "99999999");
    desconhecido.put("descricao_item", "Descrição original");

    var registros = new ArrayList<>(List.of(fora, desconhecido));

    int alterados = normalizer.normalizar(API_COMERCIAL, registros);

    assertThat(alterados).isZero();
    assertThat(fora.get("descricao_item")).isEqualTo("Descrição original");
    assertThat(desconhecido.get("descricao_item")).isEqualTo("Descrição original");
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