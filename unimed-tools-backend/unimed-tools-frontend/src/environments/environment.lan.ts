/**
 * Configuração do build servido pelo XAMPP em /unimed-tools/.
 * O Apache encaminha /api ao backend local sem expor a porta Java na rede.
 */
export const environment = {
  production: true,
  apiUrl: '/api',
};
