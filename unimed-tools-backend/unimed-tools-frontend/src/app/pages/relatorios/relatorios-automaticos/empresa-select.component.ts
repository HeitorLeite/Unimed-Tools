import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { buscarEmpresas, EMPRESAS_RELATORIOS } from './empresa-catalogo';

@Component({
  selector: 'app-empresa-select', standalone: true, imports: [CommonModule, FormsModule],
  template: `
    <label [for]="id + '-busca'">Buscar empresa</label>
    <input [id]="id + '-busca'" type="search" placeholder="Ex.: Canção Nova"
      [(ngModel)]="busca" [disabled]="bloqueado" />
    <label [for]="id">Empresa</label>
    <select [id]="id" [ngModel]="selecionada" (ngModelChange)="escolher($event)" [disabled]="bloqueado">
      <option value="">Selecione uma empresa</option>
      <option *ngFor="let empresa of opcoes" [value]="empresa.id"
        [disabled]="ocupadas.includes(empresa.id) && empresa.id !== selecionada">{{ empresa.nome }}</option>
    </select>
    <small *ngIf="!encontradas.length" role="status">Nenhuma empresa encontrada para esta busca.</small>
  `,
  styles: `
    :host { display: grid; gap: 6px; }
    label { font-size: 12px; font-weight: 650; }
    input, select { width: 100%; box-sizing: border-box; padding: 9px 12px; min-height: 40px; border: 1px solid #d7e1dc; border-radius: 8px; background: white; color: #1c3028; }
    input:focus-visible, select:focus-visible { outline: 2px solid #008557; outline-offset: 2px; }
    small { color: #60756b; }
  `,
})
export class EmpresaSelectComponent {
  @Input() id = 'empresa';
  @Input() selecionada = '';
  @Input() ocupadas: string[] = [];
  @Input() bloqueado = false;
  @Output() selecionadaChange = new EventEmitter<string>();
  busca = '';
  get encontradas() { return buscarEmpresas(this.busca); }
  get opcoes() {
    const selecionada = EMPRESAS_RELATORIOS.find((item) => item.id === this.selecionada);
    const encontradas = this.encontradas;
    return selecionada && !encontradas.includes(selecionada) ? [selecionada, ...encontradas] : encontradas;
  }
  escolher(id: string): void {
    if (this.bloqueado || (id !== this.selecionada && this.ocupadas.includes(id))) return;
    this.selecionadaChange.emit(id);
    this.busca = '';
  }
}
