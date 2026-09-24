import { EMPRESAS_RELATORIOS } from './empresa-catalogo';

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
});
