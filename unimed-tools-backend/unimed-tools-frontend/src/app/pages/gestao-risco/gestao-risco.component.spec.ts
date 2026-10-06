import { ChangeDetectorRef } from '@angular/core';
import { RelatorioService } from '../../shared/services/relatorio.service';
import { GestaoRiscoComponent } from './gestao-risco.component';

describe('GestaoRiscoComponent', () => {
  function createComponent(service: Partial<RelatorioService> = {}): GestaoRiscoComponent {
    return new GestaoRiscoComponent(
      service as RelatorioService,
      { markForCheck: vi.fn() } as unknown as ChangeDetectorRef,
    );
  }

  afterEach(() => {
    vi.useRealTimers();
  });

  it.each([
    [new Date(2026, 8, 15, 12), '202608'],
    [new Date(2026, 0, 15, 12), '202512'],
  ])('inicia em CSV com a competência do mês anterior', (today, competence) => {
    vi.useFakeTimers();
    vi.setSystemTime(today);

    const component = createComponent();

    expect(component.formatoSelecionado).toBe('csv');
    expect(component.competence).toBe(competence);
  });

  it('invalida as prévias quando a competência muda', () => {
    const component = createComponent();
    component.reports.forEach((report) => (report.previewed = true));

    component.onCompetenceChange('202607');

    expect(component.reports.every((report) => !report.previewed)).toBe(true);
  });

  it('mantém os downloads bloqueados enquanto as prévias não forem geradas', () => {
    const exportarLote = vi.fn();
    const exportar = vi.fn();
    const component = createComponent({ exportar, exportarLote } as Partial<RelatorioService>);
    component.reports.forEach((report) => {
      report.definition = { nome: report.api, consultaSQL: '', ordenacao: '', filtros: [] };
    });
    const report = component.selectedReports[0];

    expect(component.canDownloadReport(report)).toBe(false);
    component.download(report);
    component.downloadAll();
    expect(exportar).not.toHaveBeenCalled();
    expect(exportarLote).not.toHaveBeenCalled();

    component.selectedReports.forEach((selectedReport) => (selectedReport.previewed = true));

    expect(component.canDownloadReport(report)).toBe(true);
    expect(component.previewsGenerated).toBe(true);
  });
});
