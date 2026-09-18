import { SguFiltro } from '../../../shared/models/relatorio.model';

export type StatusArquivoSql = 'pendente' | 'criando' | 'sucesso' | 'erro';

export interface FiltroFixoSqlDetectado {
  id: string;
  assinatura: string;
  predicadoOriginal: string;
  marcador: string;
  filtro: SguFiltro;
}

export interface ArquivoSqlImportado {
  id: string;
  arquivoNome: string;
  tamanhoBytes: number;
  apiNome: string;
  nomeExibicao: string;
  descricao: string;
  consultaSQL: string;
  ordenacao: string;
  filtros: SguFiltro[];
  filtrosFixosDetectados: FiltroFixoSqlDetectado[];
  filtrosFixosIgnorados: string[];
  ajustesAplicados: string[];
  detalhesAbertos: boolean;
  status: StatusArquivoSql;
  erro: string;
}

export interface TokenSqlNivelZero {
  palavra: string;
  inicio: number;
  fim: number;
}

export interface EstruturaConsultaPrincipal {
  select: TokenSqlNivelZero;
  where?: TokenSqlNivelZero;
  limiteCondicoes: number;
  fimRamo: number;
  operadorConjunto?: TokenSqlNivelZero;
}
