/** Verifica a finalização visual da geração automática no modo zoneless. */
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef } from '@angular/core';
import { Subject } from 'rxjs';

import { RelatorioService } from '../../../shared/services/relatorio.service';
import { RelatoriosAutomaticosComponent } from './relatorios-automaticos.component';

describe('RelatoriosAutomaticosComponent', () => {
  it('encerra o carregamento quando a resposta do lote termina', () => {
    const resposta$ = new Subject<HttpResponse<Blob>>();
    const relatorioService = {
      exportarLote: vi.fn(() => resposta$.asObservable()),
    } as unknown as RelatorioService;
    const cdr = { detectChanges: vi.fn() } as unknown as ChangeDetectorRef;
    const component = new RelatoriosAutomaticosComponent(relatorioService, cdr);

    component.relatorios = [
      {
        id: 'relatorio-1',
        nomeExibicao: 'Relatório de teste',
        descricao: '',
        apiNome: 'api-teste',
        filtros: [],
        criadoEm: '2026-08-10T00:00:00.000Z',
      },
    ];
    component.grupoSelecionado = {
      id: 'grupo-1',
      nome: 'Grupo de teste',
      descricao: '',
      formato: 'xlsx',
      itens: [{ relatorioId: 'relatorio-1', nomeArquivo: 'relatorio_teste' }],
      criadoEm: '2026-08-10T00:00:00.000Z',
    };
    component.nomeArquivoZip = 'grupo_teste';

    component.gerarGrupoAutomaticamente();
    expect(component.executando).toBe(true);

    resposta$.next(new HttpResponse<Blob>({ body: null }));
    resposta$.complete();

    expect(component.executando).toBe(false);
    expect(component.progressoExecucao).toBe(0);
    expect(component.notificacaoExecucao).toEqual({
      tipo: 'erro',
      mensagem: 'O backend não devolveu o arquivo ZIP.',
    });
    expect(cdr.detectChanges).toHaveBeenCalled();
    component.ngOnDestroy();
  });

  it('oculta o progresso e mostra notificação após preparar o download', () => {
    vi.useFakeTimers();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:relatorios'),
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    const resposta$ = new Subject<HttpResponse<Blob>>();
    const relatorioService = {
      exportarLote: vi.fn(() => resposta$.asObservable()),
    } as unknown as RelatorioService;
    const component = new RelatoriosAutomaticosComponent(relatorioService, {
      detectChanges: vi.fn(),
    } as unknown as ChangeDetectorRef);
    component.relatorios = [
      {
        id: 'relatorio-1',
        nomeExibicao: 'Relatório de teste',
        descricao: '',
        apiNome: 'api-teste',
        filtros: [],
        criadoEm: '2026-08-10T00:00:00.000Z',
      },
    ];
    component.grupoSelecionado = {
      id: 'grupo-1',
      nome: 'Grupo de teste',
      descricao: '',
      formato: 'xlsx',
      itens: [{ relatorioId: 'relatorio-1', nomeArquivo: 'relatorio_teste' }],
      criadoEm: '2026-08-10T00:00:00.000Z',
    };
    component.nomeArquivoZip = 'grupo_teste';

    component.gerarGrupoAutomaticamente();
    resposta$.next(
      new HttpResponse<Blob>({
        body: new Blob(['zip']),
        headers: new HttpHeaders({
          'X-Relatorios-Gerados': '3',
          'X-Relatorios-Erros': '0',
        }),
      }),
    );
    resposta$.complete();

    expect(component.executando).toBe(false);
    expect(component.progressoExecucao).toBe(0);
    expect(component.etapaExecucao).toBe('');
    expect(component.notificacaoExecucao).toEqual({
      tipo: 'sucesso',
      mensagem: '3 arquivo(s) gerado(s).',
    });
    expect(component.operacaoRelatorioEmAndamento).toBe(false);

    component.ngOnDestroy();
    vi.useRealTimers();
  });
});
