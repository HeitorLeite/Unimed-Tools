const MASCARA_PREVIA = '••••••••';

/** Identifica Nome e CPF de beneficiário, inclusive em aliases legados do SGU. */
export function isProtectedBeneficiaryColumn(column: string): boolean {
  const normalized = String(column ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');

  if (normalized.includes('cpf')) {
    return true;
  }

  const isName = ['nome', 'nm', 'nomecomp', 'nomecompleto', 'nomcompleto'].includes(normalized);
  const isBeneficiaryName =
    (normalized.includes('nome') || normalized.startsWith('nm')) &&
    (normalized.includes('benef') ||
      normalized.includes('paciente') ||
      normalized.includes('pessoa'));
  const isLegacyPersonName =
    normalized.startsWith('pesnom') ||
    normalized.startsWith('nompes') ||
    normalized.startsWith('benefnom') ||
    normalized.startsWith('pacnom');
  const isBareBeneficiaryName = ['beneficiario', 'benef', 'paciente'].includes(normalized);

  return isName || isBeneficiaryName || isLegacyPersonName || isBareBeneficiaryName;
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
