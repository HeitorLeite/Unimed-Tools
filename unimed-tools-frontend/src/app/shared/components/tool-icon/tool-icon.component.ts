import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type ToolIconName = 'relatorios' | 'xml' | 'bi' | 'ans' | 'fechamento' | 'comercial' | 'assistencial' | 'revisao' | 'unica' | 'hospital' | 'risco' | 'ti';

/** Ícones estáticos compartilhados pelos cards e pela navegação lateral. */
@Component({
  selector: 'app-tool-icon',
  standalone: true,
  templateUrl: './tool-icon.component.html',
  styleUrl: './tool-icon.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToolIconComponent {
  @Input({ required: true }) name!: ToolIconName;
}
