/** Arredonda somente valores declarados decimais pelo catálogo, sem perder precisão em strings. */
export function formatAssistencialDecimal(value: unknown): string | null {
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  let text = String(value).trim();
  if (text.includes(',')) text = text.replace(/\./g, '').replace(',', '.');
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(text);
  if (!match) return null;
  const exponent = Number(match[4] ?? 0);
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 100) return null;
  const fraction = match[3] ?? '';
  const digits = BigInt(match[2] + fraction);
  const shift = 2 + exponent - fraction.length;
  let cents: bigint;
  if (shift >= 0) cents = digits * 10n ** BigInt(shift);
  else {
    const divisor = 10n ** BigInt(-shift);
    cents = digits / divisor + (digits % divisor * 2n >= divisor ? 1n : 0n);
  }
  const formatted = cents.toString().padStart(3, '0');
  return (match[1] === '-' && cents !== 0n ? '-' : '') +
    formatted.slice(0, -2) + ',' + formatted.slice(-2);
}
