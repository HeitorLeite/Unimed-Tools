import { removerComentariosFinaisSql, validarDelimitadoresSql, localizarConsultaPrincipal, removerComentariosSql, inserirClausulaSql } from './sql-lexico';


/**
 * Garante que o marcador aceito pelo SGU esteja no WHERE da consulta
 * principal. Subconsultas são deliberadamente ignoradas nesta decisão.
 */
export function ajustarEstruturaSqlImportado(
  sqlOriginal: string,
  possuiFiltros: boolean,
): { sql: string; ajustes: string[] } {
  const ajustes: string[] = [];
  const semComentariosFinais = removerComentariosFinaisSql(
    sqlOriginal.replace(/^\uFEFF/, '').trim(),
  );
  let sql = semComentariosFinais.sql.replace(/;\s*$/, '');

  if (semComentariosFinais.comentariosRemovidos) {
    ajustes.push('Comentários e anotações após o fim da consulta foram removidos.');
  }

  if (!sql) return { sql, ajustes };
  if (validarDelimitadoresSql(sql)) return { sql, ajustes };

  let estrutura = localizarConsultaPrincipal(sql);
  if (!estrutura) return { sql, ajustes };

  if (possuiFiltros) {
    const marcador = '/*FILTROS*/';
    const posicaoMarcador = sql.indexOf(marcador);

    if (posicaoMarcador >= estrutura.select.inicio && posicaoMarcador < estrutura.fimRamo) {
      sql = sql.slice(0, posicaoMarcador) + sql.slice(posicaoMarcador + marcador.length);
      ajustes.push('O marcador /*FILTROS*/ foi reposicionado no final do WHERE principal.');
      estrutura = localizarConsultaPrincipal(sql);

      if (!estrutura) return { sql, ajustes };
    }
  }

  if (estrutura.where) {
    const trechoCondicoes = sql.slice(estrutura.where.fim, estrutura.limiteCondicoes);
    const trechoAnalisavel = removerComentariosSql(
      trechoCondicoes.replace(/\/\*FILTROS\*\//g, ''),
    );

    if (!/\b1\s*=\s*1\b/i.test(trechoAnalisavel)) {
      const possuiCondicao = trechoAnalisavel.trim().length > 0;
      const complemento = possuiCondicao ? ' 1 = 1\n  AND' : ' 1 = 1';

      sql = sql.slice(0, estrutura.where.fim) + complemento + sql.slice(estrutura.where.fim);
      ajustes.push('Foi adicionado 1 = 1 ao WHERE principal.');
      estrutura = localizarConsultaPrincipal(sql);

      if (!estrutura) return { sql, ajustes };
    }
  } else {
    sql = inserirClausulaSql(sql, estrutura.limiteCondicoes, 'WHERE 1 = 1');
    ajustes.push('Foi adicionada a cláusula WHERE 1 = 1.');
    estrutura = localizarConsultaPrincipal(sql);

    if (!estrutura) return { sql, ajustes };
  }

  if (possuiFiltros && !sql.includes('/*FILTROS*/')) {
    sql = inserirClausulaSql(sql, estrutura.limiteCondicoes, '  /*FILTROS*/');
    ajustes.push('Foi adicionado o marcador /*FILTROS*/.');
  }

  if (estrutura.operadorConjunto) {
    ajustes.push(
      'A consulta usa UNION, MINUS ou INTERSECT; revise se o marcador ficou no bloco correto.',
    );
  }

  return {
    sql: sql.trim().replace(/;\s*$/, ''),
    ajustes: Array.from(new Set(ajustes)),
  };
}
