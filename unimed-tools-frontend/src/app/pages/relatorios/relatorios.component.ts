import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';

import { RelatorioCatalogo } from '../../shared/models/relatorio.model';
import { RelatorioService } from '../../shared/services/relatorio.service';
import { RelatoriosAutomaticosComponent } from './relatorios-automaticos/relatorios-automaticos.component';
import { RelatoriosInicioComponent } from './relatorios-inicio/relatorios-inicio.component';
import { RelatoriosManualComponent } from './relatorios-manual/relatorios-manual.component';
import { RelatoriosPersonalizadosComponent } from './relatorios-personalizados/relatorios-personalizados.component';

type ModoPaginaRelatorios = 'selecao' | 'manual' | 'automatico' | 'personalizado';

/**
 * Orquestra os modos da Central de Relatórios.
 *
 * Cada fluxo mantém seu próprio estado e responsabilidade em um componente
 * dedicado; este componente cuida somente da navegação e do catálogo necessário
 * para os grupos automáticos.
 */
@Component({
  selector: 'app-relatorios',
  standalone: true,
  imports: [
    CommonModule,
    RelatoriosInicioComponent,
    RelatoriosManualComponent,
    RelatoriosAutomaticosComponent,
    RelatoriosPersonalizadosComponent,
  ],
  templateUrl: './relatorios.component.html',
  styleUrls: ['./relatorios.component.scss'],
})
export class RelatoriosComponent implements OnInit {
  modoPagina: ModoPaginaRelatorios = 'selecao';
  relatorios: RelatorioCatalogo[] = [];
  quantidadeGruposAutomaticos = 0;

  constructor(private readonly relatorioService: RelatorioService) {}

  ngOnInit(): void {
    this.atualizarResumo();
  }

  selecionarModoPagina(modo: Exclude<ModoPaginaRelatorios, 'selecao'>): void {
    this.modoPagina = modo;
    if (modo === 'automatico') this.relatorios = this.relatorioService.listarCatalogo();
  }

  voltarSelecaoModo(): void {
    this.modoPagina = 'selecao';
    this.atualizarResumo();
  }

  private atualizarResumo(): void {
    this.relatorios = this.relatorioService.listarCatalogo();
    this.quantidadeGruposAutomaticos = this.relatorioService.listarGruposAutomaticos().length;
  }
}
