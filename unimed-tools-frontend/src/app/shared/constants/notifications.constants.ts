import { ApplicationNotification } from '../models/application-notification.model';

/**
 * Histórico visível no painel de notificações. Cada entrega funcional deve
 * acrescentar uma entrada nova, sem remover as versões já publicadas.
 */
export const APPLICATION_NOTIFICATIONS: readonly ApplicationNotification[] = [
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
