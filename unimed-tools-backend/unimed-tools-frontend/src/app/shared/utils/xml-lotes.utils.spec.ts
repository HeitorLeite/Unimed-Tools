import { corrigirLotesDuplicados } from './xml-lotes.utils';

describe('corrigirLotesDuplicados', () => {
  const xml = (numeroLote: string) =>
    `<ans:mensagem><ans:numeroLote>${numeroLote}</ans:numeroLote></ans:mensagem>`;

  it('mantem o primeiro lote e renumera os duplicados em sequencia', () => {
    const resultados = corrigirLotesDuplicados([xml('597'), xml('597'), xml('597')]);

    expect(resultados.map((resultado) => resultado.correctedContent)).toEqual([
      xml('597'),
      xml('598'),
      xml('599'),
    ]);
    expect(resultados.map((resultado) => resultado.lotesRenumerados)).toEqual([
      [],
      [{ original: '597', novo: '598' }],
      [{ original: '597', novo: '599' }],
    ]);
  });

  it('nao reutiliza um numero que ja existe em outro arquivo do mesmo lote', () => {
    const resultados = corrigirLotesDuplicados([xml('597'), xml('597'), xml('598')]);

    expect(resultados.map((resultado) => resultado.correctedContent)).toEqual([
      xml('597'),
      xml('599'),
      xml('598'),
    ]);
  });

  it('preserva arquivos sem duplicidade', () => {
    const resultados = corrigirLotesDuplicados([xml('597'), xml('598'), xml('599')]);

    expect(resultados.map((resultado) => resultado.correctedContent)).toEqual([
      xml('597'),
      xml('598'),
      xml('599'),
    ]);
    expect(resultados.every((resultado) => resultado.lotesRenumerados.length === 0)).toBe(true);
  });

  it('ignora numeroLote presente apenas em comentario ou CDATA', () => {
    const conteudos = [
      `<!-- <ans:numeroLote>597</ans:numeroLote> -->${xml('600')}`,
      `<![CDATA[<ans:numeroLote>597</ans:numeroLote>]]>${xml('600')}`,
    ];

    const resultados = corrigirLotesDuplicados(conteudos);

    expect(resultados[0].correctedContent).toContain('<ans:numeroLote>600</ans:numeroLote>');
    expect(resultados[1].correctedContent).toContain('<ans:numeroLote>601</ans:numeroLote>');
    expect(resultados[1].lotesRenumerados).toEqual([{ original: '600', novo: '601' }]);
  });
});
