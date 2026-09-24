import { validarDelimitadoresSql, mascaraSqlSemTextosEComentarios, localizarConsultaPrincipal, tokensSqlNivelZero } from './sql-lexico';
import { ajustarEstruturaSqlImportado } from './sql-estrutura';
import { SguFiltro, SguApiDefinicao } from '../../../shared/models/relatorio.model';

const nomeFiltroTecnicoSemFiltros = 'filtrotecnico';


export function normalizarFiltros(filtros: SguFiltro[]): SguFiltro[] {
  return (filtros ?? [])
    .map((filtro): SguFiltro => ({
      nomeFiltro: filtro.nomeFiltro.trim(),
      conteudoFiltro: filtro.conteudoFiltro.trim(),
      tipoDadoFiltro: filtro.tipoDadoFiltro.trim().toUpperCase(),
      mascaraFiltro: filtro.mascaraFiltro.trim(),
      obrigatorioFiltro: filtro.obrigatorioFiltro === 'S' ? 'S' : 'N',
    }))
    .filter(
      (filtro) =>
        Boolean(filtro.nomeFiltro) ||
        Boolean(filtro.conteudoFiltro && filtro.conteudoFiltro.toLowerCase() !== 'and'),
    );
}


// ── Normalização, compatibilidade e persistência local ─────────────────────

/**
 * O SGU exige uma definição técnica mesmo para consultas sem filtros de
 * negócio. Esse filtro sentinela nunca deve ser apresentado ao usuário.
 */
export function filtroTecnicoSemFiltros(): SguFiltro {
  return {
    nomeFiltro: nomeFiltroTecnicoSemFiltros,
    conteudoFiltro: `and :${nomeFiltroTecnicoSemFiltros} is null`,
    tipoDadoFiltro: 'VARCHAR(1)',
    mascaraFiltro: '',
    obrigatorioFiltro: 'N',
  };
}


export function ehFiltroTecnicoSemFiltros(filtro: SguFiltro): boolean {
  return (
    filtro?.nomeFiltro?.trim().toLowerCase() === nomeFiltroTecnicoSemFiltros &&
    filtro?.conteudoFiltro?.trim().toLowerCase() ===
      `and :${nomeFiltroTecnicoSemFiltros} is null` &&
    filtro?.obrigatorioFiltro !== 'S'
  );
}


export function filtrosDeNegocio(filtros: SguFiltro[] | null | undefined): SguFiltro[] {
  return normalizarFiltros(filtros ?? [])
    .filter((filtro) => !ehFiltroTecnicoSemFiltros(filtro))
    .map((filtro) => ({ ...filtro }));
}


/**
 * A API do SGU pagina a consulta em chamadas independentes. Sem uma ordenação
 * estável, o Oracle pode devolver a mesma linha em páginas diferentes e omitir
 * outras. Para SQL importado, usamos os aliases da projeção principal como
 * desempate determinístico. Se a projeção usa "*" ou não possui aliases
 * seguros, a ordenação não é inferida e deve ser informada manualmente.
 */
export function inferirOrdenacaoDeterministicaSql(sql: string): string {
  const estrutura = localizarConsultaPrincipal(sql);
  if (!estrutura) return '';

  const tokens = tokensSqlNivelZero(sql);
  const tokenFrom = tokens.find(
    (token) =>
      token.palavra === 'FROM' &&
      token.inicio >= estrutura.select.fim &&
      token.inicio < estrutura.fimRamo,
  );
  if (!tokenFrom) return '';

  const inicio = estrutura.select.fim;
  const fim = tokenFrom.inicio;
  const original = sql.slice(inicio, fim);
  const mascarado = mascaraSqlSemTextosEComentarios(sql).slice(inicio, fim);

  const limites = [0];
  let profundidade = 0;
  for (let indice = 0; indice < mascarado.length; indice += 1) {
    const caractere = mascarado[indice];
    if (caractere === '(') {
      profundidade += 1;
    } else if (caractere === ')') {
      profundidade = Math.max(0, profundidade - 1);
    } else if (caractere === ',' && profundidade === 0) {
      limites.push(indice + 1);
    }
  }
  limites.push(mascarado.length + 1);

  const aliases: string[] = [];
  const reservadas = new Set([
    'ALL', 'AND', 'ASC', 'CASE', 'DESC', 'DISTINCT', 'ELSE', 'END',
    'FROM', 'NULL', 'OR', 'THEN', 'WHEN',
  ]);

  for (let indice = 0; indice < limites.length - 1; indice += 1) {
    const inicioTrecho = limites[indice];
    const fimTrecho = Math.min(mascarado.length, limites[indice + 1] - 1);
    const trechoOriginal = original.slice(inicioTrecho, fimTrecho).trim();
    let trechoMascarado = mascarado.slice(inicioTrecho, fimTrecho).trim();

    if (indice === 0) {
      trechoMascarado = trechoMascarado.replace(/^(?:DISTINCT|ALL)\s+/i, '').trim();
    }
    if (!trechoMascarado || /^(?:[A-Za-z_][\w$#]*\.)?\*$/.test(trechoMascarado)) {
      return '';
    }

    const comAs = trechoMascarado.match(/\bAS\s+([A-Za-z_][\w$#]*)\s*$/i);
    const simples = trechoMascarado.match(
      /^(?:[A-Za-z_][\w$#]*\.)*([A-Za-z_][\w$#]*)\s*$/,
    );
    const implicito = trechoMascarado.match(/\s+([A-Za-z_][\w$#]*)\s*$/);

    let alias = comAs?.[1] ?? simples?.[1] ?? implicito?.[1] ?? '';
    if (!alias || reservadas.has(alias.toUpperCase())) {
      // Evita considerar END de CASE ou palavra reservada como alias implícito.
      alias = comAs?.[1] ?? simples?.[1] ?? '';
    }
    if (!alias || !/^[A-Za-z_][\w$#]*$/.test(alias)) return '';

    const normalizado = alias.toUpperCase();
    if (aliases.some((existente) => existente.toUpperCase() === normalizado)) return '';
    aliases.push(alias.toUpperCase());

    // Mantém a variável usada para deixar claro que o trecho original é a
    // projeção correspondente; comentários/textos não participam da inferência.
    void trechoOriginal;
  }

  return aliases.join(', ');
}


export function removerFiltroTecnicoDaDefinicao(api: SguApiDefinicao): SguApiDefinicao {
  return {
    ...api,
    filtros: filtrosDeNegocio(api.filtros),
  };
}


export function prepararDefinicaoParaSgu(api: SguApiDefinicao): SguApiDefinicao {
  const filtrosNegocio = filtrosDeNegocio(api.filtros);
  const filtrosOriginais = filtrosNegocio.length
    ? filtrosNegocio
    : [filtroTecnicoSemFiltros()];

  const sqlAjustado = ajustarEstruturaSqlImportado(api.consultaSQL, true);
  let consultaSQL = sqlAjustado.sql;
  const filtrosSgu = filtrosOriginais.map((filtro) => {
    const nomeOriginal = filtro.nomeFiltro.trim();
    const nomeSgu = nomeFiltroSgu(nomeOriginal);

    consultaSQL = substituirBindSql(consultaSQL, nomeOriginal, nomeSgu);

    return {
      ...filtro,
      nomeFiltro: nomeSgu,
      conteudoFiltro: substituirBindSql(filtro.conteudoFiltro, nomeOriginal, nomeSgu),
    };
  });

  const ordenacao =
    api.ordenacao?.trim() || inferirOrdenacaoDeterministicaSql(consultaSQL);

  return {
    nome: api.nome.trim(),
    consultaSQL,
    ordenacao,
    filtros: filtrosSgu,
  };
}


/**
 * O SGU rejeita underscore em nomeFiltro e recomenda hífen, mas o mesmo nome
 * precisa ser um bind Oracle válido. Remover os separadores atende aos dois
 * contratos: data_referencia torna-se datareferencia e :datareferencia.
 */
export function nomeFiltroSgu(nome: string): string {
  return nome.trim().toLowerCase().replace(/[_-]+/g, '');
}


export function substituirBindSql(sql: string, nomeOriginal: string, nomeSgu: string): string {
  if (!nomeOriginal || nomeOriginal === nomeSgu) return sql;

  const nomeEscapado = nomeOriginal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return sql.replace(new RegExp(`:${nomeEscapado}(?![A-Za-z0-9_])`, 'g'), `:${nomeSgu}`);
}


export function clonarDefinicaoApi(api: SguApiDefinicao): SguApiDefinicao {
  return {
    nome: api.nome,
    consultaSQL: api.consultaSQL ?? '',
    ordenacao: api.ordenacao ?? '',
    filtros: Array.isArray(api.filtros) ? api.filtros.map((filtro) => ({ ...filtro })) : [],
  };
}


export function validarDefinicaoApi(api: SguApiDefinicao, nomeExibicao: string): string {
  if (!api.nome) return 'Informe o nome da API.';
  if (!api.consultaSQL) return 'Informe a consulta SQL.';

  if (!nomeExibicao.trim()) {
    return 'Informe o nome de exibição do relatório.';
  }

  const erroDelimitadores = validarDelimitadoresSql(api.consultaSQL);
  if (erroDelimitadores) return erroDelimitadores;

  const aliasDuplicado = detectarAliasDuplicadoSql(api.consultaSQL);
  if (aliasDuplicado) {
    return (
      `O alias SQL “${aliasDuplicado.alias}” foi declarado mais de uma vez no mesmo SELECT ` +
      `(linhas ${aliasDuplicado.primeiraLinha} e ${aliasDuplicado.linhaDuplicada}). ` +
      'Renomeie ou remova uma das associações duplicadas.'
    );
  }

  const colunaDuplicada = detectarAliasColunaDuplicadoSql(api.consultaSQL);
  if (colunaDuplicada) {
    return (
      `A coluna de saída “${colunaDuplicada.alias}” foi definida mais de uma vez no mesmo SELECT ` +
      `(linhas ${colunaDuplicada.primeiraLinha} e ${colunaDuplicada.linhaDuplicada}). ` +
      'Use nomes diferentes para evitar ORA-00918 durante a execução.'
    );
  }

  if (!api.filtros.length) {
    if (!/\bwhere\s+1\s*=\s*1\b/i.test(api.consultaSQL)) {
      return 'Consultas sem filtros devem conter WHERE 1 = 1.';
    }

    return '';
  }

  if (!api.consultaSQL.includes('/*FILTROS*/')) {
    return 'A consulta SQL deve conter o marcador /*FILTROS*/ quando possuir filtros.';
  }

  for (const [indice, filtro] of api.filtros.entries()) {
    if (!filtro.nomeFiltro || !/^[a-z0-9_-]+$/.test(filtro.nomeFiltro)) {
      return `Filtro ${indice + 1}: o nome deve estar em minúsculo, sem espaços e usar apenas letras, números, underscore ou hífen.`;
    }

    if (!filtro.conteudoFiltro.startsWith('and ')) {
      return `O conteúdo do filtro “${filtro.nomeFiltro}” deve começar com “and ” em minúsculo.`;
    }

    const erroBind = validarCorrespondenciaBind(filtro);
    if (erroBind) return erroBind;

    if (!/^(NUMBER|DATE|VARCHAR\(\d+\))$/i.test(filtro.tipoDadoFiltro)) {
      return `Tipo inválido no filtro “${filtro.nomeFiltro}”. Use NUMBER, DATE ou VARCHAR(tamanho).`;
    }
  }

  const nomesSgu = new Map<string, string>();
  for (const filtro of api.filtros) {
    const nomeSgu = nomeFiltroSgu(filtro.nomeFiltro);
    const nomeAnterior = nomesSgu.get(nomeSgu);
    if (!nomeSgu) {
      return `O filtro “${filtro.nomeFiltro}” precisa conter ao menos uma letra ou um número.`;
    }
    if (nomeAnterior !== undefined) {
      return (
        `Os filtros “${nomeAnterior}” e “${filtro.nomeFiltro}” resultam no mesmo nome ` +
        `aceito pelo SGU: “${nomeSgu}”. Renomeie um deles.`
      );
    }
    nomesSgu.set(nomeSgu, filtro.nomeFiltro);
  }

  return '';
}


/**
 * Detecta aliases de tabela repetidos no mesmo bloco SELECT. O escopo leva
 * em conta a profundidade dos parênteses e o SELECT mais recente, evitando
 * confundir aliases legítimos reutilizados em CTEs e subconsultas distintas.
 */
export function detectarAliasDuplicadoSql(
  sql: string,
): { alias: string; primeiraLinha: number; linhaDuplicada: number } | null {
  const mascarado = mascaraSqlSemTextosEComentarios(sql);
  const profundidades = new Int32Array(mascarado.length);
  let profundidade = 0;

  for (let indice = 0; indice < mascarado.length; indice += 1) {
    const caractere = mascarado[indice];
    if (caractere === ')') profundidade = Math.max(0, profundidade - 1);
    profundidades[indice] = profundidade;
    if (caractere === '(') profundidade += 1;
  }

  const selectsPorProfundidade = new Map<number, number[]>();
  for (const correspondencia of mascarado.matchAll(/\bselect\b/gi)) {
    const inicio = correspondencia.index ?? 0;
    const nivel = profundidades[inicio] ?? 0;
    const selects = selectsPorProfundidade.get(nivel) ?? [];
    selects.push(inicio);
    selectsPorProfundidade.set(nivel, selects);
  }

  const palavrasReservadas = new Set([
    'connect',
    'cross',
    'full',
    'group',
    'having',
    'inner',
    'join',
    'left',
    'on',
    'order',
    'outer',
    'right',
    'start',
    'union',
    'where',
  ]);
  const aliasesPorEscopo = new Map<string, Map<string, number>>();
  const regexAlias =
    /\b(?:from|join)\s+[a-z_][\w$#]*(?:\s*\.\s*[a-z_][\w$#]*){0,2}\s+(?:as\s+)?([a-z_][\w$#]*)/gi;

  for (const correspondencia of mascarado.matchAll(regexAlias)) {
    const aliasOriginal = correspondencia[1];
    const alias = aliasOriginal.toLowerCase();
    if (palavrasReservadas.has(alias)) continue;

    const inicio = correspondencia.index ?? 0;
    const nivel = profundidades[inicio] ?? 0;
    const selects = selectsPorProfundidade.get(nivel) ?? [];
    let inicioSelect = -1;
    for (const posicaoSelect of selects) {
      if (posicaoSelect >= inicio) break;
      inicioSelect = posicaoSelect;
    }
    if (inicioSelect < 0) continue;

    const chaveEscopo = `${nivel}:${inicioSelect}`;
    const aliases = aliasesPorEscopo.get(chaveEscopo) ?? new Map<string, number>();
    const primeiraPosicao = aliases.get(alias);
    const linhaAtual = mascarado.slice(0, inicio).split('\n').length;

    if (primeiraPosicao !== undefined) {
      return {
        alias: aliasOriginal.toUpperCase(),
        primeiraLinha: mascarado.slice(0, primeiraPosicao).split('\n').length,
        linhaDuplicada: linhaAtual,
      };
    }

    aliases.set(alias, inicio);
    aliasesPorEscopo.set(chaveEscopo, aliases);
  }

  return null;
}


/**
 * O SGU pagina a consulta usando um SELECT externo. Nesse cenário, duas
 * expressões com o mesmo alias de saída causam ORA-00918 mesmo que o Oracle
 * aceite executar o SELECT isolado.
 */
export function detectarAliasColunaDuplicadoSql(
  sql: string,
): { alias: string; primeiraLinha: number; linhaDuplicada: number } | null {
  const mascarado = mascaraSqlSemTextosEComentarios(sql);
  const profundidades = new Int32Array(mascarado.length);
  let profundidade = 0;

  for (let indice = 0; indice < mascarado.length; indice += 1) {
    const caractere = mascarado[indice];
    if (caractere === ')') profundidade = Math.max(0, profundidade - 1);
    profundidades[indice] = profundidade;
    if (caractere === '(') profundidade += 1;
  }

  const selectsPorProfundidade = new Map<number, number[]>();
  for (const correspondencia of mascarado.matchAll(/\bselect\b/gi)) {
    const inicio = correspondencia.index ?? 0;
    const nivel = profundidades[inicio] ?? 0;
    const selects = selectsPorProfundidade.get(nivel) ?? [];
    selects.push(inicio);
    selectsPorProfundidade.set(nivel, selects);
  }

  const aliasesPorEscopo = new Map<string, Map<string, number>>();
  for (const correspondencia of mascarado.matchAll(/\bas\s+([a-z_][\w$#]*)/gi)) {
    const inicio = correspondencia.index ?? 0;
    const nivel = profundidades[inicio] ?? 0;
    const selects = selectsPorProfundidade.get(nivel) ?? [];
    let inicioSelect = -1;
    for (const posicaoSelect of selects) {
      if (posicaoSelect >= inicio) break;
      inicioSelect = posicaoSelect;
    }
    if (inicioSelect < 0) continue;

    const aliasOriginal = correspondencia[1];
    const alias = aliasOriginal.toLowerCase();
    const chaveEscopo = `${nivel}:${inicioSelect}`;
    const aliases = aliasesPorEscopo.get(chaveEscopo) ?? new Map<string, number>();
    const primeiraPosicao = aliases.get(alias);
    const linhaAtual = mascarado.slice(0, inicio).split('\n').length;

    if (primeiraPosicao !== undefined) {
      return {
        alias: aliasOriginal.toUpperCase(),
        primeiraLinha: mascarado.slice(0, primeiraPosicao).split('\n').length,
        linhaDuplicada: linhaAtual,
      };
    }

    aliases.set(alias, inicio);
    aliasesPorEscopo.set(chaveEscopo, aliases);
  }

  return null;
}


export function validarCorrespondenciaBind(filtro: SguFiltro): string {
  const nome = filtro.nomeFiltro.trim();
  const variaveis = Array.from(
    filtro.conteudoFiltro.matchAll(/:([A-Za-z_][A-Za-z0-9_-]*)/g),
    (resultado) => resultado[1],
  );
  const variaveisUnicas = Array.from(new Set(variaveis));

  if (!variaveisUnicas.length) {
    return `O filtro “${nome}” não possui uma variável de bind. Use :${nome} no conteúdo SQL.`;
  }

  const divergentes = variaveisUnicas.filter((variavel) => variavel !== nome);

  if (divergentes.length || !variaveisUnicas.includes(nome)) {
    return `O nome do filtro “${nome}” deve ser exatamente igual à variável do conteúdo SQL. Variável encontrada: ${variaveisUnicas
      .map((variavel) => `:${variavel}`)
      .join(', ')}. Corrija para :${nome}.`;
  }

  return '';
}


export function filtroVazio(): SguFiltro {
  return {
    nomeFiltro: '',
    conteudoFiltro: 'and ',
    tipoDadoFiltro: 'VARCHAR(120)',
    mascaraFiltro: '',
    obrigatorioFiltro: 'N',
  };
}
