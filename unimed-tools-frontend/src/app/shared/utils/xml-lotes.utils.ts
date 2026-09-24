import { RenumeracaoXml } from '../models/xml.models';

interface OcorrenciaLote {
  inicio: number;
  fim: number;
  numero: string;
  chave: string;
}

function encontrarLotes(content: string): OcorrenciaLote[] {
  // Comentários, CDATA e instruções não são elementos do lote. Os offsets
  // permitem trocar apenas os dígitos, preservando o restante do XML original.
  const pattern = /<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<\?[\s\S]*?\?>|(<ans:numeroLote(?:\s[^<>]*?)?>)(\s*)([0-9]+)(\s*)(<\/ans:numeroLote\s*>)/g;
  const ocorrencias: OcorrenciaLote[] = [];
  for (const match of content.matchAll(pattern)) {
    if (!match[1]) continue;
    const numero = match[3];
    const inicio = match.index + match[1].length + match[2].length;
    ocorrencias.push({ inicio, fim: inicio + numero.length, numero, chave: BigInt(numero).toString() });
  }
  return ocorrencias;
}

/** Mantém o primeiro arquivo de cada lote e reserva todos os números originais. */
export function corrigirLotesDuplicados(conteudos: readonly string[]): {
  correctedContent: string;
  lotesRenumerados: RenumeracaoXml[];
}[] {
  const ocorrenciasPorArquivo = conteudos.map(encontrarLotes);
  const reservados = new Set(ocorrenciasPorArquivo.flatMap((itens) => itens.map((item) => item.chave)));
  const encontrados = new Set<string>();
  const proximos = new Map<string, bigint>();

  return conteudos.map((content, index) => {
    const substituicoes = new Map<string, string>();
    const alteracoes = new Map<string, string>();
    for (const { chave } of ocorrenciasPorArquivo[index]) {
      if (substituicoes.has(chave)) continue;
      if (!encontrados.has(chave)) {
        encontrados.add(chave);
        substituicoes.set(chave, chave);
        continue;
      }

      // BigInt evita arredondar identificadores acima do limite seguro de Number.
      let candidato = proximos.get(chave) ?? BigInt(chave) + 1n;
      while (reservados.has(candidato.toString())) candidato += 1n;
      const novo = candidato.toString();
      reservados.add(novo);
      proximos.set(chave, candidato + 1n);
      substituicoes.set(chave, novo);
    }

    let cursor = 0;
    const partes: string[] = [];
    for (const ocorrencia of ocorrenciasPorArquivo[index]) {
      const novoNumero = substituicoes.get(ocorrencia.chave)!;
      if (novoNumero === ocorrencia.chave) continue;
      const novo = novoNumero.padStart(ocorrencia.numero.length, '0');
      partes.push(content.slice(cursor, ocorrencia.inicio), novo);
      cursor = ocorrencia.fim;
      alteracoes.set(ocorrencia.numero, novo);
    }
    partes.push(content.slice(cursor));
    return {
      correctedContent: partes.join(''),
      lotesRenumerados: [...alteracoes].map(([original, novo]) => ({ original, novo })),
    };
  });
}
