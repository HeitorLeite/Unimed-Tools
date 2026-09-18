import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, firstValueFrom, forkJoin, of } from 'rxjs';
import { ReportPreviewComponent } from '../../shared/components/report-preview/report-preview.component';
import { SguApiDefinicao, SguResultado } from '../../shared/models/relatorio.model';
import { RelatorioService } from '../../shared/services/relatorio.service';
import { EMPRESAS_RELATORIOS } from '../relatorios/relatorios-automaticos/empresa-catalogo';
import { EmpresaSelectComponent } from '../relatorios/relatorios-automaticos/empresa-select.component';
import { chaveLogicaFiltro } from '../relatorios/relatorios-automaticos/grupo-filtros.utils';

interface ComercialReport {
  api: string;
  nome: string;
  descricao: string;
  arquivo: string;
  selected: boolean;
  definition?: SguApiDefinicao;
  records: Record<string, unknown>[];
  columns: string[];
  loading: boolean;
  error: string;
}

@Component({
  selector: 'app-comercial',
  standalone: true,
  imports: [CommonModule, FormsModule, EmpresaSelectComponent, ReportPreviewComponent],
  templateUrl: './comercial.component.html',
  styleUrl: './comercial.component.scss',
})
export class ComercialComponent implements OnInit {
  readonly companies = EMPRESAS_RELATORIOS;
  companyId = '';
  competence = this.currentCompetence();
  referenceDate = new Date().toISOString().slice(0, 10);
  loadingDefinitions = true;
  generating = false;
  downloadingAll = false;
  error = '';

  reports: ComercialReport[] = [
    { api: '0090-beneficiario-empresa', nome: 'Beneficiários', descricao: 'Base de beneficiários vinculados à empresa escolhida.', arquivo: 'beneficiarios', selected: true, records: [], columns: [], loading: false, error: '' },
    { api: '0090-receita-empresa-com-grupo', nome: 'Receita', descricao: 'Receita da empresa no período informado.', arquivo: 'receita', selected: true, records: [], columns: [], loading: false, error: '' },
    { api: '0090-despesa-empresas', nome: 'Despesas', descricao: 'Despesas assistenciais da empresa no período informado.', arquivo: 'despesas', selected: true, records: [], columns: [], loading: false, error: '' },
    { api: '0090-faixa-etaria', nome: 'Faixa etária', descricao: 'Distribuição etária calculada na data de referência.', arquivo: 'faixa_etaria', selected: true, records: [], columns: [], loading: false, error: '' },
  ];

  constructor(private readonly reportsService: RelatorioService) {}

  ngOnInit(): void {
    forkJoin(
      this.reports.map((report) =>
        this.reportsService.buscarApi(report.api).pipe(catchError(() => of(null))),
      ),
    ).subscribe((definitions) => {
      definitions.forEach((definition, index) => {
        if (definition) this.reports[index].definition = definition;
        else this.reports[index].error = 'API não encontrada no SGU.';
      });
      this.loadingDefinitions = false;
    });
  }

  selectCompany(id: string): void {
    this.companyId = id;
    this.clearPreview();
  }

  toggleReport(report: ComercialReport): void {
    report.selected = !report.selected;
  }

  get selectedReports(): ComercialReport[] {
    return this.reports.filter((report) => report.selected && report.definition);
  }

  get company() {
    return this.companies.find((company) => company.id === this.companyId);
  }

  async generatePreview(): Promise<void> {
    this.error = '';
    if (!this.company) {
      this.error = 'Selecione a empresa para continuar.';
      return;
    }
    if (!/^\d{6}$/.test(this.competence)) {
      this.error = 'Informe a competência no formato AAAAMM.';
      return;
    }
    if (!this.selectedReports.length) {
      this.error = 'Selecione pelo menos um relatório disponível.';
      return;
    }

    this.generating = true;
    for (const report of this.selectedReports) {
      report.loading = true;
      report.error = '';
      report.records = [];
      report.columns = [];
      try {
        const response = await firstValueFrom(
          this.reportsService.executar(report.api, { ...this.parameters(report), page: 1, size: 20 }),
        );
        this.applyPreview(report, response);
      } catch (error: any) {
        report.error = error?.error?.message || error?.message || 'Não foi possível gerar a prévia.';
      } finally {
        report.loading = false;
      }
    }
    this.generating = false;
  }

  download(report: ComercialReport): void {
    if (!report.definition || !this.company) return;
    const filename = `${this.safe(this.company.nome)}_${report.arquivo}_${this.competence}`;
    this.reportsService.exportar(report.api, 'xlsx', this.parameters(report), filename).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.Response && event.body) this.saveBlob(event.body, `${filename}.xlsx`);
      },
      error: (error: any) => report.error = error?.error?.message || 'Falha ao baixar o relatório.',
    });
  }

  downloadAll(): void {
    if (!this.company || !this.selectedReports.length || this.downloadingAll) return;
    this.downloadingAll = true;
    const request = {
      nomeArquivo: `comercial_${this.safe(this.company.nome)}_${this.competence}`,
      formato: 'xlsx' as const,
      itens: this.selectedReports.map((report) => ({
        apiNome: report.api,
        nomeArquivo: `${this.safe(this.company!.nome)}_${report.arquivo}_${this.competence}`,
        combinacoesFiltros: [this.parameters(report)],
      })),
    };
    this.reportsService.exportarLote(request).subscribe({
      next: (response) => {
        if (response.body) this.saveBlob(response.body, `${request.nomeArquivo}.zip`);
        this.downloadingAll = false;
      },
      error: () => {
        this.error = 'Não foi possível gerar o pacote de relatórios.';
        this.downloadingAll = false;
      },
    });
  }

  private parameters(report: ComercialReport): Record<string, unknown> {
    const definition = report.definition;
    if (!definition || !this.company) return {};
    const params: Record<string, unknown> = {};
    for (const filter of definition.filtros ?? []) {
      const normalized = this.normalize(filter.nomeFiltro);
      const logical = chaveLogicaFiltro(filter.nomeFiltro);
      if (logical === 'empresa') {
        params[filter.nomeFiltro] = this.company.codigos.join(',');
      } else if (logical === 'competencia' || normalized.includes('competencia')) {
        params[filter.nomeFiltro] = Number(this.competence);
      } else if (normalized.includes('datareferencia') || normalized.includes('referencia')) {
        params[filter.nomeFiltro] = this.formatDateForFilter(this.referenceDate, filter.mascaraFiltro);
      }
    }
    return params;
  }

  private applyPreview(report: ComercialReport, response: SguResultado): void {
    report.records = Array.isArray(response.content) ? response.content : [];
    report.columns = report.records.length ? Object.keys(report.records[0]) : [];
  }

  private clearPreview(): void {
    this.reports.forEach((report) => { report.records = []; report.columns = []; report.error = ''; });
  }

  private formatDateForFilter(value: string, mask: string): string {
    if (!value) return '';
    if ((mask || '').toUpperCase().includes('DD/MM/YYYY')) {
      const [year, month, day] = value.split('-');
      return `${day}/${month}/${year}`;
    }
    return value;
  }

  private currentCompetence(): string {
    const now = new Date();
    return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  private safe(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  }

  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
