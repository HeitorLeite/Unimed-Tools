import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import {
  FormatoExportacao,
  RelatorioAgendamentoConfiguracao,
  RelatorioAgendamentoCriacao,
  RelatorioAgendamentoResumo,
  RecorrenciaRelatorioAgendado,
  RelatorioCatalogo,
  RelatorioPersonalizadoConfiguracao,
  RelatorioPersonalizadoRequest,
  SguFiltro,
  SguResultado,
  TipoRelatorioAgendado,
} from '../../../shared/models/relatorio.model';
import {
  DiretorioAgendamentoErro,
  DiretorioAgendamentoSelecionado,
  DiretorioAgendamentoService,
} from '../../../shared/services/diretorio-agendamento.service';
import { ExecutorAgendamentoService } from '../../../shared/services/executor-agendamento.service';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import { formatReportPreviewValue } from '../../../shared/utils/report-preview.utils';

interface ColunaAgendada {
  nome: string;
  incluir: boolean;
}

@Component({
  selector: 'app-relatorios-agendados',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './relatorios-agendados.component.html',
  styleUrls: ['./relatorios-agendados.component.scss'],
})
export class RelatoriosAgendadosComponent implements OnInit, OnDestroy {
  @Input() relatorios: RelatorioCatalogo[] = [];
  @Output() voltar = new EventEmitter<void>();

  etapa: 1 | 2 | 3 = 1;
  tipoRelatorio: TipoRelatorioAgendado = 'MANUAL';
  relatorioSelecionadoId = '';
  valoresFiltroManual: Record<string, string> = {};
  configuracaoPersonalizada: RelatorioPersonalizadoConfiguracao | null = null;
  valoresFiltroPersonalizado: Record<string, string> = {};
  colunasPersonalizadas = new Set<string>();
  distinct = false;
  ordenarPor = '';
  direcaoOrdenacao: 'ASC' | 'DESC' = 'ASC';

  registros: Record<string, unknown>[] = [];
  colunas: ColunaAgendada[] = [];
  incluirCabecalho = true;
  carregandoPrevia = false;

  agendadoPara = '';
  nomeArquivo = '';
  formato: FormatoExportacao = 'xlsx';
  recorrencia: RecorrenciaRelatorioAgendado = 'UNICA';
  diasSemanaSelecionados: number[] = [];
  diaMes = 1;
  readonly diasSemanaOpcoes = [
    { valor: 1, rotulo: 'Seg' },
    { valor: 2, rotulo: 'Ter' },
    { valor: 3, rotulo: 'Qua' },
    { valor: 4, rotulo: 'Qui' },
    { valor: 5, rotulo: 'Sex' },
    { valor: 6, rotulo: 'Sáb' },
    { valor: 7, rotulo: 'Dom' },
  ];
  diretorio: DiretorioAgendamentoSelecionado | null = null;
  selecionandoDiretorio = false;
  salvando = false;

  configuracaoAgendamento: RelatorioAgendamentoConfiguracao | null = null;
  agendamentos: RelatorioAgendamentoResumo[] = [];
  carregandoAgendamentos = false;
  erro = '';
  sucesso = '';

  private atualizacaoLista?: ReturnType<typeof setInterval>;

  constructor(
    private readonly relatorioService: RelatorioService,
    readonly diretorioService: DiretorioAgendamentoService,
    readonly executor: ExecutorAgendamentoService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.definirDataInicial();
    this.relatorioSelecionadoId = this.relatorios[0]?.id ?? '';
    this.atualizarFiltrosManual();
    void this.carregarDados();
    this.atualizacaoLista = setInterval(() => void this.carregarAgendamentos(false), 20_000);
  }

  ngOnDestroy(): void {
    if (this.atualizacaoLista) clearInterval(this.atualizacaoLista);
  }

  get relatorioManualSelecionado(): RelatorioCatalogo | null {
    return this.relatorios.find((item) => item.id === this.relatorioSelecionadoId) ?? null;
  }

  get colunasAtivas(): ColunaAgendada[] {
    return this.colunas.filter((coluna) => coluna.incluir);
  }

  get podeAgendar(): boolean {
    return Boolean(
      this.configuracaoAgendamento?.disponivel &&
        this.agendadoPara &&
        this.nomeArquivo.trim() &&
        this.diretorio &&
        this.colunasAtivas.length &&
        (this.recorrencia !== 'SEMANAL' || this.diasSemanaSelecionados.length) &&
        (this.recorrencia !== 'MENSAL' || (this.diaMes >= 1 && this.diaMes <= 31)) &&
        !this.salvando,
    );
  }

  get operacaoEmAndamento(): boolean {
    return this.carregandoPrevia || this.salvando || this.selecionandoDiretorio;
  }

  selecionarTipo(tipo: TipoRelatorioAgendado): void {
    this.tipoRelatorio = tipo;
    this.registros = [];
    this.colunas = [];
    this.erro = '';
  }

  atualizarFiltrosManual(): void {
    this.valoresFiltroManual = Object.fromEntries(
      (this.relatorioManualSelecionado?.filtros ?? []).map((filtro) => [filtro.nomeFiltro, '']),
    );
  }

  alternarColunaPersonalizada(id: string, incluir: boolean): void {
    if (incluir) this.colunasPersonalizadas.add(id);
    else this.colunasPersonalizadas.delete(id);
  }

  alterarRecorrencia(): void {
    const data = new Date(this.agendadoPara);
    if (this.recorrencia === 'SEMANAL' && !this.diasSemanaSelecionados.length) {
      const diaJs = Number.isFinite(data.getTime()) ? data.getDay() : new Date().getDay();
      this.diasSemanaSelecionados = [diaJs === 0 ? 7 : diaJs];
    }
    if (this.recorrencia === 'MENSAL') {
      this.diaMes = Number.isFinite(data.getTime()) ? data.getDate() : new Date().getDate();
    }
  }

  alternarDiaSemana(dia: number, selecionado: boolean): void {
    this.diasSemanaSelecionados = selecionado
      ? [...new Set([...this.diasSemanaSelecionados, dia])].sort((a, b) => a - b)
      : this.diasSemanaSelecionados.filter((item) => item !== dia);
  }

  async gerarPrevia(): Promise<void> {
    this.erro = '';
    this.sucesso = '';
    this.carregandoPrevia = true;
    try {
      const resposta =
        this.tipoRelatorio === 'MANUAL'
          ? await this.gerarPreviaManual()
          : await this.gerarPreviaPersonalizada();
      const registros = this.extrairRegistros(resposta);
      if (!registros.length) {
        throw new Error('A consulta não retornou registros para montar a prévia.');
      }
      this.registros = registros;
      const colunasResposta = Array.isArray(resposta['colunas'])
        ? (resposta['colunas'] as string[])
        : Object.keys(registros[0]).filter((coluna) => coluna.toUpperCase() !== 'RNUM');
      this.colunas = colunasResposta.map((nome) => ({ nome, incluir: true }));
      this.nomeArquivo = this.nomeArquivoPadrao();
      this.etapa = 2;
    } catch (erro) {
      this.erro = this.mensagemErro(erro, 'Não foi possível gerar a prévia.');
    } finally {
      this.carregandoPrevia = false;
      this.cdr.detectChanges();
    }
  }

  moverColuna(indice: number, direcao: -1 | 1): void {
    const destino = indice + direcao;
    if (destino < 0 || destino >= this.colunas.length) return;
    const copia = [...this.colunas];
    [copia[indice], copia[destino]] = [copia[destino], copia[indice]];
    this.colunas = copia;
  }

  avancarParaAgendamento(): void {
    if (!this.colunasAtivas.length) {
      this.erro = 'Mantenha pelo menos uma coluna no arquivo.';
      return;
    }
    this.erro = '';
    this.etapa = 3;
  }

  async selecionarDiretorio(): Promise<void> {
    this.selecionandoDiretorio = true;
    this.erro = '';
    try {
      this.diretorio = await this.diretorioService.selecionar();
    } catch (erro) {
      if (erro instanceof DOMException && erro.name === 'AbortError') return;
      this.erro =
        erro instanceof DiretorioAgendamentoErro
          ? erro.message
          : 'Não foi possível selecionar a pasta.';
    } finally {
      this.selecionandoDiretorio = false;
      this.cdr.detectChanges();
    }
  }

  async confirmarAgendamento(): Promise<void> {
    if (!this.podeAgendar || !this.diretorio) return;
    const instante = new Date(this.agendadoPara);
    if (!Number.isFinite(instante.getTime()) || instante.getTime() <= Date.now()) {
      this.erro = 'Escolha uma data e hora futuras.';
      return;
    }

    this.salvando = true;
    this.erro = '';
    try {
      const request = this.montarCriacao(instante);
      await firstValueFrom(this.relatorioService.criarAgendamento(request));
      this.sucesso = 'Relatório agendado. Mantenha o navegador aberto no horário escolhido.';
      this.etapa = 1;
      this.registros = [];
      this.colunas = [];
      this.diretorio = null;
      this.recorrencia = 'UNICA';
      this.diasSemanaSelecionados = [];
      this.definirDataInicial();
      await this.carregarAgendamentos(false);
    } catch (erro) {
      this.erro = this.mensagemErro(erro, 'Não foi possível criar o agendamento.');
    } finally {
      this.salvando = false;
      this.cdr.detectChanges();
    }
  }

  async alterarPasta(agendamento: RelatorioAgendamentoResumo): Promise<void> {
    this.erro = '';
    try {
      const diretorio = await this.diretorioService.selecionar();
      const resposta = await firstValueFrom(
        this.relatorioService.alterarDestinoAgendamento(
          agendamento.id,
          diretorio.referencia,
          diretorio.nome,
        ),
      );
      this.sucesso = resposta.message;
      await this.carregarAgendamentos(false);
      await this.executor.verificarAgora();
    } catch (erro) {
      if (erro instanceof DOMException && erro.name === 'AbortError') return;
      this.erro = this.mensagemErro(erro, 'Não foi possível alterar a pasta.');
    } finally {
      this.cdr.detectChanges();
    }
  }

  async cancelar(agendamento: RelatorioAgendamentoResumo): Promise<void> {
    if (!confirm(`Cancelar o agendamento “${agendamento.tituloRelatorio}”?`)) return;
    try {
      const resposta = await firstValueFrom(
        this.relatorioService.cancelarAgendamento(agendamento.id),
      );
      this.sucesso = resposta.message;
      await this.carregarAgendamentos(false);
    } catch (erro) {
      this.erro = this.mensagemErro(erro, 'Não foi possível cancelar o agendamento.');
    } finally {
      this.cdr.detectChanges();
    }
  }

  formatarValor(valor: unknown, coluna: string): string {
    return formatReportPreviewValue(coluna, valor);
  }

  formatarData(valor?: string): string {
    if (!valor) return '—';
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(valor));
  }

  rotuloStatus(status: RelatorioAgendamentoResumo['status']): string {
    return {
      PENDENTE: 'Pendente',
      EM_EXECUCAO: 'Em execução',
      CONCLUIDO: 'Concluído',
      FALHA: 'Falha',
      CANCELADO: 'Cancelado',
    }[status];
  }

  rotuloRecorrencia(agendamento: RelatorioAgendamentoResumo): string {
    if (agendamento.recorrencia === 'DIARIA') return 'Diariamente';
    if (agendamento.recorrencia === 'MENSAL') return `Mensalmente no dia ${agendamento.diaMes}`;
    if (agendamento.recorrencia === 'SEMANAL') {
      const rotulos = agendamento.diasSemana
        .map((dia) => this.diasSemanaOpcoes.find((opcao) => opcao.valor === dia)?.rotulo)
        .filter(Boolean);
      return `Semanalmente: ${rotulos.join(', ')}`;
    }
    return 'Execução única';
  }

  tipoInputManual(filtro: SguFiltro): string {
    const tipo = filtro.tipoDadoFiltro.toUpperCase();
    return tipo === 'DATE' ? 'date' : tipo === 'NUMBER' ? 'number' : 'text';
  }

  tipoInputPersonalizado(tipo: string): string {
    if (tipo === 'date') return 'date';
    if (tipo === 'number' || tipo === 'decimal') return 'number';
    if (tipo === 'competencia') return 'month';
    return 'text';
  }

  private async carregarDados(): Promise<void> {
    try {
      const [configuracao, personalizada] = await Promise.all([
        firstValueFrom(this.relatorioService.configuracaoAgendamento()),
        firstValueFrom(this.relatorioService.configuracaoPersonalizada()),
      ]);
      this.configuracaoAgendamento = configuracao;
      this.configuracaoPersonalizada = personalizada;
      this.inicializarPersonalizado(personalizada);
      await this.carregarAgendamentos(true);
    } catch (erro) {
      this.erro = this.mensagemErro(erro, 'Não foi possível carregar o agendador.');
    } finally {
      this.cdr.detectChanges();
    }
  }

  private async carregarAgendamentos(exibirCarregamento: boolean): Promise<void> {
    if (exibirCarregamento) this.carregandoAgendamentos = true;
    try {
      this.agendamentos = await firstValueFrom(this.relatorioService.listarAgendamentos());
    } finally {
      this.carregandoAgendamentos = false;
      this.cdr.detectChanges();
    }
  }

  private async gerarPreviaManual(): Promise<SguResultado> {
    const relatorio = this.relatorioManualSelecionado;
    if (!relatorio) throw new Error('Selecione um relatório existente.');
    const obrigatorio = relatorio.filtros.find(
      (filtro) =>
        filtro.obrigatorioFiltro === 'S' &&
        !String(this.valoresFiltroManual[filtro.nomeFiltro] ?? '').trim(),
    );
    if (obrigatorio) throw new Error(`Preencha o filtro “${obrigatorio.nomeFiltro}”.`);
    return firstValueFrom(
      this.relatorioService.executar(relatorio.apiNome, {
        ...this.filtrosManual(),
        page: 1,
        size: 25,
      }),
    );
  }

  private async gerarPreviaPersonalizada(): Promise<SguResultado> {
    const request = this.requestPersonalizado();
    return firstValueFrom(this.relatorioService.executarPersonalizado(request));
  }

  private requestPersonalizado(): RelatorioPersonalizadoRequest {
    if (!this.colunasPersonalizadas.size) throw new Error('Selecione pelo menos uma coluna.');
    const filtros = Object.fromEntries(
      Object.entries(this.valoresFiltroPersonalizado)
        .filter(([, valor]) => valor.trim())
        .map(([id, valor]) => [id, id.startsWith('competencia_') ? valor.replace('-', '') : valor]),
    );
    return {
      colunas: [...this.colunasPersonalizadas],
      filtros,
      distinct: this.distinct,
      ...(this.ordenarPor
        ? { ordenarPor: this.ordenarPor, direcaoOrdenacao: this.direcaoOrdenacao }
        : {}),
      pagina: 1,
      tamanhoPagina: 25,
      nomeArquivo: this.nomeArquivo || 'relatorio_agendado',
    };
  }

  private filtrosManual(): Record<string, unknown> {
    const parametros: Record<string, unknown> = {};
    for (const filtro of this.relatorioManualSelecionado?.filtros ?? []) {
      const valor = String(this.valoresFiltroManual[filtro.nomeFiltro] ?? '').trim();
      if (!valor) continue;
      parametros[filtro.nomeFiltro] =
        filtro.tipoDadoFiltro.toUpperCase() === 'NUMBER' ? Number(valor) : valor;
    }
    return parametros;
  }

  private montarCriacao(instante: Date): RelatorioAgendamentoCriacao {
    const manual = this.relatorioManualSelecionado;
    const personalizado = this.tipoRelatorio === 'PERSONALIZADO' ? this.requestPersonalizado() : null;
    return {
      tipoRelatorio: this.tipoRelatorio,
      tituloRelatorio:
        this.tipoRelatorio === 'MANUAL'
          ? manual?.nomeExibicao ?? 'Relatório manual'
          : 'Relatório personalizado',
      ...(manual && this.tipoRelatorio === 'MANUAL' ? { apiNome: manual.apiNome } : {}),
      filtros: personalizado?.filtros ?? this.filtrosManual(),
      ...(personalizado
        ? {
            colunasPersonalizadas: personalizado.colunas,
            distinct: personalizado.distinct,
            ordenarPor: personalizado.ordenarPor,
            direcaoOrdenacao: personalizado.direcaoOrdenacao,
          }
        : {}),
      colunasExportacao: this.colunasAtivas.map((coluna) => coluna.nome),
      incluirCabecalho: this.incluirCabecalho,
      formato: this.formato,
      nomeArquivo: this.nomeArquivo.trim(),
      diretorioReferencia: this.diretorio!.referencia,
      diretorioNome: this.diretorio!.nome,
      agendadoPara: instante.toISOString(),
      recorrencia: this.recorrencia,
      diasSemana: this.recorrencia === 'SEMANAL' ? this.diasSemanaSelecionados : [],
      ...(this.recorrencia === 'MENSAL' ? { diaMes: this.diaMes } : {}),
      fusoHorario:
        Intl.DateTimeFormat().resolvedOptions().timeZone || 'America/Sao_Paulo',
    };
  }

  private inicializarPersonalizado(configuracao: RelatorioPersonalizadoConfiguracao): void {
    this.valoresFiltroPersonalizado = Object.fromEntries(
      configuracao.filtros.map((filtro) => [filtro.id, '']),
    );
    const competencia = new Date().toISOString().slice(0, 7);
    this.valoresFiltroPersonalizado['competencia_inicio'] = competencia;
    this.valoresFiltroPersonalizado['competencia_fim'] = competencia;
    this.colunasPersonalizadas = new Set(
      configuracao.colunas.filter((coluna) => coluna.selecionadaPorPadrao).map((coluna) => coluna.id),
    );
  }

  private extrairRegistros(resposta: SguResultado): Record<string, unknown>[] {
    const pagina = (resposta as { data?: SguResultado }).data ?? resposta;
    const conteudo = Array.isArray(resposta.content) ? resposta.content : pagina.content;
    return Array.isArray(conteudo) ? conteudo : [];
  }

  private definirDataInicial(): void {
    const data = new Date(Date.now() + 60 * 60 * 1000);
    data.setSeconds(0, 0);
    const local = new Date(data.getTime() - data.getTimezoneOffset() * 60_000);
    this.agendadoPara = local.toISOString().slice(0, 16);
  }

  private nomeArquivoPadrao(): string {
    const origem =
      this.tipoRelatorio === 'MANUAL'
        ? this.relatorioManualSelecionado?.nomeExibicao ?? 'relatorio'
        : 'relatorio_personalizado';
    return origem
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9_-]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .toLowerCase();
  }

  private mensagemErro(erro: any, fallback: string): string {
    if (erro instanceof Error && !(erro as any)?.error) return erro.message;
    return erro?.error?.message ?? erro?.message ?? fallback;
  }
}
