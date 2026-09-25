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
