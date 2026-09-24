/** Pesquisa somente metadados autorizados; nunca monta SQL ou pesquisa valores pessoais. */
export function normalizarBuscaCampo(valor: string): string {
  return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR').trim();
}

export function buscarCampos<T extends { id: string; rotulo: string; grupo: string }>(
  campos: readonly T[], busca: string, grupo = '',
): T[] {
  const termos = normalizarBuscaCampo(busca).split(/\s+/).filter(Boolean);
  return campos.filter((campo) => (!grupo || campo.grupo === grupo)
    && termos.every((termo) => normalizarBuscaCampo(`${campo.rotulo} ${campo.grupo} ${campo.id}`).includes(termo)));
}

export function agruparCampos<T extends { grupo: string }>(campos: readonly T[]): { nome: string; itens: T[] }[] {
  const grupos = new Map<string, T[]>();
  for (const campo of campos) {
    const itens = grupos.get(campo.grupo) ?? [];
    itens.push(campo);
    grupos.set(campo.grupo, itens);
  }
  return [...grupos].map(([nome, itens]) => ({ nome, itens }));
}
