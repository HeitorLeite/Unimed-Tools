import { ChangeDetectorRef } from '@angular/core';
import { RelatorioService } from '../../shared/services/relatorio.service';
import { ComercialComponent } from './comercial.component';
import { of } from 'rxjs';

describe('ComercialComponent', () => {
  function createComponent(): ComercialComponent {
    return new ComercialComponent(
      {} as RelatorioService,
      { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    );
  }

  afterEach(() => {
    vi.useRealTimers();
  });

  it('envia todos os códigos de uma empresa com filtro NUMBER e formata a referência', async () => {
    const executar = vi.fn().mockReturnValue(of({ content: [] }));
    const component = new ComercialComponent(
      { executar } as unknown as RelatorioService,
      { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    );
    component.selectedCompanyIds = ['yakult', 'yakult'];
    component.referenceDate = '2026-08-31';
    const report = component.reports.find((item) => component.isAgeRange(item))!;
    component.reports.forEach((item) => (item.selected = item === report));
    report.definition = { nome: report.api, consultaSQL: '', ordenacao: '', filtros: [
      { nomeFiltro: 'empresas', tipoDadoFiltro: 'NUMBER', mascaraFiltro: '', conteudoFiltro: '', obrigatorioFiltro: 'S' },
      { nomeFiltro: 'datareferencia', tipoDadoFiltro: 'DATE', mascaraFiltro: 'DD/MM/YYYY', conteudoFiltro: '', obrigatorioFiltro: 'S' },
    ] };
    await component.generatePreview();
    expect(executar).toHaveBeenCalledWith(report.api, {
      combinacoesFiltros: [
        { empresas: 2232751, datareferencia: '31/08/2026' },
        { empresas: 2234452, datareferencia: '31/08/2026' },
      ], page: 1, size: 20,
    });
  });

  it.each([
    [new Date(2026, 8, 15, 12), '202608', '2026-08-31'],
    [new Date(2026, 0, 15, 12), '202512', '2025-12-31'],
  ])('sugere o mês anterior e o último dia dessa competência', (today, competence, reference) => {
    vi.useFakeTimers();
    vi.setSystemTime(today);

    const component = createComponent();

    expect(component.competence).toBe(competence);
    expect(component.referenceDate).toBe(reference);
  });

  it('recalcula a referência e invalida as prévias quando a competência muda', () => {
    const component = createComponent();
    component.reports.forEach((report) => (report.previewed = true));

    component.onCompetenceChange('202402');

    expect(component.referenceDate).toBe('2024-02-29');
    expect(component.reports.every((report) => !report.previewed)).toBe(true);
  });

  it('mantém o download bloqueado enquanto as prévias não forem geradas', () => {
    const exportarLote = vi.fn();
    const component = new ComercialComponent(
      { exportarLote } as unknown as RelatorioService,
      { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    );
    component.selectedCompanyIds = [component.companies[0].id];
    component.reports.forEach((report) => {
      report.definition = { nome: report.api, consultaSQL: '', ordenacao: '', filtros: [] };
    });

    component.downloadAll();

    expect(component.previewsGenerated).toBe(false);
    expect(exportarLote).not.toHaveBeenCalled();
  });

  it('gera o relatório final sem exigir prévias e envia as quatro APIs', () => {
    const exportarComercialFinal = vi.fn().mockReturnValue(of({ type: 0 }));
    const component = new ComercialComponent(
      { exportarComercialFinal } as unknown as RelatorioService,
      { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    );

    component.loadingDefinitions = false;
    component.competence = '202608';
    component.referenceDate = '2026-08-31';
    component.selectedCompanyIds = [component.companies[0].id];
    component.reports.forEach((report) => {
      report.definition = {
        nome: report.api,
        consultaSQL: '',
        ordenacao: '',
        filtros: report.api === '0090-faixa-etaria'
          ? [
              { nomeFiltro: 'empresa', tipoDadoFiltro: 'NUMBER', mascaraFiltro: '', conteudoFiltro: '', obrigatorioFiltro: 'S' },
              { nomeFiltro: 'datareferencia', tipoDadoFiltro: 'DATE', mascaraFiltro: 'DD/MM/YYYY', conteudoFiltro: '', obrigatorioFiltro: 'S' },
            ]
          : [
              { nomeFiltro: 'empresa', tipoDadoFiltro: 'NUMBER', mascaraFiltro: '', conteudoFiltro: '', obrigatorioFiltro: 'S' },
              { nomeFiltro: 'competencia', tipoDadoFiltro: 'NUMBER', mascaraFiltro: '', conteudoFiltro: '', obrigatorioFiltro: 'S' },
            ],
      };
      report.previewed = false;
    });

    component.downloadFinal();

    expect(exportarComercialFinal).toHaveBeenCalledTimes(1);
    const request = exportarComercialFinal.mock.calls[0][0];
    expect(request.competencia).toBe('202608');
    expect(Object.keys(request.filtrosPorApi).sort()).toEqual(
      component.reports.map((report) => report.api).sort(),
    );
    expect(request.filtrosPorApi['0090-receita-empresa-com-grupo'][0].competencia).toBe(202608);
    expect(request.filtrosPorApi['0090-faixa-etaria'][0].datareferencia).toBe('31/08/2026');
  });

  it('libera o download individual apenas após a prévia do relatório', () => {
    const component = createComponent();
    const report = component.reports[0];
    component.selectedCompanyIds = [component.companies[0].id];
    report.definition = { nome: report.api, consultaSQL: '', ordenacao: '', filtros: [] };

    expect(component.canDownloadReport(report)).toBe(false);

    report.previewed = true;

    expect(component.canDownloadReport(report)).toBe(true);
  });
});
