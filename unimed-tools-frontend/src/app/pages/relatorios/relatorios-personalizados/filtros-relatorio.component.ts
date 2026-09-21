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
  @Input() ativos: string[] = [];
  @Input() bloqueado = false;
  @Input() set versaoLimpeza(_: number) { this.busca = ''; }
  @Output() valoresChange = new EventEmitter<Record<string, string>>();
  @Output() ativosChange = new EventEmitter<string[]>();

  busca = '';

  trackGrupo(_: number, grupo: { nome: string }): string { return grupo.nome; }
  trackCampo(_: number, campo: { id: string }): string { return campo.id; }

  ativo(filtro: RelatorioPersonalizadoFiltro): boolean {
    return filtro.obrigatorio || this.ativos.includes(filtro.id) || !!this.valores[filtro.id]?.trim();
  }

  get gruposAtivos() { return agruparCampos(this.filtros.filter((filtro) => this.ativo(filtro))); }
  get disponiveis() { return buscarCampos(this.filtros.filter((filtro) => !this.ativo(filtro)), this.busca); }

  adicionar(filtro: RelatorioPersonalizadoFiltro): void {
    if (this.bloqueado || this.ativos.includes(filtro.id)) return;
    this.ativos = [...this.ativos, filtro.id];
    this.ativosChange.emit(this.ativos);
  }

  remover(filtro: RelatorioPersonalizadoFiltro): void {
    if (this.bloqueado || filtro.obrigatorio) return;
    this.ativos = this.ativos.filter((id) => id !== filtro.id);
    this.ativosChange.emit(this.ativos);
    this.alterar(filtro.id, '');
  }

  alterar(id: string, valor: string): void {
    if (this.bloqueado) return;
    this.valores = { ...this.valores, [id]: valor };
    this.valoresChange.emit(this.valores);
  }
}
