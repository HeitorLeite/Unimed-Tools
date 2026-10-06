import { Injectable } from '@angular/core';

import {
  APPLICATION_NOTIFICATIONS,
  NOTIFICATION_STORAGE_KEY,
} from '../constants/notifications.constants';
import { ApplicationNotification } from '../models/application-notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  listar(): ApplicationNotification[] {
    return [...APPLICATION_NOTIFICATIONS];
  }

  quantidadeNaoLidas(): number {
    const lidas = this.idsLidos();
    return APPLICATION_NOTIFICATIONS.filter((notificacao) => !lidas.has(notificacao.id)).length;
  }

  marcarTodasComoLidas(): void {
    if (typeof localStorage === 'undefined') return;
    try {
      localStorage.setItem(
        NOTIFICATION_STORAGE_KEY,
        JSON.stringify(APPLICATION_NOTIFICATIONS.map((notificacao) => notificacao.id)),
      );
    } catch {
      // O painel continua funcional quando o navegador bloqueia persistência local.
    }
  }

  private idsLidos(): Set<string> {
    if (typeof localStorage === 'undefined') return new Set();

    try {
      const valor = JSON.parse(localStorage.getItem(NOTIFICATION_STORAGE_KEY) ?? '[]');
      return new Set(
        Array.isArray(valor) ? valor.filter((id): id is string => typeof id === 'string') : [],
      );
    } catch {
      return new Set();
    }
  }
}
