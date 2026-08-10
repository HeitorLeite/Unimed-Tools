/** Verifica a finalização visual da geração automática no modo zoneless. */
import { HttpResponse } from '@angular/common/http';
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
    expect(component.erro).toBe('O backend não devolveu o arquivo ZIP.');
    expect(cdr.detectChanges).toHaveBeenCalled();
  });
});
