import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
<<<<<<< HEAD
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, forkJoin, of } from 'rxjs';
=======
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
import { ReportPreviewComponent } from '../../shared/components/report-preview/report-preview.component';
import { SguApiDefinicao, SguResultado } from '../../shared/models/relatorio.model';
import { RelatorioService } from '../../shared/services/relatorio.service';

interface RiskReport {
  api: string;
  nome: string;
  descricao: string;
  arquivo: string;
  selected: boolean;
  definition?: SguApiDefinicao;
  records: Record<string, unknown>[];
  columns: string[];
  loading: boolean;
<<<<<<< HEAD
  previewed?: boolean;
  downloading?: boolean;
=======
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  error: string;
}

@Component({
  selector: 'app-gestao-risco',
  standalone: true,
  imports: [CommonModule, FormsModule, ReportPreviewComponent],
  templateUrl: './gestao-risco.component.html',
  styleUrl: './gestao-risco.component.scss',
})
export class GestaoRiscoComponent implements OnInit {
  competence = this.currentCompetence();
  additionalValues: Record<string, string> = {};
  loadingDefinitions = true;
  generating = false;
<<<<<<< HEAD
  downloadingAll = false;
  error = '';

  reports: RiskReport[] = [
    {
      api: '0090-colonoscopia-ramiro',
      nome: 'Colonoscopia',
      descricao: 'Acompanhamento de colonoscopias no período.',
      arquivo: 'colonoscopia',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      error: '',
    },
    {
      api: '0090-consultas-ramiro',
      nome: 'Consultas',
      descricao: 'Consultas acompanhadas pela Gestão de Risco.',
      arquivo: 'consultas',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      error: '',
    },
    {
      api: '0090-mamografia-ramiro',
      nome: 'Mamografia',
      descricao: 'Acompanhamento de mamografias no período.',
      arquivo: 'mamografia',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      error: '',
    },
    {
      api: '0090-ressonancia-ramiro',
      nome: 'Ressonância',
      descricao: 'Acompanhamento de ressonâncias no período.',
      arquivo: 'ressonancia',
      selected: true,
      records: [],
      columns: [],
      loading: false,
      error: '',
    },
    {
      api: '0090-sangue-oculto-ramiro',
      nome: 'Sangue oculto',
      descricao: 'Acompanhamento de exames de sangue oculto.',
      arquivo: 'sangue_oculto',
      selected: true,
      records: [],
      columns: [],
      loading: false,
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
=======
  error = '';

  reports: RiskReport[] = [
    { api: '0090-colonoscopia-ramiro', nome: 'Colonoscopia', descricao: 'Acompanhamento de colonoscopias no período.', arquivo: 'colonoscopia', selected: true, records: [], columns: [], loading: false, error: '' },
    { api: '0090-consultas-ramiro', nome: 'Consultas', descricao: 'Consultas acompanhadas pela Gestão de Risco.', arquivo: 'consultas', selected: true, records: [], columns: [], loading: false, error: '' },
    { api: '0090-mamografia-ramiro', nome: 'Mamografia', descricao: 'Acompanhamento de mamografias no período.', arquivo: 'mamografia', selected: true, records: [], columns: [], loading: false, error: '' },
    { api: '0090-ressonancia-ramiro', nome: 'Ressonância', descricao: 'Acompanhamento de ressonâncias no período.', arquivo: 'ressonancia', selected: true, records: [], columns: [], loading: false, error: '' },
    { api: '0090-sangue-oculto-ramiro', nome: 'Sangue oculto', descricao: 'Acompanhamento de exames de sangue oculto.', arquivo: 'sangue_oculto', selected: true, records: [], columns: [], loading: false, error: '' },
  ];

  constructor(private readonly reportsService: RelatorioService) {}

  ngOnInit(): void {
    forkJoin(this.reports.map((report) => this.reportsService.buscarApi(report.api).pipe(catchError(() => of(null)))))
      .subscribe((definitions) => {
        definitions.forEach((definition, index) => {
          if (definition) this.reports[index].definition = definition;
          else this.reports[index].error = 'API não encontrada no SGU.';
        });
        this.loadingDefinitions = false;
      });
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  get selectedReports(): RiskReport[] {
    return this.reports.filter((report) => report.selected && report.definition);
  }

  get extraFilters(): string[] {
    const filters = this.reports.flatMap((report) => report.definition?.filtros ?? []);
<<<<<<< HEAD
    return [
      ...new Set(
        filters
          .filter((filter) => !this.normalize(filter.nomeFiltro).includes('compet'))
          .filter((filter) => filter.obrigatorioFiltro === 'S')
          .map((filter) => filter.nomeFiltro),
      ),
    ];
  }

  get hasPreview(): boolean {
    return this.selectedReports.some((report) => report.previewed || report.loading);
=======
    return [...new Set(filters
      .filter((filter) => !this.normalize(filter.nomeFiltro).includes('compet'))
      .filter((filter) => filter.obrigatorioFiltro === 'S')
      .map((filter) => filter.nomeFiltro))];
  }

  get hasPreview(): boolean {
    return this.selectedReports.some((report) => report.records.length > 0 || report.loading);
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  toggle(report: RiskReport): void {
    report.selected = !report.selected;
  }

  async generate(): Promise<void> {
<<<<<<< HEAD
    if (this.generating || this.downloadingAll) return;
=======
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    this.error = '';
    if (!/^\d{6}$/.test(this.competence)) {
      this.error = 'Informe a competência no formato AAAAMM.';
      return;
    }
    if (!this.selectedReports.length) {
      this.error = 'Selecione pelo menos um relatório disponível.';
      return;
    }
    const missing = this.extraFilters.find((filter) => !this.additionalValues[filter]?.trim());
    if (missing) {
      this.error = `Preencha o filtro obrigatório “${this.label(missing)}”.`;
      return;
    }

    this.generating = true;
    for (const report of this.selectedReports) {
<<<<<<< HEAD
      report.previewed = true;
      report.loading = true;
      report.error = '';
      report.records = [];
      report.columns = [];
      try {
        const response = await new Promise<SguResultado>((resolve, reject) => {
          this.reportsService
            .executar(report.api, { ...this.parameters(report), page: 1, size: 20 })
            .subscribe({ next: resolve, error: reject });
=======
      report.loading = true; report.error = ''; report.records = []; report.columns = [];
      try {
        const response = await new Promise<SguResultado>((resolve, reject) => {
          this.reportsService.executar(report.api, { ...this.parameters(report), page: 1, size: 20 }).subscribe({ next: resolve, error: reject });
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
        });
        report.records = Array.isArray(response.content) ? response.content : [];
        report.columns = report.records.length ? Object.keys(report.records[0]) : [];
      } catch (error: any) {
<<<<<<< HEAD
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

  download(report: RiskReport): void {
    if (report.downloading || this.generating || this.downloadingAll) return;
    report.downloading = true;
    report.error = '';
    const filename = `gestao_risco_${report.arquivo}_${this.competence}`;
    this.reportsService
      .exportar(report.api, 'xlsx', this.parameters(report), filename)
      .pipe(
        finalize(() => {
          report.downloading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.Response && event.body)
            this.save(event.body, `${filename}.xlsx`);
        },
        error: () => (report.error = 'Não foi possível baixar o relatório.'),
      });
  }

  downloadAll(): void {
    if (!this.selectedReports.length || this.downloadingAll || this.generating) return;
    this.downloadingAll = true;
    this.error = '';
=======
        report.error = error?.error?.message || error?.message || 'Não foi possível gerar a prévia.';
      } finally {
        report.loading = false;
      }
    }
    this.generating = false;
  }

  download(report: RiskReport): void {
    const filename = `gestao_risco_${report.arquivo}_${this.competence}`;
    this.reportsService.exportar(report.api, 'xlsx', this.parameters(report), filename).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.Response && event.body) this.save(event.body, `${filename}.xlsx`);
      },
      error: () => report.error = 'Não foi possível baixar o relatório.',
    });
  }

  downloadAll(): void {
    if (!this.selectedReports.length) return;
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    const request = {
      nomeArquivo: `gestao_risco_${this.competence}`,
      formato: 'xlsx' as const,
      itens: this.selectedReports.map((report) => ({
        apiNome: report.api,
        nomeArquivo: `${report.arquivo}_${this.competence}`,
        combinacoesFiltros: [this.parameters(report)],
      })),
    };
<<<<<<< HEAD
    this.reportsService
      .exportarLote(request)
      .pipe(
        finalize(() => {
          this.downloadingAll = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (response) => response.body && this.save(response.body, `${request.nomeArquivo}.zip`),
        error: () => (this.error = 'Não foi possível gerar o pacote de relatórios.'),
      });
=======
    this.reportsService.exportarLote(request).subscribe({
      next: (response) => response.body && this.save(response.body, `${request.nomeArquivo}.zip`),
      error: () => this.error = 'Não foi possível gerar o pacote de relatórios.',
    });
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  label(value: string): string {
    return value.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  private parameters(report: RiskReport): Record<string, unknown> {
    const params: Record<string, unknown> = {};
    for (const filter of report.definition?.filtros ?? []) {
<<<<<<< HEAD
      if (this.normalize(filter.nomeFiltro).includes('compet'))
        params[filter.nomeFiltro] = Number(this.competence);
      else if (this.additionalValues[filter.nomeFiltro]?.trim())
        params[filter.nomeFiltro] = this.additionalValues[filter.nomeFiltro].trim();
=======
      if (this.normalize(filter.nomeFiltro).includes('compet')) params[filter.nomeFiltro] = Number(this.competence);
      else if (this.additionalValues[filter.nomeFiltro]?.trim()) params[filter.nomeFiltro] = this.additionalValues[filter.nomeFiltro].trim();
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    }
    return params;
  }

  private normalize(value: string): string {
<<<<<<< HEAD
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
=======
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  private currentCompetence(): string {
    const date = new Date();
    return `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}`;
  }

  private save(blob: Blob, filename: string): void {
<<<<<<< HEAD
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
=======
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
