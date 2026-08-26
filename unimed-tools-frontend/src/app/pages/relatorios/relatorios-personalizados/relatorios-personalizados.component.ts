/**
 * Construtor guiado de relatórios: coleta filtros autorizados, escolhe colunas
 * e apresenta somente a projeção devolvida pelo backend.
 */
import { CommonModule } from '@angular/common';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  HostListener,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
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
import {
  formatReportPreviewValue,
  isProtectedBeneficiaryColumn,
} from '../../../shared/utils/report-preview.utils';

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
export class RelatoriosPersonalizadosComponent implements OnInit, OnDestroy {
  @Output() voltar = new EventEmitter<void>();

  configuracao: RelatorioPersonalizadoConfiguracao | null = null;
  gruposFiltros: Grupo<RelatorioPersonalizadoFiltro>[] = [];
  gruposColunas: Grupo<RelatorioPersonalizadoColuna>[] = [];
  valoresFiltro: Record<string, string> = {};
  colunasSelecionadas = new Set<string>();
  ordemColunasSelecionadas: string[] = [];

  registros: Record<string, unknown>[] = [];
  colunasResultado: string[] = [];
  pagina = 1;
  tamanhoPagina = 50;
  ultimaPagina = false;
  totalRegistros: number | null = null;
  totalRegistrosExportados: number | null = null;
  somenteDistintos = false;
  previaExpandida = false;
  colunaOrdenacao: string | null = null;
  direcaoOrdenacao: 'ASC' | 'DESC' = 'ASC';

  formatoSelecionado: FormatoExportacao = 'xlsx';
  nomeArquivo = 'relatorio_personalizado';
  carregandoConfiguracao = true;
  gerando = false;
  exportando = false;
  progressoOperacao = 0;
  segundosOperacao = 0;
  mensagemOperacao = '';
  erro = '';
  sucesso = '';
  secoesRecolhidas = new Set<SecaoRelatorio>();
  private intervaloOperacao?: ReturnType<typeof setInterval>;

  get operacaoRelatorioEmAndamento(): boolean {
    return this.gerando || this.exportando;
  }

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

  ngOnDestroy(): void {
    this.pararProgresso();
  }

  gerar(pagina = 1): void {
    if (this.gerando || !this.configuracao) return;

    const request = this.montarRequest(pagina);
    if (!request) return;

    this.gerando = true;
    this.iniciarProgresso('consulta');
    this.erro = '';
    this.sucesso = '';
    this.cdr.detectChanges();

    this.relatorioService
      .executarPersonalizado(request)
      .pipe(
        finalize(() => {
          this.gerando = false;
          this.pararProgresso();
          // HttpClient não agenda a atualização desta view no modo zoneless.
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (resposta) => {
          this.progressoOperacao = 100;
          this.mensagemOperacao = 'Prévia concluída.';
          this.aplicarResultado(resposta, pagina);
        },
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
    this.iniciarProgresso('exportacao');
    this.erro = '';
    this.sucesso = '';
    this.cdr.detectChanges();

    this.relatorioService
      .exportarPersonalizado(this.formatoSelecionado, request)
      .pipe(
        finalize(() => {
          this.exportando = false;
          this.pararProgresso();
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (evento) => {
          if (evento.type === HttpEventType.DownloadProgress) {
            this.mensagemOperacao = 'Transferindo o arquivo pronto para o navegador…';
            if (evento.total && evento.total > 0) {
              this.progressoOperacao = Math.max(
                this.progressoOperacao,
                Math.min(99, Math.round((evento.loaded / evento.total) * 100)),
              );
            }
            this.cdr.detectChanges();
            return;
          }

          if (evento.type !== HttpEventType.Response) return;

          const resposta = evento;
          const arquivo = resposta.body;
          if (!arquivo) {
            this.erro = 'O backend não devolveu o arquivo solicitado.';
            return;
          }

          this.progressoOperacao = 100;
          this.mensagemOperacao = 'Arquivo concluído. Iniciando o download…';

          const url = URL.createObjectURL(arquivo);
          const link = document.createElement('a');
          link.href = url;
          link.download = `${this.nomeArquivoSeguro()}.${this.formatoSelecionado}`;
          link.click();
          setTimeout(() => URL.revokeObjectURL(url), 0);

          const totalCabecalho = resposta.headers.get('X-Total-Registros');
          const totalConvertido = totalCabecalho === null ? Number.NaN : Number(totalCabecalho);
          this.totalRegistrosExportados = Number.isFinite(totalConvertido) ? totalConvertido : null;
          this.sucesso =
            this.totalRegistrosExportados !== null
              ? `Arquivo gerado com ${this.totalRegistrosExportados} linha(s).`
              : 'Arquivo gerado com as colunas selecionadas.';
        },
        error: (erro) => (this.erro = this.mensagemErro(erro)),
      });
  }

  alternarColuna(coluna: RelatorioPersonalizadoColuna): void {
    if (this.colunasSelecionadas.has(coluna.id)) {
      this.colunasSelecionadas.delete(coluna.id);
      this.ordemColunasSelecionadas = this.ordemColunasSelecionadas.filter(
        (id) => id !== coluna.id,
      );
      if (this.colunaOrdenacao === coluna.id) {
        this.colunaOrdenacao = null;
        this.direcaoOrdenacao = 'ASC';
      }
    } else if (this.colunasSelecionadas.size < (this.configuracao?.limites.maximoColunas ?? 0)) {
      this.colunasSelecionadas.add(coluna.id);
      this.ordemColunasSelecionadas.push(coluna.id);
    }
    this.colunasResultado = [...this.ordemColunasSelecionadas];
    this.limparPrevia();
  }

  alternarGrupo(grupo: Grupo<RelatorioPersonalizadoColuna>): void {
    const todosSelecionados = grupo.itens.every((item) => this.colunasSelecionadas.has(item.id));
    if (todosSelecionados) {
      grupo.itens.forEach((item) => this.colunasSelecionadas.delete(item.id));
      const idsGrupo = new Set(grupo.itens.map((item) => item.id));
      this.ordemColunasSelecionadas = this.ordemColunasSelecionadas.filter(
        (id) => !idsGrupo.has(id),
      );
      if (this.colunaOrdenacao && idsGrupo.has(this.colunaOrdenacao)) {
        this.colunaOrdenacao = null;
        this.direcaoOrdenacao = 'ASC';
      }
    } else {
      grupo.itens.forEach((item) => {
        if (
          !this.colunasSelecionadas.has(item.id) &&
          this.colunasSelecionadas.size < (this.configuracao?.limites.maximoColunas ?? 0)
        ) {
          this.colunasSelecionadas.add(item.id);
          this.ordemColunasSelecionadas.push(item.id);
        }
      });
    }
    this.colunasResultado = [...this.ordemColunasSelecionadas];
    this.limparPrevia();
  }

  moverColuna(id: string, deslocamento: -1 | 1): void {
    this.sincronizarOrdemColunas();
    const indiceAtual = this.ordemColunasSelecionadas.indexOf(id);
    const novoIndice = indiceAtual + deslocamento;
    if (indiceAtual < 0 || novoIndice < 0 || novoIndice >= this.ordemColunasSelecionadas.length) {
      return;
    }

    const ordem = [...this.ordemColunasSelecionadas];
    [ordem[indiceAtual], ordem[novoIndice]] = [ordem[novoIndice], ordem[indiceAtual]];
    this.ordemColunasSelecionadas = ordem;
    this.colunasResultado = [...ordem];
    this.limparPrevia();
  }

  alternarDistinct(): void {
    this.somenteDistintos = !this.somenteDistintos;
    this.limparPrevia();
  }

  ordenarPor(coluna: string): void {
    if (this.gerando || this.exportando) return;

    if (this.colunaOrdenacao === coluna) {
      this.direcaoOrdenacao = this.direcaoOrdenacao === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.colunaOrdenacao = coluna;
      this.direcaoOrdenacao = 'ASC';
    }

    if (this.registros.length) {
      this.gerar(1);
    }
  }

  ariaOrdenacao(coluna: string): 'ascending' | 'descending' | 'none' {
    if (this.colunaOrdenacao !== coluna) return 'none';
    return this.direcaoOrdenacao === 'ASC' ? 'ascending' : 'descending';
  }

  simboloOrdenacao(coluna: string): string {
    if (this.colunaOrdenacao !== coluna) return '↕';
    return this.direcaoOrdenacao === 'ASC' ? '↑' : '↓';
  }

  alternarPreviaExpandida(): void {
    this.previaExpandida = !this.previaExpandida;
  }

  @HostListener('document:keydown.escape')
  fecharPreviaComEscape(): void {
    this.previaExpandida = false;
  }

  alterarTamanhoPagina(valor: string | number): void {
    const tamanho = Number(valor);
    if (![25, 50, 100].includes(tamanho) || tamanho === this.tamanhoPagina) return;
    this.tamanhoPagina = tamanho;
    if (this.registros.length) this.gerar(1);
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
    this.totalRegistrosExportados = null;
    this.erro = '';
    this.sucesso = '';
  }

  rotuloColuna(id: string): string {
    return this.configuracao?.colunas.find((coluna) => coluna.id === id)?.rotulo ?? id;
  }

  valorCelula(coluna: string, valor: unknown): string {
    return formatReportPreviewValue(coluna, valor);
  }

  colunaProtegida(coluna: string): boolean {
    return isProtectedBeneficiaryColumn(coluna);
  }

  indiceLinha(indice: number): number {
    return (this.pagina - 1) * this.tamanhoPagina + indice + 1;
  }

  get primeiraLinhaPagina(): number {
    return this.registros.length ? (this.pagina - 1) * this.tamanhoPagina + 1 : 0;
  }

  get ultimaLinhaPagina(): number {
    return this.primeiraLinhaPagina + Math.max(0, this.registros.length - 1);
  }

  get totalPaginas(): number | null {
    return this.totalRegistros === null
      ? null
      : Math.max(1, Math.ceil(this.totalRegistros / this.tamanhoPagina));
  }

  trackById(_: number, item: { id: string }): string {
    return item.id;
  }

  trackByGrupo(_: number, grupo: Grupo<unknown>): string {
    return grupo.nome;
  }

  trackByColunaId(_: number, id: string): string {
    return id;
  }

  private prepararConfiguracao(configuracao: RelatorioPersonalizadoConfiguracao): void {
    this.configuracao = configuracao;
    this.colunasSelecionadas.clear();
    this.ordemColunasSelecionadas = [];
    this.colunaOrdenacao = null;
    this.direcaoOrdenacao = 'ASC';
    this.gruposFiltros = this.agrupar(configuracao.filtros);
    this.gruposColunas = this.agrupar(configuracao.colunas);
    this.valoresFiltro = Object.fromEntries(configuracao.filtros.map((filtro) => [filtro.id, '']));

    const competenciaAtual = this.competenciaAtual();
    this.valoresFiltro['competencia_inicio'] = competenciaAtual;
    this.valoresFiltro['competencia_fim'] = competenciaAtual;
    configuracao.colunas
      .filter((coluna) => coluna.selecionadaPorPadrao)
      .forEach((coluna) => {
        this.colunasSelecionadas.add(coluna.id);
        this.ordemColunasSelecionadas.push(coluna.id);
      });
    this.colunasResultado = [...this.ordemColunasSelecionadas];
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
    if (!this.validarIndicadoresFinanceiros()) {
      return null;
    }

    const filtros = Object.fromEntries(
      Object.entries(this.valoresFiltro)
        .filter(([, valor]) => String(valor ?? '').trim() !== '')
        .map(([id, valor]) => [id, id.startsWith('competencia_') ? valor.replace('-', '') : valor]),
    );

    this.sincronizarOrdemColunas();
    return {
      colunas: [...this.ordemColunasSelecionadas],
      filtros,
      distinct: this.somenteDistintos,
      ...(this.colunaOrdenacao
        ? {
            ordenarPor: this.colunaOrdenacao,
            direcaoOrdenacao: this.direcaoOrdenacao,
          }
        : {}),
      pagina,
      tamanhoPagina: this.tamanhoPagina,
      nomeArquivo: this.nomeArquivoSeguro(),
    };
  }

  private validarIndicadoresFinanceiros(): boolean {
    const usaIndicadores =
      this.colunasSelecionadas.has('RECEITA') || this.colunasSelecionadas.has('SINISTRALIDADE');
    if (!usaIndicadores || !this.configuracao) return true;

    const valoresPermitidos = new Set([
      'VALOR_TOTAL',
      'VALOR_TOTAL_21',
      'RECEITA',
      'SINISTRALIDADE',
    ]);
    const colunaIncompativel = this.configuracao.colunas.find(
      (coluna) =>
        this.colunasSelecionadas.has(coluna.id) &&
        coluna.grupo !== 'Beneficiário' &&
        coluna.grupo !== 'Contrato e empresa' &&
        coluna.id !== 'PERIODO' &&
        !valoresPermitidos.has(coluna.id),
    );
    if (colunaIncompativel) {
      this.erro =
        'Receita e Sinistralidade podem ser combinadas somente com Beneficiário, ' +
        'Contrato e empresa, Competência e os totais financeiros.';
      return false;
    }

    const filtrosPermitidos = new Set([
      'competencia_inicio',
      'competencia_fim',
      'codigo_beneficiario',
      'nome_beneficiario',
      'cpf',
      'grupo_beneficiario',
      'numero_contrato',
      'codigo_empresa',
      'nome_empresa',
    ]);
    const filtroIncompativel = Object.entries(this.valoresFiltro).some(
      ([id, valor]) => String(valor ?? '').trim() !== '' && !filtrosPermitidos.has(id),
    );
    if (filtroIncompativel) {
      this.erro =
        'Receita e Sinistralidade aceitam filtros de período, beneficiário, contrato ou empresa.';
      return false;
    }
    return true;
  }

  private aplicarResultado(resposta: SguResultado, paginaSolicitada: number): void {
    const respostaGenerica = resposta as any;
    const paginacao = respostaGenerica?.data ?? respostaGenerica;
    this.registros = Array.isArray(respostaGenerica?.content)
      ? respostaGenerica.content
      : Array.isArray(paginacao?.content)
        ? paginacao.content
        : [];
    const colunasResposta = Array.isArray(respostaGenerica?.colunas)
      ? (respostaGenerica.colunas as string[])
      : [...this.ordemColunasSelecionadas];
    this.colunasResultado = colunasResposta;
    this.pagina = paginaSolicitada;
    this.ultimaPagina =
      typeof paginacao?.last === 'boolean'
        ? paginacao.last
        : this.registros.length < this.tamanhoPagina;
    const totalBruto =
      respostaGenerica?.totalElements ??
      paginacao?.totalElements ??
      respostaGenerica?.numberOfElements ??
      paginacao?.numberOfElements ??
      null;
    const totalInformado =
      totalBruto === null || totalBruto === '' ? Number.NaN : Number(totalBruto);
    this.totalRegistros = Number.isFinite(totalInformado)
      ? totalInformado
      : this.ultimaPagina
        ? (paginaSolicitada - 1) * this.tamanhoPagina + this.registros.length
        : null;
    this.sucesso = this.registros.length
      ? `${this.registros.length} registro(s) carregado(s) nesta página.`
      : 'A consulta foi concluída, mas não encontrou registros.';
  }

  private limparPrevia(): void {
    this.registros = [];
    this.totalRegistros = null;
    this.totalRegistrosExportados = null;
    this.pagina = 1;
    this.ultimaPagina = false;
    this.sucesso = '';
  }

  private sincronizarOrdemColunas(): void {
    const selecionadas = this.colunasSelecionadas;
    const ordemValida = this.ordemColunasSelecionadas.filter((id) => selecionadas.has(id));
    selecionadas.forEach((id) => {
      if (!ordemValida.includes(id)) ordemValida.push(id);
    });
    this.ordemColunasSelecionadas = ordemValida;
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

  private iniciarProgresso(tipo: 'consulta' | 'exportacao'): void {
    this.pararProgresso();
    this.segundosOperacao = 0;
    this.progressoOperacao = 5;
    this.mensagemOperacao =
      tipo === 'consulta'
        ? 'Consultando os dados e preparando a prévia…'
        : 'Consultando todas as páginas do relatório…';

    const referenciaSegundos = tipo === 'consulta' ? 30 : 90;
    this.intervaloOperacao = setInterval(() => {
      this.segundosOperacao += 1;
      const aproximacao = 1 - Math.exp(-this.segundosOperacao / referenciaSegundos);
      this.progressoOperacao = Math.min(94, Math.round(5 + aproximacao * 89));
      this.mensagemOperacao =
        tipo === 'consulta'
          ? this.progressoOperacao < 65
            ? 'Consultando os dados no SGU…'
            : 'Preparando a prévia do relatório…'
          : this.progressoOperacao < 45
            ? 'Consultando todas as páginas do relatório…'
            : this.progressoOperacao < 80
              ? 'Processando os registros do arquivo…'
              : 'Finalizando o arquivo para download…';
      this.cdr.detectChanges();
    }, 1000);
  }

  private pararProgresso(): void {
    if (this.intervaloOperacao) {
      clearInterval(this.intervaloOperacao);
      this.intervaloOperacao = undefined;
    }
  }
}
