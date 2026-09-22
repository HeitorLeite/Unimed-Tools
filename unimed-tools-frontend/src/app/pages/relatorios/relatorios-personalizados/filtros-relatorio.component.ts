import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RelatorioPersonalizadoFiltro } from '../../../shared/models/relatorio.model';
import {
  buscarEmpresas,
  EMPRESAS_RELATORIOS,
  EmpresaCatalogo,
} from '../relatorios-automaticos/empresa-catalogo';
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
  @Input() set versaoLimpeza(_: number) {
    this.busca = '';
    this.buscaEmpresa = '';
  }

  @Output() valoresChange = new EventEmitter<Record<string, string>>();
  @Output() ativosChange = new EventEmitter<string[]>();

  busca = '';
  buscaEmpresa = '';

  readonly empresas = EMPRESAS_RELATORIOS;

  trackGrupo(_: number, grupo: { nome: string }): string {
    return grupo.nome;
  }

  trackCampo(_: number, campo: { id: string }): string {
    return campo.id;
  }

  trackEmpresa(_: number, empresa: EmpresaCatalogo): string {
    return empresa.id;
  }

  ativo(filtro: RelatorioPersonalizadoFiltro): boolean {
    return filtro.obrigatorio || this.ativos.includes(filtro.id) || !!this.valores[filtro.id]?.trim();
  }

  get gruposAtivos() {
    return agruparCampos(this.filtros.filter((filtro) => this.ativo(filtro)));
  }

  get disponiveis() {
    return buscarCampos(
      this.filtros.filter((filtro) => !this.ativo(filtro)),
      this.busca,
    );
  }

  get empresasVisiveis(): readonly EmpresaCatalogo[] {
    return buscarEmpresas(this.buscaEmpresa);
  }

  get empresasSelecionadas(): readonly EmpresaCatalogo[] {
    const codigos = new Set(
      (this.valores['nome_empresa'] || '')
        .split(',')
        .map((codigo) => codigo.trim())
        .filter(Boolean),
    );
    if (!codigos.size) return [];
    return this.empresas.filter((empresa) =>
      empresa.codigos.every((codigo) => codigos.has(codigo)),
    );
  }

  empresaSelecionada(id: string): boolean {
    return this.empresasSelecionadas.some((empresa) => empresa.id === id);
  }

  alternarEmpresa(empresa: EmpresaCatalogo, marcada: boolean): void {
    if (this.bloqueado) return;
    const ids = new Set(this.empresasSelecionadas.map((item) => item.id));
    if (marcada) {
      ids.add(empresa.id);
    } else {
      ids.delete(empresa.id);
    }

    const codigos = this.empresas
      .filter((item) => ids.has(item.id))
      .flatMap((item) => [...item.codigos]);

    this.alterar('nome_empresa', [...new Set(codigos)].join(','));
  }

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
    if (filtro.id === 'nome_empresa') {
      this.buscaEmpresa = '';
    }
  }

  alterar(id: string, valor: string): void {
    if (this.bloqueado) return;
    this.valores = { ...this.valores, [id]: valor };
    this.valoresChange.emit(this.valores);
  }
}
