import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import {
  formatReportPreviewValue,
  isProtectedBeneficiaryColumn,
} from '../../utils/report-preview.utils';

@Component({
  selector: 'app-report-preview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './report-preview.component.html',
  styleUrl: './report-preview.component.scss',
})
export class ReportPreviewComponent {
  @Input() title = 'Prévia';
  @Input() records: Record<string, unknown>[] = [];
  @Input() columns: string[] = [];
  @Input() emptyMessage = 'Nenhum registro encontrado para os filtros informados.';
  @Input() maxRows = 20;

  // Mantém a proteção por padrão para todas as outras ferramentas.
  @Input() protectBeneficiary = true;

  // Só terá efeito nas ferramentas que informarem qual é a coluna de validade.
  @Input() expirationDateColumn: string | null = null;

  get visibleRecords(): Record<string, unknown>[] {
    return this.records.slice(0, this.maxRows);
  }

  format(column: string, value: unknown): string {
    // No Hospital permitimos mostrar o nome do beneficiário.
    if (!this.protectBeneficiary && isProtectedBeneficiaryColumn(column)) {
      if (value === null || value === undefined || value === '') {
        return '—';
      }

      if (typeof value === 'object') {
        return JSON.stringify(value);
      }

      return String(value);
    }

    return formatReportPreviewValue(column, value);
  }

  protected(column: string): boolean {
    return this.protectBeneficiary && isProtectedBeneficiaryColumn(column);
  }

  expired(column: string, value: unknown): boolean {
    if (!this.expirationDateColumn) {
      return false;
    }

    if (column.toUpperCase() !== this.expirationDateColumn.toUpperCase()) {
      return false;
    }

    if (value === null || value === undefined || value === '') {
      return false;
    }

    const date = this.parseDate(String(value));

    if (!date) {
      return false;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    return date.getTime() < today.getTime();
  }

  private parseDate(value: string): Date | null {
    const text = value.trim();

    // Aceita DD/MM/YYYY e também DD/MM/YYYY HH:mm:ss
    const br = text.match(/^(\d{2})\/(\d{2})\/(\d{4})(?:\s.*)?$/);

    if (br) {
      const [, day, month, year] = br;

      const date = new Date(Number(year), Number(month) - 1, Number(day));

      date.setHours(0, 0, 0, 0);

      return date;
    }

    // Aceita YYYY-MM-DD e timestamps ISO.
    const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})/);

    if (iso) {
      const [, year, month, day] = iso;

      const date = new Date(Number(year), Number(month) - 1, Number(day));

      date.setHours(0, 0, 0, 0);

      return date;
    }

    return null;
  }
}
