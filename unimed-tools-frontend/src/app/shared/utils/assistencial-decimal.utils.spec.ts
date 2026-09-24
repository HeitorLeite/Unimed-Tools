import { formatAssistencialDecimal } from './assistencial-decimal.utils';

describe('Decimais do Assistencial', () => {
  it('formata duas casas com arredondamento decimal e mantém nulos', () => {
    for (const [entrada, saida] of [[10, '10,00'], [0, '0,00'], ['1.005', '1,01'], ['-2,345', '-2,35'], ['1.234,56', '1.234,56'], [1234567.8, '1.234.567,80'], ['1e-3', '0,00'], ['99999999999999.995', '100.000.000.000.000,00']] as const) {
      expect(formatAssistencialDecimal(entrada)).toBe(saida);
    }
    expect(formatAssistencialDecimal(null)).toBeNull();
    expect(formatAssistencialDecimal('texto')).toBeNull();
  });
});
