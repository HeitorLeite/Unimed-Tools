import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RelatorioPersonalizadoColuna } from '../../../shared/models/relatorio.model';
import { agruparCampos, buscarCampos } from './seletor-campos.utils';

@Component({
  selector: 'app-colunas-relatorio',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './colunas-relatorio.component.html',
  styleUrl: './seletor-campos.scss',
})
export class ColunasRelatorioComponent {
  @Input() colunas: RelatorioPersonalizadoColuna[] = [];
  @Input() ordem: string[] = [];
  @Input() maximo = 0;
  @Input() bloqueado = false;

  @Output() alternar = new EventEmitter<RelatorioPersonalizadoColuna>();
  @Output() alternarGrupo = new EventEmitter<{
    nome: string;
    itens: RelatorioPersonalizadoColuna[];
  }>();
  @Output() reordenar = new EventEmitter<string[]>();

  busca = '';
  grupo = '';
  arrastandoId = '';

  trackGrupo(_: number, grupo: { nome: string }): string {
    return grupo.nome;
  }

  trackCampo(_: number, campo: { id: string }): string {
    return campo.id;
  }

  get categorias(): string[] {
    return [...new Set(this.colunas.map((coluna) => coluna.grupo))];
  }

  get gruposVisiveis() {
    return agruparCampos(buscarCampos(this.colunas, this.busca, this.grupo));
  }

  get selecionadas(): RelatorioPersonalizadoColuna[] {
    const mapa = new Map(this.colunas.map((coluna) => [coluna.id, coluna]));
    return this.ordem
      .map((id) => mapa.get(id))
      .filter((coluna): coluna is RelatorioPersonalizadoColuna => !!coluna);
  }

  selecionado(id: string): boolean {
    return this.ordem.includes(id);
  }

  grupoSelecionado(itens: RelatorioPersonalizadoColuna[]): boolean {
    return itens.every((coluna) => this.selecionado(coluna.id));
  }

  podeAdicionar(coluna: RelatorioPersonalizadoColuna): boolean {
    return this.selecionado(coluna.id) || this.ordem.length < this.maximo;
  }

  iniciarArraste(evento: DragEvent, id: string): void {
    if (this.bloqueado) {
      evento.preventDefault();
      return;
    }
    this.arrastandoId = id;
    evento.dataTransfer?.setData('text/plain', id);
    if (evento.dataTransfer) {
      evento.dataTransfer.effectAllowed = 'move';
    }
  }

  permitirSoltar(evento: DragEvent): void {
    if (this.bloqueado) return;
    evento.preventDefault();
    if (evento.dataTransfer) {
      evento.dataTransfer.dropEffect = 'move';
    }
  }

  soltar(evento: DragEvent, destinoId: string): void {
    if (this.bloqueado) return;
    evento.preventDefault();

    const origemId =
      this.arrastandoId || evento.dataTransfer?.getData('text/plain') || '';
    this.arrastandoId = '';

    if (!origemId || origemId === destinoId) return;

    const ordem = [...this.ordem];
    const origem = ordem.indexOf(origemId);
    const destino = ordem.indexOf(destinoId);
    if (origem < 0 || destino < 0) return;

    ordem.splice(origem, 1);
    ordem.splice(destino, 0, origemId);
    this.reordenar.emit(ordem);
  }

  finalizarArraste(): void {
    this.arrastandoId = '';
  }
}
