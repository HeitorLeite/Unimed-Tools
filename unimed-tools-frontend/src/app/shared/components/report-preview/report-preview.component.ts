import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { formatReportPreviewValue, isProtectedBeneficiaryColumn } from '../../utils/report-preview.utils';

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

  get visibleRecords(): Record<string, unknown>[] {
    return this.records.slice(0, this.maxRows);
  }

  format(column: string, value: unknown): string {
    return formatReportPreviewValue(column, value);
  }

  protected(column: string): boolean {
    return isProtectedBeneficiaryColumn(column);
  }
}
