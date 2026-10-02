import {
  codigoCarteirinhaInativo,
  codigosCarteirinhaPorSituacao,
  EMPRESAS_RELATORIOS,
} from './empresa-catalogo';

describe('EMPRESAS_RELATORIOS', () => {
  it('mantém os nomes e códigos usados nos relatórios automáticos de empresas', () => {
    expect(
      Object.fromEntries(
        EMPRESAS_RELATORIOS.map((empresa) => [empresa.nome, empresa.codigos.join(',')]),
      ),
    ).toEqual({
      Yakult: '2232751,2234452',
      'Canção Nova': '2234623,2234624,2234625,2234626,2234627,2234628',
      Orica: '2010038,2011533,2011372',
      'Saint-Gobain': '2052097',
      'Grupo Biondi': '2236880,2236881,2236882,2236883,2236884,2236886,2236887',
      Biemme: '2010497',
      'Grupo Liceu': '2220974,2220179,2237996',
      Apollo: '2177395',
      'Comunidade Canção Nova': '2237647',
      'Grupo Eppo': '1485,1488',
      AKG: '2218214',
      'Instituto Santa Teresa': '2046655',
      AECI: '2227713',
      ICE: '2234145',
      'Faculdade Serra Dourada': '2227006,2214653',
    });
  });

  it('mantém os códigos de carteirinha separados dos identificadores das empresas', () => {
    expect(
      Object.fromEntries(
        EMPRESAS_RELATORIOS.map((empresa) => [empresa.id, empresa.codigosCarteirinha.join(',')]),
      ),
    ).toEqual({
      yakult: '2128,9128',
      'cancao-nova': '2152,2154,2156,2158,2160,2162,9152',
      orica: '422,5002',
      'saint-gobain': '99',
      'grupo-biondi': '2173,2174,2175,2176,2177,2178,2179,2180,9173',
      biemme: '45,5045',
      'grupo-liceu': '364,365,366,367,369,2190,5364,5365',
      apollo: '156,2260,5156',
      'comunidade-cancao-nova': '2191',
      'grupo-eppo': '2416,2417',
      akg: '2401',
      'instituto-santa-teresa': '92,5092',
      aeci: '2022,2303',
      ice: '2142',
      'faculdade-serra-dourada': '2311,2314',
    });
  });

  it('classifica como inativos somente códigos de quatro posições iniciados por 5 ou 9', () => {
    expect(codigoCarteirinhaInativo('9128')).toBe(true);
    expect(codigoCarteirinhaInativo('5045')).toBe(true);
    expect(codigoCarteirinhaInativo('2128')).toBe(false);
    expect(codigoCarteirinhaInativo('45')).toBe(false);

    const yakultEOrica = EMPRESAS_RELATORIOS.filter((empresa) =>
      ['yakult', 'orica'].includes(empresa.id),
    );
    expect(codigosCarteirinhaPorSituacao(yakultEOrica, 'ATIVOS')).toEqual(['2128', '422']);
    expect(codigosCarteirinhaPorSituacao(yakultEOrica, 'INATIVOS')).toEqual(['9128', '5002']);
  });
});
