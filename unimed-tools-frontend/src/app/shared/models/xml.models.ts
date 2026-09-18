export interface CorretorXmlError {
  position: number;
  original: string;
  corrected: string;
}

export interface RemovedorBlock {
  dataExecucao: string;
  tabelaProcedimento: string;
  descricao: string;
  valorTotal: string;
}

export interface OutrasDespesasVazia {
  position: number;
  linha: number;
  coluna: number;
  numeroGuia: string;
  original: string;
}

export interface RenumeracaoXml {
  original: string;
  novo: string;
}

export interface ArquivoResultado {
  nome: string;
  prefixos: CorretorXmlError[];
  blocos: RemovedorBlock[];
  outrasDespesasVazias: OutrasDespesasVazia[];
  guiasRenomeadas: RenumeracaoXml[];
  lotesRenumerados: RenumeracaoXml[];
  correctedContent: string;
}

export interface ArquivoZip {
  nome: string;
  conteudo: string;
}
