package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

class EspecialidadeRelatorioResolverTest {

  private final EspecialidadeRelatorioResolver resolver =
    new EspecialidadeRelatorioResolver();

  @ParameterizedTest
  @MethodSource("nomesObrigatorios")
  void deveAplicarMapaDeNomes(String entrada, String esperado) {
    var registros = new ArrayList<>(List.of(registro("A", "1", "01/01/2026", entrada, "", "")));
    resolver.normalizar(registros);
    assertThat(registros.getFirst().get("NOME_ESPECIALIDADE")).isEqualTo(esperado);
  }

  static Stream<Arguments> nomesObrigatorios() {
    return Stream.of(
      Arguments.of("ADMINISTRAÇÃO EM SAÚDE", "CLINICO"),
      Arguments.of("CIRURGIA DO TRAUMA", "ORTOPEDIA E TRAUMATOLOGIA"),
      Arguments.of("GINECOLOGIA E OBSTETRÍCIA", "OBSTETRICIA"),
      Arguments.of("CONSULTA EM GINECOLOGIA", "GINECOLOGIA"),
      Arguments.of("RECANALIZAÇÃO TUBÁRIA", "GINECOLOGIA"),
      Arguments.of("OBSTETRÍCIA", "OBSTETRICIA"),
      Arguments.of("MEDICINA DE EMERGÊNCIA", "CLINICO"),
      Arguments.of("PRONTO SOCORRO", "CLINICO"),
      Arguments.of("CIRURGIA CARDIOVASCULAR", "CARDIOLOGIA"),
      Arguments.of("NEUROLOGIA", "NEUROLOGIA"),
      Arguments.of("NEUROCIRURGIA", "NEUROCIRURGIA"),
      Arguments.of("TRATAMENTO CIRÚRGICO DA EPILEP", "NEUROCIRURGIA"),
      Arguments.of("NEUROLOGIA PEDIATRICA", "NEUROLOGIA PEDIATRICA"),
      Arguments.of("Clínica de Oftalmologia", "OFTALMOLOGIA"),
      Arguments.of("CIRURGIA DE IMPLANTE COCLEAR", "OTORRINOLARINGOLOGIA"),
      Arguments.of("PEDIATRIA", "PEDIATRIA"),
      Arguments.of("CIRURGIA PEDIATRICA", "CIRURGIA PEDIATRICA")
    );
  }

  @ParameterizedTest
  @MethodSource("cidsResiduaisAprovados")
  void deveAplicarMapaCidResidualComOuSemPontuacao(String cid, String esperado) {
    var registros = new ArrayList<>(List.of(
      registro("A", cid, "01/01/2026", "CLINICO", "HEMOGRAMA", cid)
    ));

    resolver.normalizar(registros);

    assertThat(registros.getFirst().get("NOME_ESPECIALIDADE")).isEqualTo(esperado);
    assertThat(registros.getFirst().get("CID")).isEqualTo(cid);
  }

  static Stream<Arguments> cidsResiduaisAprovados() {
    return Stream.of(
      Arguments.of("K804", "GASTROENTEROLOGIA"),
      Arguments.of("K80.4", "GASTROENTEROLOGIA"),
      Arguments.of(" k80.4 ", "GASTROENTEROLOGIA"),
      Arguments.of("K573", "GASTROENTEROLOGIA"),
      Arguments.of("K57.3", "GASTROENTEROLOGIA"),
      Arguments.of("K40", "CIRURGIA GERAL"),
      Arguments.of("N390", "UROLOGIA"),
      Arguments.of("N39.0", "UROLOGIA"),
      Arguments.of("K801", "CIRURGIA GERAL"),
      Arguments.of("K80.1", "CIRURGIA GERAL"),
      Arguments.of("S829", "ORTOPEDIA E TRAUMATOLOGIA"),
      Arguments.of("S82.9", "ORTOPEDIA E TRAUMATOLOGIA"),
      Arguments.of("O809", "OBSTETRICIA"),
      Arguments.of("O80.9", "OBSTETRICIA"),
      Arguments.of("S729", "ORTOPEDIA E TRAUMATOLOGIA"),
      Arguments.of("S72.9", "ORTOPEDIA E TRAUMATOLOGIA")
    );
  }

  @ParameterizedTest
  @MethodSource("descricoesResiduaisObrigatorias")
  void deveAplicarRegrasResiduaisOrdenadas(String descricao, String esperado) {
    var registros = new ArrayList<>(List.of(
      registro("A", descricao, "01/01/2026", "CLINICO", descricao, "")
    ));

    resolver.normalizar(registros);

    assertThat(registros.getFirst().get("NOME_ESPECIALIDADE")).isEqualTo(esperado);
    assertThat(registros.getFirst().get("DESCRICAO_ITEM")).isEqualTo(descricao);
  }

  static Stream<Arguments> descricoesResiduaisObrigatorias() {
    return Stream.of(
      Arguments.of("TC ANGIOTOMOGRAFIA CORONARIANA", "CARDIOLOGIA"),
      Arguments.of("HERNIORRAFIA UMBILICAL", "CIRURGIA GERAL"),
      Arguments.of("HERNIORRAFIA INGUINAL", "CIRURGIA GERAL"),
      Arguments.of("COLECISTECTOMIA", "CIRURGIA GERAL"),
      Arguments.of("MAPEAMENTO DE RETINA MONOCULAR", "OFTALMOLOGIA"),
      Arguments.of("TONOMETRIA BINOCULAR", "OFTALMOLOGIA"),
      Arguments.of("PAQUIMETRIA MONOCULAR", "OFTALMOLOGIA"),
      Arguments.of("MICROSCOPIA DE CORNEA", "OFTALMOLOGIA"),
      Arguments.of("EXERESE DE PTERIGIO", "OFTALMOLOGIA"),
      Arguments.of("TELECONSULTA ELETIVA", "TELECONSULTA"),
      Arguments.of("US TRANSVAGINAL", "GINECOLOGIA"),
      Arguments.of("US PROSTATA", "UROLOGIA"),
      Arguments.of("US APARELHO URINARIO", "UROLOGIA"),
      Arguments.of("MAMOGRAFIA BILATERAL", "MASTOLOGIA"),
      Arguments.of("US MAMAS", "MASTOLOGIA"),
      Arguments.of("DOPPLER COLORIDO VENOSO DE MEMBRO", "CIRURGIA VASCULAR"),
      Arguments.of("PROCEDIMENTO DIAGNOSTICO EM PECA CIRURGICA OU ANATOMICA",
        "ANATOMIA PATOLOGICA"),
      Arguments.of("RM ARTICULAR", "ORTOPEDIA E TRAUMATOLOGIA"),
      Arguments.of("RX PUNHO", "ORTOPEDIA E TRAUMATOLOGIA"),
      Arguments.of("TC DE ABDOME", "RADIOLOGIA E DIAGNOSTICO POR IMAGEM")
    );
  }

  @ParameterizedTest
  @MethodSource("descricoesQueDevemContinuarClinico")
  void naoDeveClassificarTermosGenericosOuFalsosPositivos(String descricao) {
    var registros = new ArrayList<>(List.of(
      registro("A", descricao, "01/01/2026", "CLINICO", descricao, "")
    ));

    resolver.normalizar(registros);

    assertThat(registros.getFirst().get("NOME_ESPECIALIDADE")).isEqualTo("CLINICO");
  }

  static Stream<String> descricoesQueDevemContinuarClinico() {
    return Stream.of(
      "HEMOGRAMA COMPLETO",
      "MEDICAMENTO",
      "",
      "LUVA CIRURGICA",
      "LAMINA CIRURGICA",
      "PECA DE MATERIAL CIRURGICO",
      "AVALIACAO MUSCULAR",
      "CULTURA DE URINA",
      "ROTINA DE URINA",
      "DOSAGEM DE AMILASE E LIPASE"
    );
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void cidDesconhecidoDeveSerRegistradoEPermitirDescricaoResidual(CapturedOutput output) {
    var registros = new ArrayList<>(List.of(
      registro("A", "CID", "01/01/2026", "CLINICO", "US TRANSVAGINAL", "Z99.9")
    ));

    resolver.normalizar(registros);

    assertThat(registros.getFirst())
      .containsEntry("NOME_ESPECIALIDADE", "GINECOLOGIA")
      .containsEntry("CID", "Z99.9");
    assertThat(output).contains("CID(s) ainda não mapeados: Z999");
  }

  @Test
  void especialidadeEspecificaDeveVencerTodaACamadaResidual() {
    var registros = new ArrayList<>(List.of(
      registro("A", "21", "01/01/2026", "CARDIOLOGIA", "PARTO CESAREA", "O80.9"),
      registro("A", "21", "01/01/2026", "CLINICO", "US TRANSVAGINAL", "K80.4")
    ));

    resolver.normalizar(registros);

    assertThat(registros).extracting(item -> item.get("NOME_ESPECIALIDADE"))
      .containsOnly("CARDIOLOGIA");
  }

  @Test
  void devePropagarCidResidualParaTodosOsItensDaGuia() {
    var registros = new ArrayList<>(List.of(
      registro("A", "22", "01/01/2026", "CLINICO", "HEMOGRAMA", "K57.3"),
      registro("A", "22", "01/01/2026", "CLINICO", "MEDICAMENTO", ""),
      registro("A", "22", "01/01/2026", "CLINICO", "MATERIAL", "")
    ));

    resolver.normalizar(registros);

    assertThat(registros).extracting(item -> item.get("NOME_ESPECIALIDADE"))
      .containsOnly("GASTROENTEROLOGIA");
  }

  @ParameterizedTest
  @MethodSource("especialidadesProduzidasPelaCamadaResidual")
  void resultadoResidualDeveSerIdempotente(String especialidade) {
    var registros = new ArrayList<>(List.of(
      registro("A", especialidade, "01/01/2026", especialidade, "HEMOGRAMA", "")
    ));

    resolver.normalizar(registros);

    assertThat(registros.getFirst().get("NOME_ESPECIALIDADE")).isEqualTo(especialidade);
  }

  static Stream<String> especialidadesProduzidasPelaCamadaResidual() {
    return Stream.of(
      "TELECONSULTA",
      "ANATOMIA PATOLOGICA",
      "RADIOLOGIA E DIAGNOSTICO POR IMAGEM"
    );
  }

  @Test
  void devePropagarEspecialidadeDaGuiaEPreservarOsOutrosCampos() {
    var registros = new ArrayList<LinkedHashMap<String, Object>>();
    registros.add(registro("A", "10", "02/02/2026", "", "medicamento", "Z00"));
    registros.add(registro("A", "10", "02/02/2026", "", "material", ""));
    registros.add(registro("A", "10", "02/02/2026", "", "CIRURGIA DE IMPLANTE COCLEAR", ""));
    registros.add(registro("A", "10", "02/02/2026", "", "diária", ""));
    var descricaoAntes = registros.stream().map(item -> item.get("DESCRICAO_ITEM")).toList();
    var cidAntes = registros.stream().map(item -> item.get("CID")).toList();

    resolver.normalizar(registros);

    assertThat(registros).hasSize(4);
    assertThat(registros).extracting(item -> item.get("NOME_ESPECIALIDADE"))
      .containsOnly("OTORRINOLARINGOLOGIA");
    assertThat(registros.stream().map(item -> item.get("DESCRICAO_ITEM")).toList())
      .isEqualTo(descricaoAntes);
    assertThat(registros.stream().map(item -> item.get("CID")).toList()).isEqualTo(cidAntes);
  }

  @Test
  void especialidadeEspecificaDeNomeDeveVencerClinicoEmTodaAGuia() {
    var registros = new ArrayList<>(List.of(
      registro("A", "11", "02/02/2026", "PRONTO SOCORRO", "", ""),
      registro("A", "11", "02/02/2026", "CARDIOLOGIA", "", ""),
      registro("A", "11", "02/02/2026", "", "medicamento", "")
    ));
    resolver.normalizar(registros);
    assertThat(registros).extracting(item -> item.get("NOME_ESPECIALIDADE"))
      .containsOnly("CARDIOLOGIA");
  }

  @Test
  void deveUsarDescricaoSomenteDepoisDosNomes() {
    var parto = new ArrayList<>(List.of(
      registro("A", "12", "02/02/2026", "", "PARTO NORMAL", "")
    ));
    var nomeTemPrioridade = new ArrayList<>(List.of(
      registro("A", "13", "02/02/2026", "CARDIOLOGIA", "PARTO NORMAL", "")
    ));

    resolver.normalizar(parto);
    resolver.normalizar(nomeTemPrioridade);

    assertThat(parto.getFirst().get("NOME_ESPECIALIDADE")).isEqualTo("OBSTETRICIA");
    assertThat(nomeTemPrioridade.getFirst().get("NOME_ESPECIALIDADE")).isEqualTo("CARDIOLOGIA");
  }

  @Test
  void descricaoGenericaETudoVazioDevemUsarClinicoSemInventarDados() {
    var registros = new ArrayList<>(List.of(
      registro("A", "14", "02/02/2026", "", "HEMOGRAMA", ""),
      registro("A", "15", "02/02/2026", "", "", "")
    ));
    resolver.normalizar(registros);
    assertThat(registros).extracting(item -> item.get("NOME_ESPECIALIDADE"))
      .containsOnly("CLINICO");
    assertThat(registros.get(0)).containsEntry("DESCRICAO_ITEM", "HEMOGRAMA").containsEntry("CID", "");
    assertThat(registros.get(1)).containsEntry("DESCRICAO_ITEM", "").containsEntry("CID", "");
  }

  @Test
  void naoDeveMisturarMesmoNumeroDeGuiaEntreBeneficiarios() {
    var registros = new ArrayList<>(List.of(
      registro("A", "123", "03/03/2026", "CARDIOLOGIA", "", ""),
      registro("B", "123", "03/03/2026", "OFTALMOLOGIA", "", "")
    ));
    resolver.normalizar(registros);
    assertThat(registros.get(0).get("NOME_ESPECIALIDADE")).isEqualTo("CARDIOLOGIA");
    assertThat(registros.get(1).get("NOME_ESPECIALIDADE")).isEqualTo("OFTALMOLOGIA");
  }

  @Test
  void devePreservarDuplicidadesLegitimas() {
    var repetido = registro("A", "20", "04/04/2026", "", "HOLTER 24 HORAS", "");
    var registros = new ArrayList<>(List.of(repetido, new LinkedHashMap<>(repetido)));
    resolver.normalizar(registros);
    assertThat(registros).hasSize(2);
    assertThat(registros).extracting(item -> item.get("NOME_ESPECIALIDADE"))
      .containsExactly("CARDIOLOGIA", "CARDIOLOGIA");
  }

  private static LinkedHashMap<String, Object> registro(
    String beneficiario,
    String guia,
    String data,
    String especialidade,
    String descricao,
    String cid
  ) {
    LinkedHashMap<String, Object> registro = new LinkedHashMap<>();
    registro.put("COD_BENEFICIARIO", beneficiario);
    registro.put("NUMERO_GUIA", guia);
    registro.put("DATA_GUIA", data);
    registro.put("NOME_ESPECIALIDADE", especialidade);
    registro.put("DESCRICAO_ITEM", descricao);
    registro.put("CID", cid);
    return registro;
  }
}
