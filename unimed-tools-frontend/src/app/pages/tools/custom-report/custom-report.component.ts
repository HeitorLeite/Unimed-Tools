import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { ReportPreviewComponent } from '../../../shared/components/report-preview/report-preview.component';
import { SguApiDefinicao, SguFiltro } from '../../../shared/models/relatorio.model';
import { CustomReportTool } from '../../../shared/models/tool.model';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import { ToolRegistryService } from '../../../shared/services/tool-registry.service';

@Component({
  selector: 'app-custom-report',
  standalone: true,
  imports: [CommonModule, FormsModule, ReportPreviewComponent],
  templateUrl: './custom-report.component.html',
  styleUrl: './custom-report.component.scss',
})
export class CustomReportComponent implements OnInit {
  tool: CustomReportTool | null = null;
  definition: SguApiDefinicao | null = null;
  filters: SguFiltro[] = [];
  values: Record<string, string> = {};
  records: Record<string, unknown>[] = [];
  columns: string[] = [];
  page = 1;
  pageSize = 25;
  last = false;
  loading = true;
  generating = false;
  exporting = false;
  error = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly registry: ToolRegistryService,
    private readonly reports: RelatorioService,
  ) {}

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug') ?? '';
    this.registry.refresh().pipe(
      switchMap(() => {
        const tool = this.registry.findCustom(slug);
        if (!tool) throw new Error('Ferramenta não encontrada ou desativada.');
        this.tool = tool;
        this.registry.recordOpened(`custom-${tool.id}`);
        return this.reports.buscarApi(tool.apiNome);
      }),
      finalize(() => this.loading = false),
    ).subscribe({
      next: (definition) => {
        this.definition = definition;
        const allowed = new Set(this.tool?.filtros ?? []);
        this.filters = allowed.size
          ? (definition.filtros ?? []).filter((filter) => allowed.has(filter.nomeFiltro))
          : definition.filtros ?? [];
        this.values = Object.fromEntries(this.filters.map((filter) => [filter.nomeFiltro, '']));
      },
      error: (error: any) => this.error = error?.error?.message || error?.message || 'Não foi possível abrir a ferramenta.',
    });
  }

  generate(page = 1): void {
    if (!this.definition || !this.tool || this.generating) return;
    const missing = this.filters.find(
      (filter) => filter.obrigatorioFiltro === 'S' && !this.values[filter.nomeFiltro]?.trim(),
    );
    if (missing) {
      this.error = `Preencha o filtro obrigatório “${this.label(missing.nomeFiltro)}”.`;
      return;
    }
    this.generating = true;
    this.error = '';
    this.reports.executar(this.tool.apiNome, {
      ...this.parameters(),
      page,
      size: this.pageSize,
    }).pipe(finalize(() => this.generating = false)).subscribe({
      next: (response) => {
        this.records = Array.isArray(response.content) ? response.content : [];
        const preferred = (this.tool?.colunasPreview ?? [])
          .filter((column) => this.records.some((row) => Object.prototype.hasOwnProperty.call(row, column)));
        this.columns = preferred.length
          ? preferred
          : this.records.length
            ? Object.keys(this.records[0])
            : this.tool?.colunasPreview ?? [];
        this.page = page;
        this.last = Boolean(response.last) || this.records.length < this.pageSize;
      },
      error: (error: any) => this.error = error?.error?.message || error?.message || 'Não foi possível gerar a prévia.',
    });
  }

  download(): void {
    if (!this.tool || this.exporting) return;
    this.exporting = true;
    const filename = this.safe(this.tool.nome);
    this.reports.exportar(this.tool.apiNome, 'xlsx', this.parameters(), filename)
      .pipe(finalize(() => this.exporting = false))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.Response && event.body) {
            const url = URL.createObjectURL(event.body);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${filename}.xlsx`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(() => URL.revokeObjectURL(url), 0);
          }
        },
        error: (error: any) => this.error = error?.error?.message || 'Não foi possível baixar o relatório.',
      });
  }

  back(): void {
    void this.router.navigateByUrl('/');
  }

  inputType(filter: SguFiltro): string {
    const name = this.normalize(filter.nomeFiltro);
    const mask = (filter.mascaraFiltro || '').toUpperCase();
    if (mask.includes('DD/MM/YYYY') || name.includes('data')) return 'date';
    if (filter.tipoDadoFiltro?.toUpperCase().includes('NUMBER')) return 'number';
    return 'text';
  }

  label(value: string): string {
    return value.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  private parameters(): Record<string, unknown> {
    const output: Record<string, unknown> = {};
    for (const filter of this.filters) {
      const raw = this.values[filter.nomeFiltro]?.trim();
      if (!raw) continue;
      output[filter.nomeFiltro] = this.formatValue(filter, raw);
    }
    return output;
  }

  private formatValue(filter: SguFiltro, value: string): unknown {
    const type = filter.tipoDadoFiltro?.toUpperCase() ?? '';
    if (type.includes('NUMBER') && /^-?\d+(?:[.,]\d+)?$/.test(value)) {
      return Number(value.replace(',', '.'));
    }
    if ((filter.mascaraFiltro || '').toUpperCase().includes('DD/MM/YYYY') && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
      const [year, month, day] = value.split('-');
      return `${day}/${month}/${year}`;
    }
    return value;
  }

  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');
  }

  private safe(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
      .replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'relatorio';
  }
}
