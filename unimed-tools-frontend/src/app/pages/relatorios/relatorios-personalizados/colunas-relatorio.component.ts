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
  arrastandoId: string | null = null;
  sobreId: string | null = null;

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

  iniciarArraste(id: string, event: DragEvent): void {
    if (this.bloqueado) {
      event.preventDefault();
      return;
    }
    this.arrastandoId = id;
    this.sobreId = id;
    event.dataTransfer?.setData('text/plain', id);
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }

  sobreArraste(id: string, event: DragEvent): void {
    if (this.bloqueado || !this.arrastandoId) return;
    event.preventDefault();
    this.sobreId = id;
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
  }

  soltarSobre(id: string, event: DragEvent): void {
    event.preventDefault();
    if (this.bloqueado || !this.arrastandoId || this.arrastandoId === id) {
      this.finalizarArraste();
      return;
    }

    const ordem = [...this.ordem];
    const origem = ordem.indexOf(this.arrastandoId);
    const destino = ordem.indexOf(id);
    if (origem >= 0 && destino >= 0) {
      const [movida] = ordem.splice(origem, 1);
      ordem.splice(destino, 0, movida);
      this.reordenar.emit(ordem);
    }
    this.finalizarArraste();
  }

  finalizarArraste(): void {
    this.arrastandoId = null;
    this.sobreId = null;
  }
}
