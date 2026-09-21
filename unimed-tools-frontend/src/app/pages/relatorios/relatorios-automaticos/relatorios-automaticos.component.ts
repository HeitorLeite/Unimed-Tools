import { ContextoEmpresa, EmpresaGrupoExecucao, FiltroGrupoExecucao, ValorFiltroGrupo } from './grupo-execucao.model';
import { montarFiltrosGrupo, chaveLogicaFiltro, filtrosNegocio, valoresPreenchidos, nomeCurtoRelatorio, sanitizarNomeArquivo, novoValorFiltro } from './grupo-filtros.utils';
/**
 * Gerencia grupos e combinações usadas na exportação automática de relatórios.
 */
import { CommonModule } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, timeout } from 'rxjs';

import {
  FormatoExportacao,
  RelatorioCatalogo,
  RelatorioGrupoAutomatico,
  RelatorioGrupoItem,
  RelatorioLoteItemRequest,
  RelatorioLoteRequest,
  SguFiltro,
} from '../../../shared/models/relatorio.model';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import { EMPRESAS_RELATORIOS } from './empresa-catalogo';
import { EmpresaSelectComponent } from './empresa-select.component';

interface NotificacaoExecucao {
  tipo: 'sucesso' | 'erro';
  mensagem: string;
}

@Component({
  selector: 'app-relatorios-automaticos',
  standalone: true,
  imports: [CommonModule, FormsModule, EmpresaSelectComponent],
  templateUrl: './relatorios-automaticos.component.html',
  styleUrls: ['./relatorios-automaticos.component.scss'],
})
export class RelatoriosAutomaticosComponent implements OnInit, OnChanges, OnDestroy {
  @Input() embedded = false;
  @Input() relatorios: RelatorioCatalogo[] = [];
  @Output() voltar = new EventEmitter<void>();

  grupos: RelatorioGrupoAutomatico[] = [];
  grupoSelecionado: RelatorioGrupoAutomatico | null = null;

  filtrosExecucao: FiltroGrupoExecucao[] = [];
  empresasExecucao: EmpresaGrupoExecucao[] = [];
  readonly empresasCatalogo = EMPRESAS_RELATORIOS;
  formatoExecucao: FormatoExportacao = 'xlsx';
  nomeArquivoZip = '';

  modalGrupoAberto = false;
  grupoEmEdicaoId: string | null = null;
  grupoNome = '';
  grupoDescricao = '';
  grupoFormato: FormatoExportacao = 'xlsx';
  relatoriosGrupoSelecionados: Record<string, boolean> = {};
  nomesArquivoRelatorio: Record<string, string> = {};

  executando = false;
  segundosExecucao = 0;
  progressoExecucao = 0;
  etapaExecucao = '';
  erro = '';
  sucesso = '';
  notificacaoExecucao: NotificacaoExecucao | null = null;

  private intervaloExecucao?: ReturnType<typeof setInterval>;
  private temporizadorNotificacao?: ReturnType<typeof setTimeout>;
  private readonly timeoutLoteMs = 3_600_000;

  get operacaoRelatorioEmAndamento(): boolean {
    return this.executando;
  }

  constructor(
    private readonly relatorioService: RelatorioService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.grupos = this.relatorioService.listarGruposAutomaticos();
    this.reconciliarGrupos();

    if (this.grupos.length) {
      this.selecionarGrupo(this.grupos[0]);
    }
  }

  // ── Ciclo de vida e edição dos grupos persistidos localmente ───────────────

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['relatorios'] && !changes['relatorios'].firstChange) {
      this.reconciliarGrupos();
    }
  }

  ngOnDestroy(): void {
    this.pararCronometro();
    if (this.temporizadorNotificacao) {
      clearTimeout(this.temporizadorNotificacao);
    }
  }

  abrirNovoGrupo(): void {
    if (!this.relatorios.length) {
      this.erro = 'Adicione relatórios ao catálogo manual antes de criar um grupo automático.';
      return;
    }

    this.grupoEmEdicaoId = null;
    this.grupoNome = '';
    this.grupoDescricao = '';
    this.grupoFormato = 'xlsx';
    this.relatoriosGrupoSelecionados = {};
    this.nomesArquivoRelatorio = {};
    this.modalGrupoAberto = true;
    this.erro = '';
  }

  abrirEdicaoGrupo(grupo: RelatorioGrupoAutomatico, evento?: Event): void {
    evento?.stopPropagation();

    this.grupoEmEdicaoId = grupo.id;
    this.grupoNome = grupo.nome;
    this.grupoDescricao = grupo.descricao;
    this.grupoFormato = grupo.formato;
    this.relatoriosGrupoSelecionados = {};
    this.nomesArquivoRelatorio = {};

    grupo.itens.forEach((item) => {
      this.relatoriosGrupoSelecionados[item.relatorioId] = true;
      this.nomesArquivoRelatorio[item.relatorioId] = item.nomeArquivo;
    });

    this.modalGrupoAberto = true;
    this.erro = '';
  }

  fecharModalGrupo(): void {
    if (!this.executando) {
      this.modalGrupoAberto = false;
    }
  }

  alternarRelatorioGrupo(relatorio: RelatorioCatalogo, selecionado: boolean): void {
    this.relatoriosGrupoSelecionados[relatorio.id] = selecionado;

    if (selecionado) {
      this.nomesArquivoRelatorio[relatorio.id] ||= nomeCurtoRelatorio(relatorio);
    }
  }

  get quantidadeRelatoriosGrupoSelecionados(): number {
    return this.relatorios.filter((relatorio) => this.relatoriosGrupoSelecionados[relatorio.id])
      .length;
  }

  salvarGrupo(): void {
    const nome = this.grupoNome.trim();
    const descricao = this.grupoDescricao.trim();

    if (!nome) {
      this.erro = 'Informe um nome para o grupo automático.';
      return;
    }

    const itens: RelatorioGrupoItem[] = this.relatorios
      .filter((relatorio) => this.relatoriosGrupoSelecionados[relatorio.id])
      .map((relatorio) => ({
        relatorioId: relatorio.id,
        nomeArquivo: sanitizarNomeArquivo(
          this.nomesArquivoRelatorio[relatorio.id] || nomeCurtoRelatorio(relatorio),
        ),
      }));

    if (!itens.length) {
      this.erro = 'Selecione pelo menos um relatório para o grupo.';
      return;
    }

    const nomeArquivoVazio = itens.find((item) => !item.nomeArquivo);
    if (nomeArquivoVazio) {
      this.erro = 'Informe o nome que será usado no arquivo de cada relatório selecionado.';
      return;
    }

    const agora = new Date().toISOString();
    const existente = this.grupos.find((grupo) => grupo.id === this.grupoEmEdicaoId);

    const grupo: RelatorioGrupoAutomatico = {
      id: existente?.id ?? this.gerarId(),
      nome,
      descricao,
      formato: this.grupoFormato,
      itens,
      criadoEm: existente?.criadoEm ?? agora,
      atualizadoEm: agora,
    };

    this.grupos = existente
      ? this.grupos.map((item) => (item.id === grupo.id ? grupo : item))
      : [...this.grupos, grupo];

    this.persistirGrupos();
    this.modalGrupoAberto = false;
    this.selecionarGrupo(grupo);
    this.sucesso = existente
      ? `O grupo “${grupo.nome}” foi atualizado.`
      : `O grupo “${grupo.nome}” foi criado.`;
    this.erro = '';
  }

  selecionarGrupo(grupo: RelatorioGrupoAutomatico): void {
    this.grupoSelecionado = grupo;
    this.formatoExecucao = grupo.formato;
    this.nomeArquivoZip = `${sanitizarNomeArquivo(grupo.nome)}_${this.dataAtualCompacta()}`;
    this.montarFiltrosExecucao(grupo);
    this.erro = '';
    this.sucesso = '';
    this.progressoExecucao = 0;
    this.etapaExecucao = '';
  }

  // ── Seleção e resumo dos relatórios que compõem um grupo ───────────────────

  excluirGrupo(grupo: RelatorioGrupoAutomatico, evento?: Event): void {
    evento?.stopPropagation();

    const confirmou =
      typeof window === 'undefined' ||
      window.confirm(`Excluir o grupo automático “${grupo.nome}”?`);

    if (!confirmou) return;

    this.grupos = this.grupos.filter((item) => item.id !== grupo.id);
    this.persistirGrupos();

    if (this.grupoSelecionado?.id === grupo.id) {
      this.grupoSelecionado = null;
      this.filtrosExecucao = [];
      this.empresasExecucao = [];

      if (this.grupos.length) {
        this.selecionarGrupo(this.grupos[0]);
      }
    }

    this.sucesso = `O grupo “${grupo.nome}” foi excluído.`;
  }

  relatoriosDoGrupo(
    grupo: RelatorioGrupoAutomatico | null = this.grupoSelecionado,
  ): RelatorioCatalogo[] {
    if (!grupo) return [];

    return grupo.itens
      .map((item) => this.relatorios.find((relatorio) => relatorio.id === item.relatorioId))
      .filter((relatorio): relatorio is RelatorioCatalogo => Boolean(relatorio));
  }

  resumoGrupo(grupo: RelatorioGrupoAutomatico): string {
    const nomes = this.relatoriosDoGrupo(grupo).map((relatorio) => relatorio.nomeExibicao);

    if (!nomes.length) return 'Nenhum relatório disponível';
    if (nomes.length <= 2) return nomes.join(' · ');
    return `${nomes.slice(0, 2).join(' · ')} +${nomes.length - 2}`;
  }

  get filtroEmpresa(): FiltroGrupoExecucao | undefined {
    return this.filtrosExecucao.find((filtro) => filtro.chave === 'empresa');
  }

  get filtrosGerais(): FiltroGrupoExecucao[] {
    return this.filtrosExecucao.filter((filtro) => filtro.chave !== 'empresa');
  }

  get empresasSelecionadasIds(): string[] {
    return this.empresasExecucao.map((empresa) => empresa.catalogoId).filter(Boolean);
  }

  adicionarEmpresa(): void {
    this.empresasExecucao.push(this.novaEmpresa());
  }

  selecionarEmpresaCatalogo(empresa: EmpresaGrupoExecucao, catalogoId: string): void {
    const cadastrada = this.empresasCatalogo.find((item) => item.id === catalogoId);

    empresa.catalogoId = catalogoId;
    empresa.nome = cadastrada?.nome ?? '';
    empresa.codigos = cadastrada?.codigos.join(',') ?? '';
  }

  // ── Valores informados para empresas e filtros ─────────────────────────────

  removerEmpresa(indice: number): void {
    if (this.empresasExecucao.length > 1) {
      this.empresasExecucao.splice(indice, 1);
    }
  }

  adicionarValorFiltro(filtro: FiltroGrupoExecucao): void {
    filtro.valores.push(novoValorFiltro());
  }

  removerValorFiltro(filtro: FiltroGrupoExecucao, indice: number): void {
    if (filtro.valores.length > 1) {
      filtro.valores.splice(indice, 1);
    }
  }

  tipoInputFiltro(filtro: FiltroGrupoExecucao): string {
    const todosNumericos = filtro.usos.every(
      (uso) => uso.tipoDadoFiltro.toUpperCase() === 'NUMBER',
    );
    return todosNumericos ? 'number' : 'text';
  }

  placeholderFiltro(filtro: FiltroGrupoExecucao): string {
    if (filtro.chave === 'competencia') return 'Ex.: 202601';
    return filtro.rotulo;
  }

  get quantidadeArquivosPrevista(): number {
    if (!this.grupoSelecionado) return 0;

    const empresasValidas = this.empresasExecucao.filter(
      (empresa) => empresa.codigos.trim() && empresa.nome.trim(),
    ).length;

    return this.grupoSelecionado.itens.reduce((total, item) => {
      const relatorio = this.relatorios.find((atual) => atual.id === item.relatorioId);
      if (!relatorio) return total;

      const usaEmpresa = relatorio.filtros.some(
        (filtro) => chaveLogicaFiltro(filtro.nomeFiltro) === 'empresa',
      );

      return total + (usaEmpresa ? empresasValidas : 1);
    }, 0);
  }

  gerarGrupoAutomaticamente(): void {
    if (!this.grupoSelecionado || this.executando) return;

    let request: RelatorioLoteRequest;

    try {
      request = this.montarRequestLote();
    } catch (erro) {
      this.erro =
        erro instanceof Error ? erro.message : 'Não foi possível preparar o grupo de relatórios.';
      return;
    }

    this.executando = true;
    this.segundosExecucao = 0;
    this.progressoExecucao = 5;
    this.etapaExecucao = 'Preparando consultas e arquivos…';
    this.erro = '';
    this.sucesso = '';
    this.fecharNotificacaoExecucao();
    this.iniciarCronometro(request.itens.length);
    this.cdr.detectChanges();

    this.relatorioService
      .exportarLote(request)
      .pipe(
        timeout(this.timeoutLoteMs),
        finalize(() => {
          this.executando = false;
          this.pararCronometro();
          if (this.progressoExecucao < 100) {
            this.progressoExecucao = 0;
            this.etapaExecucao = '';
          }
          // HttpClient não atualiza automaticamente esta view no modo zoneless.
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (resposta: HttpResponse<Blob>) => {
          const blob = resposta.body;
          if (!blob) {
            this.progressoExecucao = 0;
            this.etapaExecucao = '';
            this.mostrarNotificacaoExecucao('erro', 'O backend não devolveu o arquivo ZIP.');
            return;
          }

          const nomeZip = sanitizarNomeArquivo(request.nomeArquivo);
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `${nomeZip}.zip`;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
          setTimeout(() => URL.revokeObjectURL(url), 0);

          const gerados = resposta.headers.get('X-Relatorios-Gerados');
          const falhas = resposta.headers.get('X-Relatorios-Erros');

          if (gerados === '0' && falhas && falhas !== '0') {
            this.progressoExecucao = 0;
            this.etapaExecucao = '';
            this.mostrarNotificacaoExecucao(
              'erro',
              `Nenhum relatório foi gerado. O ZIP contém o resumo de ${falhas} falha(s).`,
            );
          } else {
            const mensagem = gerados
              ? `${gerados} arquivo(s) gerado(s).${
                  falhas && falhas !== '0'
                    ? ` ${falhas} falha(s) foram registradas dentro do ZIP.`
                    : ''
                }`
              : `Lote concluído: ${nomeZip}.zip`;
            this.progressoExecucao = 0;
            this.etapaExecucao = '';
            this.mostrarNotificacaoExecucao(
              falhas && falhas !== '0' ? 'erro' : 'sucesso',
              mensagem,
            );
          }
          this.cdr.detectChanges();
        },
        error: async (erro: unknown) => {
          this.mostrarNotificacaoExecucao(
            'erro',
            await this.mensagemErroBlob(erro, 'Não foi possível gerar o grupo automático.'),
          );
          this.progressoExecucao = 0;
          this.etapaExecucao = '';
          this.cdr.detectChanges();
        },
      });
  }

  fecharNotificacaoExecucao(): void {
    if (this.temporizadorNotificacao) {
      clearTimeout(this.temporizadorNotificacao);
      this.temporizadorNotificacao = undefined;
    }
    this.notificacaoExecucao = null;
  }

  private mostrarNotificacaoExecucao(tipo: NotificacaoExecucao['tipo'], mensagem: string): void {
    this.fecharNotificacaoExecucao();
    this.notificacaoExecucao = { tipo, mensagem };
    this.temporizadorNotificacao = setTimeout(() => {
      this.notificacaoExecucao = null;
      this.temporizadorNotificacao = undefined;
      this.cdr.detectChanges();
    }, 7000);
  }

  // ── Execução do lote e atualização da interface ────────────────────────────

  trackByGrupoId(_indice: number, grupo: RelatorioGrupoAutomatico): string {
    return grupo.id;
  }

  trackByRelatorioId(_indice: number, relatorio: RelatorioCatalogo): string {
    return relatorio.id;
  }

  trackByEmpresaId(_indice: number, empresa: EmpresaGrupoExecucao): string {
    return empresa.id;
  }

  trackByFiltroChave(_indice: number, filtro: FiltroGrupoExecucao): string {
    return filtro.chave;
  }

  trackByValorId(_indice: number, valor: ValorFiltroGrupo): string {
    return valor.id;
  }

  /**
   * Reconcilia filtros de relatórios diferentes pela chave lógica. Assim, um
   * mesmo valor pode alimentar nomes equivalentes sem expor filtros técnicos.
   */
  private montarFiltrosExecucao(grupo: RelatorioGrupoAutomatico): void {
    this.filtrosExecucao = montarFiltrosGrupo(grupo, this.relatorios);

    this.empresasExecucao = this.filtroEmpresa ? [this.novaEmpresa()] : [];
  }

  /**
   * Expande grupos e empresas em itens independentes. A combinação ocorre no
   * frontend para que cada arquivo receba nome e filtros previsíveis.
   */
  private montarRequestLote(): RelatorioLoteRequest {
    const grupo = this.grupoSelecionado;
    if (!grupo) throw new Error('Selecione um grupo automático.');

    const nomeZip = sanitizarNomeArquivo(this.nomeArquivoZip);
    if (!nomeZip) throw new Error('Informe o nome do arquivo ZIP.');

    const empresas = this.empresasExecucao
      .map((empresa) => {
        if (!empresa.catalogoId) return null;

        const cadastrada = this.empresasCatalogo.find((item) => item.id === empresa.catalogoId);
        if (!cadastrada) {
          throw new Error('Selecione uma empresa válida da lista disponível.');
        }

        return {
          catalogoId: cadastrada.id,
          nome: sanitizarNomeArquivo(cadastrada.nome),
          codigos: cadastrada.codigos.join(','),
        };
      })
      .filter((empresa): empresa is { catalogoId: string; nome: string; codigos: string } =>
        Boolean(empresa),
      );

    if (this.filtroEmpresa) {
      if (!empresas.length) {
        throw new Error('Selecione pelo menos uma empresa para gerar o grupo.');
      }

      const ids = empresas.map((empresa) => empresa.catalogoId);
      if (new Set(ids).size !== ids.length) {
        throw new Error('A mesma empresa não pode ser selecionada mais de uma vez.');
      }
    }

    this.filtrosGerais.forEach((filtro) => {
      const valores = valoresPreenchidos(filtro);
      if (filtro.obrigatorio && !valores.length) {
        throw new Error(`Preencha o filtro obrigatório “${filtro.rotulo}”.`);
      }
    });

    const itens: RelatorioLoteItemRequest[] = [];
    const nomesUsados = new Map<string, number>();

    grupo.itens.forEach((itemGrupo) => {
      const relatorio = this.relatorios.find((atual) => atual.id === itemGrupo.relatorioId);
      if (!relatorio) return;

      const filtrosRelatorio = filtrosNegocio(relatorio.filtros);
      const usaEmpresa = filtrosRelatorio.some(
        (filtro) => chaveLogicaFiltro(filtro.nomeFiltro) === 'empresa',
      );

      const contextos: ContextoEmpresa[] = usaEmpresa
        ? empresas
        : [
            {
              nome: sanitizarNomeArquivo(grupo.nome) || 'grupo',
              codigos: '',
            },
          ];

      contextos.forEach((contexto) => {
        const combinacoes = this.criarCombinacoesFiltros(relatorio, filtrosRelatorio, contexto);

        const nomeBase = this.nomeArquivoDoItem(grupo, itemGrupo, relatorio, contexto, usaEmpresa);
        const nomeArquivo = this.nomeArquivoUnico(nomeBase, nomesUsados);

        itens.push({
          apiNome: relatorio.apiNome,
          nomeArquivo,
          combinacoesFiltros: combinacoes,
        });
      });
    });

    if (!itens.length) {
      throw new Error('Nenhum arquivo pôde ser preparado. Verifique os relatórios do grupo.');
    }

    return {
      nomeArquivo: nomeZip,
      formato: this.formatoExecucao,
      itens,
    };
  }

  /**
   * Calcula o produto cartesiano somente dos filtros multivalorados. Filtros
   * vazios continuam ausentes da requisição, preservando o comportamento do SGU.
   */
  private criarCombinacoesFiltros(
    relatorio: RelatorioCatalogo,
    filtrosRelatorio: SguFiltro[],
    contexto: ContextoEmpresa,
  ): Record<string, unknown>[] {
    let combinacoes: Record<string, unknown>[] = [{}];

    const chaves = [
      ...new Set(filtrosRelatorio.map((filtro) => chaveLogicaFiltro(filtro.nomeFiltro))),
    ];

    chaves.forEach((chave) => {
      const filtrosDaChave = filtrosRelatorio.filter(
        (filtro) => chaveLogicaFiltro(filtro.nomeFiltro) === chave,
      );

      if (chave === 'empresa') {
        // NUMBER exige uma consulta por código. O backend consolida as combinações
        // no mesmo arquivo; VARCHAR continua recebendo a lista completa de códigos.
        const possuiNumerico = filtrosDaChave.some((filtro) => filtro.tipoDadoFiltro.toUpperCase() === 'NUMBER');
        const codigos = possuiNumerico ? contexto.codigos.split(',') : [contexto.codigos];
        combinacoes = combinacoes.flatMap((combinacao) => codigos.map((codigo) => {
          const atualizada = { ...combinacao };
          filtrosDaChave.forEach((filtro) => {
            atualizada[filtro.nomeFiltro] = this.converterValorFiltro(
              possuiNumerico ? codigo : contexto.codigos,
              filtro,
              relatorio,
            );
          });
          return atualizada;
        }));
        return;
      }

      const unificado = this.filtrosExecucao.find((filtro) => filtro.chave === chave);
      const valores = unificado ? valoresPreenchidos(unificado) : [];
      const obrigatorioNesteRelatorio = filtrosDaChave.some(
        (filtro) => filtro.obrigatorioFiltro === 'S',
      );

      if (!valores.length) {
        if (obrigatorioNesteRelatorio) {
          throw new Error(
            `O relatório “${relatorio.nomeExibicao}” exige o filtro “${
              unificado?.rotulo ?? chave
            }”.`,
          );
        }
        return;
      }

      const expandidas: Record<string, unknown>[] = [];
      combinacoes.forEach((combinacao) => {
        valores.forEach((valor) => {
          const atualizada = { ...combinacao };
          filtrosDaChave.forEach((filtro) => {
            atualizada[filtro.nomeFiltro] = this.converterValorFiltro(valor, filtro, relatorio);
          });
          expandidas.push(atualizada);
        });
      });
      combinacoes = expandidas;
    });

    const unicas = new Map<string, Record<string, unknown>>();
    combinacoes.forEach((combinacao) => {
      unicas.set(JSON.stringify(combinacao), combinacao);
    });

    return [...unicas.values()];
  }

  private converterValorFiltro(
    valor: string,
    filtro: SguFiltro,
    relatorio: RelatorioCatalogo,
  ): unknown {
    const tipo = filtro.tipoDadoFiltro.toUpperCase();
    const limpo = valor.trim();

    if (tipo === 'NUMBER') {
      if (limpo.includes(',')) {
        throw new Error(
          `O filtro “${filtro.nomeFiltro}” do relatório “${relatorio.nomeExibicao}” ` +
            'é NUMBER e aceita apenas um número por campo. Separe os valores usando o botão Adicionar.',
        );
      }

      const numero = Number(limpo.replace(',', '.'));
      if (!Number.isFinite(numero)) {
        throw new Error(
          `O valor “${valor}” não é válido para o filtro numérico “${filtro.nomeFiltro}”.`,
        );
      }
      return numero;
    }

    return limpo;
  }

  private nomeArquivoDoItem(
    grupo: RelatorioGrupoAutomatico,
    itemGrupo: RelatorioGrupoItem,
    relatorio: RelatorioCatalogo,
    contexto: ContextoEmpresa,
    usaEmpresa: boolean,
  ): string {
    const partes = [
      usaEmpresa ? contexto.nome : sanitizarNomeArquivo(grupo.nome) || 'grupo',
      sanitizarNomeArquivo(itemGrupo.nomeArquivo) || nomeCurtoRelatorio(relatorio),
    ];

    const chavesRelatorio = new Set(
      filtrosNegocio(relatorio.filtros).map((filtro) =>
        chaveLogicaFiltro(filtro.nomeFiltro),
      ),
    );

    this.filtrosGerais.forEach((filtro) => {
      if (!chavesRelatorio.has(filtro.chave)) return;
      const valores = valoresPreenchidos(filtro);
      if (!valores.length) return;

      if (filtro.chave === 'competencia') {
        partes.push(this.sufixoCompetencias(valores));
      } else if (valores.length > 1) {
        partes.push(
          valores
            .map((valor) => sanitizarNomeArquivo(valor))
            .filter(Boolean)
            .join('_'),
        );
      }
    });

    return partes.filter(Boolean).join('_');
  }

  private sufixoCompetencias(valores: string[]): string {
    const limpos = valores.map((valor) => valor.trim());
    const todosAnoMes = limpos.every((valor) => /^\d{6}$/.test(valor));

    if (!todosAnoMes) {
      return limpos.map((valor) => sanitizarNomeArquivo(valor)).join('_');
    }

    const anos = new Set(limpos.map((valor) => valor.slice(0, 4)));
    return anos.size === 1 ? limpos.map((valor) => valor.slice(4, 6)).join('_') : limpos.join('_');
  }

  private nomeArquivoUnico(nomeBase: string, nomesUsados: Map<string, number>): string {
    const base = sanitizarNomeArquivo(nomeBase) || 'relatorio';
    const quantidade = nomesUsados.get(base) ?? 0;
    nomesUsados.set(base, quantidade + 1);
    return quantidade === 0 ? base : `${base}_${quantidade + 1}`;
  }

  private reconciliarGrupos(): void {
    const idsDisponiveis = new Set(this.relatorios.map((relatorio) => relatorio.id));

    this.grupos = (this.grupos ?? [])
      .map((grupo) => ({
        ...grupo,
        formato: grupo.formato || 'xlsx',
        itens: (grupo.itens ?? []).filter((item) => idsDisponiveis.has(item.relatorioId)),
      }))
      .filter((grupo) => grupo.id && grupo.nome?.trim() && grupo.itens.length);

    this.persistirGrupos();

    if (this.grupoSelecionado) {
      const atualizado = this.grupos.find((grupo) => grupo.id === this.grupoSelecionado?.id);
      if (atualizado) {
        this.selecionarGrupo(atualizado);
      } else {
        this.grupoSelecionado = null;
      }
    }
  }

  // ── Compatibilidade do armazenamento e utilitários internos ────────────────

  private persistirGrupos(): void {
    this.relatorioService.salvarGruposAutomaticos(this.grupos);
  }

  private novaEmpresa(): EmpresaGrupoExecucao {
    return {
      id: this.gerarId(),
      catalogoId: '',
      codigos: '',
      nome: '',
    };
  }

  private dataAtualCompacta(): string {
    const agora = new Date();
    const ano = agora.getFullYear();
    const mes = String(agora.getMonth() + 1).padStart(2, '0');
    const dia = String(agora.getDate()).padStart(2, '0');
    return `${ano}${mes}${dia}`;
  }

  private iniciarCronometro(quantidadeItens: number): void {
    this.pararCronometro();
    const segundosEstimados = Math.max(20, quantidadeItens * 12);
    this.intervaloExecucao = setInterval(() => {
      this.segundosExecucao += 1;
      const aproximacao = 1 - Math.exp(-this.segundosExecucao / segundosEstimados);
      this.progressoExecucao = Math.min(94, Math.round(5 + aproximacao * 89));
      this.etapaExecucao =
        this.progressoExecucao < 30
          ? 'Preparando consultas e arquivos…'
          : this.progressoExecucao < 70
            ? 'Consultando os relatórios do grupo…'
            : 'Consolidando os resultados e montando o ZIP…';
      this.cdr.detectChanges();
    }, 1000);
  }

  private pararCronometro(): void {
    if (this.intervaloExecucao) {
      clearInterval(this.intervaloExecucao);
      this.intervaloExecucao = undefined;
    }
  }

  private async mensagemErroBlob(erro: unknown, mensagemPadrao: string): Promise<string> {
    if (erro instanceof HttpErrorResponse) {
      let detalhe = '';

      if (erro.error instanceof Blob) {
        try {
          const texto = await erro.error.text();
          try {
            const json = JSON.parse(texto);
            detalhe = json?.message ?? json?.error ?? texto;
          } catch {
            detalhe = texto;
          }
        } catch {
          detalhe = '';
        }
      } else if (typeof erro.error === 'string') {
        detalhe = erro.error;
      } else {
        detalhe = erro.error?.message ?? erro.error?.error ?? '';
      }

      if (erro.status === 0) {
        return 'Não foi possível conectar ao backend.';
      }

      return `Erro ${erro.status}: ${detalhe || erro.statusText || mensagemPadrao}`;
    }

    if (erro instanceof Error && erro.name === 'TimeoutError') {
      return 'A geração ultrapassou o limite de uma hora.';
    }

    return erro instanceof Error ? erro.message : mensagemPadrao;
  }

  private gerarId(): string {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
      return crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}
