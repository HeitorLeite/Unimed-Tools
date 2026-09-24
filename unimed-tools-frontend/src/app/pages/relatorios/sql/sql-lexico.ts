import { TokenSqlNivelZero, EstruturaConsultaPrincipal } from './sql-importacao.model';


/**
 * Arquivos usados pela equipe podem manter consultas auxiliares ou notas
 * depois do SELECT principal. O SGU aceita uma única instrução e rejeita o
 * terminador `;`, por isso somente o primeiro terminador fora de textos e
 * comentários delimita o SQL importado.
 */
export function extrairPrimeiraInstrucaoSql(sqlOriginal: string): {
  sql: string;
  conteudoPosteriorIgnorado: boolean;
} {
  let indice = 0;
  let estado: 'normal' | 'texto' | 'identificador' | 'linha' | 'bloco' = 'normal';

  while (indice < sqlOriginal.length) {
    const atual = sqlOriginal[indice];
    const proximo = sqlOriginal[indice + 1] ?? '';

    if (estado === 'texto') {
      if (atual === "'" && proximo === "'") {
        indice += 2;
        continue;
      }
      if (atual === "'") estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'identificador') {
      if (atual === '"' && proximo === '"') {
        indice += 2;
        continue;
      }
      if (atual === '"') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'linha') {
      if (atual === '\n') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'bloco') {
      if (atual === '*' && proximo === '/') {
        estado = 'normal';
        indice += 2;
        continue;
      }
      indice += 1;
      continue;
    }

    if (atual === "'") estado = 'texto';
    else if (atual === '"') estado = 'identificador';
    else if (atual === '-' && proximo === '-') {
      estado = 'linha';
      indice += 2;
      continue;
    } else if (atual === '/' && proximo === '*') {
      estado = 'bloco';
      indice += 2;
      continue;
    } else if (atual === ';') {
      const trechoPrincipal = sqlOriginal.slice(0, indice).trim();
      const semComentariosFinais = removerComentariosFinaisSql(trechoPrincipal);
      const posterior = sqlOriginal.slice(indice + 1).trim();
      return {
        sql: semComentariosFinais.sql,
        conteudoPosteriorIgnorado:
          posterior.length > 0 || semComentariosFinais.comentariosRemovidos,
      };
    }

    indice += 1;
  }

  const semComentariosFinais = removerComentariosFinaisSql(sqlOriginal.trim());

  return {
    sql: semComentariosFinais.sql,
    conteudoPosteriorIgnorado: semComentariosFinais.comentariosRemovidos,
  };
}


/**
 * Comentários após o último token executável são anotações do arquivo,
 * não parte da consulta. Removê-los evita que o SGU reposicione o marcador
 * de filtros dentro de um bloco comentado ao persistir a definição.
 */
export function removerComentariosFinaisSql(sqlOriginal: string): {
  sql: string;
  comentariosRemovidos: boolean;
} {
  let indice = 0;
  let fimExecutavel = 0;
  let estado: 'normal' | 'texto' | 'identificador' | 'linha' | 'bloco' = 'normal';

  while (indice < sqlOriginal.length) {
    const atual = sqlOriginal[indice];
    const proximo = sqlOriginal[indice + 1] ?? '';

    if (estado === 'texto') {
      fimExecutavel = indice + 1;
      if (atual === "'" && proximo === "'") {
        fimExecutavel = indice + 2;
        indice += 2;
        continue;
      }
      if (atual === "'") estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'identificador') {
      fimExecutavel = indice + 1;
      if (atual === '"' && proximo === '"') {
        fimExecutavel = indice + 2;
        indice += 2;
        continue;
      }
      if (atual === '"') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'linha') {
      if (atual === '\n') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'bloco') {
      if (atual === '*' && proximo === '/') {
        estado = 'normal';
        indice += 2;
        continue;
      }
      indice += 1;
      continue;
    }

    if (atual === '-' && proximo === '-') {
      estado = 'linha';
      indice += 2;
      continue;
    }

    if (atual === '/' && proximo === '*') {
      estado = 'bloco';
      indice += 2;
      continue;
    }

    if (atual === "'") estado = 'texto';
    else if (atual === '"') estado = 'identificador';

    if (!/\s/.test(atual)) fimExecutavel = indice + 1;
    indice += 1;
  }

  // Um delimitador aberto deve permanecer no texto para a validação acusar
  // o erro, em vez de ser silenciosamente descartado como comentário final.
  if (estado === 'bloco' || estado === 'texto' || estado === 'identificador') {
    return { sql: sqlOriginal.trim(), comentariosRemovidos: false };
  }

  const sqlSemEspacosFinais = sqlOriginal.trimEnd();
  const comentariosRemovidos = fimExecutavel < sqlSemEspacosFinais.length;

  return {
    sql: comentariosRemovidos
      ? sqlOriginal.slice(0, fimExecutavel).trimEnd()
      : sqlSemEspacosFinais,
    comentariosRemovidos,
  };
}


export function validarDelimitadoresSql(sql: string): string {
  let indice = 0;
  let linha = 1;
  let linhaAbertura = 1;
  let estado: 'normal' | 'texto' | 'identificador' | 'linha' | 'bloco' = 'normal';

  while (indice < sql.length) {
    const atual = sql[indice];
    const proximo = sql[indice + 1] ?? '';

    if (estado === 'texto') {
      if (atual === "'" && proximo === "'") {
        indice += 2;
        continue;
      }
      if (atual === "'") estado = 'normal';
      if (atual === '\n') linha += 1;
      indice += 1;
      continue;
    }

    if (estado === 'identificador') {
      if (atual === '"' && proximo === '"') {
        indice += 2;
        continue;
      }
      if (atual === '"') estado = 'normal';
      if (atual === '\n') linha += 1;
      indice += 1;
      continue;
    }

    if (estado === 'linha') {
      if (atual === '\n') {
        linha += 1;
        estado = 'normal';
      }
      indice += 1;
      continue;
    }

    if (estado === 'bloco') {
      if (atual === '*' && proximo === '/') {
        estado = 'normal';
        indice += 2;
        continue;
      }
      if (atual === '\n') linha += 1;
      indice += 1;
      continue;
    }

    if (atual === '-' && proximo === '-') {
      estado = 'linha';
      indice += 2;
      continue;
    }

    if (atual === '/' && proximo === '*') {
      linhaAbertura = linha;
      estado = 'bloco';
      indice += 2;
      continue;
    }

    if (atual === '*' && proximo === '/') {
      return `A consulta SQL possui um fechamento de comentário */ sem abertura na linha ${linha}.`;
    }

    if (atual === "'") {
      linhaAbertura = linha;
      estado = 'texto';
    } else if (atual === '"') {
      linhaAbertura = linha;
      estado = 'identificador';
    } else if (atual === '\n') {
      linha += 1;
    }

    indice += 1;
  }

  if (estado === 'bloco') {
    return `A consulta SQL possui um comentário /* sem fechamento, iniciado na linha ${linhaAbertura}.`;
  }

  if (estado === 'texto') {
    return `A consulta SQL possui um texto entre aspas simples sem fechamento, iniciado na linha ${linhaAbertura}.`;
  }

  if (estado === 'identificador') {
    return `A consulta SQL possui um identificador entre aspas duplas sem fechamento, iniciado na linha ${linhaAbertura}.`;
  }

  return '';
}


/**
 * Produz uma máscara com o mesmo comprimento do SQL original. Preservar as
 * posições permite localizar tokens sem confundir palavras em strings ou
 * comentários com cláusulas reais da consulta.
 */
export function mascaraSqlSemTextosEComentarios(sql: string): string {
  let resultado = '';
  let indice = 0;
  let estado: 'normal' | 'texto' | 'identificador' | 'linha' | 'bloco' = 'normal';

  while (indice < sql.length) {
    const atual = sql[indice];
    const proximo = sql[indice + 1] ?? '';

    if (estado === 'texto') {
      if (atual === "'" && proximo === "'") {
        resultado += '  ';
        indice += 2;
        continue;
      }

      resultado += atual === '\n' ? '\n' : ' ';
      if (atual === "'") estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'identificador') {
      resultado += atual === '\n' ? '\n' : ' ';
      if (atual === '"') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'linha') {
      resultado += atual === '\n' ? '\n' : ' ';
      if (atual === '\n') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'bloco') {
      if (atual === '*' && proximo === '/') {
        resultado += '  ';
        indice += 2;
        estado = 'normal';
        continue;
      }

      resultado += atual === '\n' ? '\n' : ' ';
      indice += 1;
      continue;
    }

    if (atual === "'") {
      resultado += ' ';
      estado = 'texto';
      indice += 1;
      continue;
    }

    if (atual === '"') {
      resultado += ' ';
      estado = 'identificador';
      indice += 1;
      continue;
    }

    if (atual === '-' && proximo === '-') {
      resultado += '  ';
      indice += 2;
      estado = 'linha';
      continue;
    }

    if (atual === '/' && proximo === '*') {
      resultado += '  ';
      indice += 2;
      estado = 'bloco';
      continue;
    }

    resultado += atual;
    indice += 1;
  }

  return resultado;
}


// ── Análise estrutural e ajustes seguros do SQL importado ──────────────────

export function encontrarFechamentoParentesesSql(sql: string, indiceAbertura: number): number {
  let profundidade = 0;
  let indice = indiceAbertura;
  let estado: 'normal' | 'texto' | 'identificador' | 'linha' | 'bloco' = 'normal';

  while (indice < sql.length) {
    const atual = sql[indice];
    const proximo = sql[indice + 1] ?? '';

    if (estado === 'texto') {
      if (atual === "'" && proximo === "'") {
        indice += 2;
        continue;
      }

      if (atual === "'") estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'identificador') {
      if (atual === '"' && proximo === '"') {
        indice += 2;
        continue;
      }

      if (atual === '"') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'linha') {
      if (atual === '\n') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'bloco') {
      if (atual === '*' && proximo === '/') {
        indice += 2;
        estado = 'normal';
        continue;
      }

      indice += 1;
      continue;
    }

    if (atual === "'") {
      estado = 'texto';
      indice += 1;
      continue;
    }

    if (atual === '"') {
      estado = 'identificador';
      indice += 1;
      continue;
    }

    if (atual === '-' && proximo === '-') {
      estado = 'linha';
      indice += 2;
      continue;
    }

    if (atual === '/' && proximo === '*') {
      estado = 'bloco';
      indice += 2;
      continue;
    }

    if (atual === '(') {
      profundidade += 1;
    } else if (atual === ')') {
      profundidade -= 1;

      if (profundidade === 0) {
        return indice;
      }
    }

    indice += 1;
  }

  return -1;
}


export function normalizarVariaveisBindSql(sql: string): {
  sql: string;
  variaveis: string[];
} {
  let resultado = '';
  let indice = 0;
  let estado: 'normal' | 'texto' | 'linha' | 'bloco' = 'normal';
  const variaveis: string[] = [];

  while (indice < sql.length) {
    const atual = sql[indice];
    const proximo = sql[indice + 1] ?? '';

    if (estado === 'texto') {
      resultado += atual;

      if (atual === "'" && proximo === "'") {
        resultado += proximo;
        indice += 2;
        continue;
      }

      if (atual === "'") estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'linha') {
      resultado += atual;
      if (atual === '\n') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'bloco') {
      resultado += atual;
      if (atual === '*' && proximo === '/') {
        resultado += proximo;
        indice += 2;
        estado = 'normal';
        continue;
      }
      indice += 1;
      continue;
    }

    if (atual === "'") {
      resultado += atual;
      estado = 'texto';
      indice += 1;
      continue;
    }

    if (atual === '-' && proximo === '-') {
      resultado += atual + proximo;
      indice += 2;
      estado = 'linha';
      continue;
    }

    if (atual === '/' && proximo === '*') {
      resultado += atual + proximo;
      indice += 2;
      estado = 'bloco';
      continue;
    }

    if (atual === ':' && /[A-Za-z_]/.test(proximo)) {
      let fim = indice + 2;
      while (fim < sql.length && /[A-Za-z0-9_]/.test(sql[fim])) {
        fim += 1;
      }

      const nome = sql.slice(indice + 1, fim).toLowerCase();
      resultado += `:${nome}`;

      if (!variaveis.includes(nome)) {
        variaveis.push(nome);
      }

      indice = fim;
      continue;
    }

    resultado += atual;
    indice += 1;
  }

  return { sql: resultado, variaveis };
}


export function localizarConsultaPrincipal(sql: string): EstruturaConsultaPrincipal | null {
  const tokens = tokensSqlNivelZero(sql);
  const indiceSelect = tokens.findIndex((token) => token.palavra === 'SELECT');

  if (indiceSelect < 0) return null;

  const select = tokens[indiceSelect];
  const tokensDepoisSelect = tokens.slice(indiceSelect + 1);
  const operadorConjunto = tokensDepoisSelect.find((token) =>
    ['UNION', 'MINUS', 'INTERSECT'].includes(token.palavra),
  );
  const fimRamo = operadorConjunto?.inicio ?? sql.length;

  const tokensDoRamo = tokensDepoisSelect.filter((token) => token.inicio < fimRamo);
  const where = tokensDoRamo.find((token) => token.palavra === 'WHERE');

  const inicioBuscaLimite = where?.fim ?? select.fim;
  const palavrasLimite = new Set([
    'GROUP',
    'HAVING',
    'ORDER',
    'CONNECT',
    'START',
    'MODEL',
    'QUALIFY',
    'FETCH',
    'OFFSET',
    'FOR',
  ]);

  const proximaClausula = tokensDoRamo.find(
    (token) => token.inicio >= inicioBuscaLimite && palavrasLimite.has(token.palavra),
  );

  return {
    select,
    where,
    limiteCondicoes: proximaClausula?.inicio ?? fimRamo,
    fimRamo,
    operadorConjunto,
  };
}


/**
 * Tokeniza apenas o nível externo da consulta, preservando posições do texto
 * original. Isso evita interpretar WHERE e ORDER BY de subconsultas como se
 * pertencessem à consulta principal.
 */
export function tokensSqlNivelZero(sql: string): TokenSqlNivelZero[] {
  const tokens: TokenSqlNivelZero[] = [];
  let indice = 0;
  let profundidade = 0;
  let estado: 'normal' | 'texto' | 'identificador' | 'linha' | 'bloco' = 'normal';

  while (indice < sql.length) {
    const atual = sql[indice];
    const proximo = sql[indice + 1] ?? '';

    if (estado === 'texto') {
      if (atual === "'" && proximo === "'") {
        indice += 2;
        continue;
      }

      if (atual === "'") estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'identificador') {
      if (atual === '"' && proximo === '"') {
        indice += 2;
        continue;
      }

      if (atual === '"') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'linha') {
      if (atual === '\n') estado = 'normal';
      indice += 1;
      continue;
    }

    if (estado === 'bloco') {
      if (atual === '*' && proximo === '/') {
        indice += 2;
        estado = 'normal';
        continue;
      }

      indice += 1;
      continue;
    }

    if (atual === "'") {
      estado = 'texto';
      indice += 1;
      continue;
    }

    if (atual === '"') {
      estado = 'identificador';
      indice += 1;
      continue;
    }

    if (atual === '-' && proximo === '-') {
      estado = 'linha';
      indice += 2;
      continue;
    }

    if (atual === '/' && proximo === '*') {
      estado = 'bloco';
      indice += 2;
      continue;
    }

    if (atual === '(') {
      profundidade += 1;
      indice += 1;
      continue;
    }

    if (atual === ')') {
      profundidade = Math.max(0, profundidade - 1);
      indice += 1;
      continue;
    }

    if (profundidade === 0 && /[A-Za-z_]/.test(atual)) {
      let fim = indice + 1;

      while (fim < sql.length && /[A-Za-z0-9_$#]/.test(sql[fim])) {
        fim += 1;
      }

      tokens.push({
        palavra: sql.slice(indice, fim).toUpperCase(),
        inicio: indice,
        fim,
      });
      indice = fim;
      continue;
    }

    indice += 1;
  }

  return tokens;
}


export function removerComentariosSql(sql: string): string {
  return sql.replace(/--[^\r\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');
}


export function inserirClausulaSql(sql: string, posicao: number, clausula: string): string {
  const antes = sql.slice(0, posicao).replace(/[ \t]+$/g, '');
  const depois = sql.slice(posicao).replace(/^[ \t]+/g, '');
  const quebraAntes = antes.endsWith('\n') ? '' : '\n';
  const quebraDepois = depois ? (depois.startsWith('\n') ? '' : '\n') : '';

  return `${antes}${quebraAntes}${clausula}${quebraDepois}${depois}`;
}
