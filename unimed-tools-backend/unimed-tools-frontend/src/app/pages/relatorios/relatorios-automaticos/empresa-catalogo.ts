/** Empresas indicadas para a geração em grupo. Os códigos são parâmetros, nunca SQL. */
export interface EmpresaCatalogo {
  id: string;
  nome: string;
  codigos: readonly string[];
  /** Códigos numéricos de b.bnf_cod_cntrat_cart, sem zeros decorativos à esquerda. */
  codigosCarteirinha: readonly string[];
}

export type SituacaoCodigoCarteirinha = 'TODOS' | 'ATIVOS' | 'INATIVOS';

export const EMPRESAS_RELATORIOS: readonly EmpresaCatalogo[] = [
  {
    id: 'yakult',
    nome: 'Yakult',
    codigos: ['2232751', '2234452'],
    codigosCarteirinha: ['2128', '9128'],
  },
  {
    id: 'cancao-nova',
    nome: 'Canção Nova',
    codigos: ['2234623', '2234624', '2234625', '2234626', '2234627', '2234628'],
    codigosCarteirinha: ['2152', '2154', '2156', '2158', '2160', '2162', '9152'],
  },
  {
    id: 'orica',
    nome: 'Orica',
    codigos: ['2010038', '2011533', '2011372'],
    codigosCarteirinha: ['422', '5002'],
  },
  { id: 'saint-gobain', nome: 'Saint-Gobain', codigos: ['2052097'], codigosCarteirinha: ['99'] },
  {
    id: 'grupo-biondi',
    nome: 'Grupo Biondi',
    codigos: ['2236880', '2236881', '2236882', '2236883', '2236884', '2236886', '2236887'],
    codigosCarteirinha: ['2173', '2174', '2175', '2176', '2177', '2178', '2179', '2180', '9173'],
  },
  { id: 'biemme', nome: 'Biemme', codigos: ['2010497'], codigosCarteirinha: ['45', '5045'] },
  {
    id: 'grupo-liceu',
    nome: 'Grupo Liceu',
    codigos: ['2220974', '2220179', '2237996'],
    codigosCarteirinha: ['364', '365', '366', '367', '369', '2190', '5364', '5365'],
  },
  {
    id: 'apollo',
    nome: 'Apollo',
    codigos: ['2177395'],
    codigosCarteirinha: ['156', '2260', '5156'],
  },
  {
    id: 'comunidade-cancao-nova',
    nome: 'Comunidade Canção Nova',
    codigos: ['2237647'],
    codigosCarteirinha: ['2191'],
  },
  {
    id: 'grupo-eppo',
    nome: 'Grupo Eppo',
    codigos: ['1485', '1488'],
    codigosCarteirinha: ['2416', '2417'],
  },
  { id: 'akg', nome: 'AKG', codigos: ['2218214'], codigosCarteirinha: ['2401'] },
  {
    id: 'instituto-santa-teresa',
    nome: 'Instituto Santa Teresa',
    codigos: ['2046655'],
    codigosCarteirinha: ['92', '5092'],
  },
  { id: 'aeci', nome: 'AECI', codigos: ['2227713'], codigosCarteirinha: ['2022', '2303'] },
  { id: 'ice', nome: 'ICE', codigos: ['2234145'], codigosCarteirinha: ['2142'] },
  {
    id: 'faculdade-serra-dourada',
    nome: 'Faculdade Serra Dourada',
    codigos: ['2227006', '2214653'],
    codigosCarteirinha: ['2311', '2314'],
  },
];

export function codigoCarteirinhaInativo(codigo: string): boolean {
  return /^[59]\d{3}$/.test(codigo.padStart(4, '0'));
}

export function codigosCarteirinhaPorSituacao(
  empresas: readonly EmpresaCatalogo[],
  situacao: Exclude<SituacaoCodigoCarteirinha, 'TODOS'>,
): string[] {
  return [
    ...new Set(
      empresas
        .flatMap((empresa) => [...empresa.codigosCarteirinha])
        .filter((codigo) => codigoCarteirinhaInativo(codigo) === (situacao === 'INATIVOS')),
    ),
  ];
}

export function buscarEmpresas(termo: string): readonly EmpresaCatalogo[] {
  const busca = normalizarBusca(termo);
  return EMPRESAS_RELATORIOS.filter((empresa) => normalizarBusca(empresa.nome).includes(busca));
}

function normalizarBusca(valor: string): string {
  return valor
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}
