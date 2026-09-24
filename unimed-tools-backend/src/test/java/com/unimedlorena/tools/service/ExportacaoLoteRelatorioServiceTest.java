package com.unimedlorena.tools.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import com.unimedlorena.tools.dto.RelatorioLoteRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExportacaoLoteRelatorioServiceTest {
  @ParameterizedTest @ValueSource(strings={"csv","txt","xlsx"})
  void loteNaoIncluiArquivosVaziosOuParciaisEInformaFalhas(String formato) throws Exception {
    var sgu=mock(SguRelatorioService.class);
    when(sgu.executar(eq("valido"),anyMap())).thenReturn(Map.of("content",List.of(Map.of("ID","001")),"last",true));
    when(sgu.executar(eq("vazio"),anyMap())).thenReturn(Map.of("content",List.of(),"last",true));
    when(sgu.executar(eq("falha"),anyMap())).thenThrow(new IllegalStateException("detalhe interno sigiloso"));
    var service=new ExportacaoLoteRelatorioService(new ExportacaoRelatorioService(sgu,1000,0));
    var itens=List.of("valido","vazio","falha").stream()
      .map(nome->new RelatorioLoteRequest.Item(nome,nome,List.of(Map.of()))).toList();
    var resultado=service.exportar(new RelatorioLoteRequest("lote",formato,itens));
    assertThat(resultado.arquivosGerados()).isEqualTo(1);
    assertThat(resultado.arquivosComErro()).isEqualTo(2);
    Map<String,byte[]> entradas=new LinkedHashMap<>();
    try(var zip=new ZipInputStream(new ByteArrayInputStream(resultado.conteudo()),StandardCharsets.UTF_8)){
      ZipEntry entrada;
      while((entrada=zip.getNextEntry())!=null)entradas.put(entrada.getName(),zip.readAllBytes());
    }
    assertThat(entradas.keySet()).containsExactly("valido."+formato,"_RESUMO_GERACAO.txt");
    assertThat(new String(entradas.get("_RESUMO_GERACAO.txt"),StandardCharsets.UTF_8))
      .contains("Falhas: 2","Nenhum registro").doesNotContain("sigiloso");
  }
}
