import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RelatorioCatalogo } from '../../shared/models/relatorio.model';
import { RelatorioService } from '../../shared/services/relatorio.service';
import { RelatoriosAutomaticosComponent } from '../relatorios/relatorios-automaticos/relatorios-automaticos.component';
import { RelatoriosManualComponent } from '../relatorios/relatorios-manual/relatorios-manual.component';
import { ToolManagerComponent } from './tool-manager/tool-manager.component';

type TiTab = 'apis' | 'grupos' | 'ferramentas';

@Component({
  selector: 'app-ti',
  standalone: true,
  imports: [
    CommonModule,
    RelatoriosManualComponent,
    RelatoriosAutomaticosComponent,
    ToolManagerComponent,
  ],
  templateUrl: './ti.component.html',
  styleUrl: './ti.component.scss',
})
export class TiComponent implements OnInit {
  tab: TiTab = 'apis';
  relatorios: RelatorioCatalogo[] = [];

  constructor(private readonly reports: RelatorioService) {}

  ngOnInit(): void {
    this.refreshCatalog();
  }

  select(tab: TiTab): void {
    this.tab = tab;
    if (tab === 'grupos') this.refreshCatalog();
  }

  private refreshCatalog(): void {
    this.relatorios = this.reports.listarCatalogo();
  }
}
