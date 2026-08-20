/*
 * Responsabilidade: Expõe as operações de catálogo SGU, execução e exportação de relatórios.
 */
package com.unimedlorena.tools.controller;

import com.unimedlorena.tools.dto.RelatorioExportacaoRequest;
import com.unimedlorena.tools.dto.RelatorioLoteRequest;
import com.unimedlorena.tools.dto.RelatorioPersonalizadoRequest;
import com.unimedlorena.tools.service.ExportacaoLoteRelatorioService;
import com.unimedlorena.tools.service.ExportacaoRelatorioService;
import com.unimedlorena.tools.service.RelatorioPersonalizadoService;
import com.unimedlorena.tools.service.SguRelatorioService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

  private final SguRelatorioService sgu;
  private final ExportacaoRelatorioService exportacao;
  private final ExportacaoLoteRelatorioService exportacaoLote;
  private final RelatorioPersonalizadoService relatorioPersonalizado;

  public RelatorioController(
    SguRelatorioService sgu,
    ExportacaoRelatorioService exportacao,
    ExportacaoLoteRelatorioService exportacaoLote,
    RelatorioPersonalizadoService relatorioPersonalizado
  ) {
    this.sgu = sgu;
    this.exportacao = exportacao;
    this.exportacaoLote = exportacaoLote;
    this.relatorioPersonalizado = relatorioPersonalizado;
  }

  /**
   * Fornece ao frontend somente os filtros e as colunas aprovados. A consulta
   * SQL e as credenciais do SGU permanecem protegidas no servidor.
   */
  @GetMapping("/personalizado/configuracao")
  public RelatorioPersonalizadoService.Configuracao configuracaoPersonalizada() {
    return relatorioPersonalizado.configuracao();
  }

  @PostMapping("/personalizado/executar")
  public Map<String, Object> executarPersonalizado(
    @RequestBody RelatorioPersonalizadoRequest request
  ) {
    return relatorioPersonalizado.executar(request);
  }

  @PostMapping("/personalizado/exportar")
  public ResponseEntity<byte[]> exportarPersonalizado(
    @RequestParam(defaultValue = "xlsx") String formato,
    @RequestBody RelatorioPersonalizadoRequest request
  ) throws Exception {
    var arquivo = relatorioPersonalizado.exportar(formato, request);
    String nomeBase = sanitizarNome(
      request == null || request.nomeArquivo() == null
        ? "relatorio_personalizado"
        : request.nomeArquivo()
    );

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(arquivo.contentType()))
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
          .filename(nomeBase + "." + arquivo.extensao(), StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .header("X-Total-Registros", String.valueOf(arquivo.quantidadeRegistros()))
      .body(arquivo.conteudo());
  }

  /** Encaminha a busca opcional por nome sem expor a chave do SGU ao cliente. */
  @PostMapping("/sgu/listar")
  public Map<String, Object> listar(
    @RequestBody(required = false) Map<String, Object> body
  ) {
    String nome =
      body == null ? "" : String.valueOf(body.getOrDefault("nome", ""));
    return ocultarApiReservada(sgu.listar(nome));
  }

  @PostMapping("/sgu/criar")
  public Map<String, Object> criarOuAtualizar(
    @RequestBody Map<String, Object> definicao
  ) {
    validarApiNaoReservada(
      definicao == null ? null : String.valueOf(definicao.get("nome"))
    );
    return sgu.criarOuAtualizar(definicao);
  }

  @DeleteMapping("/sgu/{nome}")
  public Map<String, Object> apagar(@PathVariable String nome) {
    validarApiNaoReservada(nome);
    return sgu.apagar(nome);
  }

  @PostMapping("/sgu/executar/{nome}")
  public Map<String, Object> executar(
    @PathVariable String nome,
    @RequestBody(required = false) Map<String, Object> parametros
  ) {
    validarApiNaoReservada(nome);
    return sgu.executar(nome, parametros == null ? Map.of() : parametros);
  }

  /** Processa e transmite cada página sem manter o relatório inteiro em memória. */
  @PostMapping("/sgu/exportar/{nome}")
  public ResponseEntity<StreamingResponseBody> exportar(
    @PathVariable String nome,
    @RequestParam(defaultValue = "xlsx") String formato,
    @RequestBody(required = false) RelatorioExportacaoRequest request
  ) {
    validarApiNaoReservada(nome);
    var descricao = exportacao.descreverArquivo(formato);
    String nomeBase = sanitizarNome(
      request == null || request.nomeArquivo() == null
        ? nome
        : request.nomeArquivo()
    );
    String nomeCompleto = nomeBase + "." + descricao.extensao();
    StreamingResponseBody corpo = destino ->
      exportacao.exportarPara(nome, formato, request, destino);

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(descricao.contentType()))
      .header(HttpHeaders.CACHE_CONTROL, "no-store")
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
          .filename(nomeCompleto, StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .body(corpo);
  }

  /**
   * Retorna um ZIP mesmo quando itens individuais falham. As contagens nos
   * cabeçalhos permitem que o frontend apresente o resultado parcial.
   */
  @PostMapping("/sgu/exportar-lote")
  public ResponseEntity<byte[]> exportarLote(
    @RequestBody RelatorioLoteRequest request
  ) throws Exception {
    if (request != null && request.itens() != null) {
      request.itens().forEach(item -> validarApiNaoReservada(item.apiNome()));
    }
    var resultado = exportacaoLote.exportar(request);
    String nomeBase = sanitizarNome(
      request == null || request.nomeArquivo() == null
        ? "relatorios_automaticos"
        : request.nomeArquivo()
    );

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("application/zip"))
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment()
          .filename(nomeBase + ".zip", StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .header(
        "X-Relatorios-Gerados",
        String.valueOf(resultado.arquivosGerados())
      )
      .header("X-Relatorios-Erros", String.valueOf(resultado.arquivosComErro()))
      .body(resultado.conteudo());
  }

  private String sanitizarNome(String nome) {
    // Impede que o nome recebido seja interpretado como caminho no download.
    String limpo =
      nome == null
        ? "relatorio"
        : nome
            .replaceAll("[^a-zA-Z0-9._-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
    return limpo.isBlank() ? "relatorio" : limpo;
  }

  private void validarApiNaoReservada(String nome) {
    if (relatorioPersonalizado.ehApiReservada(nome)) {
      throw new IllegalArgumentException(
        "A API " +
        RelatorioPersonalizadoService.API_NOME +
        " é reservada ao construtor de relatório personalizado."
      );
    }
  }

  private Map<String, Object> ocultarApiReservada(
    Map<String, Object> resposta
  ) {
    if (resposta == null || !(resposta.get("content") instanceof List<?> itens)) {
      return resposta;
    }

    List<?> visiveis = itens
      .stream()
      .filter(item -> {
        if (!(item instanceof Map<?, ?> api)) return true;
        Object nome = api.get("nome");
        return !relatorioPersonalizado.ehApiReservada(
          nome == null ? null : String.valueOf(nome)
        );
      })
      .toList();

    Map<String, Object> filtrada = new LinkedHashMap<>(resposta);
    filtrada.put("content", visiveis);
    if (filtrada.containsKey("numberOfElements")) {
      filtrada.put("numberOfElements", visiveis.size());
    }
    return filtrada;
  }
}
