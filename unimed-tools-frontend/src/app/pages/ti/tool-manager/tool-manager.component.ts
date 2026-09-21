import { CommonModule } from '@angular/common';
<<<<<<< HEAD
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
=======
import { Component, OnInit } from '@angular/core';
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { CustomReportTool } from '../../../shared/models/tool.model';
import { SguApiDefinicao } from '../../../shared/models/relatorio.model';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import { ToolRegistryService } from '../../../shared/services/tool-registry.service';
import { NativeToolManagerComponent } from '../native-tool-manager/native-tool-manager.component';

@Component({
  selector: 'app-tool-manager',
  standalone: true,
  imports: [CommonModule, FormsModule, NativeToolManagerComponent],
  templateUrl: './tool-manager.component.html',
  styleUrl: './tool-manager.component.scss',
})
export class ToolManagerComponent implements OnInit {
  tools: CustomReportTool[] = [];
  apiDefinition: SguApiDefinicao | null = null;
  editingId = '';
  nome = '';
  slug = '';
  descricao = '';
  apiNome = '';
  selectedFilters: Record<string, boolean> = {};
  previewColumns = '';
  loadingApi = false;
  saving = false;
  error = '';
  success = '';

  constructor(
    private readonly registry: ToolRegistryService,
    private readonly reports: RelatorioService,
<<<<<<< HEAD
    private readonly cdr: ChangeDetectorRef,
=======
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
<<<<<<< HEAD
    this.registry
      .refresh()
      .pipe(finalize(() => this.cdr.markForCheck()))
      .subscribe({
        next: () => (this.tools = this.registry.listCustom()),
        error: (error: any) =>
          (this.error =
            error?.error?.message || 'Não foi possível carregar as ferramentas configuráveis.'),
      });
=======
    this.registry.refresh().subscribe({
      next: () => this.tools = this.registry.listCustom(),
      error: (error: any) =>
        this.error = error?.error?.message || 'Não foi possível carregar as ferramentas configuráveis.',
    });
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  loadApi(): void {
    const api = this.apiNome.trim();
    if (!api || this.loadingApi) return;
    this.loadingApi = true;
    this.error = '';
<<<<<<< HEAD
    this.reports
      .buscarApi(api)
      .pipe(
        finalize(() => {
          this.loadingApi = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (definition) => {
          this.apiDefinition = definition;
          const selected = new Set(
            Object.keys(this.selectedFilters).filter((key) => this.selectedFilters[key]),
          );
          this.selectedFilters = {};
          for (const filter of definition.filtros ?? []) {
            this.selectedFilters[filter.nomeFiltro] = selected.size
              ? selected.has(filter.nomeFiltro)
              : true;
          }
        },
        error: (error: any) => {
          this.apiDefinition = null;
          this.error = error?.error?.message || error?.message || 'API não encontrada.';
        },
      });
=======
    this.reports.buscarApi(api).pipe(finalize(() => this.loadingApi = false)).subscribe({
      next: (definition) => {
        this.apiDefinition = definition;
        const selected = new Set(
          Object.keys(this.selectedFilters).filter((key) => this.selectedFilters[key]),
        );
        this.selectedFilters = {};
        for (const filter of definition.filtros ?? []) {
          this.selectedFilters[filter.nomeFiltro] =
            selected.size ? selected.has(filter.nomeFiltro) : true;
        }
      },
      error: (error: any) => {
        this.apiDefinition = null;
        this.error = error?.error?.message || error?.message || 'API não encontrada.';
      },
    });
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  edit(tool: CustomReportTool): void {
    this.editingId = tool.id;
    this.nome = tool.nome;
    this.slug = tool.slug;
    this.descricao = tool.descricao;
    this.apiNome = tool.apiNome;
    this.previewColumns = tool.colunasPreview.join(', ');
    this.selectedFilters = Object.fromEntries(tool.filtros.map((filter) => [filter, true]));
    this.loadApi();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  save(): void {
    if (this.saving) return;
    if (!this.nome.trim() || !this.descricao.trim() || !this.apiNome.trim()) {
      this.error = 'Preencha nome, descrição e API.';
      return;
    }
    this.saving = true;
    this.error = '';
    this.success = '';
    const filtros = Object.entries(this.selectedFilters)
      .filter(([, selected]) => selected)
      .map(([name]) => name);
<<<<<<< HEAD
    const columns = this.previewColumns
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
    try {
      this.registry
        .saveCustom({
          ...(this.editingId ? { id: this.editingId } : {}),
          slug: this.slug,
          nome: this.nome,
          descricao: this.descricao,
          apiNome: this.apiNome,
          filtros,
          colunasPreview: columns,
        })
        .pipe(
          finalize(() => {
            this.saving = false;
            this.cdr.markForCheck();
          }),
        )
        .subscribe({
          next: (saved) => {
            this.success = `Ferramenta “${saved.nome}” salva e disponibilizada na Home.`;
            this.tools = this.registry.listCustom();
            this.resetForm(false);
          },
          error: (error: any) =>
            (this.error =
              error?.error?.message || error?.message || 'Não foi possível salvar a ferramenta.'),
        });
=======
    const columns = this.previewColumns.split(',').map((item) => item.trim()).filter(Boolean);
    try {
      this.registry.saveCustom({
        ...(this.editingId ? { id: this.editingId } : {}),
        slug: this.slug,
        nome: this.nome,
        descricao: this.descricao,
        apiNome: this.apiNome,
        filtros,
        colunasPreview: columns,
      }).pipe(finalize(() => this.saving = false)).subscribe({
        next: (saved) => {
          this.success =
            `Ferramenta “${saved.nome}” salva e disponibilizada na Home.`;
          this.tools = this.registry.listCustom();
          this.resetForm(false);
        },
        error: (error: any) =>
          this.error = error?.error?.message || error?.message || 'Não foi possível salvar a ferramenta.',
      });
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    } catch (error) {
      this.saving = false;
      this.error = error instanceof Error ? error.message : 'Não foi possível salvar a ferramenta.';
    }
  }

  remove(tool: CustomReportTool): void {
    if (!window.confirm(`Remover a ferramenta “${tool.nome}” da aplicação?`)) return;
<<<<<<< HEAD
    this.registry
      .deleteCustom(tool.id)
      .pipe(finalize(() => this.cdr.markForCheck()))
      .subscribe({
        next: () => {
          this.tools = this.registry.listCustom();
          if (this.editingId === tool.id) this.resetForm();
        },
        error: (error: any) =>
          (this.error = error?.error?.message || 'Não foi possível remover a ferramenta.'),
      });
=======
    this.registry.deleteCustom(tool.id).subscribe({
      next: () => {
        this.tools = this.registry.listCustom();
        if (this.editingId === tool.id) this.resetForm();
      },
      error: (error: any) =>
        this.error = error?.error?.message || 'Não foi possível remover a ferramenta.',
    });
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  label(value: string): string {
    return value.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  private resetForm(clearMessages = true): void {
    this.editingId = '';
    this.nome = '';
    this.slug = '';
    this.descricao = '';
    this.apiNome = '';
    this.selectedFilters = {};
    this.previewColumns = '';
    this.apiDefinition = null;
    if (clearMessages) {
      this.error = '';
      this.success = '';
    }
  }
}
