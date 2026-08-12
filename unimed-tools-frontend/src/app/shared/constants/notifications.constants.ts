import { ApplicationNotification } from '../models/application-notification.model';

/**
 * Histórico visível no painel de notificações. Cada entrega funcional deve
 * acrescentar uma entrada nova, sem remover as versões já publicadas.
 */
export const APPLICATION_NOTIFICATIONS: readonly ApplicationNotification[] = [
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
