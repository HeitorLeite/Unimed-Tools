import { RelatorioCatalogo, RelatorioGrupoAutomatico, SguFiltro } from '../../../shared/models/relatorio.model';
import { FiltroGrupoExecucao, ValorFiltroGrupo } from './grupo-execucao.model';

// Somente aliases de códigos: nome_empresa e status_empresa são filtros comuns.
const ALIASES_CODIGO_EMPRESA = new Set([
  'empresa', 'empresas', 'codigoempresa', 'codigosempresa', 'codigosempresas',
  'codempresa', 'codempresas', 'empresacod', 'empresacodigo',
  'empcncodpessoa', 'codpessoaempresa',
  'empresaslista',
]);

export function chaveLogicaFiltro(nome: string): string {
  const normalizado = nome.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]/g, '');
  if (ALIASES_CODIGO_EMPRESA.has(normalizado)) return 'empresa';
  if (normalizado.startsWith('compet')) return 'competencia';
  return normalizado || 'filtro';
}

export function filtrosNegocio(filtros: SguFiltro[]): SguFiltro[] {
  return (filtros ?? []).filter((filtro) => filtro.nomeFiltro?.trim().toLowerCase() !== 'filtrotecnico');
}

export function montarFiltrosGrupo(grupo: RelatorioGrupoAutomatico, relatorios: RelatorioCatalogo[]): FiltroGrupoExecucao[] {
  const mapa = new Map<string, FiltroGrupoExecucao>();
  grupo.itens.forEach((item) => {
    const relatorio = relatorios.find((atual) => atual.id === item.relatorioId);
    if (!relatorio) return;
    filtrosNegocio(relatorio.filtros).forEach((filtro) => {
      const chave = chaveLogicaFiltro(filtro.nomeFiltro);
      const existente = mapa.get(chave);
      const uso = {
        relatorioId: relatorio.id,
        nomeFiltro: filtro.nomeFiltro,
        tipoDadoFiltro: filtro.tipoDadoFiltro,
        obrigatorioFiltro: filtro.obrigatorioFiltro,
      };
      if (existente) {
        existente.usos.push(uso);
        existente.obrigatorio ||= filtro.obrigatorioFiltro === 'S';
      } else {
        mapa.set(chave, {
          chave,
          rotulo: chave === 'empresa' ? 'Empresas' : chave === 'competencia' ? 'Competências'
            : filtro.nomeFiltro.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letra) => letra.toUpperCase()),
          usos: [uso],
          obrigatorio: filtro.obrigatorioFiltro === 'S',
          valores: [novoValorFiltro()],
        });
      }
    });
  });
  return [...mapa.values()].sort((a, b) => {
    const prioridade = (chave: string) => chave === 'empresa' ? 0 : chave === 'competencia' ? 1 : 2;
    return prioridade(a.chave) - prioridade(b.chave) || a.rotulo.localeCompare(b.rotulo, 'pt-BR');
  });
}

export function valoresPreenchidos(filtro: FiltroGrupoExecucao): string[] {
  return [...new Set(filtro.valores.map((item) => String(item.valor ?? '').trim()).filter(Boolean))];
}

export function sanitizarNomeArquivo(valor: string): string {
  return String(valor ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9._-]+/g, '_').replace(/_+/g, '_').replace(/^[_-]+|[_-]+$/g, '');
}

export function nomeCurtoRelatorio(relatorio: RelatorioCatalogo): string {
  const nome = relatorio.nomeExibicao.replace(/\brelat[oó]rio\b/gi, '')
    .replace(/^\s*(de|da|do|dos|das)\s+/i, '').trim();
  return sanitizarNomeArquivo(nome || relatorio.apiNome);
}

export function gerarIdGrupo(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function novoValorFiltro(): ValorFiltroGrupo {
  return { id: gerarIdGrupo(), valor: '' };
}
