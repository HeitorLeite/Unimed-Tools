/**
 * Configuração usada pelo build de produção.
 */
export const environment = {
  production: true,
  // Sessões e CSRF exigem que o navegador use a mesma origem do frontend.
  apiUrl: '/api',
};
