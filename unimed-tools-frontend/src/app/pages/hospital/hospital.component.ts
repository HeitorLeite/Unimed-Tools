import { CommonModule } from '@angular/common';
import { HttpEventType } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ReportPreviewComponent } from '../../shared/components/report-preview/report-preview.component';
import { HospitalConfiguration } from '../../shared/models/hospital.model';
import { RelatorioService } from '../../shared/services/relatorio.service';

@Component({
  selector: 'app-hospital',
  standalone: true,
  imports: [CommonModule, FormsModule, ReportPreviewComponent],
  templateUrl: './hospital.component.html',
  styleUrl: './hospital.component.scss',
})
export class HospitalComponent implements OnInit {
  config: HospitalConfiguration | null = null;
  values: Record<string, string> = {};
  records: Record<string, unknown>[] = [];
  columns: string[] = [];
  page = 1;
  pageSize = 25;
  last = false;
  total: number | null = null;
  loadingConfig = true;
  generating = false;
  exporting = false;
  error = '';

  constructor(private readonly reports: RelatorioService) {}

  ngOnInit(): void {
    this.reports.configuracaoHospital().pipe(finalize(() => this.loadingConfig = false)).subscribe({
      next: (config) => {
        this.config = config;
        this.columns = config.colunas;
        this.values = Object.fromEntries(config.filtros.map((filter) => [filter.id, '']));
      },
      error: (error: any) => this.error = error?.error?.message || 'Não foi possível carregar o relatório Hospital.',
    });
  }

  generate(page = 1): void {
    if (!this.config || this.generating) return;
    this.generating = true;
    this.error = '';
    this.reports.executarHospital({
      filtros: this.activeFilters(),
      pagina: page,
      tamanhoPagina: this.pageSize,
      nomeArquivo: 'hospital_autorizacoes',
    }).pipe(finalize(() => this.generating = false)).subscribe({
      next: (response) => {
        this.records = Array.isArray(response.content) ? response.content : [];
        this.columns = this.records.length ? Object.keys(this.records[0]) : this.config!.colunas;
        this.page = page;
        this.last = Boolean(response.last) || this.records.length < this.pageSize;
        const totalValue = response.totalElements ?? response.numberOfElements;
        const parsed = Number(totalValue);
        this.total = Number.isFinite(parsed) ? parsed : null;
      },
      error: (error: any) => this.error = error?.error?.message || 'Não foi possível consultar as autorizações.',
    });
  }

  download(): void {
    if (!this.config || this.exporting) return;
    this.exporting = true;
    this.error = '';
    this.reports.exportarHospital('xlsx', {
      filtros: this.activeFilters(),
      pagina: 1,
      tamanhoPagina: this.pageSize,
      nomeArquivo: 'hospital_autorizacoes',
    }).pipe(finalize(() => this.exporting = false)).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.Response && event.body) this.save(event.body, 'hospital_autorizacoes.xlsx');
      },
      error: (error: any) => this.error = error?.error?.message || 'Não foi possível baixar o relatório.',
    });
  }

  clear(): void {
    Object.keys(this.values).forEach((key) => this.values[key] = '');
    this.records = [];
    this.page = 1;
    this.total = null;
    this.error = '';
  }

  private activeFilters(): Record<string, unknown> {
    return Object.fromEntries(Object.entries(this.values).filter(([, value]) => value.trim()));
  }

  private save(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
