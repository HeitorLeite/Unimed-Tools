import { NOTIFICATION_STORAGE_KEY } from '../constants/notifications.constants';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  beforeEach(() => localStorage.clear());

  it('apresenta lançamentos novos até o painel ser visualizado', () => {
    const service = new NotificationService();

    expect(service.listar().length).toBeGreaterThan(0);
    expect(service.quantidadeNaoLidas()).toBe(service.listar().length);

    service.marcarTodasComoLidas();

    expect(service.quantidadeNaoLidas()).toBe(0);
    expect(localStorage.getItem(NOTIFICATION_STORAGE_KEY)).toContain(service.listar()[0].id);
  });

  it('ignora estado local inválido sem impedir a abertura do painel', () => {
    localStorage.setItem(NOTIFICATION_STORAGE_KEY, '{inválido');

    expect(new NotificationService().quantidadeNaoLidas()).toBeGreaterThan(0);
  });
});
