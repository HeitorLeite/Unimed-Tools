/**
 * Chaves versionadas usadas pela Central de Relatórios no armazenamento local.
 * Alterar o sufixo de versão exige uma estratégia de migração para não tornar
 * catálogos, templates e grupos existentes invisíveis ao usuário.
 */
export const RELATORIO_STORAGE_KEYS = {
  catalogo: 'unimed-tools.relatorios.v1',
  templates: 'unimed-tools.relatorios.templates.v1',
  gruposAutomaticos: 'unimed-tools.relatorios.grupos-automaticos.v1',
} as const;
