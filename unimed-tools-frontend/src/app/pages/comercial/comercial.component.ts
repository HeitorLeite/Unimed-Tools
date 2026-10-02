import { baixarBlob } from '../../shared/utils/file.utils';
import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, firstValueFrom, forkJoin, of } from 'rxjs';
import { ReportPreviewComponent } from '../../shared/components/report-preview/report-preview.component';
import { ToolHelpComponent } from '../../shared/components/tool-help/tool-help.component';
import { TOOL_HELP_CONTENT } from '../../shared/constants/tool-help.constants';
import { SguApiDefinicao, SguResultado } from '../../shared/models/relatorio.model';
import { RelatorioService } from '../../shared/services/relatorio.service';
import {
  codigosCarteirinhaPorSituacao,
  EmpresaCatalogo,
  EMPRESAS_RELATORIOS,
  SituacaoCodigoCarteirinha,
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
  imports: [CommonModule, FormsModule, ReportPreviewComponent, ToolHelpComponent],
  templateUrl: './comercial.component.html',
  styleUrl: './comercial.component.scss',
})
export class ComercialComponent implements OnInit {
  readonly help = TOOL_HELP_CONTENT.comercial;

  formatoSelecionado: 'csv' | 'txt' | 'xlsx' = 'csv';
  readonly companies = EMPRESAS_RELATORIOS;
  selectedCompanyIds: string[] = [];
  companySearch = '';
  competence = this.previousCompetence();
  referenceDate = this.lastDayOfCompetence(this.competence);
  situacaoCodigoCarteirinha: SituacaoCodigoCarteirinha = 'TODOS';
  loadingDefinitions = true;
  generating = false;
  downloadingAll = false;
  generatingFinal = false;
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
      descricao: 'Tabela geral de titulares e dependentes de todas as empresas selecionadas.',
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

  get ageRangeSelected(): boolean {
    return this.reports.some(
      (report) =>
        report.api === '0090-faixa-etaria' && report.selected && Boolean(report.definition),
    );
  }

  get previewsGenerated(): boolean {
    return (
      this.selectedReports.length > 0 &&
      this.selectedReports.every((report) => report.previewed && !report.loading)
    );
  }

  get canGenerateFinal(): boolean {
    const ageRange = this.reports.find((report) => this.isAgeRange(report));
    return (
      this.selectedCompanies.length === 1 &&
      /^\d{6}$/.test(this.competence) &&
      !this.loadingDefinitions &&
      !this.generating &&
      !this.downloadingAll &&
      !this.generatingFinal &&
      this.reports.every((report) => report.selected && Boolean(report.definition)) &&
      Boolean(ageRange && this.ageRangeCardFilter(ageRange))
    );
  }

  canDownloadReport(report: ComercialReport): boolean {
    return (
      report.previewed &&
      !report.loading &&
      !report.downloading &&
      !this.generating &&
      !this.downloadingAll &&
      !this.generatingFinal &&
      Boolean(report.definition) &&
      this.selectedCompanies.length > 0
    );
  }

  isCompanySelected(id: string): boolean {
    return this.selectedCompanyIds.includes(id);
  }

  toggleCompany(id: string): void {
    if (this.generating || this.downloadingAll || this.generatingFinal) return;
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
    if (this.generating || this.downloadingAll || this.generatingFinal) return;
    this.selectedCompanyIds = [];
    this.clearPreview();
  }

  removeCompany(id: string): void {
    if (this.generating || this.downloadingAll || this.generatingFinal) return;
    this.selectedCompanyIds = this.selectedCompanyIds.filter((current) => current !== id);
    this.clearPreview();
  }

  toggleReport(report: ComercialReport): void {
    if (this.generating || this.downloadingAll || this.generatingFinal) return;
    report.selected = !report.selected;
    report.previewed = false;
    report.records = [];
    report.columns = [];

    if (report.api === '0090-faixa-etaria' && report.selected && !this.referenceDate) {
      this.referenceDate = this.lastDayOfCompetence(this.competence);
    }
  }

  onCompetenceChange(value: string): void {
    this.competence = value;
    if (/^\d{6}$/.test(value)) {
      this.referenceDate = this.lastDayOfCompetence(value);
    }
    this.clearPreview();
  }

  onReferenceDateChange(value: string): void {
    this.referenceDate = value;
    this.clearPreview();
  }

  onSituacaoCodigoCarteirinhaChange(value: SituacaoCodigoCarteirinha): void {
    if (this.generating || this.downloadingAll || this.generatingFinal) return;
    this.situacaoCodigoCarteirinha = value;
    this.clearPreview();
  }

  async generatePreview(): Promise<void> {
    if (this.generating || this.downloadingAll || this.generatingFinal) return;
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
      report.previewed = false;
      report.loading = true;
      report.error = '';
      report.records = [];
      report.columns = [];

      try {
        const combinations = this.parameterCombinations(report, company);
        const response = await firstValueFrom(
          this.reportsService.executar(report.api, {
            ...(this.isAgeRange(report)
              ? { combinacoesFiltros: this.ageRangeCombinations(report) }
              : (combinations[0] ?? {})),
            page: 1,
            size: 20,
          }),
        );
        this.applyPreview(report, response);
        report.previewed = true;
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
    const formato = this.formatoSelecionado;
    if (
      report.downloading ||
      this.generating ||
      this.downloadingAll ||
      this.generatingFinal ||
      !report.definition ||
      !this.selectedCompanies.length ||
      !report.previewed
    ) {
      return;
    }

    report.downloading = true;
    report.error = '';
    const company = this.selectedCompanies[0];
    const combinations = company ? this.parameterCombinations(report, company) : [];
    const companyLabel = this.companyFileLabel();

    if (
      this.isAgeRange(report) ||
      (this.selectedCompanies.length === 1 && combinations.length === 1)
    ) {
      const filename = `${companyLabel}_${report.arquivo}_${this.competence}`;
      this.reportsService
        .exportar(
          report.api,
          formato,
          this.isAgeRange(report)
            ? { combinacoesFiltros: this.ageRangeCombinations(report) }
            : combinations[0],
          filename,
        )
        .pipe(finalize(() => this.finishReportDownload(report)))
        .subscribe({
          next: (event) => {
            if (event.type === HttpEventType.Response && event.body) {
              this.saveBlob(event.body, `${filename}.${formato}`);
            }
          },
          error: (error: any) => {
            report.error =
              error?.error?.message || error?.message || 'Falha ao baixar o relatório.';
          },
        });
      return;
    }

    const request = {
      nomeArquivo: `comercial_${companyLabel}_${report.arquivo}_${this.competence}`,
      formato,
      itens: this.selectedCompanies.map((selectedCompany) => ({
        apiNome: report.api,
        nomeArquivo: `${this.safe(selectedCompany.nome)}_${report.arquivo}_${this.competence}`,
        combinacoesFiltros: this.parameterCombinations(report, selectedCompany),
      })),
    };

    this.reportsService
      .exportarLote(request)
      .pipe(finalize(() => this.finishReportDownload(report)))
      .subscribe({
        next: (response) => {
          if (response.body) this.saveBlob(response.body, `${request.nomeArquivo}.zip`);
          if (Number(response.headers.get('X-Relatorios-Erros')) > 0)
            report.error =
              'O pacote contém falhas. Confira o resumo de geração dentro do ZIP antes de usar os relatórios.';
        },
        error: () => {
          report.error = 'Não foi possível gerar o arquivo com todas as empresas.';
        },
      });
  }

  downloadFinal(): void {
    this.error = '';

    if (this.selectedCompanies.length !== 1) {
      this.error = 'Selecione exatamente uma empresa para gerar o relatório final.';
      return;
    }
    if (!/^\d{6}$/.test(this.competence)) {
      this.error = 'Informe a competência no formato AAAAMM.';
      return;
    }
    if (!this.reports.every((report) => report.selected)) {
      this.error = 'Selecione os quatro relatórios para gerar o relatório final.';
      return;
    }
    if (this.loadingDefinitions || this.reports.some((report) => !report.definition)) {
      this.error =
        'As quatro APIs do Comercial precisam estar disponíveis para gerar o relatório final.';
      return;
    }
    const ageRange = this.reports.find((report) => this.isAgeRange(report));
    if (!ageRange || !this.ageRangeCardFilter(ageRange)) {
      this.error =
        'A API de faixa etária ainda não possui o filtro codigoscarteirinha. Solicite à TI a publicação da definição atualizada no SGU.';
      return;
    }
    if (!this.canGenerateFinal) return;

    const company = this.selectedCompanies[0];
    const filtrosPorApi: Record<string, Record<string, unknown>[]> = {};

    for (const report of this.reports) {
      filtrosPorApi[report.api] = this.isAgeRange(report)
        ? this.ageRangeCombinations(report)
        : this.parameterCombinations(report, company);
    }

    this.generatingFinal = true;
    const filename = `sinistralidade_${this.safe(company.nome)}_${this.competence}.xlsx`;

    this.reportsService
      .exportarComercialFinal({
        empresa: company.nome,
        competencia: this.competence,
        filtrosPorApi,
      })
      .pipe(
        finalize(() => {
          this.generatingFinal = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.Response && event.body) {
            this.saveBlob(event.body, filename);
          }
        },
        error: (error: any) => {
          this.error =
            error?.error?.message ||
            error?.message ||
            'Não foi possível gerar o relatório final de sinistralidade.';
        },
      });
  }

  downloadAll(): void {
    const formato = this.formatoSelecionado;
    if (
      !this.selectedCompanies.length ||
      !this.selectedReports.length ||
      this.downloadingAll ||
      this.generating ||
      this.generatingFinal ||
      !this.previewsGenerated
    ) {
      return;
    }

    this.downloadingAll = true;
    this.error = '';
    const companyLabel = this.companyFileLabel();

    const request = {
      nomeArquivo: `comercial_${companyLabel}_${this.competence}`,
      formato,
      itens: this.selectedReports.flatMap((report) =>
        this.isAgeRange(report)
          ? [
              {
                apiNome: report.api,
                nomeArquivo: `${companyLabel}_${report.arquivo}_${this.competence}`,
                combinacoesFiltros: this.ageRangeCombinations(report),
              },
            ]
          : this.selectedCompanies.map((company) => ({
              apiNome: report.api,
              nomeArquivo: `${this.safe(company.nome)}_${report.arquivo}_${this.competence}`,
              combinacoesFiltros: this.parameterCombinations(report, company),
            })),
      ),
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
          if (Number(response.headers.get('X-Relatorios-Erros')) > 0)
            this.error =
              'O pacote contém falhas. Confira o resumo de geração dentro do ZIP antes de usar os relatórios.';
        },
        error: () => {
          this.error = 'Não foi possível gerar o pacote de relatórios.';
        },
      });
  }

  isAgeRange(report: ComercialReport): boolean {
    return report.api === '0090-faixa-etaria';
  }

  private ageRangeCombinations(report: ComercialReport): Record<string, unknown>[] {
    // Um código compartilhado por entradas do catálogo deve ser consultado só uma vez.
    const codigos = [...new Set(this.selectedCompanies.flatMap((company) => [...company.codigos]))];
    const filtroCarteirinha = this.ageRangeCardFilter(report);
    if (!filtroCarteirinha) {
      throw new Error(
        'A API de faixa etária ainda não possui o filtro codigoscarteirinha. Solicite à TI a publicação da definição atualizada no SGU.',
      );
    }

    const codigosCarteirinha =
      this.situacaoCodigoCarteirinha === 'TODOS'
        ? [...new Set(this.selectedCompanies.flatMap((company) => company.codigosCarteirinha))]
        : codigosCarteirinhaPorSituacao(this.selectedCompanies, this.situacaoCodigoCarteirinha);

    // O SGU interpola filtros NUMBER no bloco PL/SQL. Enviar "2152,2154" produz
    // uma expressão inválida; cada código precisa seguir em uma combinação própria.
    const valores = codigosCarteirinha.length
      ? codigosCarteirinha.map((codigo) => Number(codigo))
      : [0];

    return this.parameterCombinations(report, { codigos }).flatMap((combination) =>
      valores.map((codigo) => ({
        ...combination,
        [filtroCarteirinha.nomeFiltro]: codigo,
      })),
    );
  }

  private ageRangeCardFilter(report: ComercialReport) {
    return report.definition?.filtros?.find((filter) =>
      this.normalize(filter.nomeFiltro).includes('codigoscarteirinha'),
    );
  }

  private parameterCombinations(
    report: ComercialReport,
    company: Pick<EmpresaCatalogo, 'codigos'>,
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
          [filter.nomeFiltro]: this.formatDateForFilter(this.referenceDate, filter.mascaraFiltro),
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

  private previousCompetence(): string {
    const now = new Date();
    const previousMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    return `${previousMonth.getFullYear()}${String(previousMonth.getMonth() + 1).padStart(2, '0')}`;
  }

  private lastDayOfCompetence(competence: string): string {
    const match = /^(\d{4})(\d{2})$/.exec(competence);
    if (!match) return '';

    const year = Number(match[1]);
    const month = Number(match[2]);
    if (month < 1 || month > 12) return '';

    const lastDay = new Date(year, month, 0).getDate();
    return `${match[1]}-${match[2]}-${String(lastDay).padStart(2, '0')}`;
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
    baixarBlob(blob, filename);
  }
}
