import { ApplicationNotification } from '../models/application-notification.model';

/**
 * Histórico visível no painel de notificações. Cada entrega funcional deve
 * acrescentar uma entrada nova, sem remover as versões já publicadas.
 */
export const APPLICATION_NOTIFICATIONS: readonly ApplicationNotification[] = [
  {
    id: '8.0.2-hospital-prestador',
    versao: '8.0.2',
    titulo: 'Prestador no relatório Hospital',
    resumo: 'Adiciona a coluna Prestador na prévia e no arquivo e permite pesquisar por parte do nome do prestador.',
    publicadaEm: '2026-09-22',
  },
  {
    id: '8.0.1-exportacao-falhas-temporarias',
    versao: '8.0.1',
    titulo: 'Recuperação de falhas temporárias na exportação',
    resumo: 'Repete uma vez a página que falhar temporariamente no SGU e apresenta mensagens claras de indisponibilidade ou tempo limite.',
    publicadaEm: '2026-09-22',
  },
  {
    id: '8.0.0-nova-experiencia-ferramentas',
    versao: '8.0.0',
    titulo: 'Nova experiência do Unimed Tools',
    resumo:
      'Reorganiza a navegação por áreas de trabalho, adiciona Comercial, Hospital, Gestão de Risco e TI, renova o Assistencial e simplifica o login sem segundo fator.',
    publicadaEm: '2026-09-18',
  },
  {
    id: '7.8.0-empresas-relatorios-automaticos',
    versao: '7.8.0',
    titulo: 'Empresas por nome nos relatórios automáticos',
    resumo:
      'Permite selecionar a empresa pelo nome e aplica automaticamente seus códigos na geração em lote.',
    publicadaEm: '2026-09-17',
  },
  {
    id: '7.7.0-filtro-id-guia',
    versao: '7.7.0',
    titulo: 'Filtro por ID da guia',
    resumo:
      'Separa o ID interno do número da guia e permite consultar um ou vários IDs separados por vírgula.',
    publicadaEm: '2026-09-02',
  },
  {
    id: '7.6.0-filtro-multiplo-empresa-e-bloqueio-operacoes',
    versao: '7.6.0',
    titulo: 'Empresas múltiplas e operações protegidas',
    resumo:
      'Permite filtrar vários códigos de empresa, adiciona o nome da pessoa da empresa, bloqueia ações durante geração e melhora a conclusão do lote automático.',
    publicadaEm: '2026-08-26',
  },
  {
    id: '7.5.1-correcao-ordenacao-relatorio-personalizado',
    versao: '7.5.1',
    titulo: 'Correção da ordenação do relatório personalizado',
    resumo:
      'Reduz a definição de ordenação enviada ao SGU para evitar falha de buffer ao ordenar a prévia.',
    publicadaEm: '2026-08-26',
  },
  {
    id: '7.5.0-indicadores-financeiros-e-ordenacao',
    versao: '7.5.0',
    titulo: 'Indicadores financeiros e ordenação da prévia',
    resumo:
      'Adiciona Receita, Sinistralidade e Despesa total por beneficiário, contrato ou empresa e permite ordenar qualquer coluna da prévia.',
    publicadaEm: '2026-08-26',
  },
  {
    id: '7.4.0-exportacoes-e-filtros-sql',
    versao: '7.4.0',
    titulo: 'Exportações extensas e filtros SQL ampliados',
    resumo:
      'Adiciona progresso, remove RNUM, libera relatórios extensos e reconhece filtros SQL sem transformar constantes técnicas.',
    publicadaEm: '2026-08-20',
  },
  {
    id: '7.3.0-filtros-cte-importacao-sql',
    versao: '7.3.0',
    titulo: 'Filtros de CTE na importação SQL',
    resumo:
      'Reconhece datas compartilhadas e listas vazias de empresas ou itens dentro de CTEs importadas.',
    publicadaEm: '2026-08-17',
  },
  {
    id: '7.2.2-acesso-completo-relatorios',
    versao: '7.2.2',
    titulo: 'Acesso completo à Central de Relatórios',
    resumo:
      'Permite que usuários autorizados no módulo importem SQL e realizem todas as operações dos relatórios.',
    publicadaEm: '2026-08-17',
  },
  {
    id: '7.2.1-protecao-nome-comp',
    versao: '7.2.1',
    titulo: 'Proteção ampliada nas prévias manuais',
    resumo: 'Adiciona o alias NOME_COMP à identificação de nomes de beneficiários.',
    publicadaEm: '2026-08-12',
  },
  {
    id: '7.2.0-inicializacao-acesso-e-interface',
    versao: '7.2.0',
    titulo: 'Inicialização simplificada e interface ajustada',
    resumo:
      'Adiciona o iniciador local, corrige os ícones e amplia a proteção de Nome e CPF nas prévias manuais.',
    publicadaEm: '2026-08-12',
  },
  {
    id: '7.1.0-relatorios-e-notificacoes',
    versao: '7.1.0',
    titulo: 'Relatórios mais flexíveis e seguros',
    resumo:
      'Corrige o total do relatório personalizado, permite ordenar colunas, protege Nome e CPF nas prévias e adiciona este painel de atualizações.',
    publicadaEm: '2026-08-12',
  },
];

export const NOTIFICATION_STORAGE_KEY = 'unimed-tools.notificacoes.lidas.v1';
