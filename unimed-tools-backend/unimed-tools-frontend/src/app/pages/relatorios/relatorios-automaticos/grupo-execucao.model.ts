/** Estado transitório dos filtros e empresas usados na geração de um grupo. */
export interface UsoFiltroGrupo {
  relatorioId: string;
  nomeFiltro: string;
  tipoDadoFiltro: string;
  obrigatorioFiltro: 'S' | 'N';
}

export interface ValorFiltroGrupo {
  id: string;
  valor: string | number;
}

export interface FiltroGrupoExecucao {
  chave: string;
  rotulo: string;
  usos: UsoFiltroGrupo[];
  obrigatorio: boolean;
  valores: ValorFiltroGrupo[];
}

export interface EmpresaGrupoExecucao {
  id: string;
  codigos: string;
  nome: string;
  catalogoId: string;
}

export interface ContextoEmpresa {
  nome: string;
  codigos: string;
}
