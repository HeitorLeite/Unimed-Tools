import { ApplicationNotification } from '../models/application-notification.model';

/**
 * Histórico visível no painel de notificações. Cada entrega funcional deve
 * acrescentar uma entrada nova, sem remover as versões já publicadas.
 */
export const APPLICATION_NOTIFICATIONS: readonly ApplicationNotification[] = [
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
      'Adiciona o iniciador local, mantém o MFA no login administrativo sem repeti-lo nas ações, corrige os ícones e amplia a proteção de Nome e CPF nas prévias manuais.',
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
