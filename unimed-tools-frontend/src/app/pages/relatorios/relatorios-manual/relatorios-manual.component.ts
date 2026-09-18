import { extrairPrimeiraInstrucaoSql, normalizarVariaveisBindSql } from '../sql/sql-lexico';
import { filtroDetectadoDoSql, detectarFiltrosFixosSimples, converterParametrosFixosSql, converterFiltrosFixosCteSql } from '../sql/sql-filtros';
import { ajustarEstruturaSqlImportado } from '../sql/sql-estrutura';
import { normalizarFiltros, filtrosDeNegocio, removerFiltroTecnicoDaDefinicao, prepararDefinicaoParaSgu, clonarDefinicaoApi, validarDefinicaoApi, validarCorrespondenciaBind, filtroVazio } from '../sql/sgu-definicao';
import { ArquivoSqlImportado } from '../sql/sql-importacao.model';
/**
 * Coordena catálogo, APIs SGU, SQL importado, execução manual, templates e exportações.
 */
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, firstValueFrom, timeout } from 'rxjs';

import {
  FormatoExportacao,
  RelatorioCatalogo,
  RelatorioTemplate,
  SguApiDefinicao,
  SguFiltro,
  SguResultado,
} from '../../../shared/models/relatorio.model';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import {
  formatReportPreviewValue,
  isProtectedBeneficiaryColumn,
} from '../../../shared/utils/report-preview.utils';

type ModoCadastro = 'existente' | 'lista' | 'nova' | 'arquivos';

@Component({
  selector: 'app-relatorios-manual',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './relatorios-manual.component.html',
  styleUrls: ['./relatorios-manual.component.scss'],
})
export class RelatoriosManualComponent implements OnInit, OnDestroy {
  @Output() voltar = new EventEmitter<void>();
  relatorios: RelatorioCatalogo[] = [];
  relatoriosFiltrados: RelatorioCatalogo[] = [];
  selecionado: RelatorioCatalogo | null = null;
  pesquisa = '';

  valoresFiltro: Record<string, string | number> = {};
  registros: Record<string, unknown>[] = [];
  colunas: string[] = [];
  pagina = 1;
  tamanhoPagina = 50;
  ultimaPagina = false;
  totalRegistros: number | null = null;

  carregando = false;
  segundosGeracao = 0;
  progressoGeracao = 0;
  mensagemGeracao = '';
  duracaoUltimaConsultaMs: number | null = null;

  exportando: FormatoExportacao | null = null;
  segundosExportacao = 0;
  progressoExportacao = 0;
  mensagemExportacao = '';
  formatoSelecionado: FormatoExportacao = 'xlsx';
  nomeArquivoDownload = '';
  erro = '';
  sucesso = '';

  modalNovoAberto = false;
  modoCadastro: ModoCadastro = 'existente';
  buscandoApi = false;
  salvandoApi = false;
  apiEncontrada: SguApiDefinicao | null = null;
  novoApiNome = '';
  novoNomeExibicao = '';
  novaDescricao = '';
  novaConsultaSql = '';
  novaOrdenacao = '';
  novosFiltros: SguFiltro[] = [filtroVazio()];

  arquivosSqlImportados: ArquivoSqlImportado[] = [];
  carregandoArquivosSql = false;
  criandoApisEmLote = false;
  arrastandoArquivosSql = false;
  progressoCriacaoLote = 0;
  totalCriacaoLote = 0;

  modalEditarAberto = false;
  carregandoEdicao = false;
  salvandoEdicao = false;
  relatorioEmEdicao: RelatorioCatalogo | null = null;
  apiOriginalEdicao: SguApiDefinicao | null = null;
  editarApiNome = '';
  editarNomeExibicao = '';
  editarDescricao = '';
  editarConsultaSql = '';
  editarOrdenacao = '';
  filtrosEdicao: SguFiltro[] = [filtroVazio()];

  carregandoListaApis = false;
  listaApisCarregada = false;
  apisDisponiveis: SguApiDefinicao[] = [];
  apisDisponiveisFiltradas: SguApiDefinicao[] = [];
  apisSelecionadas: Record<string, boolean> = {};
  pesquisaApis = '';

  templates: RelatorioTemplate[] = [];
  templateAtivo: RelatorioTemplate | null = null;
  relatoriosAbertos: RelatorioCatalogo[] = [];
  modalTemplateAberto = false;
  novoTemplateNome = '';
  novoTemplateDescricao = '';
  relatoriosTemplateSelecionados: Record<string, boolean> = {};

  modalExcluirAberto = false;
  relatorioParaExcluir: RelatorioCatalogo | null = null;
  apagarTambemNoSgu = false;
  excluindo = false;

  private intervaloGeracao?: ReturnType<typeof setInterval>;
  private intervaloExportacao?: ReturnType<typeof setInterval>;
  private inicioGeracao = 0;
  private readonly timeoutGeracaoMs = 120_000;
  private readonly valoresFiltroPorRelatorio: Record<string, Record<string, string | number>> = {};

  get operacaoRelatorioEmAndamento(): boolean {
    return this.carregando || this.exportando !== null;
  }

  constructor(
    private readonly relatorioService: RelatorioService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.relatorios = this.relatorioService.listarCatalogo();
    this.templates = this.normalizarTemplates(this.relatorioService.listarTemplates());
    this.persistirTemplates();
    this.aplicarPesquisa();

    if (this.relatorios.length) {
      this.selecionar(this.relatorios[0]);
    }
  }

  // ── Navegação e ciclo de vida ───────────────────────────────────────────────

  ngOnDestroy(): void {
    this.salvarFiltrosSelecionadoAtual();
    this.pararCronometroGeracao();
    this.pararCronometroExportacao();
  }

  aplicarPesquisa(): void {
    const termo = this.pesquisa.trim().toLowerCase();

    this.relatoriosFiltrados = !termo
      ? [...this.relatorios]
      : this.relatorios.filter((relatorio) =>
          `${relatorio.nomeExibicao} ${relatorio.descricao} ${relatorio.apiNome}`
            .toLowerCase()
            .includes(termo),
        );
  }

  // ── Seleção do catálogo e manutenção de APIs no SGU ────────────────────────

  selecionar(relatorio: RelatorioCatalogo, manterTemplateAtivo = false): void {
    this.salvarFiltrosSelecionadoAtual();

    if (!manterTemplateAtivo) {
      this.templateAtivo = null;
      this.relatoriosAbertos = [];
    }

    this.selecionado = relatorio;
    const valoresSalvos = this.valoresFiltroPorRelatorio[relatorio.id];

    this.valoresFiltro = valoresSalvos
      ? { ...valoresSalvos }
      : this.criarValoresFiltroVazios(relatorio);

    this.nomeArquivoDownload = this.nomeArquivo(relatorio.nomeExibicao);
    this.limparResultado();
  }

  selecionarRelatorioTemplate(relatorio: RelatorioCatalogo): void {
    this.selecionar(relatorio, true);
  }

  limparResultado(): void {
    this.registros = [];
    this.colunas = [];
    this.pagina = 1;
    this.ultimaPagina = false;
    this.totalRegistros = null;
    this.duracaoUltimaConsultaMs = null;
    this.erro = '';
    this.sucesso = '';
  }

  abrirNovo(): void {
    this.modalNovoAberto = true;
    this.modoCadastro = 'existente';
    this.apiEncontrada = null;
    this.novoApiNome = '';
    this.novoNomeExibicao = '';
    this.novaDescricao = '';
    this.novaConsultaSql = '';
    this.novaOrdenacao = '';
    this.novosFiltros = [filtroVazio()];
    this.arquivosSqlImportados = [];
    this.carregandoArquivosSql = false;
    this.criandoApisEmLote = false;
    this.arrastandoArquivosSql = false;
    this.progressoCriacaoLote = 0;
    this.totalCriacaoLote = 0;
    this.pesquisaApis = '';
    this.apisSelecionadas = {};
    this.erro = '';
  }

  fecharNovo(): void {
    if (
      !this.buscandoApi &&
      !this.salvandoApi &&
      !this.carregandoListaApis &&
      !this.carregandoArquivosSql &&
      !this.criandoApisEmLote
    ) {
      this.modalNovoAberto = false;
    }
  }

  mudarModo(modo: ModoCadastro): void {
    this.modoCadastro = modo;
    this.apiEncontrada = null;
    this.erro = '';

    if (modo === 'lista') {
      this.carregarApisCadastradas();
    }
  }

  buscarApiExistente(): void {
    const nome = this.novoApiNome.trim();

    if (!nome) {
      this.erro = 'Informe o nome da API cadastrada no SGU.';
      return;
    }

    this.buscandoApi = true;
    this.erro = '';
    this.apiEncontrada = null;

    this.relatorioService
      .buscarApi(nome)
      .pipe(
        timeout(this.timeoutGeracaoMs),
        finalize(() => {
          this.buscandoApi = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (api) => {
          this.apiEncontrada = removerFiltroTecnicoDaDefinicao(api);
          this.novoNomeExibicao ||= this.tituloAPartirDoNome(api.nome);
        },
        error: (err) => {
          this.erro = this.mensagemErro(err, 'Não foi possível localizar essa API no SGU.');
        },
      });
  }

  salvarApiExistente(): void {
    if (!this.apiEncontrada) return;

    if (!this.novoNomeExibicao.trim()) {
      this.erro = 'Informe o nome que será exibido na página.';
      return;
    }

    this.adicionarAoCatalogo(this.apiEncontrada);
    this.modalNovoAberto = false;
  }

  carregarApisCadastradas(recarregar = false): void {
    if (this.carregandoListaApis) return;

    if (this.listaApisCarregada && !recarregar) {
      this.aplicarPesquisaApis();
      return;
    }

    this.carregandoListaApis = true;
    this.erro = '';

    this.relatorioService
      .listarApis()
      .pipe(
        timeout(this.timeoutGeracaoMs),
        finalize(() => {
          this.carregandoListaApis = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (apis) => {
          this.apisDisponiveis = this.normalizarListaApis(apis);
          this.listaApisCarregada = true;
          this.apisSelecionadas = {};
          this.aplicarPesquisaApis();
        },
        error: (err) => {
          this.erro = this.mensagemErro(err, 'Não foi possível listar as APIs cadastradas no SGU.');
        },
      });
  }

  aplicarPesquisaApis(): void {
    const termo = this.pesquisaApis.trim().toLowerCase();

    this.apisDisponiveisFiltradas = !termo
      ? [...this.apisDisponiveis]
      : this.apisDisponiveis.filter((api) =>
          `${api.nome} ${api.ordenacao ?? ''}`.toLowerCase().includes(termo),
        );
  }

  apiJaAdicionada(nome: string): boolean {
    return this.relatorios.some((relatorio) => relatorio.apiNome === nome);
  }

  get quantidadeApisSelecionadas(): number {
    return this.apisDisponiveis.filter(
      (api) => this.apisSelecionadas[api.nome] && !this.apiJaAdicionada(api.nome),
    ).length;
  }

  adicionarApisSelecionadas(): void {
    const selecionadas = this.apisDisponiveis.filter(
      (api) => this.apisSelecionadas[api.nome] && !this.apiJaAdicionada(api.nome),
    );

    if (!selecionadas.length) {
      this.erro = 'Selecione pelo menos uma API que ainda não foi adicionada.';
      return;
    }

    const agora = new Date().toISOString();
    const novosRelatorios: RelatorioCatalogo[] = selecionadas.map((api) => ({
      id: this.gerarId(),
      nomeExibicao: this.tituloAPartirDoNome(api.nome),
      descricao: '',
      apiNome: api.nome,
      filtros: Array.isArray(api.filtros) ? api.filtros : [],
      criadoEm: agora,
    }));

    this.relatorios = [...this.relatorios, ...novosRelatorios];
    this.persistir();
    this.aplicarPesquisa();
    this.selecionar(novosRelatorios[0]);

    this.modalNovoAberto = false;
    this.apisSelecionadas = {};
    this.sucesso =
      novosRelatorios.length === 1
        ? `O relatório ${novosRelatorios[0].nomeExibicao} foi adicionado.`
        : `${novosRelatorios.length} relatórios foram adicionados ao catálogo.`;
  }

  adicionarFiltro(): void {
    this.novosFiltros.push(filtroVazio());
  }

  removerFiltro(indice: number): void {
    if (this.novosFiltros.length > 1) {
      this.novosFiltros.splice(indice, 1);
    }
  }

  erroVariavelFiltro(filtro: SguFiltro): string {
    const nome = filtro.nomeFiltro.trim();
    const conteudo = filtro.conteudoFiltro.trim();

    if (!nome || !conteudo || conteudo === 'and') return '';

    return validarCorrespondenciaBind(filtro);
  }

  criarNovaApi(): void {
    const definicaoNegocio: SguApiDefinicao = {
      nome: this.novoApiNome.trim(),
      consultaSQL: this.novaConsultaSql.trim(),
      ordenacao: this.novaOrdenacao.trim(),
      filtros: normalizarFiltros(this.novosFiltros),
    };

    const validacao = this.validarNovaApi(definicaoNegocio);

    if (validacao) {
      this.erro = validacao;
      return;
    }

    const definicaoSgu = prepararDefinicaoParaSgu(definicaoNegocio);

    this.salvandoApi = true;
    this.erro = '';

    this.relatorioService
      .criarApi(definicaoSgu)
      .pipe(
        timeout(this.timeoutGeracaoMs),
        finalize(() => {
          this.salvandoApi = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: () => {
          this.adicionarAoCatalogo(definicaoSgu);
          this.modalNovoAberto = false;
          this.listaApisCarregada = false;
          this.sucesso = `A API ${definicaoNegocio.nome} foi cadastrada e adicionada aos relatórios.`;
        },
        error: (err) => {
          this.erro = this.mensagemErro(err, 'Não foi possível cadastrar a API no SGU.');
        },
      });
  }

  async selecionarArquivosSql(evento: Event): Promise<void> {
    const input = evento.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    await this.processarArquivosSql(arquivos);
  }

  // ── Importação em lote de consultas SQL ────────────────────────────────────

  aoArrastarArquivosSql(evento: DragEvent): void {
    evento.preventDefault();
    evento.stopPropagation();

    if (!this.criandoApisEmLote) {
      this.arrastandoArquivosSql = true;
    }
  }

  aoSairDaAreaArquivosSql(evento: DragEvent): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.arrastandoArquivosSql = false;
  }

  async soltarArquivosSql(evento: DragEvent): Promise<void> {
    evento.preventDefault();
    evento.stopPropagation();
    this.arrastandoArquivosSql = false;

    if (this.criandoApisEmLote) return;

    const arquivos = Array.from(evento.dataTransfer?.files ?? []);
    await this.processarArquivosSql(arquivos);
  }

  removerArquivoSql(id: string): void {
    if (this.criandoApisEmLote) return;

    this.arquivosSqlImportados = this.arquivosSqlImportados.filter((arquivo) => arquivo.id !== id);
    this.erro = '';
  }

  alternarDetalhesArquivoSql(arquivo: ArquivoSqlImportado): void {
    arquivo.detalhesAbertos = !arquivo.detalhesAbertos;
  }

  adicionarFiltroArquivoSql(arquivo: ArquivoSqlImportado): void {
    arquivo.filtros.push(filtroVazio());
    this.ajustarArquivoSql(arquivo);
  }

  removerFiltroArquivoSql(arquivo: ArquivoSqlImportado, indice: number): void {
    const filtroRemovido = arquivo.filtros[indice];
    const deteccaoFixa = arquivo.filtrosFixosDetectados.find(
      (item) => item.filtro === filtroRemovido,
    );

    if (deteccaoFixa) {
      const trechoAutomatico = `1 = 1 ${deteccaoFixa.marcador}`;

      arquivo.consultaSQL = arquivo.consultaSQL.includes(trechoAutomatico)
        ? arquivo.consultaSQL.replace(trechoAutomatico, deteccaoFixa.predicadoOriginal)
        : arquivo.consultaSQL.replace(deteccaoFixa.marcador, deteccaoFixa.predicadoOriginal);

      arquivo.filtrosFixosIgnorados = Array.from(
        new Set([...arquivo.filtrosFixosIgnorados, deteccaoFixa.assinatura]),
      );
      arquivo.filtrosFixosDetectados = arquivo.filtrosFixosDetectados.filter(
        (item) => item.id !== deteccaoFixa.id,
      );
      arquivo.ajustesAplicados = [
        ...arquivo.ajustesAplicados,
        `O filtro ${filtroRemovido.nomeFiltro} foi removido; a condição original permaneceu fixa no SQL.`,
      ];
    }

    arquivo.filtros.splice(indice, 1);
    this.ajustarArquivoSql(arquivo);
  }

  descricaoFiltroAutomatico(arquivo: ArquivoSqlImportado, filtro: SguFiltro): string {
    const deteccao = arquivo.filtrosFixosDetectados.find((item) => item.filtro === filtro);

    return deteccao
      ? `Detectado de: ${deteccao.predicadoOriginal}. Se remover, esse valor continuará fixo no SQL.`
      : '';
  }

  ajustarArquivoSql(arquivo: ArquivoSqlImportado): void {
    if (this.criandoApisEmLote) return;

    const parametrosConvertidos = converterParametrosFixosSql(arquivo.consultaSQL);
    const filtrosCteConvertidos = converterFiltrosFixosCteSql(parametrosConvertidos.sql);
    const bindsNormalizados = normalizarVariaveisBindSql(filtrosCteConvertidos.sql);

    const nomesExistentes = new Set(
      arquivo.filtros.map((filtro) => filtro.nomeFiltro.trim().toLowerCase()),
    );

    for (const nome of bindsNormalizados.variaveis) {
      if (!nomesExistentes.has(nome)) {
        arquivo.filtros.push(filtroDetectadoDoSql(nome));
        nomesExistentes.add(nome);
      }
    }

    const filtrosSimples = detectarFiltrosFixosSimples(
      bindsNormalizados.sql,
      nomesExistentes,
      new Set(arquivo.filtrosFixosIgnorados),
    );

    for (const deteccao of filtrosSimples.deteccoes) {
      arquivo.filtros.push(deteccao.filtro);
      arquivo.filtrosFixosDetectados.push(deteccao);
      nomesExistentes.add(deteccao.filtro.nomeFiltro.trim().toLowerCase());
    }

    /*
     * O SGU exige que o elemento filtros seja enviado mesmo quando a
     * consulta não possui filtros de negócio. Por isso sempre deixamos o
     * marcador preparado; no payload será incluído um filtro técnico
     * opcional e invisível para o usuário quando necessário.
     */
    const resultado = ajustarEstruturaSqlImportado(filtrosSimples.sql, true);

    arquivo.consultaSQL = resultado.sql;
    arquivo.ajustesAplicados = Array.from(
      new Set([
        ...arquivo.ajustesAplicados,
        ...parametrosConvertidos.ajustes,
        ...filtrosCteConvertidos.ajustes,
        ...filtrosSimples.ajustes,
        ...resultado.ajustes,
      ]),
    );
    arquivo.status = 'pendente';
    arquivo.erro = '';
  }

  erroArquivoSql(arquivo: ArquivoSqlImportado): string {
    const nomeApi = arquivo.apiNome.trim();

    if (nomeApi && !/^0090-[a-z0-9-]+$/.test(nomeApi)) {
      return 'O nome da API deve começar com 0090- e usar apenas letras minúsculas, números e hífen.';
    }

    const repetidoNoLote =
      this.arquivosSqlImportados.filter(
        (item) => item.apiNome.trim().toLowerCase() === arquivo.apiNome.trim().toLowerCase(),
      ).length > 1;

    if (repetidoNoLote && arquivo.apiNome.trim()) {
      return `O nome da API “${arquivo.apiNome.trim()}” está repetido no lote.`;
    }

    if (
      this.relatorios.some(
        (relatorio) => relatorio.apiNome.toLowerCase() === arquivo.apiNome.trim().toLowerCase(),
      )
    ) {
      return `A API “${arquivo.apiNome.trim()}” já está no catálogo. Use a opção Editar API.`;
    }

    return validarDefinicaoApi(
      {
        nome: arquivo.apiNome.trim(),
        consultaSQL: arquivo.consultaSQL,
        ordenacao: arquivo.ordenacao.trim(),
        filtros: normalizarFiltros(arquivo.filtros),
      },
      arquivo.nomeExibicao,
    );
  }

  get quantidadeArquivosProntos(): number {
    return this.arquivosSqlImportados.filter((arquivo) => !this.erroArquivoSql(arquivo)).length;
  }

  async criarApisDosArquivos(): Promise<void> {
    if (!this.arquivosSqlImportados.length || this.criandoApisEmLote) {
      return;
    }

    this.erro = '';
    this.sucesso = '';

    let possuiErroLocal = false;

    for (const arquivo of this.arquivosSqlImportados) {
      this.ajustarArquivoSql(arquivo);
      arquivo.status = 'pendente';
      arquivo.erro = this.erroArquivoSql(arquivo);

      if (arquivo.erro) {
        arquivo.status = 'erro';
        arquivo.detalhesAbertos = true;
        possuiErroLocal = true;
      }
    }

    if (possuiErroLocal) {
      this.erro = 'Corrija os arquivos destacados antes de iniciar o cadastro em lote.';
      return;
    }

    this.criandoApisEmLote = true;
    this.progressoCriacaoLote = 0;
    this.totalCriacaoLote = this.arquivosSqlImportados.length;

    const criadas: Array<{
      arquivo: ArquivoSqlImportado;
      definicao: SguApiDefinicao;
    }> = [];

    try {
      const apisExistentes = await firstValueFrom(
        this.relatorioService.listarApis().pipe(timeout(this.timeoutGeracaoMs)),
      );

      const nomesExistentes = new Set(
        (apisExistentes ?? []).map((api) => api.nome.trim().toLowerCase()),
      );

      for (const arquivo of this.arquivosSqlImportados) {
        if (nomesExistentes.has(arquivo.apiNome.trim().toLowerCase())) {
          arquivo.status = 'erro';
          arquivo.erro = `A API “${arquivo.apiNome.trim()}” já existe no SGU. Use a funcionalidade Editar API para substituí-la.`;
          arquivo.detalhesAbertos = true;
        }
      }

      const pendentes = this.arquivosSqlImportados.filter((arquivo) => arquivo.status !== 'erro');

      this.totalCriacaoLote = pendentes.length;

      for (const arquivo of pendentes) {
        arquivo.status = 'criando';
        arquivo.erro = '';
        this.cdr.detectChanges();

        const definicao = this.definicaoDoArquivoSql(arquivo);

        try {
          await firstValueFrom(
            this.relatorioService.criarApi(definicao).pipe(timeout(this.timeoutGeracaoMs)),
          );

          arquivo.status = 'sucesso';
          criadas.push({ arquivo, definicao });
          nomesExistentes.add(definicao.nome.toLowerCase());
        } catch (erroCriacao) {
          arquivo.status = 'erro';
          arquivo.erro = this.mensagemErro(
            erroCriacao,
            `Não foi possível cadastrar a API ${definicao.nome}.`,
          );
          arquivo.detalhesAbertos = true;
        } finally {
          this.progressoCriacaoLote += 1;
          this.cdr.detectChanges();
        }
      }
    } catch (erroLista) {
      this.erro = this.mensagemErro(
        erroLista,
        'Não foi possível verificar as APIs já cadastradas no SGU.',
      );
      return;
    } finally {
      this.criandoApisEmLote = false;
      this.cdr.detectChanges();
    }

    if (criadas.length) {
      const agora = new Date().toISOString();
      const novosRelatorios: RelatorioCatalogo[] = criadas.map(({ arquivo, definicao }) => ({
        id: this.gerarId(),
        nomeExibicao: arquivo.nomeExibicao.trim(),
        descricao: arquivo.descricao.trim(),
        apiNome: definicao.nome,
        filtros: filtrosDeNegocio(definicao.filtros),
        criadoEm: agora,
      }));

      this.relatorios = [...this.relatorios, ...novosRelatorios];
      this.persistir();
      this.aplicarPesquisa();
      this.listaApisCarregada = false;
      this.selecionar(novosRelatorios[0]);
    }

    const falhas = this.arquivosSqlImportados.filter((arquivo) => arquivo.status === 'erro');

    if (!falhas.length) {
      const quantidade = criadas.length;
      this.modalNovoAberto = false;
      this.arquivosSqlImportados = [];
      this.sucesso =
        quantidade === 1
          ? 'A API do arquivo SQL foi cadastrada e adicionada aos relatórios.'
          : `${quantidade} APIs foram cadastradas e adicionadas aos relatórios.`;
      this.cdr.detectChanges();
      return;
    }

    this.arquivosSqlImportados = falhas;
    this.erro =
      `${falhas.length} arquivo(s) não foram cadastrado(s). ` +
      'Corrija os erros exibidos e tente novamente.';

    if (criadas.length) {
      this.sucesso = `${criadas.length} API(s) foram cadastradas com sucesso.`;
    }
    this.cdr.detectChanges();
  }

  abrirEdicao(relatorio: RelatorioCatalogo, evento?: Event): void {
    evento?.stopPropagation();

    if (this.carregandoEdicao || this.salvandoEdicao) return;

    this.relatorioEmEdicao = relatorio;
    this.apiOriginalEdicao = null;
    this.editarApiNome = relatorio.apiNome;
    this.editarNomeExibicao = relatorio.nomeExibicao;
    this.editarDescricao = relatorio.descricao;
    this.editarConsultaSql = '';
    this.editarOrdenacao = '';
    this.filtrosEdicao = relatorio.filtros.map((filtro) => ({ ...filtro }));

    this.modalEditarAberto = true;
    this.carregandoEdicao = true;
    this.erro = '';
    this.sucesso = '';

    this.relatorioService
      .buscarApi(relatorio.apiNome)
      .pipe(
        timeout(this.timeoutGeracaoMs),
        finalize(() => {
          this.carregandoEdicao = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (api) => {
          this.apiOriginalEdicao = clonarDefinicaoApi(api);
          this.editarApiNome = api.nome;
          this.editarConsultaSql = api.consultaSQL ?? '';
          this.editarOrdenacao = api.ordenacao ?? '';
          this.filtrosEdicao = filtrosDeNegocio(api.filtros);
        },
        error: (err) => {
          this.erro = this.mensagemErro(
            err,
            `Não foi possível carregar a definição da API ${relatorio.apiNome}.`,
          );
        },
      });
  }

  // ── Edição de uma API já cadastrada ────────────────────────────────────────

  fecharEdicao(): void {
    if (this.carregandoEdicao || this.salvandoEdicao) return;

    this.modalEditarAberto = false;
    this.relatorioEmEdicao = null;
    this.apiOriginalEdicao = null;
  }

  adicionarFiltroEdicao(): void {
    this.filtrosEdicao.push(filtroVazio());
  }

  removerFiltroEdicao(indice: number): void {
    this.filtrosEdicao.splice(indice, 1);
  }

  salvarEdicaoApi(): void {
    if (!this.relatorioEmEdicao || !this.apiOriginalEdicao || this.salvandoEdicao) {
      return;
    }

    const novaDefinicaoNegocio: SguApiDefinicao = {
      nome: this.editarApiNome.trim(),
      consultaSQL: this.editarConsultaSql.trim(),
      ordenacao: this.editarOrdenacao.trim(),
      filtros: normalizarFiltros(this.filtrosEdicao),
    };

    const validacao = validarDefinicaoApi(novaDefinicaoNegocio, this.editarNomeExibicao);

    if (validacao) {
      this.erro = validacao;
      return;
    }

    const novaDefinicaoSgu = prepararDefinicaoParaSgu(novaDefinicaoNegocio);
    const nomeAnterior = this.relatorioEmEdicao.apiNome;
    const idRelatorio = this.relatorioEmEdicao.id;
    const definicaoAnterior = clonarDefinicaoApi(this.apiOriginalEdicao);

    this.salvandoEdicao = true;
    this.erro = '';
    this.sucesso = '';

    this.relatorioService
      .substituirApi(nomeAnterior, definicaoAnterior, novaDefinicaoSgu)
      .pipe(
        timeout(this.timeoutGeracaoMs),
        finalize(() => {
          this.salvandoEdicao = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: () => {
          const atualizado: RelatorioCatalogo = {
            ...this.relatorioEmEdicao!,
            nomeExibicao: this.editarNomeExibicao.trim(),
            descricao: this.editarDescricao.trim(),
            apiNome: novaDefinicaoSgu.nome,
            filtros: filtrosDeNegocio(novaDefinicaoSgu.filtros),
          };

          this.atualizarRelatorioEditado(atualizado);
          this.listaApisCarregada = false;
          this.modalEditarAberto = false;
          this.relatorioEmEdicao = null;
          this.apiOriginalEdicao = null;

          this.sucesso =
            nomeAnterior === novaDefinicaoNegocio.nome
              ? `A API ${novaDefinicaoNegocio.nome} foi substituída com sucesso.`
              : `A API ${nomeAnterior} foi substituída por ${novaDefinicaoNegocio.nome}.`;

          const selecionadoAtualizado = this.relatorios.find(
            (relatorio) => relatorio.id === idRelatorio,
          );

          if (selecionadoAtualizado) {
            this.selecionar(selecionadoAtualizado, Boolean(this.templateAtivo));
          }
        },
        error: (err) => {
          this.erro = this.mensagemErro(err, 'Não foi possível substituir a API.');
        },
      });
  }

  gerar(pagina = 1): void {
    if (!this.selecionado || this.carregando) return;

    const erroFiltros = this.validarFiltrosExecucao();

    if (erroFiltros) {
      this.erro = erroFiltros;
      return;
    }

    this.salvarFiltrosSelecionadoAtual();

    const paginaSolicitada = Math.max(1, pagina);
    const parametros = this.montarParametros(true);
    parametros['page'] = paginaSolicitada;
    parametros['size'] = this.tamanhoPagina;

    this.iniciarGeracao();
    this.cdr.detectChanges();

    // Aguarda um ciclo do navegador para que a barra de carregamento apareça
    // imediatamente, antes da chamada HTTP.
    setTimeout(() => {
      if (!this.carregando || !this.selecionado) return;

      const apiNome = this.selecionado.apiNome;

      this.relatorioService
        .executar(apiNome, parametros)
        .pipe(
          timeout(this.timeoutGeracaoMs),
          finalize(() => this.finalizarGeracao()),
        )
        .subscribe({
          next: (resposta) => {
            try {
              const duracao = performance.now() - this.inicioGeracao;
              this.aplicarResultado(resposta, paginaSolicitada, duracao);
            } catch (erroProcessamento) {
              console.error('Erro ao montar a tabela do relatório:', erroProcessamento);
              this.registros = [];
              this.colunas = [];
              this.erro = 'A API respondeu, mas ocorreu um erro ao montar a tabela do relatório.';
            }
          },
          error: (err) => {
            console.error('Erro ao gerar relatório:', err);
            this.erro = this.mensagemErro(err, 'Não foi possível gerar o relatório.');
          },
        });
    }, 0);
  }

  // ── Execução, paginação e exportação manual ────────────────────────────────

  paginaAnterior(): void {
    if (this.pagina > 1 && !this.carregando) {
      this.gerar(this.pagina - 1);
    }
  }

  proximaPagina(): void {
    if (!this.ultimaPagina && !this.carregando) {
      this.gerar(this.pagina + 1);
    }
  }

  selecionarFormato(formato: FormatoExportacao): void {
    if (!this.exportando) {
      this.formatoSelecionado = formato;
    }
  }

  baixar(): void {
    if (!this.selecionado || this.exportando) return;

    const erroFiltros = this.validarFiltrosExecucao();

    if (erroFiltros) {
      this.erro = erroFiltros;
      return;
    }

    this.salvarFiltrosSelecionadoAtual();

    const formato = this.formatoSelecionado;
    const nomeArquivo = this.nomeArquivoEscolhido();

    this.exportando = formato;
    this.iniciarExportacao();
    this.erro = '';
    this.sucesso = '';

    this.relatorioService
      .exportar(this.selecionado.apiNome, formato, this.montarParametros(false), nomeArquivo)
      .pipe(finalize(() => this.finalizarExportacao()))
      .subscribe({
        next: (evento) => {
          if (evento.type === HttpEventType.DownloadProgress) {
            this.atualizarProgressoDownload(evento.loaded, evento.total);
            return;
          }

          if (evento.type !== HttpEventType.Response) return;

          const blob = evento.body;
          if (!blob) {
            this.erro = 'O backend não devolveu o arquivo solicitado.';
            return;
          }

          this.progressoExportacao = 100;
          this.mensagemExportacao = 'Arquivo concluído. Iniciando o download…';
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');

          link.href = url;
          link.download = `${nomeArquivo}.${formato}`;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
          setTimeout(() => URL.revokeObjectURL(url), 0);

          this.sucesso = `Download preparado: ${nomeArquivo}.${formato}`;
        },
        error: async (err) => {
          this.erro = await this.mensagemErroBlob(err, 'Não foi possível exportar o relatório.');
          this.cdr.detectChanges();
        },
      });
  }

  abrirNovoTemplate(): void {
    if (!this.relatorios.length) {
      this.erro = 'Adicione pelo menos um relatório antes de criar um template.';
      return;
    }

    this.novoTemplateNome = '';
    this.novoTemplateDescricao = '';
    this.relatoriosTemplateSelecionados = {};
    this.modalTemplateAberto = true;
    this.erro = '';
  }

  // ── Templates locais de filtros ────────────────────────────────────────────

  fecharNovoTemplate(): void {
    this.modalTemplateAberto = false;
  }

  get quantidadeRelatoriosTemplateSelecionados(): number {
    return this.relatorios.filter((relatorio) => this.relatoriosTemplateSelecionados[relatorio.id])
      .length;
  }

  salvarTemplate(): void {
    const nome = this.novoTemplateNome.trim();
    const descricao = this.novoTemplateDescricao.trim();
    const relatorioIds = this.relatorios
      .filter((relatorio) => Boolean(this.relatoriosTemplateSelecionados[relatorio.id]))
      .map((relatorio) => relatorio.id);

    if (!nome) {
      this.erro = 'Informe um nome para o template.';
      return;
    }

    if (!relatorioIds.length) {
      this.erro = 'Selecione pelo menos um relatório para o template.';
      return;
    }

    const template: RelatorioTemplate = {
      id: this.gerarId(),
      nome,
      descricao,
      relatorioIds,
      criadoEm: new Date().toISOString(),
    };

    this.templates = [...this.templates, template];
    this.persistirTemplates();
    this.modalTemplateAberto = false;
    this.usarTemplate(template);
    this.sucesso = `O template “${template.nome}” foi criado.`;
  }

  usarTemplate(template: RelatorioTemplate, evento?: Event): void {
    evento?.stopPropagation();

    const relatorios = template.relatorioIds
      .map((id) => this.relatorios.find((relatorio) => relatorio.id === id))
      .filter((relatorio): relatorio is RelatorioCatalogo => Boolean(relatorio));

    if (!relatorios.length) {
      this.erro = 'Nenhum dos relatórios deste template está disponível no catálogo.';
      return;
    }

    this.templateAtivo = template;
    this.relatoriosAbertos = relatorios;
    this.selecionar(relatorios[0], true);
  }

  fecharTemplateAtivo(): void {
    this.templateAtivo = null;
    this.relatoriosAbertos = [];
  }

  excluirTemplate(template: RelatorioTemplate, evento?: Event): void {
    evento?.stopPropagation();

    const confirmar =
      typeof window === 'undefined' || window.confirm(`Excluir o template “${template.nome}”?`);

    if (!confirmar) return;

    this.templates = this.templates.filter((item) => item.id !== template.id);
    this.persistirTemplates();

    if (this.templateAtivo?.id === template.id) {
      this.fecharTemplateAtivo();
    }

    this.sucesso = `O template “${template.nome}” foi excluído.`;
  }

  resumoTemplate(template: RelatorioTemplate): string {
    const nomes = template.relatorioIds
      .map((id) => this.relatorios.find((relatorio) => relatorio.id === id))
      .filter((relatorio): relatorio is RelatorioCatalogo => Boolean(relatorio))
      .map((relatorio) => relatorio.nomeExibicao);

    if (!nomes.length) return 'Nenhum relatório disponível';
    if (nomes.length <= 2) return nomes.join(' · ');

    return `${nomes.slice(0, 2).join(' · ')} +${nomes.length - 2}`;
  }

  abrirExclusao(relatorio: RelatorioCatalogo, evento?: Event): void {
    evento?.stopPropagation();
    this.relatorioParaExcluir = relatorio;
    this.apagarTambemNoSgu = false;
    this.modalExcluirAberto = true;
    this.erro = '';
  }

  fecharExclusao(): void {
    if (!this.excluindo) {
      this.modalExcluirAberto = false;
    }
  }

  confirmarExclusao(): void {
    if (!this.relatorioParaExcluir) return;

    if (!this.apagarTambemNoSgu) {
      this.removerDoCatalogo(this.relatorioParaExcluir);
      this.modalExcluirAberto = false;
      return;
    }

    this.excluindo = true;

    this.relatorioService
      .apagarApi(this.relatorioParaExcluir.apiNome)
      .pipe(
        timeout(this.timeoutGeracaoMs),
        finalize(() => {
          this.excluindo = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: () => {
          this.removerDoCatalogo(this.relatorioParaExcluir!);
          this.modalExcluirAberto = false;
          this.listaApisCarregada = false;
        },
        error: (err) => {
          this.erro = this.mensagemErro(err, 'Não foi possível apagar a API no SGU.');
        },
      });
  }

  tipoInput(filtro: SguFiltro): string {
    return filtro.tipoDadoFiltro.toUpperCase() === 'NUMBER' ? 'number' : 'text';
  }

  // ── Adaptadores usados exclusivamente pelo template ────────────────────────

  placeholderFiltro(filtro: SguFiltro): string {
    if (filtro.tipoDadoFiltro.toUpperCase() === 'DATE') {
      return filtro.mascaraFiltro || 'DD/MM/YYYY';
    }

    return filtro.nomeFiltro;
  }

  rotuloFiltro(filtro: SguFiltro): string {
    return filtro.nomeFiltro
      .replace(/[_-]+/g, ' ')
      .replace(/\b\w/g, (letra) => letra.toUpperCase());
  }

  formatarValorPrevia(coluna: string, valor: unknown): string {
    return formatReportPreviewValue(coluna, valor);
  }

  colunaProtegidaPrevia(coluna: string): boolean {
    return isProtectedBeneficiaryColumn(coluna);
  }

  formatarDuracao(ms: number | null): string {
    if (ms === null || !Number.isFinite(ms)) return '';

    if (ms < 1000) return `${Math.round(ms)} ms`;
    return `${(ms / 1000).toFixed(1).replace('.', ',')} s`;
  }

  trackByRelatorioId(_indice: number, relatorio: RelatorioCatalogo): string {
    return relatorio.id;
  }

  trackByArquivoSqlId(_indice: number, arquivo: ArquivoSqlImportado): string {
    return arquivo.id;
  }

  trackByTemplateId(_indice: number, template: RelatorioTemplate): string {
    return template.id;
  }

  trackByApiNome(_indice: number, api: SguApiDefinicao): string {
    return api.nome;
  }

  trackByFiltroNome(_indice: number, filtro: SguFiltro): string {
    return filtro.nomeFiltro;
  }

  trackByColuna(_indice: number, coluna: string): string {
    return coluna;
  }

  trackByRegistro(indice: number): number {
    return indice;
  }

  private aplicarResultado(resposta: SguResultado, pagina: number, duracaoMs: number): void {
    const respostaGenerica = resposta as any;

    const conteudo = Array.isArray(respostaGenerica)
      ? respostaGenerica
      : Array.isArray(respostaGenerica?.content)
        ? respostaGenerica.content
        : Array.isArray(respostaGenerica?.data?.content)
          ? respostaGenerica.data.content
          : [];

    this.registros = conteudo
      .filter((item: unknown) => item !== null && typeof item === 'object' && !Array.isArray(item))
      .map((item: Record<string, unknown>) =>
        Object.fromEntries(
          Object.entries(item).filter(([coluna]) => coluna.trim().toUpperCase() !== 'RNUM'),
        ),
      );

    const colunas = new Set<string>();
    this.registros.forEach((registro) => {
      Object.keys(registro).forEach((coluna) => colunas.add(coluna));
    });

    this.colunas = Array.from(colunas);
    this.pagina = pagina;
    this.ultimaPagina =
      respostaGenerica?.last === true || this.registros.length < this.tamanhoPagina;

    const totalBruto =
      respostaGenerica?.totalElements ?? respostaGenerica?.numberOfElements ?? null;
    const total = Number(totalBruto);

    this.totalRegistros = totalBruto !== null && Number.isFinite(total) ? total : null;
    this.duracaoUltimaConsultaMs = duracaoMs;

    this.sucesso = this.registros.length
      ? `${this.registros.length} registro(s) carregado(s) em ${this.formatarDuracao(duracaoMs)}.`
      : `Nenhum registro encontrado. Consulta concluída em ${this.formatarDuracao(duracaoMs)}.`;
  }

  // ── Contratos de execução e normalização dos resultados SGU ────────────────

  private validarFiltrosExecucao(): string {
    if (!this.selecionado) return 'Selecione um relatório.';

    const obrigatorioVazio = this.selecionado.filtros.find(
      (filtro) =>
        filtro.obrigatorioFiltro === 'S' &&
        String(this.valoresFiltro[filtro.nomeFiltro] ?? '').trim() === '',
    );

    if (obrigatorioVazio) {
      return `Preencha o filtro obrigatório “${this.rotuloFiltro(obrigatorioVazio)}”.`;
    }

    const numeroInvalido = this.selecionado.filtros.find((filtro) => {
      const valor = String(this.valoresFiltro[filtro.nomeFiltro] ?? '').trim();

      return (
        valor !== '' &&
        filtro.tipoDadoFiltro.toUpperCase() === 'NUMBER' &&
        !Number.isFinite(Number(valor))
      );
    });

    if (numeroInvalido) {
      return `O filtro “${this.rotuloFiltro(numeroInvalido)}” deve conter um número válido.`;
    }

    return '';
  }

  private montarParametros(omitirVazios: boolean): Record<string, unknown> {
    const parametros: Record<string, unknown> = {};

    if (!this.selecionado) return parametros;

    this.selecionado.filtros.forEach((filtro) => {
      const valor = this.valoresFiltro[filtro.nomeFiltro];
      const texto = String(valor ?? '').trim();

      if (omitirVazios && texto === '') return;
      if (texto === '') return;

      parametros[filtro.nomeFiltro] =
        filtro.tipoDadoFiltro.toUpperCase() === 'NUMBER' ? Number(texto) : texto;
    });

    return parametros;
  }

  /**
   * Lê cada arquivo selecionado e cria uma definição independente. As leituras
   * podem ocorrer em paralelo, mas `Promise.all` mantém a ordem escolhida pelo
   * usuário na lista apresentada pela interface.
   */
  private async processarArquivosSql(arquivos: File[]): Promise<void> {
    if (!arquivos.length || this.criandoApisEmLote) return;

    const extensoesAceitas = new Set(['sql', 'txt']);
    const validos = arquivos.filter((arquivo) => {
      const extensao = arquivo.name.split('.').pop()?.toLowerCase() ?? '';
      return extensoesAceitas.has(extensao);
    });

    const ignorados = arquivos.length - validos.length;

    if (!validos.length) {
      this.erro = 'Selecione arquivos com extensão .sql ou .txt.';
      return;
    }

    this.carregandoArquivosSql = true;
    this.erro = '';

    try {
      const importados = await Promise.all(
        validos.map(async (arquivo) => {
          const textoOriginal = await arquivo.text();
          const textoSemBom = textoOriginal.replace(/^\uFEFF/, '').trim();
          const instrucaoPrincipal = extrairPrimeiraInstrucaoSql(textoSemBom);
          const nomeBase = arquivo.name.replace(/\.(sql|txt)$/i, '');

          const importado: ArquivoSqlImportado = {
            id: this.gerarId(),
            arquivoNome: arquivo.name,
            tamanhoBytes: arquivo.size,
            apiNome: this.nomeApiAPartirDoArquivo(nomeBase),
            nomeExibicao: this.tituloAPartirDoNome(nomeBase),
            descricao: `Importado do arquivo ${arquivo.name}`,
            consultaSQL: instrucaoPrincipal.sql,
            ordenacao: '',
            filtros: [],
            filtrosFixosDetectados: [],
            filtrosFixosIgnorados: [],
            ajustesAplicados: instrucaoPrincipal.conteudoPosteriorIgnorado
              ? [
                  'Foi importada somente a instrução SQL executável; anotações, comentários finais ou consultas auxiliares foram ignorados.',
                ]
              : [],
            detalhesAbertos: false,
            status: 'pendente',
            erro: instrucaoPrincipal.sql ? '' : 'O arquivo SQL está vazio.',
          };

          if (instrucaoPrincipal.sql) {
            this.ajustarArquivoSql(importado);
          }

          return importado;
        }),
      );

      this.arquivosSqlImportados = [...this.arquivosSqlImportados, ...importados];

      if (ignorados) {
        this.erro = `${ignorados} arquivo(s) foram ignorados porque não possuem extensão .sql ou .txt.`;
      }
    } catch (erroLeitura) {
      this.erro =
        erroLeitura instanceof Error
          ? `Não foi possível ler os arquivos: ${erroLeitura.message}`
          : 'Não foi possível ler os arquivos selecionados.';
    } finally {
      this.carregandoArquivosSql = false;
      this.cdr.detectChanges();
    }
  }

  private definicaoDoArquivoSql(arquivo: ArquivoSqlImportado): SguApiDefinicao {
    return prepararDefinicaoParaSgu({
      nome: arquivo.apiNome.trim(),
      consultaSQL: arquivo.consultaSQL,
      ordenacao: arquivo.ordenacao.trim(),
      filtros: normalizarFiltros(arquivo.filtros),
    });
  }

  private nomeApiAPartirDoArquivo(nomeArquivo: string): string {
    const base = nomeArquivo
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');

    if (/^\d{4}-/.test(base)) return base;
    return `0090-${base || 'nova-api'}`;
  }

  formatarTamanhoArquivo(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  private atualizarRelatorioEditado(atualizado: RelatorioCatalogo): void {
    this.relatorios = this.relatorios.map((relatorio) =>
      relatorio.id === atualizado.id ? atualizado : relatorio,
    );

    /*
     * Os templates armazenam o ID do relatório, não o nome da API.
     * Como o ID é preservado na edição, todos os templates continuam
     * apontando automaticamente para a nova definição.
     */
    this.relatoriosAbertos = this.relatoriosAbertos.map((relatorio) =>
      relatorio.id === atualizado.id ? atualizado : relatorio,
    );

    delete this.valoresFiltroPorRelatorio[atualizado.id];

    this.persistir();
    this.persistirTemplates();
    this.aplicarPesquisa();

    if (this.selecionado?.id === atualizado.id) {
      this.selecionado = atualizado;
    }
  }

  private adicionarAoCatalogo(api: SguApiDefinicao): void {
    const existente = this.relatorios.find((relatorio) => relatorio.apiNome === api.nome);

    const relatorio: RelatorioCatalogo = {
      id: existente?.id ?? this.gerarId(),
      nomeExibicao: this.novoNomeExibicao.trim() || this.tituloAPartirDoNome(api.nome),
      descricao: this.novaDescricao.trim(),
      apiNome: api.nome,
      filtros: filtrosDeNegocio(api.filtros),
      criadoEm: existente?.criadoEm ?? new Date().toISOString(),
    };

    this.relatorios = existente
      ? this.relatorios.map((item) => (item.id === existente.id ? relatorio : item))
      : [...this.relatorios, relatorio];

    this.persistir();
    this.aplicarPesquisa();
    this.selecionar(relatorio);
  }

  private removerDoCatalogo(relatorio: RelatorioCatalogo): void {
    this.relatorios = this.relatorios.filter((item) => item.id !== relatorio.id);

    delete this.valoresFiltroPorRelatorio[relatorio.id];

    this.templates = this.templates
      .map((template) => ({
        ...template,
        relatorioIds: template.relatorioIds.filter((id) => id !== relatorio.id),
      }))
      .filter((template) => template.relatorioIds.length > 0);

    this.persistir();
    this.persistirTemplates();
    this.aplicarPesquisa();

    if (this.templateAtivo) {
      const templateAtualizado = this.templates.find(
        (template) => template.id === this.templateAtivo?.id,
      );

      if (templateAtualizado) {
        this.templateAtivo = templateAtualizado;
        this.relatoriosAbertos = templateAtualizado.relatorioIds
          .map((id) => this.relatorios.find((item) => item.id === id))
          .filter((item): item is RelatorioCatalogo => Boolean(item));
      } else {
        this.fecharTemplateAtivo();
      }
    }

    if (this.selecionado?.id === relatorio.id) {
      this.selecionado = null;
      this.limparResultado();

      const proximo = this.relatoriosAbertos[0] ?? this.relatorios[0];
      if (proximo) {
        this.selecionar(proximo, Boolean(this.templateAtivo));
      }
    }
  }

  private persistir(): void {
    this.relatorioService.salvarCatalogo(this.relatorios);
  }

  // ── Validação dos contratos enviados ao SGU ────────────────────────────────

  private persistirTemplates(): void {
    this.relatorioService.salvarTemplates(this.templates);
  }

  private validarNovaApi(api: SguApiDefinicao): string {
    return validarDefinicaoApi(api, this.novoNomeExibicao);
  }

  private normalizarListaApis(apis: SguApiDefinicao[]): SguApiDefinicao[] {
    const unicas = new Map<string, SguApiDefinicao>();

    for (const api of apis ?? []) {
      const nome = String(api?.nome ?? '').trim();
      if (!nome) continue;

      unicas.set(nome, {
        ...api,
        nome,
        consultaSQL: api.consultaSQL ?? '',
        ordenacao: api.ordenacao ?? '',
        filtros: filtrosDeNegocio(api.filtros),
      });
    }

    return Array.from(unicas.values()).sort((a, b) =>
      a.nome.localeCompare(b.nome, 'pt-BR', {
        numeric: true,
        sensitivity: 'base',
      }),
    );
  }

  private normalizarTemplates(templates: RelatorioTemplate[]): RelatorioTemplate[] {
    const idsRelatorios = new Set(this.relatorios.map((item) => item.id));

    return (templates ?? [])
      .map((template) => ({
        ...template,
        relatorioIds: Array.from(
          new Set((template.relatorioIds ?? []).filter((id) => idsRelatorios.has(id))),
        ),
      }))
      .filter(
        (template) =>
          Boolean(template.id) &&
          Boolean(template.nome?.trim()) &&
          template.relatorioIds.length > 0,
      );
  }

  private criarValoresFiltroVazios(relatorio: RelatorioCatalogo): Record<string, string | number> {
    const valores: Record<string, string | number> = {};

    relatorio.filtros.forEach((filtro) => {
      valores[filtro.nomeFiltro] = '';
    });

    return valores;
  }

  private salvarFiltrosSelecionadoAtual(): void {
    if (!this.selecionado) return;
    this.valoresFiltroPorRelatorio[this.selecionado.id] = {
      ...this.valoresFiltro,
    };
  }

  private iniciarGeracao(): void {
    this.pararCronometroGeracao();
    this.carregando = true;
    this.segundosGeracao = 0;
    this.progressoGeracao = 5;
    this.mensagemGeracao = 'Enviando a consulta para o SGU…';
    this.inicioGeracao = performance.now();
    this.erro = '';
    this.sucesso = '';

    this.intervaloGeracao = setInterval(() => {
      this.segundosGeracao += 1;
      const aproximacao = 1 - Math.exp(-this.segundosGeracao / 30);
      this.progressoGeracao = Math.min(94, Math.round(5 + aproximacao * 89));

      if (this.segundosGeracao >= 20) {
        this.mensagemGeracao = 'A consulta continua em execução. Aguarde a resposta do SGU…';
      } else if (this.segundosGeracao >= 8) {
        this.mensagemGeracao = 'Processando os dados e preparando a tabela…';
      } else if (this.segundosGeracao >= 2) {
        this.mensagemGeracao = 'Consultando os dados no SGU…';
      }

      this.cdr.detectChanges();
    }, 1000);
  }

  // ── Cronômetro, nomes de arquivo e mensagens de erro ───────────────────────

  private finalizarGeracao(): void {
    this.pararCronometroGeracao();
    this.carregando = false;
    this.progressoGeracao = 0;
    this.mensagemGeracao = '';
    this.cdr.detectChanges();
  }

  private pararCronometroGeracao(): void {
    if (this.intervaloGeracao) {
      clearInterval(this.intervaloGeracao);
      this.intervaloGeracao = undefined;
    }
  }

  private iniciarExportacao(): void {
    this.pararCronometroExportacao();
    this.segundosExportacao = 0;
    this.progressoExportacao = 5;
    this.mensagemExportacao = 'Consultando todas as páginas do relatório…';

    this.intervaloExportacao = setInterval(() => {
      this.segundosExportacao += 1;
      const aproximacao = 1 - Math.exp(-this.segundosExportacao / 90);
      this.progressoExportacao = Math.min(94, Math.round(5 + aproximacao * 89));
      this.mensagemExportacao =
        this.progressoExportacao < 45
          ? 'Consultando todas as páginas do relatório…'
          : this.progressoExportacao < 80
            ? 'Processando os registros do arquivo…'
            : 'Finalizando o arquivo para download…';
      this.cdr.detectChanges();
    }, 1000);
  }

  private atualizarProgressoDownload(carregados: number, total?: number): void {
    this.mensagemExportacao = 'Transferindo o arquivo pronto para o navegador…';
    if (total && total > 0) {
      const percentualRecebido = Math.round((carregados / total) * 100);
      this.progressoExportacao = Math.max(
        this.progressoExportacao,
        Math.min(99, percentualRecebido),
      );
    }
    this.cdr.detectChanges();
  }

  private finalizarExportacao(): void {
    this.pararCronometroExportacao();
    this.exportando = null;
    this.progressoExportacao = 0;
    this.mensagemExportacao = '';
    this.cdr.detectChanges();
  }

  private pararCronometroExportacao(): void {
    if (this.intervaloExportacao) {
      clearInterval(this.intervaloExportacao);
      this.intervaloExportacao = undefined;
    }
  }

  private tituloAPartirDoNome(nome: string): string {
    return nome
      .replace(/^\d+-/, '')
      .replace(/[-_]+/g, ' ')
      .replace(/\b\w/g, (letra) => letra.toUpperCase());
  }

  private nomeArquivo(nome: string): string {
    const base = nome
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .toLowerCase();
    const data = new Date().toISOString().slice(0, 10);

    return `${base || 'relatorio'}_${data}`;
  }

  private nomeArquivoEscolhido(): string {
    const padrao = this.nomeArquivo(this.selecionado?.nomeExibicao ?? 'relatorio');

    const semExtensao = this.nomeArquivoDownload.trim().replace(/\.(csv|txt|xlsx)$/i, '');

    const limpo = semExtensao
      .replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_')
      .replace(/[. ]+$/g, '')
      .trim();

    const resultado = limpo || padrao;
    this.nomeArquivoDownload = resultado;

    return resultado;
  }

  private gerarId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  private mensagemErro(erro: any, fallback: string): string {
    if (erro?.name === 'TimeoutError') {
      return 'A operação ultrapassou o tempo limite e foi encerrada. Verifique os filtros e tente novamente.';
    }

    const httpErro = erro as HttpErrorResponse;
    const status = Number(httpErro?.status ?? httpErro?.error?.status ?? 0);
    const detalhe = this.extrairDetalheErro(erro) || fallback;

    if (status === 0) {
      return `Não foi possível conectar ao backend. ${detalhe}`;
    }

    if (status > 0) {
      return `Erro ${status}: ${detalhe}`;
    }

    return detalhe;
  }

  private extrairDetalheErro(erro: any): string {
    const corpo = erro?.error;

    if (typeof corpo === 'string') {
      try {
        const json = JSON.parse(corpo);
        return json?.message ?? json?.error ?? corpo;
      } catch {
        return corpo;
      }
    }

    return corpo?.message ?? corpo?.error ?? erro?.message ?? '';
  }

  private async mensagemErroBlob(erro: any, fallback: string): Promise<string> {
    try {
      if (erro?.error instanceof Blob) {
        const texto = await erro.error.text();

        try {
          const json = JSON.parse(texto);
          const mensagem = json?.message ?? json?.error ?? texto;
          const status = Number(erro?.status ?? json?.status ?? 0);
          return status > 0 ? `Erro ${status}: ${mensagem}` : mensagem || fallback;
        } catch {
          const status = Number(erro?.status ?? 0);
          return status > 0 ? `Erro ${status}: ${texto || fallback}` : texto || fallback;
        }
      }
    } catch {
      return fallback;
    }

    return this.mensagemErro(erro, fallback);
  }
}
