import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RelatorioPersonalizadoFiltro } from '../../../shared/models/relatorio.model';
import { agruparCampos, buscarCampos } from './seletor-campos.utils';

@Component({
  selector: 'app-filtros-relatorio',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './filtros-relatorio.component.html',
  styleUrl: './seletor-campos.scss',
})
export class FiltrosRelatorioComponent {
  @Input() filtros: RelatorioPersonalizadoFiltro[] = [];
  @Input() valores: Record<string, string> = {};
  @Input() bloqueado = false;
  @Input() set versaoLimpeza(_: number) { this.adicionados.clear(); this.busca = ''; }
  @Output() valoresChange = new EventEmitter<Record<string, string>>();
  busca = '';
  private adicionados = new Set<string>();
  trackGrupo(_: number, grupo: { nome: string }): string { return grupo.nome; }
  trackCampo(_: number, campo: { id: string }): string { return campo.id; }

  ativo(filtro: RelatorioPersonalizadoFiltro): boolean {
    // Um valor preenchido nunca fica oculto por causa de busca ou reconstrução da view.
    return filtro.obrigatorio || this.adicionados.has(filtro.id) || !!this.valores[filtro.id]?.trim();
  }

  get gruposAtivos() { return agruparCampos(this.filtros.filter((filtro) => this.ativo(filtro))); }
  get disponiveis() { return buscarCampos(this.filtros.filter((filtro) => !this.ativo(filtro)), this.busca); }

  adicionar(filtro: RelatorioPersonalizadoFiltro): void {
    if (!this.bloqueado) this.adicionados.add(filtro.id);
  }

  remover(filtro: RelatorioPersonalizadoFiltro): void {
    if (this.bloqueado || filtro.obrigatorio) return;
    this.adicionados.delete(filtro.id);
    this.alterar(filtro.id, '');
  }

  alterar(id: string, valor: string): void {
    if (this.bloqueado) return;
    this.valores = { ...this.valores, [id]: valor };
    this.valoresChange.emit(this.valores);
  }
}
