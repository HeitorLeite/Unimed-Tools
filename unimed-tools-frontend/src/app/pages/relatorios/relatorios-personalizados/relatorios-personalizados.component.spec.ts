/** Verifica a atualização imediata da tela após a resposta assíncrona do relatório. */
import { ChangeDetectorRef } from '@angular/core';
import { Subject } from 'rxjs';

import { SguResultado } from '../../../shared/models/relatorio.model';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import { RelatoriosPersonalizadosComponent } from './relatorios-personalizados.component';

describe('RelatoriosPersonalizadosComponent', () => {
  it('recolhe e expande cada seção sem alterar as demais', () => {
    const component = new RelatoriosPersonalizadosComponent(
      {} as RelatorioService,
      { detectChanges: vi.fn() } as unknown as ChangeDetectorRef,
    );

    expect(component.secaoRecolhida('filtros')).toBe(false);
    expect(component.secaoRecolhida('colunas')).toBe(false);

    component.alternarSecao('filtros');

    expect(component.secaoRecolhida('filtros')).toBe(true);
    expect(component.secaoRecolhida('colunas')).toBe(false);

    component.alternarSecao('filtros');

    expect(component.secaoRecolhida('filtros')).toBe(false);
  });

  it('encerra o carregamento e atualiza a tabela sem depender de evento do navegador', () => {
    const resposta$ = new Subject<SguResultado>();
    const relatorioService = {
      executarPersonalizado: vi.fn(() => resposta$.asObservable()),
    } as unknown as RelatorioService;
    const cdr = {
      detectChanges: vi.fn(),
    } as unknown as ChangeDetectorRef;
    const component = new RelatoriosPersonalizadosComponent(relatorioService, cdr);

    component.configuracao = {
      apiNome: '0090-relatorio-personalizado',
      fonte: 'Teste',
      colunas: [
        {
          id: 'COD_BENEFICIARIO',
          rotulo: 'Código do beneficiário',
          grupo: 'Beneficiário',
          selecionadaPorPadrao: true,
          sensivel: true,
        },
      ],
      filtros: [],
      limites: { maximoColunas: 1, maximoMeses: 12, maximoLinhasPagina: 100 },
    };
    component.valoresFiltro = {
      competencia_inicio: '2026-01',
      competencia_fim: '2026-01',
    };
    component.colunasSelecionadas.add('COD_BENEFICIARIO');
    component.somenteDistintos = true;

    component.gerar();

    expect(component.gerando).toBe(true);
    expect(relatorioService.executarPersonalizado).toHaveBeenCalledWith(
      expect.objectContaining({ distinct: true }),
    );
    expect(cdr.detectChanges).toHaveBeenCalledTimes(1);

    resposta$.next({
      content: [{ COD_BENEFICIARIO: '000.0000.000000.00' }],
      colunas: ['COD_BENEFICIARIO'],
      last: true,
    });
    resposta$.complete();

    expect(component.gerando).toBe(false);
    expect(component.registros).toHaveLength(1);
    expect(component.colunasResultado).toEqual(['COD_BENEFICIARIO']);
    expect(component.totalRegistros).toBe(1);
    expect(cdr.detectChanges).toHaveBeenCalledTimes(2);
  });
});
