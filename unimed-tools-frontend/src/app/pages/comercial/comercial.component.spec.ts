import { ChangeDetectorRef } from '@angular/core';
import { RelatorioService } from '../../shared/services/relatorio.service';
import { ComercialComponent } from './comercial.component';

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
