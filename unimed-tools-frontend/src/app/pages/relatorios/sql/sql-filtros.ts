import { mascaraSqlSemTextosEComentarios, encontrarFechamentoParentesesSql, normalizarVariaveisBindSql, localizarConsultaPrincipal } from './sql-lexico';
import { SguFiltro } from '../../../shared/models/relatorio.model';
import { FiltroFixoSqlDetectado } from './sql-importacao.model';

function gerarId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }


export function filtroDetectadoDoSql(nome: string): SguFiltro {
  const tipo = inferirTipoFiltroSql(nome);

  if (nome === 'competencia' || nome === 'compet') {
    return {
      nomeFiltro: nome,
      conteudoFiltro: `and TO_NUMBER(:${nome}) BETWEEN 190001 AND 299912`,
      tipoDadoFiltro: 'NUMBER',
      mascaraFiltro: '',
      obrigatorioFiltro: 'S',
    };
  }

  return {
    nomeFiltro: nome,
    conteudoFiltro: `and :${nome} is not null`,
    tipoDadoFiltro: tipo,
    mascaraFiltro: tipo === 'DATE' ? 'DD/MM/YYYY' : '',
    obrigatorioFiltro: 'S',
  };
}


export function inferirTipoFiltroSql(nome: string): string {
  const normalizado = nome.toLowerCase();

  if (
    /(^|_)(data|dt)(_|$)/.test(normalizado) ||
    normalizado.includes('nascimento') ||
    normalizado.includes('vencimento')
  ) {
    return 'DATE';
  }

  if (/(empresas|itens|codigos|nomes|lista|ids)$/.test(normalizado)) {
    return 'VARCHAR(4000)';
  }

  if (/(competencia|ano|mes|codigo|cod|id|numero|nro|grupo|unimed|empresa)$/.test(normalizado)) {
    return 'NUMBER';
  }

  return 'VARCHAR(4000)';
}


/**
 * Identifica comparações literais simples e as transforma em filtros
 * editáveis. Casos ambíguos permanecem no SQL para evitar alterar a consulta
 * silenciosamente.
 */
export function detectarFiltrosFixosSimples(
  sqlOriginal: string,
  nomesExistentes: Set<string>,
  assinaturasIgnoradas: Set<string>,
): {
  sql: string;
  deteccoes: FiltroFixoSqlDetectado[];
  ajustes: string[];
} {
  let sql = sqlOriginal;
  const deteccoes: FiltroFixoSqlDetectado[] = [];
  const ajustes: string[] = [];
  const sqlMascarado = mascaraSqlSemTextosEComentarios(sqlOriginal);
  const estruturaPrincipal = localizarConsultaPrincipal(sqlOriginal);
  const inicioWherePrincipal = estruturaPrincipal?.where?.fim ?? -1;
  const fimWherePrincipal = estruturaPrincipal?.limiteCondicoes ?? -1;
  const candidatos: Array<{
    inicio: number;
    fim: number;
    coluna: string;
    operador: 'IN' | '=';
    valores: string[];
    predicadoOriginal: string;
  }> = [];

  const identificador = '((?:[A-Za-z_][A-Za-z0-9_$#]*\\.)?[A-Za-z_][A-Za-z0-9_$#]*)';
  const literal = "(?:-?\\d+(?:\\.\\d+)?|'(?:''|[^'])*')";
  const regexIn = new RegExp(
    `\\b${identificador}\\s+IN\\s*\\(\\s*(${literal}(?:\\s*,\\s*${literal})*)\\s*\\)`,
    'gi',
  );
  let correspondencia: RegExpExecArray | null;

  while ((correspondencia = regexIn.exec(sqlOriginal)) !== null) {
    if (!ehTrechoSqlExecutavel(sqlMascarado, correspondencia.index, correspondencia[1])) {
      continue;
    }

    candidatos.push({
      inicio: correspondencia.index,
      fim: correspondencia.index + correspondencia[0].length,
      coluna: correspondencia[1],
      operador: 'IN',
      valores: extrairLiteraisSql(correspondencia[2]),
      predicadoOriginal: sqlOriginal.slice(
        correspondencia.index,
        correspondencia.index + correspondencia[0].length,
      ),
    });
  }

  const regexIgual = new RegExp(`\\b${identificador}\\s*=\\s*(${literal})`, 'gi');

  while ((correspondencia = regexIgual.exec(sqlOriginal)) !== null) {
    const inicio = correspondencia.index;
    const fim = correspondencia.index + correspondencia[0].length;

    if (!ehTrechoSqlExecutavel(sqlMascarado, inicio, correspondencia[1])) continue;

    if (candidatos.some((candidato) => inicio >= candidato.inicio && fim <= candidato.fim)) {
      continue;
    }

    candidatos.push({
      inicio,
      fim,
      coluna: correspondencia[1],
      operador: '=',
      valores: [correspondencia[2].trim()],
      predicadoOriginal: sqlOriginal.slice(inicio, fim),
    });
  }

  const candidatosValidos = candidatos
    .map((candidato) => ({
      ...candidato,
      nomeFiltro: nomeFiltroPorColunaSql(candidato.coluna),
    }))
    .filter(
      (candidato) =>
        Boolean(candidato.nomeFiltro) &&
        (candidato.operador === 'IN' ||
          nomeFiltroConhecidoPorColunaSql(candidato.coluna) !== null) &&
        inicioWherePrincipal >= 0 &&
        candidato.inicio >= inicioWherePrincipal &&
        candidato.fim <= fimWherePrincipal &&
        !nomesExistentes.has(candidato.nomeFiltro!),
    )
    .sort((a, b) => b.inicio - a.inicio);

  for (const candidato of candidatosValidos) {
    const nomeFiltro = candidato.nomeFiltro!;

    if (nomesExistentes.has(nomeFiltro)) continue;

    const assinatura = assinaturaFiltroFixo(
      candidato.coluna,
      candidato.operador,
      candidato.valores,
    );

    if (assinaturasIgnoradas.has(assinatura)) continue;

    const id = gerarId();
    const marcador = `/*AUTO_FILTRO_FIXO:${id}*/`;
    const filtro = criarFiltroDeCondicaoFixa(
      nomeFiltro,
      candidato.coluna,
      candidato.operador,
      candidato.valores,
    );

    sql = sql.slice(0, candidato.inicio) + `1 = 1 ${marcador}` + sql.slice(candidato.fim);

    const deteccao: FiltroFixoSqlDetectado = {
      id,
      assinatura,
      predicadoOriginal: candidato.predicadoOriginal.trim(),
      marcador,
      filtro,
    };

    deteccoes.unshift(deteccao);
    nomesExistentes.add(nomeFiltro);
    ajustes.push(
      `A condição fixa “${deteccao.predicadoOriginal}” foi transformada no filtro :${nomeFiltro}.`,
    );
  }

  return { sql, deteccoes, ajustes };
}


export function nomeFiltroPorColunaSql(colunaCompleta: string): string | null {
  const conhecido = nomeFiltroConhecidoPorColunaSql(colunaCompleta);
  if (conhecido) return conhecido;

  const coluna = colunaCompleta.split('.').pop()!.toUpperCase();

  if (coluna === 'RNUM' || coluna === 'ROWNUM' || coluna === 'RN') return null;

  const generico = coluna
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 60);

  return generico || null;
}


export function nomeFiltroConhecidoPorColunaSql(colunaCompleta: string): string | null {
  const coluna = colunaCompleta.split('.').pop()!.toUpperCase();

  if (coluna.includes('COMPET')) return 'competencia';

  if (coluna === 'GRBNF_COD') return 'grbnf_cod';

  if (
    coluna === 'GRUPO_COD' ||
    coluna === 'COD_GRUPO' ||
    coluna === 'GRPRE_COD' ||
    coluna.endsWith('_COD_GRUPO')
  ) {
    return 'grupo';
  }

  if (
    coluna === 'EMPCN_COD_PESSOA' ||
    coluna === 'COD_EMPRESA' ||
    coluna === 'EMPRESA_COD' ||
    coluna === 'COD_PESSOA_EMPRESA'
  ) {
    return 'empresas';
  }

  if (
    coluna === 'GUIA_COD_UNIMED_EXECUT' ||
    coluna === 'COD_UNIMED_EXECUT' ||
    coluna === 'UNIMED_EXECUTORA'
  ) {
    return 'unimedexecutora';
  }

  if (
    coluna === 'ITEM_COD' ||
    coluna === 'COD_TUSS' ||
    coluna === 'COD_AMB' ||
    coluna === 'COD_PROCEDIMENTO'
  ) {
    return 'itens';
  }

  return null;
}


export function criarFiltroDeCondicaoFixa(
  nomeFiltro: string,
  coluna: string,
  operador: 'IN' | '=',
  valores: string[],
): SguFiltro {
  const filtroLista = nomeFiltro === 'empresas' || nomeFiltro === 'itens' || operador === 'IN';

  if (filtroLista) {
    return {
      nomeFiltro,
      conteudoFiltro: `and instr(',' || replace(:${nomeFiltro}, ' ', '') || ',', ',' || to_char(${coluna}) || ',') > 0`,
      tipoDadoFiltro: 'VARCHAR(4000)',
      mascaraFiltro: '',
      obrigatorioFiltro: 'S',
    };
  }

  return {
    nomeFiltro,
    conteudoFiltro: `and ${coluna} = :${nomeFiltro}`,
    tipoDadoFiltro: valores.every((valor) => ehLiteralTextoSql(valor))
      ? 'VARCHAR(4000)'
      : 'NUMBER',
    mascaraFiltro: '',
    obrigatorioFiltro: 'S',
  };
}


export function assinaturaFiltroFixo(coluna: string, operador: string, valores: string[]): string {
  return `${coluna.toUpperCase()}|${operador.toUpperCase()}|${valores.join(',')}`;
}


export function extrairLiteraisSql(lista: string): string[] {
  return lista.match(/'(?:''|[^'])*'|-?\d+(?:\.\d+)?/g)?.map((valor) => valor.trim()) ?? [];
}


export function ehLiteralTextoSql(valor: string): boolean {
  return /^'(?:''|[^'])*'$/.test(valor.trim());
}


export function ehTrechoSqlExecutavel(sqlMascarado: string, inicio: number, coluna: string): boolean {
  return sqlMascarado.slice(inicio, inicio + coluna.length).trim().length > 0;
}


export function converterParametrosFixosSql(sqlOriginal: string): {
  sql: string;
  ajustes: string[];
} {
  let sql = sqlOriginal;
  const ajustes: string[] = [];
  const regexCte = /\b(PARAM|PARAMETROS)\s+AS\s*\(/gi;
  let correspondencia: RegExpExecArray | null;

  while ((correspondencia = regexCte.exec(sql)) !== null) {
    const inicioAbertura = correspondencia.index + correspondencia[0].lastIndexOf('(');
    const fimAbertura = encontrarFechamentoParentesesSql(sql, inicioAbertura);

    if (fimAbertura < 0) continue;

    const blocoOriginal = sql.slice(inicioAbertura + 1, fimAbertura);

    if (/:competencia\b/i.test(blocoOriginal)) {
      continue;
    }

    const indicaCompetencia =
      /\bAS\s+COMPET(?:ENCIA)?\b/i.test(blocoOriginal) ||
      /filtros?\s*:?\s*compet[eê]ncia/i.test(blocoOriginal);

    if (!indicaCompetencia) continue;

    const valoresEncontrados = new Set<string>();
    let blocoAjustado = blocoOriginal;

    blocoAjustado = blocoAjustado.replace(
      /TO_DATE\s*\(\s*'(\d{6})'\s*,\s*'YYYYMM'\s*\)/gi,
      (_trecho, valor: string) => {
        valoresEncontrados.add(valor);
        return "TO_DATE(TO_CHAR(:competencia), 'YYYYMM')";
      },
    );

    blocoAjustado = blocoAjustado.replace(
      /'(\d{6})'\s+AS\s+(COMPET(?:ENCIA)?)\b/gi,
      (_trecho, valor: string, alias: string) => {
        valoresEncontrados.add(valor);
        return `TO_CHAR(:competencia) AS ${alias}`;
      },
    );

    blocoAjustado = blocoAjustado.replace(
      /\b(\d{6})\s+AS\s+(COMPET(?:ENCIA)?)\b/gi,
      (_trecho, valor: string, alias: string) => {
        valoresEncontrados.add(valor);
        return `TO_NUMBER(:competencia) AS ${alias}`;
      },
    );

    if (blocoAjustado === blocoOriginal) continue;

    sql = sql.slice(0, inicioAbertura + 1) + blocoAjustado + sql.slice(fimAbertura);

    const valores = Array.from(valoresEncontrados);
    const cteNome = correspondencia[1].toUpperCase();

    ajustes.push(
      valores.length
        ? `A competência fixa ${valores.join(
            ', ',
          )} da CTE ${cteNome} foi transformada no filtro :competencia.`
        : `A competência fixa da CTE ${cteNome} foi transformada no filtro :competencia.`,
    );

    break;
  }

  return { sql, ajustes };
}


/**
 * Converte filtros que precisam permanecer dentro da CTE porque utilizam
 * aliases locais. Datas repetidas só compartilham um bind quando todas as
 * ocorrências literais da CTE possuem o mesmo valor.
 */
export function converterFiltrosFixosCteSql(sqlOriginal: string): {
  sql: string;
  ajustes: string[];
} {
  let sql = sqlOriginal;
  const ajustes: string[] = [];
  const nomesBindReservados = new Set(normalizarVariaveisBindSql(sqlOriginal).variaveis);
  const bindsPorFiltro = new Map<string, string>();

  const bindPara = (nomeBase: string): string => {
    const existente = bindsPorFiltro.get(nomeBase);
    if (existente) return existente;

    let nome = nomeBase;
    let sufixo = 2;

    while (nomesBindReservados.has(nome)) {
      nome = `${nomeBase}_${sufixo}`;
      sufixo += 1;
    }

    nomesBindReservados.add(nome);
    bindsPorFiltro.set(nomeBase, nome);
    return nome;
  };

  const sqlMascarado = mascaraSqlSemTextosEComentarios(sqlOriginal);
  const regexCte =
    /\bWITH\s+([A-Za-z_][A-Za-z0-9_$#]*)\s+AS\s*\(|,\s*([A-Za-z_][A-Za-z0-9_$#]*)\s+AS\s*\(/gi;
  const blocos: Array<{
    nome: string;
    inicioConteudo: number;
    fimConteudo: number;
  }> = [];
  let correspondencia: RegExpExecArray | null;

  while ((correspondencia = regexCte.exec(sqlMascarado)) !== null) {
    const inicioAbertura = correspondencia.index + correspondencia[0].lastIndexOf('(');
    const fimAbertura = encontrarFechamentoParentesesSql(sqlOriginal, inicioAbertura);

    if (fimAbertura < 0) continue;

    blocos.push({
      nome: (correspondencia[1] ?? correspondencia[2]).toUpperCase(),
      inicioConteudo: inicioAbertura + 1,
      fimConteudo: fimAbertura,
    });
  }

  for (const bloco of blocos.sort((a, b) => b.inicioConteudo - a.inicioConteudo)) {
    const conteudoOriginal = sql.slice(bloco.inicioConteudo, bloco.fimConteudo);
    let conteudoAjustado = conteudoOriginal;

    const regexData = /TO_DATE\s*\(\s*'(\d{2}\/\d{2}\/\d{4})'\s*,\s*'DD\/MM\/YYYY'\s*\)/gi;
    const datas = Array.from(conteudoOriginal.matchAll(regexData), (item) => item[1]);
    const datasUnicas = new Set(datas);

    if (datas.length > 1 && datasUnicas.size === 1) {
      const dataOriginal = datas[0];
      const nomeBindData = bindPara('data_referencia');

      conteudoAjustado = conteudoAjustado.replace(regexData, `:${nomeBindData}`);
      ajustes.push(
        `As ${datas.length} ocorrências da data fixa ${dataOriginal} na CTE ${bloco.nome} foram vinculadas ao filtro :${nomeBindData}.`,
      );
    }

    const regexListaVazia =
      /\b((?:[A-Za-z_][A-Za-z0-9_$#]*\.)?[A-Za-z_][A-Za-z0-9_$#]*)\s+IN\s*\(\s*\)/gi;

    conteudoAjustado = conteudoAjustado.replace(
      regexListaVazia,
      (predicadoOriginal: string, coluna: string) => {
        const nomeFiltro = nomeFiltroPorColunaSql(coluna);

        if (nomeFiltro !== 'empresas' && nomeFiltro !== 'itens') {
          return predicadoOriginal;
        }

        const nomeBind = bindPara(nomeFiltro);
        ajustes.push(
          `A lista vazia “${predicadoOriginal.trim()}” da CTE ${bloco.nome} foi transformada no filtro :${nomeBind}.`,
        );

        return `instr(',' || replace(:${nomeBind}, ' ', '') || ',', ',' || to_char(${coluna}) || ',') > 0`;
      },
    );

    const comparacoesFixas = converterComparacoesFixasWhereCte(
      conteudoAjustado,
      bloco.nome,
      bindPara,
    );
    conteudoAjustado = comparacoesFixas.sql;
    ajustes.push(...comparacoesFixas.ajustes);

    if (conteudoAjustado !== conteudoOriginal) {
      sql = sql.slice(0, bloco.inicioConteudo) + conteudoAjustado + sql.slice(bloco.fimConteudo);
    }
  }

  return { sql, ajustes };
}


/**
 * Condições literais de uma CTE precisam manter o predicado no próprio
 * bloco, pois seus aliases não existem no WHERE externo onde o SGU injeta
 * filtros. Somente comparações simples no WHERE principal da CTE são
 * convertidas; expressões, subconsultas e operadores ambíguos permanecem
 * intactos para revisão manual.
 */
export function converterComparacoesFixasWhereCte(
  conteudoOriginal: string,
  nomeCte: string,
  bindPara: (nomeBase: string) => string,
): { sql: string; ajustes: string[] } {
  const estrutura = localizarConsultaPrincipal(conteudoOriginal);
  if (!estrutura?.where) return { sql: conteudoOriginal, ajustes: [] };

  const inicioWhere = estrutura.where.fim;
  const fimWhere = estrutura.limiteCondicoes;
  const sqlMascarado = mascaraSqlSemTextosEComentarios(conteudoOriginal);
  const identificador = '((?:[A-Za-z_][A-Za-z0-9_$#]*\\.)?[A-Za-z_][A-Za-z0-9_$#]*)';
  const literal = "(?:-?\\d+(?:\\.\\d+)?|'(?:''|[^'])*')";
  const candidatos: Array<{
    inicio: number;
    fim: number;
    coluna: string;
    operador: 'IN' | '=';
    valores: string[];
    original: string;
  }> = [];

  const adicionar = (match: RegExpExecArray, operador: 'IN' | '='): void => {
    const inicio = match.index;
    const fim = inicio + match[0].length;
    if (
      inicio < inicioWhere ||
      fim > fimWhere ||
      !ehTrechoSqlExecutavel(sqlMascarado, inicio, match[1])
    ) {
      return;
    }

    candidatos.push({
      inicio,
      fim,
      coluna: match[1],
      operador,
      valores: extrairLiteraisSql(match[2]),
      original: conteudoOriginal.slice(inicio, fim).trim(),
    });
  };

  let match: RegExpExecArray | null;
  const regexIn = new RegExp(
    `\\b${identificador}\\s+IN\\s*\\(\\s*(${literal}(?:\\s*,\\s*${literal})*)\\s*\\)`,
    'gi',
  );
  while ((match = regexIn.exec(conteudoOriginal)) !== null) adicionar(match, 'IN');

  const regexIgual = new RegExp(`\\b${identificador}\\s*=\\s*(${literal})`, 'gi');
  while ((match = regexIgual.exec(conteudoOriginal)) !== null) {
    const fim = match.index + match[0].length;
    if (candidatos.some((item) => match!.index >= item.inicio && fim <= item.fim)) continue;
    adicionar(match, '=');
  }

  let sql = conteudoOriginal;
  const ajustes: string[] = [];
  const nomesUsados = new Set<string>();

  for (const candidato of candidatos.sort((a, b) => b.inicio - a.inicio)) {
    if (
      candidato.operador === '=' &&
      nomeFiltroConhecidoPorColunaSql(candidato.coluna) === null
    ) {
      continue;
    }

    let nomeBase = nomeFiltroPorColunaSql(candidato.coluna);
    if (!nomeBase || !candidato.valores.length) continue;

    if (candidato.operador === 'IN' && candidato.valores.length > 1) {
      nomeBase = `${nomeBase}_lista`;
    }

    let nomeUnico = nomeBase;
    let sufixo = 2;
    while (nomesUsados.has(nomeUnico)) nomeUnico = `${nomeBase}_${sufixo++}`;
    nomesUsados.add(nomeUnico);

    const nomeBind = bindPara(nomeUnico);
    const predicado =
      candidato.operador === 'IN' && candidato.valores.length > 1
        ? `instr(',' || replace(:${nomeBind}, ' ', '') || ',', ',' || to_char(${candidato.coluna}) || ',') > 0`
        : `${candidato.coluna} = :${nomeBind}`;

    sql = sql.slice(0, candidato.inicio) + predicado + sql.slice(candidato.fim);
    ajustes.unshift(
      `A condição fixa “${candidato.original}” da CTE ${nomeCte} foi transformada no filtro :${nomeBind}.`,
    );
  }

  return { sql, ajustes };
}
