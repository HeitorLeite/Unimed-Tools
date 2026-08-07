/**
 * Construtor guiado de relatórios: coleta filtros autorizados, escolhe colunas
 * e apresenta somente a projeção devolvida pelo backend.
 */
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  FormatoExportacao,
  RelatorioPersonalizadoColuna,
  RelatorioPersonalizadoConfiguracao,
  RelatorioPersonalizadoFiltro,
  RelatorioPersonalizadoRequest,
  SguResultado,
} from '../../../shared/models/relatorio.model';
import { RelatorioService } from '../../../shared/services/relatorio.service';

interface Grupo<T> {
  nome: string;
  itens: T[];
}

type SecaoRelatorio = 'filtros' | 'colunas' | 'resultado';

@Component({
  selector: 'app-relatorios-personalizados',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './relatorios-personalizados.component.html',
  styleUrls: ['./relatorios-personalizados.component.scss'],
})
export class RelatoriosPersonalizadosComponent implements OnInit {
  @Output() voltar = new EventEmitter<void>();

  configuracao: RelatorioPersonalizadoConfiguracao | null = null;
  gruposFiltros: Grupo<RelatorioPersonalizadoFiltro>[] = [];
  gruposColunas: Grupo<RelatorioPersonalizadoColuna>[] = [];
  valoresFiltro: Record<string, string> = {};
  colunasSelecionadas = new Set<string>();

  registros: Record<string, unknown>[] = [];
  colunasResultado: string[] = [];
  pagina = 1;
  tamanhoPagina = 50;
  ultimaPagina = false;
  totalRegistros: number | null = null;

  formatoSelecionado: FormatoExportacao = 'xlsx';
  nomeArquivo = 'relatorio_personalizado';
  carregandoConfiguracao = true;
  gerando = false;
  exportando = false;
  erro = '';
  sucesso = '';
  secoesRecolhidas = new Set<SecaoRelatorio>();

  constructor(
    private readonly relatorioService: RelatorioService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.relatorioService
      .configuracaoPersonalizada()
      .pipe(
        finalize(() => {
          this.carregandoConfiguracao = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (configuracao) => this.prepararConfiguracao(configuracao),
        error: (erro) => (this.erro = this.mensagemErro(erro)),
      });
  }

  gerar(pagina = 1): void {
    if (this.gerando || !this.configuracao) return;

    const request = this.montarRequest(pagina);
    if (!request) return;

    this.gerando = true;
    this.erro = '';
    this.sucesso = '';
    this.cdr.detectChanges();

    this.relatorioService
      .executarPersonalizado(request)
      .pipe(
        finalize(() => {
          this.gerando = false;
          // HttpClient não agenda a atualização desta view no modo zoneless.
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (resposta) => this.aplicarResultado(resposta, pagina),
        error: (erro) => {
          this.registros = [];
          this.erro = this.mensagemErro(erro);
        },
      });
  }

  exportar(): void {
    if (this.exportando || !this.configuracao) return;

    const request = this.montarRequest(1);
    if (!request) return;

    this.exportando = true;
    this.erro = '';
    this.sucesso = '';
    this.cdr.detectChanges();

    this.relatorioService
      .exportarPersonalizado(this.formatoSelecionado, request)
      .pipe(
        finalize(() => {
          this.exportando = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (arquivo) => {
          const url = URL.createObjectURL(arquivo);
          const link = document.createElement('a');
          link.href = url;
          link.download = `${this.nomeArquivoSeguro()}.${this.formatoSelecionado}`;
          link.click();
          URL.revokeObjectURL(url);
          this.sucesso = 'Arquivo gerado com as colunas selecionadas.';
        },
        error: (erro) => (this.erro = this.mensagemErro(erro)),
      });
  }

  alternarColuna(coluna: RelatorioPersonalizadoColuna): void {
    if (this.colunasSelecionadas.has(coluna.id)) {
      this.colunasSelecionadas.delete(coluna.id);
    } else if (this.colunasSelecionadas.size < (this.configuracao?.limites.maximoColunas ?? 0)) {
      this.colunasSelecionadas.add(coluna.id);
    }
    this.colunasResultado = [...this.colunasSelecionadas];
  }

  alternarGrupo(grupo: Grupo<RelatorioPersonalizadoColuna>): void {
    const todosSelecionados = grupo.itens.every((item) => this.colunasSelecionadas.has(item.id));
    if (todosSelecionados) {
      grupo.itens.forEach((item) => this.colunasSelecionadas.delete(item.id));
    } else {
      grupo.itens.forEach((item) => {
        if (this.colunasSelecionadas.size < (this.configuracao?.limites.maximoColunas ?? 0)) {
          this.colunasSelecionadas.add(item.id);
        }
      });
    }
    this.colunasResultado = [...this.colunasSelecionadas];
  }

  grupoSelecionado(grupo: Grupo<RelatorioPersonalizadoColuna>): boolean {
    return (
      grupo.itens.length > 0 && grupo.itens.every((item) => this.colunasSelecionadas.has(item.id))
    );
  }

  alternarSecao(secao: SecaoRelatorio): void {
    if (this.secoesRecolhidas.has(secao)) {
      this.secoesRecolhidas.delete(secao);
    } else {
      this.secoesRecolhidas.add(secao);
    }
  }

  secaoRecolhida(secao: SecaoRelatorio): boolean {
    return this.secoesRecolhidas.has(secao);
  }

  limparFiltros(): void {
    const competenciaAtual = this.competenciaAtual();
    Object.keys(this.valoresFiltro).forEach((chave) => (this.valoresFiltro[chave] = ''));
    this.valoresFiltro['competencia_inicio'] = competenciaAtual;
    this.valoresFiltro['competencia_fim'] = competenciaAtual;
    this.registros = [];
    this.totalRegistros = null;
    this.erro = '';
    this.sucesso = '';
  }

  rotuloColuna(id: string): string {
    return this.configuracao?.colunas.find((coluna) => coluna.id === id)?.rotulo ?? id;
  }

  valorCelula(valor: unknown): string {
    if (valor === null || valor === undefined || valor === '') return '—';
    if (typeof valor === 'object') return JSON.stringify(valor);
    return String(valor);
  }

  trackById(_: number, item: { id: string }): string {
    return item.id;
  }

  trackByGrupo(_: number, grupo: Grupo<unknown>): string {
    return grupo.nome;
  }

  private prepararConfiguracao(configuracao: RelatorioPersonalizadoConfiguracao): void {
    this.configuracao = configuracao;
    this.gruposFiltros = this.agrupar(configuracao.filtros);
    this.gruposColunas = this.agrupar(configuracao.colunas);
    this.valoresFiltro = Object.fromEntries(configuracao.filtros.map((filtro) => [filtro.id, '']));

    const competenciaAtual = this.competenciaAtual();
    this.valoresFiltro['competencia_inicio'] = competenciaAtual;
    this.valoresFiltro['competencia_fim'] = competenciaAtual;
    configuracao.colunas
      .filter((coluna) => coluna.selecionadaPorPadrao)
      .forEach((coluna) => this.colunasSelecionadas.add(coluna.id));
    this.colunasResultado = [...this.colunasSelecionadas];
  }

  private montarRequest(pagina: number): RelatorioPersonalizadoRequest | null {
    const competenciaInicio = this.valoresFiltro['competencia_inicio'];
    const competenciaFim = this.valoresFiltro['competencia_fim'];

    if (!competenciaInicio || !competenciaFim) {
      this.erro = 'Informe a competência inicial e a competência final.';
      return null;
    }
    if (!this.colunasSelecionadas.size) {
      this.erro = 'Selecione pelo menos uma coluna para o relatório.';
      return null;
    }

    const filtros = Object.fromEntries(
      Object.entries(this.valoresFiltro)
        .filter(([, valor]) => String(valor ?? '').trim() !== '')
        .map(([id, valor]) => [id, id.startsWith('competencia_') ? valor.replace('-', '') : valor]),
    );

    return {
      colunas: [...this.colunasSelecionadas],
      filtros,
      pagina,
      tamanhoPagina: this.tamanhoPagina,
      nomeArquivo: this.nomeArquivoSeguro(),
    };
  }

  private aplicarResultado(resposta: SguResultado, paginaSolicitada: number): void {
    this.registros = Array.isArray(resposta?.content) ? resposta.content : [];
    const colunasResposta = Array.isArray(resposta?.['colunas'])
      ? (resposta['colunas'] as string[])
      : [...this.colunasSelecionadas];
    this.colunasResultado = colunasResposta;
    this.pagina = paginaSolicitada;
    this.ultimaPagina = resposta.last ?? this.registros.length < this.tamanhoPagina;
    this.totalRegistros =
      typeof resposta.totalElements === 'number' ? resposta.totalElements : null;
    this.sucesso = this.registros.length
      ? `${this.registros.length} registro(s) carregado(s) nesta página.`
      : 'A consulta foi concluída, mas não encontrou registros.';
  }

  private agrupar<T extends { grupo: string }>(itens: T[]): Grupo<T>[] {
    const mapa = new Map<string, T[]>();
    itens.forEach((item) => mapa.set(item.grupo, [...(mapa.get(item.grupo) ?? []), item]));
    return [...mapa.entries()].map(([nome, itensGrupo]) => ({ nome, itens: itensGrupo }));
  }

  private competenciaAtual(): string {
    const hoje = new Date();
    return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`;
  }

  private nomeArquivoSeguro(): string {
    const normalizado = this.nomeArquivo
      .trim()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9._-]/g, '_')
      .replace(/_+/g, '_');
    return normalizado || 'relatorio_personalizado';
  }

  private mensagemErro(erro: unknown): string {
    if (erro instanceof HttpErrorResponse) {
      if (erro.status === 0) return 'Não foi possível acessar o backend de relatórios.';
      if (typeof erro.error === 'string') return erro.error;
      return erro.error?.message ?? erro.error?.error ?? `A operação falhou (HTTP ${erro.status}).`;
    }
    return erro instanceof Error ? erro.message : 'Não foi possível concluir a operação.';
  }
}
