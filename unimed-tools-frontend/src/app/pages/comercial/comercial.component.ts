import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, firstValueFrom, forkJoin, of } from 'rxjs';
import { ReportPreviewComponent } from '../../shared/components/report-preview/report-preview.component';
import { SguApiDefinicao, SguResultado } from '../../shared/models/relatorio.model';
import { RelatorioService } from '../../shared/services/relatorio.service';
import {
  EmpresaCatalogo,
  EMPRESAS_RELATORIOS,
} from '../relatorios/relatorios-automaticos/empresa-catalogo';
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
  previewed: boolean;
  downloading: boolean;
  error: string;
}

@Component({
  selector: 'app-comercial',
  standalone: true,
  imports: [CommonModule, FormsModule, ReportPreviewComponent],
  templateUrl: './comercial.component.html',
  styleUrl: './comercial.component.scss',
})
export class ComercialComponent implements OnInit {
  readonly companies = EMPRESAS_RELATORIOS;
  selectedCompanyIds: string[] = [];
  companySearch = '';
  competence = this.currentCompetence();
  referenceDate = new Date().toISOString().slice(0, 10);
  loadingDefinitions = true;
  generating = false;
  downloadingAll = false;
  error = '';

  reports: ComercialReport[] = [
    {
      api: '0090-beneficiario-empresa',
      nome: 'Beneficiários',
      descricao: 'Base de beneficiários vinculados às empresas escolhidas.',
      arquivo: 'beneficiarios',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      previewed: false,
      downloading: false,
      error: '',
    },
    {
      api: '0090-receita-empresa-com-grupo',
      nome: 'Receita',
      descricao: 'Receita das empresas no período informado.',
      arquivo: 'receita',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      previewed: false,
      downloading: false,
      error: '',
    },
    {
      api: '0090-despesa-empresas',
      nome: 'Despesas',
      descricao: 'Despesas assistenciais das empresas no período informado.',
      arquivo: 'despesas',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      previewed: false,
      downloading: false,
      error: '',
    },
    {
      api: '0090-faixa-etaria',
      nome: 'Faixa etária',
      descricao: 'Distribuição etária calculada na data de referência.',
      arquivo: 'faixa_etaria',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      previewed: false,
      downloading: false,
      error: '',
    },
  ];

  constructor(
    private readonly reportsService: RelatorioService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

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
      this.cdr.markForCheck();
    });
  }

  get filteredCompanies(): readonly EmpresaCatalogo[] {
    const term = this.normalize(this.companySearch);
    if (!term) return this.companies;
    return this.companies.filter((company) => this.normalize(company.nome).includes(term));
  }

  get selectedCompanies(): EmpresaCatalogo[] {
    const byId = new Map(this.companies.map((company) => [company.id, company]));
    return this.selectedCompanyIds
      .map((id) => byId.get(id))
      .filter((company): company is EmpresaCatalogo => Boolean(company));
  }

  get previewCompany(): EmpresaCatalogo | undefined {
    return this.selectedCompanies[0];
  }

  get selectedReports(): ComercialReport[] {
    return this.reports.filter((report) => report.selected && report.definition);
  }

  get hasPreview(): boolean {
    return this.selectedReports.some((report) => report.previewed || report.loading);
  }

  isCompanySelected(id: string): boolean {
    return this.selectedCompanyIds.includes(id);
  }

  toggleCompany(id: string): void {
    if (this.generating || this.downloadingAll) return;
    if (this.isCompanySelected(id)) {
      this.selectedCompanyIds = this.selectedCompanyIds.filter((current) => current !== id);
    } else {
      this.selectedCompanyIds = [...this.selectedCompanyIds, id];
    }
    this.clearPreview();
  }

  selectVisibleCompanies(): void {
    const visibleIds = this.filteredCompanies.map((company) => company.id);
    this.selectedCompanyIds = [
      ...this.selectedCompanyIds,
      ...visibleIds.filter((id) => !this.selectedCompanyIds.includes(id)),
    ];
    this.clearPreview();
  }

  clearCompanies(): void {
    if (this.generating || this.downloadingAll) return;
    this.selectedCompanyIds = [];
    this.clearPreview();
  }

  removeCompany(id: string): void {
    if (this.generating || this.downloadingAll) return;
    this.selectedCompanyIds = this.selectedCompanyIds.filter((current) => current !== id);
    this.clearPreview();
  }

  toggleReport(report: ComercialReport): void {
    if (this.generating || this.downloadingAll) return;
    report.selected = !report.selected;
    report.previewed = false;
    report.records = [];
    report.columns = [];
  }

  async generatePreview(): Promise<void> {
    if (this.generating || this.downloadingAll) return;
    this.error = '';

    const company = this.previewCompany;
    if (!company) {
      this.error = 'Selecione pelo menos uma empresa para continuar.';
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
      report.previewed = true;
      report.loading = true;
      report.error = '';
      report.records = [];
      report.columns = [];

      try {
        const combinations = this.parameterCombinations(report, company);
        const response = await firstValueFrom(
          this.reportsService.executar(report.api, {
            ...(combinations[0] ?? {}),
            page: 1,
            size: 20,
          }),
        );
        this.applyPreview(report, response);
      } catch (error: any) {
        report.error =
          error?.error?.message || error?.message || 'Não foi possível gerar a prévia.';
      } finally {
        report.loading = false;
        this.cdr.markForCheck();
      }
    }

    this.generating = false;
    this.cdr.markForCheck();
  }

  download(report: ComercialReport): void {
    if (
      report.downloading ||
      this.generating ||
      this.downloadingAll ||
      !report.definition ||
      !this.selectedCompanies.length
    ) {
      return;
    }

    report.downloading = true;
    report.error = '';
    const combinations = this.selectedCompanies.flatMap((company) =>
      this.parameterCombinations(report, company),
    );
    const companyLabel = this.companyFileLabel();

    if (this.selectedCompanies.length === 1 && combinations.length === 1) {
      const filename = `${companyLabel}_${report.arquivo}_${this.competence}`;
      this.reportsService
        .exportar(report.api, 'xlsx', combinations[0], filename)
        .pipe(finalize(() => this.finishReportDownload(report)))
        .subscribe({
          next: (event) => {
            if (event.type === HttpEventType.Response && event.body) {
              this.saveBlob(event.body, `${filename}.xlsx`);
            }
          },
          error: (error: any) => {
            report.error = error?.error?.message || 'Falha ao baixar o relatório.';
          },
        });
      return;
    }

    const request = {
      nomeArquivo: `comercial_${companyLabel}_${report.arquivo}_${this.competence}`,
      formato: 'xlsx' as const,
      itens: [
        {
          apiNome: report.api,
          nomeArquivo: `${companyLabel}_${report.arquivo}_${this.competence}`,
          combinacoesFiltros: combinations,
        },
      ],
    };

    this.reportsService
      .exportarLote(request)
      .pipe(finalize(() => this.finishReportDownload(report)))
      .subscribe({
        next: (response) => {
          if (response.body) this.saveBlob(response.body, `${request.nomeArquivo}.zip`);
        },
        error: () => {
          report.error = 'Não foi possível gerar o arquivo com todas as empresas.';
        },
      });
  }

  downloadAll(): void {
    if (
      !this.selectedCompanies.length ||
      !this.selectedReports.length ||
      this.downloadingAll ||
      this.generating
    ) {
      return;
    }

    this.downloadingAll = true;
    this.error = '';
    const companyLabel = this.companyFileLabel();

    const request = {
      nomeArquivo: `comercial_${companyLabel}_${this.competence}`,
      formato: 'xlsx' as const,
      itens: this.selectedReports.map((report) => ({
        apiNome: report.api,
        nomeArquivo: `${companyLabel}_${report.arquivo}_${this.competence}`,
        combinacoesFiltros: this.selectedCompanies.flatMap((company) =>
          this.parameterCombinations(report, company),
        ),
      })),
    };

    this.reportsService
      .exportarLote(request)
      .pipe(
        finalize(() => {
          this.downloadingAll = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (response) => {
          if (response.body) this.saveBlob(response.body, `${request.nomeArquivo}.zip`);
        },
        error: () => {
          this.error = 'Não foi possível gerar o pacote de relatórios.';
        },
      });
  }

  private parameterCombinations(
    report: ComercialReport,
    company: EmpresaCatalogo,
  ): Record<string, unknown>[] {
    const definition = report.definition;
    if (!definition) return [{}];

    let combinations: Record<string, unknown>[] = [{}];

    for (const filter of definition.filtros ?? []) {
      const normalized = this.normalize(filter.nomeFiltro);
      const logical = chaveLogicaFiltro(filter.nomeFiltro);

      if (logical === 'empresa') {
        const numeric = filter.tipoDadoFiltro?.toUpperCase() === 'NUMBER';
        const values = numeric ? company.codigos : [company.codigos.join(',')];

        combinations = combinations.flatMap((combination) =>
          values.map((value) => ({
            ...combination,
            [filter.nomeFiltro]: numeric ? Number(value) : value,
          })),
        );
        continue;
      }

      if (logical === 'competencia' || normalized.includes('competencia')) {
        combinations = combinations.map((combination) => ({
          ...combination,
          [filter.nomeFiltro]: Number(this.competence),
        }));
        continue;
      }

      if (normalized.includes('datareferencia') || normalized.includes('referencia')) {
        combinations = combinations.map((combination) => ({
          ...combination,
          [filter.nomeFiltro]: this.formatDateForFilter(
            this.referenceDate,
            filter.mascaraFiltro,
          ),
        }));
      }
    }

    return combinations;
  }

  private applyPreview(report: ComercialReport, response: SguResultado): void {
    report.records = Array.isArray(response.content) ? response.content : [];
    report.columns = report.records.length ? Object.keys(report.records[0]) : [];
  }

  private clearPreview(): void {
    this.reports.forEach((report) => {
      report.records = [];
      report.columns = [];
      report.error = '';
      report.previewed = false;
    });
  }

  private finishReportDownload(report: ComercialReport): void {
    report.downloading = false;
    this.cdr.markForCheck();
  }

  private companyFileLabel(): string {
    const selected = this.selectedCompanies;
    if (selected.length === 1) return this.safe(selected[0].nome);
    return `${selected.length}_empresas`;
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
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
