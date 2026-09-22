import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RelatorioPersonalizadoFiltro } from '../../../shared/models/relatorio.model';
import {
  EmpresaCatalogo,
  EMPRESAS_RELATORIOS,
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
    this.empresasSelecionadasIds = [];
    this.modoCodigoManual = false;
  }

  @Output() valoresChange = new EventEmitter<Record<string, string>>();
  @Output() ativosChange = new EventEmitter<string[]>();

  readonly empresas = EMPRESAS_RELATORIOS;
  busca = '';
  buscaEmpresa = '';
  empresasSelecionadasIds: string[] = [];
  modoCodigoManual = false;

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

  private filtroEmpresa(filtro: RelatorioPersonalizadoFiltro): boolean {
    return filtro.id === 'codigo_empresa' || filtro.id === 'nome_empresa';
  }

  get possuiFiltroEmpresa(): boolean {
    return this.filtros.some((filtro) => filtro.id === 'codigo_empresa');
  }

  get filtrosGerais(): RelatorioPersonalizadoFiltro[] {
    return this.filtros.filter((filtro) => !this.filtroEmpresa(filtro));
  }

  get gruposAtivos() {
    return agruparCampos(this.filtrosGerais.filter((filtro) => this.ativo(filtro)));
  }

  get disponiveis() {
    return buscarCampos(
      this.filtrosGerais.filter((filtro) => !this.ativo(filtro)),
      this.busca,
    );
  }

  get empresasFiltradas(): readonly EmpresaCatalogo[] {
    const termo = this.normalizarBusca(this.buscaEmpresa);
    if (!termo) return this.empresas;
    return this.empresas.filter((empresa) =>
      this.normalizarBusca(empresa.nome).includes(termo),
    );
  }

  get empresasSelecionadas(): EmpresaCatalogo[] {
    const porId = new Map(this.empresas.map((empresa) => [empresa.id, empresa]));
    return this.empresasSelecionadasIds
      .map((id) => porId.get(id))
      .filter((empresa): empresa is EmpresaCatalogo => !!empresa);
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
  }

  alterar(id: string, valor: string): void {
    if (this.bloqueado) return;
    this.valores = { ...this.valores, [id]: valor };
    this.valoresChange.emit(this.valores);
  }

  empresaSelecionada(id: string): boolean {
    return this.empresasSelecionadasIds.includes(id);
  }

  alternarEmpresa(id: string): void {
    if (this.bloqueado) return;
    this.modoCodigoManual = false;
    if (this.empresaSelecionada(id)) {
      this.empresasSelecionadasIds = this.empresasSelecionadasIds.filter(
        (atual) => atual !== id,
      );
    } else {
      this.empresasSelecionadasIds = [...this.empresasSelecionadasIds, id];
    }
    this.sincronizarEmpresas();
  }

  selecionarEmpresasVisiveis(): void {
    if (this.bloqueado) return;
    this.modoCodigoManual = false;
    const ids = this.empresasFiltradas.map((empresa) => empresa.id);
    this.empresasSelecionadasIds = [
      ...this.empresasSelecionadasIds,
      ...ids.filter((id) => !this.empresasSelecionadasIds.includes(id)),
    ];
    this.sincronizarEmpresas();
  }

  limparEmpresas(): void {
    if (this.bloqueado) return;
    this.empresasSelecionadasIds = [];
    this.modoCodigoManual = false;
    this.sincronizarEmpresas();
  }

  alternarCodigoManual(): void {
    if (this.bloqueado) return;
    this.modoCodigoManual = !this.modoCodigoManual;
    if (this.modoCodigoManual) {
      this.empresasSelecionadasIds = [];
    }
  }

  alterarCodigoManual(valor: string): void {
    this.empresasSelecionadasIds = [];
    this.garantirFiltroEmpresaAtivo();
    this.alterar('codigo_empresa', valor);
  }

  private sincronizarEmpresas(): void {
    const codigos = this.empresasSelecionadas.flatMap((empresa) => [...empresa.codigos]);
    const unicos = [...new Set(codigos)];
    this.garantirFiltroEmpresaAtivo();

    this.valores = {
      ...this.valores,
      codigo_empresa: unicos.join(','),
      nome_empresa: '',
    };
    this.valoresChange.emit(this.valores);

    if (!unicos.length) {
      this.ativos = this.ativos.filter((id) => id !== 'codigo_empresa' && id !== 'nome_empresa');
      this.ativosChange.emit(this.ativos);
    }
  }

  private garantirFiltroEmpresaAtivo(): void {
    if (!this.ativos.includes('codigo_empresa')) {
      this.ativos = [
        ...this.ativos.filter((id) => id !== 'nome_empresa'),
        'codigo_empresa',
      ];
      this.ativosChange.emit(this.ativos);
    }
  }

  private normalizarBusca(valor: string): string {
    return valor
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }
}
