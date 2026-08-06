/**
 * Formata tamanhos de arquivo com a mesma convenção visual usada nas páginas.
 * A função fica centralizada para evitar diferenças de unidade e arredondamento
 * entre componentes que recebem arquivos do usuário.
 */
export function formatarTamanhoArquivo(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Inicia o download de um Blob e sempre libera a URL temporária criada pelo
 * navegador. O elemento é anexado ao documento para manter compatibilidade
 * com navegadores que ignoram cliques em links desconectados do DOM.
 */
export function baixarBlob(blob: Blob, nomeArquivo: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');

  link.href = url;
  link.download = nomeArquivo;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
