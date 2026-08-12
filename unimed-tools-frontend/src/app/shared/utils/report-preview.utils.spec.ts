import { formatReportPreviewValue, isProtectedBeneficiaryColumn } from './report-preview.utils';

describe('utilitários da prévia de relatórios', () => {
  it.each(['CPF', 'NUM_CPF', 'CPF_BENEFICIARIO', 'NOME', 'NOME_BENEFICIARIO', 'NM_BENEF'])(
    'protege a coluna pessoal %s',
    (coluna) => {
      expect(isProtectedBeneficiaryColumn(coluna)).toBe(true);
      expect(formatReportPreviewValue(coluna, 'dado pessoal')).toBe('••••••••');
    },
  );

  it('preserva outros valores da prévia', () => {
    expect(formatReportPreviewValue('NUMERO_GUIA', 123)).toBe('123');
    expect(formatReportPreviewValue('NUMERO_GUIA', null)).toBe('—');
  });
});
