/**
 * Camada de acesso ao backend de relatórios e ao catálogo mantido no localStorage.
 */
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, concatMap, map, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { RELATORIO_STORAGE_KEYS } from '../constants/storage.constants';
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
  ): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.baseUrl}/personalizado/exportar?formato=${formato}`, request, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  // ── Execução e exportação ──────────────────────────────────────────────────

  exportar(
    nome: string,
    formato: FormatoExportacao,
    filtros: Record<string, unknown>,
    nomeArquivo: string,
  ): Observable<Blob> {
    return this.http.post(
      `${this.baseUrl}/sgu/exportar/${encodeURIComponent(nome)}?formato=${formato}`,
      { filtros, nomeArquivo },
      { responseType: 'blob' },
    );
  }

  exportarLote(request: RelatorioLoteRequest): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.baseUrl}/sgu/exportar-lote`, request, {
      observe: 'response',
      responseType: 'blob',
    });
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
