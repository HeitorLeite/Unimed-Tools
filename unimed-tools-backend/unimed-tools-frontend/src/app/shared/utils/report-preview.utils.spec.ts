import { formatReportPreviewValue, isProtectedBeneficiaryColumn } from './report-preview.utils';

describe('utilitários da prévia de relatórios', () => {
  it.each([
    'CPF',
    'NUM_CPF',
    'CPF_BENEFICIARIO',
    'NOME',
    'NOME_COMP',
    'NOME_COMPLETO',
    'NOME_BENEFICIARIO',
    'NM_BENEF',
    'NM_PACIENTE',
    'PES_NOM_COMP',
    'BENEFICIARIO',
  ])('protege a coluna pessoal %s', (coluna) => {
    expect(isProtectedBeneficiaryColumn(coluna)).toBe(true);
    expect(formatReportPreviewValue(coluna, 'dado pessoal')).toBe('••••••••');
  });

  it('preserva outros valores da prévia', () => {
    expect(formatReportPreviewValue('NUMERO_GUIA', 123)).toBe('123');
    expect(formatReportPreviewValue('NOME_PRESTADOR', 'Clinica teste')).toBe('Clinica teste');
    expect(formatReportPreviewValue('COD_BENEFICIARIO', 456)).toBe('456');
    expect(formatReportPreviewValue('NUMERO_GUIA', null)).toBe('—');
  });
});
