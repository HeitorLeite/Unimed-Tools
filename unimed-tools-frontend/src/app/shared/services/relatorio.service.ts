/**
 * Camada de acesso ao backend de relatórios e ao catálogo mantido no localStorage.
 */
import { HttpClient, HttpErrorResponse, HttpEvent, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, concatMap, from, map, of, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { RELATORIO_STORAGE_KEYS } from '../constants/storage.constants';
import { HospitalConfiguration, HospitalRequest } from '../models/hospital.model';
import {
  FormatoExportacao,
  RelatorioCatalogo,
  RelatorioGrupoAutomatico,
  RelatorioLoteRequest,
  RelatorioPersonalizadoConfiguracao,
  RelatorioPersonalizadoRequest,
  RelatorioTemplate,
  SguApiDefinicao,
  SguListaResponse,
  SguResultado,
} from '../models/relatorio.model';

@Injectable({ providedIn: 'root' })
export class RelatorioService {
  private readonly baseUrl = `${environment.apiUrl}/relatorios`;

  constructor(private readonly http: HttpClient) {}

  listarCatalogo(): RelatorioCatalogo[] {
    return this.lerLocalStorage<RelatorioCatalogo[]>(RELATORIO_STORAGE_KEYS.catalogo, []);
  }

  salvarCatalogo(relatorios: RelatorioCatalogo[]): void {
    this.salvarLocalStorage(RELATORIO_STORAGE_KEYS.catalogo, relatorios);
  }

  listarTemplates(): RelatorioTemplate[] {
    return this.lerLocalStorage<RelatorioTemplate[]>(RELATORIO_STORAGE_KEYS.templates, []);
  }

  salvarTemplates(templates: RelatorioTemplate[]): void {
    this.salvarLocalStorage(RELATORIO_STORAGE_KEYS.templates, templates);
  }

  listarGruposAutomaticos(): RelatorioGrupoAutomatico[] {
    return this.lerLocalStorage<RelatorioGrupoAutomatico[]>(
      RELATORIO_STORAGE_KEYS.gruposAutomaticos,
      [],
    );
  }

  salvarGruposAutomaticos(grupos: RelatorioGrupoAutomatico[]): void {
    this.salvarLocalStorage(RELATORIO_STORAGE_KEYS.gruposAutomaticos, grupos);
  }

  buscarApi(nome: string): Observable<SguApiDefinicao> {
    const nomeNormalizado = nome.trim();

    return this.http
      .post<SguListaResponse>(`${this.baseUrl}/sgu/listar`, {
        nome: nomeNormalizado,
      })
      .pipe(
        map((resposta) => {
          const conteudo = Array.isArray(resposta?.content) ? resposta.content : [];

          const encontrada =
            conteudo.find((api) => api.nome?.toLowerCase() === nomeNormalizado.toLowerCase()) ??
            conteudo[0];

          if (!encontrada) {
            throw new Error(`A API ${nomeNormalizado} não foi encontrada no SGU.`);
          }

          return encontrada;
        }),
      );
  }

  // ── Administração das definições mantidas no SGU ───────────────────────────

  listarApis(): Observable<SguApiDefinicao[]> {
    return this.http
      .post<SguListaResponse>(`${this.baseUrl}/sgu/listar`, { nome: '' })
      .pipe(map((resposta) => (Array.isArray(resposta?.content) ? resposta.content : [])));
  }

  criarApi(definicao: SguApiDefinicao): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/sgu/criar`, definicao);
  }

  apagarApi(nome: string): Observable<unknown> {
    return this.http.delete(`${this.baseUrl}/sgu/${encodeURIComponent(nome)}`);
  }

  substituirApi(
    nomeAnterior: string,
    definicaoAnterior: SguApiDefinicao,
    novaDefinicao: SguApiDefinicao,
  ): Observable<unknown> {
    // Como o SGU não oferece atualização atômica, guardamos uma cópia completa
    // para restaurar a definição anterior se a recriação falhar.
    const nomeNormalizado = nomeAnterior.trim();
    const novoNomeNormalizado = novaDefinicao.nome.trim();
    const backup: SguApiDefinicao = {
      ...definicaoAnterior,
      nome: nomeNormalizado,
      filtros: Array.isArray(definicaoAnterior.filtros)
        ? definicaoAnterior.filtros.map((filtro) => ({ ...filtro }))
        : [],
    };

    if (!nomeNormalizado) {
      return throwError(() => new Error('O nome atual da API não foi informado.'));
    }

    if (!novoNomeNormalizado) {
      return throwError(() => new Error('O novo nome da API não foi informado.'));
    }

    const executarSubstituicao = (): Observable<unknown> =>
      this.apagarApi(nomeNormalizado).pipe(
        concatMap(() =>
          this.criarApi({
            ...novaDefinicao,
            nome: novoNomeNormalizado,
          }).pipe(
            catchError((erroCriacao) =>
              this.criarApi(backup).pipe(
                catchError((erroRestauracao) =>
                  throwError(
                    () =>
                      new Error(
                        `A API antiga foi apagada, a nova não pôde ser criada e a restauração também falhou. ` +
                          `Erro da criação: ${this.detalheErro(erroCriacao)}. ` +
                          `Erro da restauração: ${this.detalheErro(erroRestauracao)}.`,
                      ),
                  ),
                ),
                concatMap(() =>
                  throwError(
                    () =>
                      new Error(
                        `Não foi possível criar a nova versão da API. ` +
                          `A definição anterior foi restaurada automaticamente. ` +
                          `Detalhe: ${this.detalheErro(erroCriacao)}`,
                      ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );

    if (novoNomeNormalizado.toLowerCase() === nomeNormalizado.toLowerCase()) {
      return executarSubstituicao();
    }

    return this.listarApis().pipe(
      concatMap((apis) => {
        const nomeJaExiste = apis.some(
          (api) =>
            String(api?.nome ?? '')
              .trim()
              .toLowerCase() === novoNomeNormalizado.toLowerCase(),
        );

        if (nomeJaExiste) {
          return throwError(
            () =>
              new Error(
                `Já existe uma API chamada ${novoNomeNormalizado}. ` +
                  'Escolha outro nome antes de salvar a edição.',
              ),
          );
        }

        return executarSubstituicao();
      }),
    );
  }

  executar(nome: string, parametros: Record<string, unknown>): Observable<SguResultado> {
    return this.http.post<SguResultado>(
      `${this.baseUrl}/sgu/executar/${encodeURIComponent(nome)}`,
      parametros,
    );
  }

  configuracaoPersonalizada(): Observable<RelatorioPersonalizadoConfiguracao> {
    return this.http.get<RelatorioPersonalizadoConfiguracao>(
      `${this.baseUrl}/personalizado/configuracao`,
    );
  }

  executarPersonalizado(request: RelatorioPersonalizadoRequest): Observable<SguResultado> {
    return this.http.post<SguResultado>(`${this.baseUrl}/personalizado/executar`, request);
  }

  exportarPersonalizado(
    formato: FormatoExportacao,
    request: RelatorioPersonalizadoRequest,
  ): Observable<HttpEvent<Blob>> {
    return this.http.post(`${this.baseUrl}/personalizado/exportar?formato=${formato}`, request, {
      observe: 'events',
      reportProgress: true,
      responseType: 'blob',
    }).pipe(this.validarDownload(formato));
  }

  configuracaoHospital(): Observable<HospitalConfiguration> {
    return this.http.get<HospitalConfiguration>(`${this.baseUrl}/hospital/configuracao`);
  }

  executarHospital(request: HospitalRequest): Observable<SguResultado> {
    return this.http.post<SguResultado>(`${this.baseUrl}/hospital/executar`, request);
  }

  exportarHospital(
    formato: FormatoExportacao,
    request: HospitalRequest,
  ): Observable<HttpEvent<Blob>> {
    return this.http.post(`${this.baseUrl}/hospital/exportar?formato=${formato}`, request, {
      observe: 'events',
      reportProgress: true,
      responseType: 'blob',
    }).pipe(this.validarDownload(formato));
  }

  // ── Execução e exportação ──────────────────────────────────────────────────

  exportar(
    nome: string,
    formato: FormatoExportacao,
    filtros: Record<string, unknown>,
    nomeArquivo: string,
  ): Observable<HttpEvent<Blob>> {
    return this.http.post(
      `${this.baseUrl}/sgu/exportar/${encodeURIComponent(nome)}?formato=${formato}`,
      { filtros, nomeArquivo },
      { observe: 'events', reportProgress: true, responseType: 'blob' },
    ).pipe(this.validarDownload(formato));
  }

  exportarLote(request: RelatorioLoteRequest): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.baseUrl}/sgu/exportar-lote`, request, {
      observe: 'response',
      responseType: 'blob',
    }).pipe(this.validarDownload('zip'));
  }

  private validarDownload<T extends HttpEvent<Blob>>(formato: string) {
    return (origem: Observable<T>): Observable<T> => origem.pipe(
      concatMap(async (evento) => {
        if (!(evento instanceof HttpResponse)) return evento;
        const blob = evento.body;
        const tipo = (blob?.type || evento.headers.get('Content-Type') || '').toLowerCase();
        if (!blob || blob.size === 0 || tipo.includes('json') || tipo.includes('html')) {
          throw new Error('O servidor não devolveu um relatório válido. Nenhum arquivo foi salvo.');
        }
        if (evento.headers.get('X-Total-Registros') === '0') {
          throw new Error('Nenhum registro foi encontrado para os filtros informados.');
        }
        if (formato === 'xlsx' || formato === 'zip') {
          const assinatura = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
          if (assinatura.length !== 4 || assinatura[0] !== 0x50 || assinatura[1] !== 0x4b ||
              assinatura[2] !== 3 || assinatura[3] !== 4) {
            throw new Error('O arquivo recebido está inválido ou incompleto. Gere o relatório novamente.');
          }
        } else if (!(await blob.slice(0, 1024).text()).replace(/^\uFEFF/, '').trim()) {
          throw new Error('O servidor devolveu um arquivo vazio. Nenhum arquivo foi salvo.');
        }
        return evento;
      }),
      catchError((erro: unknown) => this.tratarErroDownload(erro)),
    );
  }

  private tratarErroDownload(erro: unknown): Observable<never> {
    if (!(erro instanceof HttpErrorResponse)) return throwError(() => erro);
    let corpo = erro.error;
    if (corpo instanceof Blob && corpo.type.includes('json')) {
      return from(corpo.text()).pipe(
        catchError(() => of('')),
        concatMap((texto) => {
          let json: unknown = null;
          try { json = JSON.parse(texto); } catch { /* Usa a mensagem pública abaixo. */ }
          return this.tratarErroDownload(new HttpErrorResponse({ status: erro.status,
            statusText: erro.statusText, headers: erro.headers, url: erro.url ?? undefined, error: json }));
        }),
      );
    }
    // O gateway pode responder HTML. Só mensagens JSON do backend são exibidas.
    if (corpo && typeof corpo === 'object' && !(corpo instanceof Blob) && typeof corpo.message === 'string') {
      if (corpo === erro.error) return throwError(() => erro);
    } else {
      const message = erro.status === 504
        ? 'O serviço de relatórios excedeu o tempo de resposta. Aguarde um pouco e tente novamente.'
        : [502, 503].includes(erro.status)
          ? 'O serviço de relatórios está temporariamente indisponível. Aguarde um pouco e tente novamente.'
          : erro.status === 0
            ? 'A conexão foi interrompida. Nenhum arquivo foi salvo; tente novamente.'
            : 'Não foi possível concluir o download. Gere o relatório novamente.';
      corpo = { message };
    }
    return throwError(() => new HttpErrorResponse({ status: erro.status, statusText: erro.statusText,
      headers: erro.headers, url: erro.url ?? undefined, error: corpo }));
  }

  private detalheErro(erro: any): string {
    const corpo = erro?.error;

    if (typeof corpo === 'string') {
      return corpo;
    }

    return corpo?.message ?? corpo?.error ?? erro?.message ?? 'erro não informado';
  }

  private lerLocalStorage<T>(chave: string, padrao: T): T {
    // A verificação permite executar testes ou renderizações sem API de navegador.
    if (typeof localStorage === 'undefined') return padrao;

    try {
      const salvo = localStorage.getItem(chave);
      return salvo ? (JSON.parse(salvo) as T) : padrao;
    } catch {
      return padrao;
    }
  }

  private salvarLocalStorage(chave: string, valor: unknown): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(chave, JSON.stringify(valor));
  }
}
