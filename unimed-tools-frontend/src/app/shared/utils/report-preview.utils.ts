const MASCARA_PREVIA = '••••••••';

/** Identifica apenas Nome e CPF de beneficiário por aliases comuns dos relatórios. */
export function isProtectedBeneficiaryColumn(column: string): boolean {
  const normalized = String(column ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');

  if (normalized.includes('cpf')) {
    return true;
  }

  const isName = normalized === 'nome' || normalized === 'nm';
  const isBeneficiaryName =
    (normalized.includes('nome') || normalized.startsWith('nm')) &&
    (normalized.includes('beneficiario') || normalized.includes('benef'));
  return isName || isBeneficiaryName;
}

export function formatReportPreviewValue(column: string, value: unknown): string {
  if (
    isProtectedBeneficiaryColumn(column) &&
    value !== null &&
    value !== undefined &&
    value !== ''
  ) {
    return MASCARA_PREVIA;
  }
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
