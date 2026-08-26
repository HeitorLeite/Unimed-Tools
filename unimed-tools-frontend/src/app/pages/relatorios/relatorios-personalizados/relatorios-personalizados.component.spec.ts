/** Verifica a atualização imediata da tela após a resposta assíncrona do relatório. */
import { ChangeDetectorRef } from '@angular/core';
import { of, Subject } from 'rxjs';

import { SguResultado } from '../../../shared/models/relatorio.model';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import { RelatoriosPersonalizadosComponent } from './relatorios-personalizados.component';

describe('RelatoriosPersonalizadosComponent', () => {
  it('bloqueia as ações enquanto consulta ou exporta', () => {
    const component = new RelatoriosPersonalizadosComponent(
      {} as RelatorioService,
      { detectChanges: vi.fn() } as unknown as ChangeDetectorRef,
    );

    expect(component.operacaoRelatorioEmAndamento).toBe(false);
    component.gerando = true;
    expect(component.operacaoRelatorioEmAndamento).toBe(true);
    component.gerando = false;
    component.exportando = true;
    expect(component.operacaoRelatorioEmAndamento).toBe(true);
  });

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
      expect.objectContaining({ colunas: ['COD_BENEFICIARIO'], distinct: true }),
    );
    expect(cdr.detectChanges).toHaveBeenCalledTimes(1);

    resposta$.next({
      content: [{ COD_BENEFICIARIO: '000.0000.000000.00' }],
      colunas: ['COD_BENEFICIARIO'],
      numberOfElements: 148,
      last: false,
    });
    resposta$.complete();

    expect(component.gerando).toBe(false);
    expect(component.registros).toHaveLength(1);
    expect(component.colunasResultado).toEqual(['COD_BENEFICIARIO']);
    expect(component.totalRegistros).toBe(148);
    expect(component.totalPaginas).toBe(3);
    expect(component.ultimaPagina).toBe(false);
    expect(cdr.detectChanges).toHaveBeenCalledTimes(2);
  });

  it('altera explicitamente a ordem das colunas selecionadas', () => {
    const component = new RelatoriosPersonalizadosComponent(
      {} as RelatorioService,
      { detectChanges: vi.fn() } as unknown as ChangeDetectorRef,
    );
    component.colunasSelecionadas = new Set(['CPF', 'NOME_BENEFICIARIO', 'NUMERO_GUIA']);
    component.ordemColunasSelecionadas = ['CPF', 'NOME_BENEFICIARIO', 'NUMERO_GUIA'];

    component.moverColuna('NUMERO_GUIA', -1);
    component.moverColuna('NUMERO_GUIA', -1);

    expect(component.ordemColunasSelecionadas).toEqual(['NUMERO_GUIA', 'CPF', 'NOME_BENEFICIARIO']);
    expect(component.colunasResultado).toEqual(component.ordemColunasSelecionadas);
  });

  it('ordena a prévia inteira pelo backend e alterna entre crescente e decrescente', () => {
    const relatorioService = {
      executarPersonalizado: vi.fn(() =>
        of({ content: [{ VALOR_TOTAL: 10 }], colunas: ['VALOR_TOTAL'], last: true }),
      ),
    } as unknown as RelatorioService;
    const component = new RelatoriosPersonalizadosComponent(relatorioService, {
      detectChanges: vi.fn(),
    } as unknown as ChangeDetectorRef);
    component.configuracao = {
      apiNome: '0090-relatorio-personalizado',
      fonte: 'Teste',
      colunas: [
        {
          id: 'VALOR_TOTAL',
          rotulo: 'Despesa total',
          grupo: 'Valores',
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
    component.colunasSelecionadas.add('VALOR_TOTAL');
    component.ordemColunasSelecionadas = ['VALOR_TOTAL'];
    component.registros = [{ VALOR_TOTAL: 5 }];

    component.ordenarPor('VALOR_TOTAL');
    component.ordenarPor('VALOR_TOTAL');

    expect(relatorioService.executarPersonalizado).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ ordenarPor: 'VALOR_TOTAL', direcaoOrdenacao: 'ASC' }),
    );
    expect(relatorioService.executarPersonalizado).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ ordenarPor: 'VALOR_TOTAL', direcaoOrdenacao: 'DESC' }),
    );
    expect(component.ariaOrdenacao('VALOR_TOTAL')).toBe('descending');
    expect(component.simboloOrdenacao('VALOR_TOTAL')).toBe('↓');
  });
});
