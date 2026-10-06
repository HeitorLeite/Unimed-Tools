export interface HospitalFilterOption {
  valor: string;
  rotulo: string;
}

export interface HospitalFilter {
  id: string;
  rotulo: string;
  tipo: 'text' | 'date' | 'select';
  placeholder: string;
  opcoes: HospitalFilterOption[];
}

export interface HospitalConfiguration {
  apiNome: string;
  colunas: string[];
  filtros: HospitalFilter[];
}

export interface HospitalRequest {
  filtros: Record<string, unknown>;
  pagina: number;
  tamanhoPagina: number;
  nomeArquivo: string;
}
