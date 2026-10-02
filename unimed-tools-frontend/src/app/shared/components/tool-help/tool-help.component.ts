import { CommonModule } from '@angular/common';
import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { ToolHelpContent } from '../../models/tool-help.model';

@Component({
  selector: 'app-tool-help',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tool-help.component.html',
  styleUrl: './tool-help.component.scss',
})
export class ToolHelpComponent {
  private static nextId = 0;

  @Input({ required: true }) content!: ToolHelpContent;
  @ViewChild('helpDialog') private dialog?: ElementRef<HTMLDialogElement>;

  readonly titleId = `tool-help-title-${ToolHelpComponent.nextId++}`;
  readonly summaryId = `tool-help-summary-${ToolHelpComponent.nextId++}`;

  open(): void {
    const dialog = this.dialog?.nativeElement;
    if (!dialog) return;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
  }

  close(): void {
    const dialog = this.dialog?.nativeElement;
    if (!dialog) return;
    if (typeof dialog.close === 'function') dialog.close();
    else dialog.removeAttribute('open');
  }

  closeOnBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog?.nativeElement) this.close();
  }
}
