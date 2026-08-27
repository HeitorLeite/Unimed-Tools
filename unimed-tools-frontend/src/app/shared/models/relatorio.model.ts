/**
 * Contratos compartilhados entre a interface de relatórios, o armazenamento local e a API.
 */
export interface SguFiltro {
  nomeFiltro: string;
  conteudoFiltro: string;
  tipoDadoFiltro: string;
  mascaraFiltro: string;
  obrigatorioFiltro: 'S' | 'N';
}

export interface SguApiDefinicao {
  nome: string;
  consultaSQL: string;
  ordenacao: string;
  filtros: SguFiltro[];
}

export interface SguListaResponse {
  content: SguApiDefinicao[];
  numberOfElements?: number | string;
}

export interface RelatorioCatalogo {
  id: string;
  nomeExibicao: string;
  descricao: string;
  apiNome: string;
  filtros: SguFiltro[];
  criadoEm: string;
}

export interface RelatorioTemplate {
  id: string;
  nome: string;
  descricao: string;
  relatorioIds: string[];
  criadoEm: string;
}

export interface RelatorioGrupoItem {
  relatorioId: string;
  nomeArquivo: string;
}

export interface RelatorioGrupoAutomatico {
  id: string;
  nome: string;
  descricao: string;
  formato: FormatoExportacao;
  itens: RelatorioGrupoItem[];
  criadoEm: string;
  atualizadoEm?: string;
}

export interface RelatorioLoteItemRequest {
  apiNome: string;
  nomeArquivo: string;
  combinacoesFiltros: Record<string, unknown>[];
}

export interface RelatorioLoteRequest {
  nomeArquivo: string;
  formato: FormatoExportacao;
  itens: RelatorioLoteItemRequest[];
}

export interface SguResultado {
  content: Record<string, unknown>[];
  totalElements?: number | string;
  numberOfElements?: number | string;
  totalPages?: number;
  number?: number;
  last?: boolean;
  [key: string]: unknown;
}

export interface RelatorioPersonalizadoColuna {
  id: string;
  rotulo: string;
  grupo: string;
  selecionadaPorPadrao: boolean;
  sensivel: boolean;
}

export interface RelatorioPersonalizadoOpcao {
  valor: string;
  rotulo: string;
}

export interface RelatorioPersonalizadoFiltro {
  id: string;
  rotulo: string;
  grupo: string;
  tipo: 'text' | 'number' | 'decimal' | 'date' | 'competencia' | 'select';
  placeholder: string;
  obrigatorio: boolean;
  opcoes: RelatorioPersonalizadoOpcao[];
}

export interface RelatorioPersonalizadoConfiguracao {
  apiNome: string;
  fonte: string;
  colunas: RelatorioPersonalizadoColuna[];
  filtros: RelatorioPersonalizadoFiltro[];
  limites: {
    maximoColunas: number;
    maximoMeses: number;
    maximoLinhasPagina: number;
  };
}

export interface RelatorioPersonalizadoRequest {
  colunas: string[];
  filtros: Record<string, unknown>;
  distinct: boolean;
  ordenarPor?: string;
  direcaoOrdenacao?: 'ASC' | 'DESC';
  pagina: number;
  tamanhoPagina: number;
  nomeArquivo: string;
}

export type FormatoExportacao = 'csv' | 'txt' | 'xlsx';

export type TipoRelatorioAgendado = 'MANUAL' | 'PERSONALIZADO';
export type RecorrenciaRelatorioAgendado = 'UNICA' | 'DIARIA' | 'SEMANAL' | 'MENSAL';
export type StatusRelatorioAgendado =
  | 'PENDENTE'
  | 'EM_EXECUCAO'
  | 'CONCLUIDO'
  | 'FALHA'
  | 'CANCELADO';

export interface RelatorioAgendamentoCriacao {
  tipoRelatorio: TipoRelatorioAgendado;
  tituloRelatorio: string;
  apiNome?: string;
  filtros: Record<string, unknown>;
  colunasPersonalizadas?: string[];
  distinct?: boolean;
  ordenarPor?: string;
  direcaoOrdenacao?: 'ASC' | 'DESC';
  colunasExportacao: string[];
  incluirCabecalho: boolean;
  formato: FormatoExportacao;
  nomeArquivo: string;
  diretorioReferencia: string;
  diretorioNome: string;
  agendadoPara: string;
  recorrencia: RecorrenciaRelatorioAgendado;
  diasSemana: number[];
  diaMes?: number;
  fusoHorario: string;
}

export interface RelatorioAgendamentoResumo {
  id: string;
  usuarioId: number;
  usuarioNome?: string;
  tipoRelatorio: TipoRelatorioAgendado;
  tituloRelatorio: string;
  formato: FormatoExportacao;
  nomeArquivo: string;
  nomeArquivoExecucao: string;
  diretorioReferencia: string | null;
  diretorioNome: string;
  incluirCabecalho: boolean;
  agendadoPara: string;
  recorrencia: RecorrenciaRelatorioAgendado;
  diasSemana: number[];
  diaMes?: number;
  fusoHorario: string;
  status: StatusRelatorioAgendado;
  erroCodigo?: string;
  erroMensagem?: string;
  tentativas: number;
  execucoesConcluidas: number;
  criadoEm: string;
  concluidoEm?: string;
}

export interface RelatorioAgendamentoConfiguracao {
  disponivel: boolean;
  intervaloVerificacaoSegundos: number;
  retencaoDias: number;
  limitacaoExecucao: string;
}

export interface RelatorioAgendamentoOperacao {
  message: string;
  agendamento: RelatorioAgendamentoResumo;
}
