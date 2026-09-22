/**
 * Construtor guiado de relatórios Assistencial.
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
import { ColunasRelatorioComponent } from './colunas-relatorio.component';
import { FiltrosRelatorioComponent } from './filtros-relatorio.component';

interface Grupo<T> {
  nome: string;
  itens: T[];
}

interface AssistencialPreset {
  id: string;
  nome: string;
  colunas: string[];
  filtros: string[];
  distinct: boolean;
  separarMeses?: boolean;
  colunasMeses?: string[];
  rankingAtivo?: boolean;
  rankingDimensao?: string;
  rankingMetrica?: string;
  rankingDirecao?: 'MAIORES' | 'MENORES';
  rankingLimite?: number;
}

type SecaoRelatorio = 'filtros' | 'colunas' | 'resultado';

@Component({
  selector: 'app-relatorios-personalizados',
  standalone: true,
  imports: [CommonModule, FormsModule, FiltrosRelatorioComponent, ColunasRelatorioComponent],
  templateUrl: './relatorios-personalizados.component.html',
  styleUrls: ['./relatorios-personalizados.component.scss'],
})
export class RelatoriosPersonalizadosComponent implements OnInit, OnDestroy {
  @Output() voltar = new EventEmitter<void>();

  configuracao: RelatorioPersonalizadoConfiguracao | null = null;
  gruposFiltros: Grupo<RelatorioPersonalizadoFiltro>[] = [];
  gruposColunas: Grupo<RelatorioPersonalizadoColuna>[] = [];
  valoresFiltro: Record<string, string> = {};
  versaoLimpezaFiltros = 0;
  filtrosAtivos: string[] = [];
  presets: AssistencialPreset[] = [];
  presetSelecionado = '';
  nomeNovoPreset = '';

  colunasSelecionadas = new Set<string>();
  ordemColunasSelecionadas: string[] = [];
  colunasResultado: string[] = [];
  rotulosResultado: Record<string, string> = {};
  registros: Record<string, unknown>[] = [];

  somenteDistintos = false;
  separarMeses = false;
  colunasMesesSelecionadas: string[] = [];
  rankingAtivo = false;
  rankingDimensao = '';
  rankingMetrica = '';
  rankingDirecao: 'MAIORES' | 'MENORES' = 'MAIORES';
  rankingLimite = 10;

  pagina = 1;
  tamanhoPagina = 50;
  ultimaPagina = false;
  totalRegistros: number | null = null;
  totalRegistrosExportados: number | null = null;
  previaExpandida = false;
  colunaOrdenacao: string | null = null;
  direcaoOrdenacao: 'ASC' | 'DESC' = 'ASC';

  colunaArrastadaResultado: string | null = null;
  colunaSobreResultado: string | null = null;

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

  get colunasNumericasSelecionadas(): RelatorioPersonalizadoColuna[] {
    return (this.configuracao?.colunas ?? []).filter(
      (coluna) => this.colunasSelecionadas.has(coluna.id) && !!coluna.numerica,
    );
  }

  get metricasRankingSelecionadas(): RelatorioPersonalizadoColuna[] {
    return (this.configuracao?.colunas ?? []).filter(
      (coluna) => this.colunasSelecionadas.has(coluna.id) && !!coluna.ranqueavel,
    );
  }

  get dimensoesRankingSelecionadas(): RelatorioPersonalizadoColuna[] {
    return (this.configuracao?.colunas ?? []).filter(
      (coluna) => this.colunasSelecionadas.has(coluna.id) && !coluna.numerica,
    );
  }

  get rankingDisponivel(): boolean {
    return this.metricasRankingSelecionadas.length > 0 && this.dimensoesRankingSelecionadas.length > 0;
  }

  constructor(
    private readonly relatorioService: RelatorioService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.presets = this.carregarPresets();
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
          const arquivo = evento.body;
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

          const totalCabecalho = evento.headers.get('X-Total-Registros');
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
      this.colunasMesesSelecionadas = this.colunasMesesSelecionadas.filter(
        (id) => id !== coluna.id,
      );
      if (this.colunaOrdenacao === coluna.id || this.baseColunaResultado(this.colunaOrdenacao) === coluna.id) {
        this.colunaOrdenacao = null;
        this.direcaoOrdenacao = 'ASC';
      }
      this.validarRankingAposColunas();
    } else if (this.colunasSelecionadas.size < (this.configuracao?.limites.maximoColunas ?? 0)) {
      this.colunasSelecionadas.add(coluna.id);
      this.ordemColunasSelecionadas.push(coluna.id);
    }

    this.sincronizarColunasResultadoComBase();
    this.limparPrevia();
  }

  alternarGrupo(grupo: Grupo<RelatorioPersonalizadoColuna>): void {
    const todosSelecionados = grupo.itens.every((item) => this.colunasSelecionadas.has(item.id));

    if (todosSelecionados) {
      const idsGrupo = new Set(grupo.itens.map((item) => item.id));
      grupo.itens.forEach((item) => this.colunasSelecionadas.delete(item.id));
      this.ordemColunasSelecionadas = this.ordemColunasSelecionadas.filter(
        (id) => !idsGrupo.has(id),
      );
      this.colunasMesesSelecionadas = this.colunasMesesSelecionadas.filter(
        (id) => !idsGrupo.has(id),
      );
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

    this.validarRankingAposColunas();
    this.sincronizarColunasResultadoComBase();
    this.limparPrevia();
  }

  reordenarColunas(ordem: string[]): void {
    const permitidas = new Set(this.colunasSelecionadas);
    const novaOrdem = ordem.filter((id) => permitidas.has(id));
    permitidas.forEach((id) => {
      if (!novaOrdem.includes(id)) novaOrdem.push(id);
    });
    this.ordemColunasSelecionadas = novaOrdem;
    this.reordenarResultadoPelaBase();
  }

  // Mantido para compatibilidade com testes antigos; a interface usa drag and drop.
  moverColuna(id: string, deslocamento: -1 | 1): void {
    this.sincronizarOrdemColunas();
    const indiceAtual = this.ordemColunasSelecionadas.indexOf(id);
    const novoIndice = indiceAtual + deslocamento;
    if (indiceAtual < 0 || novoIndice < 0 || novoIndice >= this.ordemColunasSelecionadas.length) {
      return;
    }
    const ordem = [...this.ordemColunasSelecionadas];
    [ordem[indiceAtual], ordem[novoIndice]] = [ordem[novoIndice], ordem[indiceAtual]];
    this.reordenarColunas(ordem);
  }

  alternarDistinct(): void {
    this.somenteDistintos = !this.somenteDistintos;
    this.limparPrevia();
  }

  alternarSeparacaoMeses(): void {
    if (!this.colunasNumericasSelecionadas.length) return;
    this.separarMeses = !this.separarMeses;
    if (this.separarMeses && !this.colunasMesesSelecionadas.length) {
      this.colunasMesesSelecionadas = [this.colunasNumericasSelecionadas[0].id];
    }
    if (!this.separarMeses) {
      this.colunasMesesSelecionadas = [];
    }
    this.limparPrevia();
  }

  alternarColunaMes(id: string): void {
    if (this.operacaoRelatorioEmAndamento) return;
    if (this.colunasMesesSelecionadas.includes(id)) {
      this.colunasMesesSelecionadas = this.colunasMesesSelecionadas.filter((atual) => atual !== id);
    } else {
      this.colunasMesesSelecionadas = [...this.colunasMesesSelecionadas, id];
    }
    this.limparPrevia();
  }

  alternarRanking(): void {
    if (!this.rankingDisponivel || this.operacaoRelatorioEmAndamento) return;
    this.rankingAtivo = !this.rankingAtivo;
    if (this.rankingAtivo) {
      this.rankingDimensao ||= this.dimensoesRankingSelecionadas[0]?.id ?? '';
      this.rankingMetrica ||= this.metricasRankingSelecionadas[0]?.id ?? '';
      this.rankingLimite = this.limitarRanking(this.rankingLimite);
    }
    this.limparPrevia();
  }

  atualizarRankingLimite(valor: string | number): void {
    this.rankingLimite = this.limitarRanking(Number(valor));
    this.limparPrevia();
  }

  ordenarPor(coluna: string, direcao: 'ASC' | 'DESC'): void {
    if (this.gerando || this.exportando) return;
    this.colunaOrdenacao = coluna;
    this.direcaoOrdenacao = direcao;
    if (this.registros.length) this.gerar(1);
  }

  limparOrdenacao(): void {
    this.colunaOrdenacao = null;
    this.direcaoOrdenacao = 'ASC';
    if (this.registros.length) this.gerar(1);
  }

  ariaOrdenacao(coluna: string): 'ascending' | 'descending' | 'none' {
    if (this.colunaOrdenacao !== coluna) return 'none';
    return this.direcaoOrdenacao === 'ASC' ? 'ascending' : 'descending';
  }

  simboloOrdenacao(coluna: string): string {
    if (this.colunaOrdenacao !== coluna) return '↕';
    return this.direcaoOrdenacao === 'ASC' ? '↑' : '↓';
  }

  colunaNumerica(coluna: string): boolean {
    const base = this.baseColunaResultado(coluna);
    return !!this.configuracao?.colunas.find((item) => item.id === base)?.numerica;
  }

  rotuloOrdenacaoAsc(coluna: string): string {
    return this.colunaNumerica(coluna) ? 'Menor → maior' : 'A → Z';
  }

  rotuloOrdenacaoDesc(coluna: string): string {
    return this.colunaNumerica(coluna) ? 'Maior → menor' : 'Z → A';
  }

  iniciarArrasteResultado(coluna: string, event: DragEvent): void {
    if (this.operacaoRelatorioEmAndamento) {
      event.preventDefault();
      return;
    }
    this.colunaArrastadaResultado = coluna;
    this.colunaSobreResultado = coluna;
    event.dataTransfer?.setData('text/plain', coluna);
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }

  sobreArrasteResultado(coluna: string, event: DragEvent): void {
    if (!this.colunaArrastadaResultado || this.operacaoRelatorioEmAndamento) return;
    event.preventDefault();
    this.colunaSobreResultado = coluna;
  }

  soltarResultado(coluna: string, event: DragEvent): void {
    event.preventDefault();
    const origemId = this.colunaArrastadaResultado;
    if (!origemId || origemId === coluna) {
      this.finalizarArrasteResultado();
      return;
    }

    const ordem = [...this.colunasResultado];
    const origem = ordem.indexOf(origemId);
    const destino = ordem.indexOf(coluna);
    if (origem >= 0 && destino >= 0) {
      const [movida] = ordem.splice(origem, 1);
      ordem.splice(destino, 0, movida);
      this.colunasResultado = ordem;
      this.sincronizarBasePelaOrdemResultado();
    }
    this.finalizarArrasteResultado();
  }

  finalizarArrasteResultado(): void {
    this.colunaArrastadaResultado = null;
    this.colunaSobreResultado = null;
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
    if (this.secoesRecolhidas.has(secao)) this.secoesRecolhidas.delete(secao);
    else this.secoesRecolhidas.add(secao);
  }

  secaoRecolhida(secao: SecaoRelatorio): boolean {
    return this.secoesRecolhidas.has(secao);
  }

  limparFiltros(): void {
    this.versaoLimpezaFiltros++;
    const competenciaAtual = this.competenciaAtual();
    Object.keys(this.valoresFiltro).forEach((chave) => (this.valoresFiltro[chave] = ''));
    this.valoresFiltro['competencia_inicio'] = competenciaAtual;
    this.valoresFiltro['competencia_fim'] = competenciaAtual;
    this.filtrosAtivos =
      this.configuracao?.filtros.filter((filtro) => filtro.obrigatorio).map((filtro) => filtro.id) ?? [];
    this.limparPrevia();
    this.erro = '';
  }

  rotuloColuna(id: string): string {
    return (
      this.rotulosResultado[id] ??
      this.configuracao?.colunas.find((coluna) => coluna.id === id)?.rotulo ??
      id
    );
  }

  atualizarFiltros(valores: Record<string, string>): void {
    this.valoresFiltro = valores;
    this.limparPrevia();
  }

  atualizarFiltrosAtivos(ids: string[]): void {
    this.filtrosAtivos = [...new Set(ids)];
    this.limparPrevia();
  }

  salvarPreset(): void {
    const nome = this.nomeNovoPreset.trim();
    if (!nome || !this.configuracao || !this.ordemColunasSelecionadas.length) {
      this.erro = 'Informe um nome e selecione ao menos uma coluna antes de salvar o modelo.';
      return;
    }

    const preset: AssistencialPreset = {
      id: this.novoId(),
      nome,
      colunas: [...this.ordemColunasSelecionadas],
      filtros: [...new Set(this.filtrosAtivos)],
      distinct: this.somenteDistintos,
      separarMeses: this.separarMeses,
      colunasMeses: [...this.colunasMesesSelecionadas],
      rankingAtivo: this.rankingAtivo,
      rankingDimensao: this.rankingDimensao,
      rankingMetrica: this.rankingMetrica,
      rankingDirecao: this.rankingDirecao,
      rankingLimite: this.rankingLimite,
    };

    this.presets = [...this.presets, preset];
    this.salvarPresets();
    this.presetSelecionado = preset.id;
    this.nomeNovoPreset = '';
    this.sucesso = `Modelo “${preset.nome}” salvo. Os valores dos filtros não foram armazenados.`;
  }

  aplicarPreset(id: string): void {
    const preset = this.presets.find((item) => item.id === id);
    if (!preset || !this.configuracao) return;

    const permitidas = new Set(this.configuracao.colunas.map((coluna) => coluna.id));
    this.ordemColunasSelecionadas = preset.colunas.filter((idColuna) => permitidas.has(idColuna));
    this.colunasSelecionadas = new Set(this.ordemColunasSelecionadas);

    const obrigatorios = this.configuracao.filtros
      .filter((filtro) => filtro.obrigatorio)
      .map((filtro) => filtro.id);
    this.filtrosAtivos = [...new Set([...obrigatorios, ...preset.filtros])];
    this.somenteDistintos = preset.distinct;
    this.separarMeses = !!preset.separarMeses;
    this.colunasMesesSelecionadas = (preset.colunasMeses ?? []).filter(
      (coluna) => this.colunasSelecionadas.has(coluna),
    );
    this.rankingAtivo = !!preset.rankingAtivo;
    this.rankingDimensao = preset.rankingDimensao ?? '';
    this.rankingMetrica = preset.rankingMetrica ?? '';
    this.rankingDirecao = preset.rankingDirecao ?? 'MAIORES';
    this.rankingLimite = this.limitarRanking(preset.rankingLimite ?? 10);
    this.validarRankingAposColunas();

    Object.keys(this.valoresFiltro).forEach((key) => (this.valoresFiltro[key] = ''));
    const competencia = this.competenciaAtual();
    this.valoresFiltro['competencia_inicio'] = competencia;
    this.valoresFiltro['competencia_fim'] = competencia;
    this.colunasResultado = [...this.ordemColunasSelecionadas];
    this.rotulosResultado = {};
    this.limparPrevia();
    this.sucesso = `Modelo “${preset.nome}” aplicado. Preencha os filtros para gerar o relatório.`;
  }

  excluirPreset(): void {
    if (!this.presetSelecionado) return;
    this.presets = this.presets.filter((item) => item.id !== this.presetSelecionado);
    this.salvarPresets();
    this.presetSelecionado = '';
  }

  valorCelula(coluna: string, valor: unknown): string {
    return formatReportPreviewValue(this.baseColunaResultado(coluna), valor);
  }

  colunaProtegida(coluna: string): boolean {
    return isProtectedBeneficiaryColumn(this.baseColunaResultado(coluna));
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
    this.filtrosAtivos = configuracao.filtros
      .filter((filtro) => filtro.obrigatorio)
      .map((filtro) => filtro.id);

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
    if (this.separarMeses && !this.colunasMesesSelecionadas.length) {
      this.erro = 'Escolha pelo menos uma coluna numérica para separar por mês.';
      return null;
    }
    if (this.rankingAtivo) {
      if (!this.rankingDisponivel || !this.rankingDimensao || !this.rankingMetrica) {
        this.erro = 'Escolha a dimensão e a métrica para maiores/menores gastadores.';
        return null;
      }
      this.rankingLimite = this.limitarRanking(this.rankingLimite);
    }
    if (!this.validarIndicadoresFinanceiros()) return null;

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
      separarMeses: this.separarMeses,
      colunasMeses: this.separarMeses ? [...this.colunasMesesSelecionadas] : [],
      ...(this.rankingAtivo
        ? {
            rankingDimensao: this.rankingDimensao,
            rankingMetrica: this.rankingMetrica,
            rankingDirecao: this.rankingDirecao,
            rankingLimite: this.rankingLimite,
          }
        : {}),
      ordemResultado: this.registros.length ? [...this.colunasResultado] : [],
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
    this.rotulosResultado =
      respostaGenerica?.rotulosColunas && typeof respostaGenerica.rotulosColunas === 'object'
        ? { ...respostaGenerica.rotulosColunas }
        : {};

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

  limparPrevia(): void {
    this.registros = [];
    this.totalRegistros = null;
    this.totalRegistrosExportados = null;
    this.pagina = 1;
    this.ultimaPagina = false;
    this.sucesso = '';
    this.rotulosResultado = {};
  }

  private sincronizarOrdemColunas(): void {
    const selecionadas = this.colunasSelecionadas;
    const ordemValida = this.ordemColunasSelecionadas.filter((id) => selecionadas.has(id));
    selecionadas.forEach((id) => {
      if (!ordemValida.includes(id)) ordemValida.push(id);
    });
    this.ordemColunasSelecionadas = ordemValida;
  }

  private sincronizarColunasResultadoComBase(): void {
    if (!this.registros.length) {
      this.colunasResultado = [...this.ordemColunasSelecionadas];
      return;
    }
    this.reordenarResultadoPelaBase();
  }

  private reordenarResultadoPelaBase(): void {
    if (!this.colunasResultado.length) {
      this.colunasResultado = [...this.ordemColunasSelecionadas];
      return;
    }

    const indices = new Map(this.ordemColunasSelecionadas.map((id, index) => [id, index]));
    this.colunasResultado = this.colunasResultado
      .map((coluna, index) => ({ coluna, index }))
      .sort((a, b) => {
        const ia = indices.get(this.baseColunaResultado(a.coluna)) ?? Number.MAX_SAFE_INTEGER;
        const ib = indices.get(this.baseColunaResultado(b.coluna)) ?? Number.MAX_SAFE_INTEGER;
        return ia === ib ? a.index - b.index : ia - ib;
      })
      .map((item) => item.coluna);
  }

  private sincronizarBasePelaOrdemResultado(): void {
    const vistos = new Set<string>();
    const novaBase: string[] = [];

    this.colunasResultado.forEach((coluna) => {
      const base = this.baseColunaResultado(coluna);
      if (this.colunasSelecionadas.has(base) && !vistos.has(base)) {
        vistos.add(base);
        novaBase.push(base);
      }
    });

    this.ordemColunasSelecionadas.forEach((coluna) => {
      if (!vistos.has(coluna) && this.colunasSelecionadas.has(coluna)) {
        vistos.add(coluna);
        novaBase.push(coluna);
      }
    });

    this.ordemColunasSelecionadas = novaBase;
  }

  private baseColunaResultado(coluna: string | null): string {
    if (!coluna) return '';
    if (this.configuracao?.colunas.some((item) => item.id === coluna)) return coluna;
    const match = coluna.match(/^(.+)_\d{6}$/);
    if (match && this.configuracao?.colunas.some((item) => item.id === match[1])) {
      return match[1];
    }
    return coluna;
  }

  private validarRankingAposColunas(): void {
    if (!this.rankingDisponivel) {
      this.rankingAtivo = false;
      this.rankingDimensao = '';
      this.rankingMetrica = '';
      return;
    }

    if (!this.dimensoesRankingSelecionadas.some((item) => item.id === this.rankingDimensao)) {
      this.rankingDimensao = this.dimensoesRankingSelecionadas[0]?.id ?? '';
    }
    if (!this.metricasRankingSelecionadas.some((item) => item.id === this.rankingMetrica)) {
      this.rankingMetrica = this.metricasRankingSelecionadas[0]?.id ?? '';
    }
  }

  private limitarRanking(valor: number): number {
    if (!Number.isFinite(valor)) return 10;
    return Math.min(1000, Math.max(1, Math.trunc(valor)));
  }

  private agrupar<T extends { grupo: string }>(itens: T[]): Grupo<T>[] {
    const mapa = new Map<string, T[]>();
    itens.forEach((item) => mapa.set(item.grupo, [...(mapa.get(item.grupo) ?? []), item]));
    return [...mapa.entries()].map(([nome, itensGrupo]) => ({ nome, itens: itensGrupo }));
  }

  private carregarPresets(): AssistencialPreset[] {
    if (typeof localStorage === 'undefined') return [];
    try {
      return JSON.parse(
        localStorage.getItem('unimed-tools.assistencial.modelos.v1') || '[]',
      ) as AssistencialPreset[];
    } catch {
      return [];
    }
  }

  private salvarPresets(): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('unimed-tools.assistencial.modelos.v1', JSON.stringify(this.presets));
    }
  }

  private novoId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
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
