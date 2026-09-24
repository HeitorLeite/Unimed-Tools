/** Empresas indicadas para a geração em grupo. Os códigos são parâmetros, nunca SQL. */
export interface EmpresaCatalogo {
  id: string;
  nome: string;
  codigos: readonly string[];
}

export const EMPRESAS_RELATORIOS: readonly EmpresaCatalogo[] = [
  { id: 'yakult', nome: 'Yakult', codigos: ['2232751', '2234452'] },
  {
    id: 'cancao-nova',
    nome: 'Canção Nova',
    codigos: ['2234623', '2234624', '2234625', '2234626', '2234627', '2234628'],
  },
  { id: 'orica', nome: 'Orica', codigos: ['2010038', '2011533', '2011372'] },
  { id: 'saint-gobain', nome: 'Saint-Gobain', codigos: ['2052097'] },
  {
    id: 'grupo-biondi',
    nome: 'Grupo Biondi',
    codigos: ['2236880', '2236881', '2236882', '2236883', '2236884', '2236886', '2236887'],
  },
  { id: 'biemme', nome: 'Biemme', codigos: ['2010497'] },
  { id: 'grupo-liceu', nome: 'Grupo Liceu', codigos: ['2220974', '2220179', '2237996'] },
  { id: 'apollo', nome: 'Apollo', codigos: ['2177395'] },
  { id: 'comunidade-cancao-nova', nome: 'Comunidade Canção Nova', codigos: ['2237647'] },
  { id: 'grupo-eppo', nome: 'Grupo Eppo', codigos: ['1485', '1488'] },
  { id: 'akg', nome: 'AKG', codigos: ['2218214'] },
  { id: 'instituto-santa-teresa', nome: 'Instituto Santa Teresa', codigos: ['2046655'] },
  { id: 'aeci', nome: 'AECI', codigos: ['2227713'] },
  { id: 'ice', nome: 'ICE', codigos: ['2234145'] },
  {
    id: 'faculdade-serra-dourada',
    nome: 'Faculdade Serra Dourada',
    codigos: ['2227006', '2214653'],
  },
];

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
