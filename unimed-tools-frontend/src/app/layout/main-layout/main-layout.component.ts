/** Layout autenticado que coordena navegação, contexto da rota e conta atual. */
import { CommonModule } from '@angular/common';
import { Component, HostListener, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { ApplicationNotification } from '../../shared/models/application-notification.model';
import { NotificationService } from '../../shared/services/notification.service';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, SidebarComponent],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss'],
})
export class MainLayoutComponent {
  sidebarOpen = signal(true);
  isMobile = signal(false);
  pageTitle = signal('Visão geral');
  pageContext = signal('Central operacional');
  notificationPanelOpen = signal(false);
  unreadNotifications = signal(0);
  notifications: ApplicationNotification[] = [];

  constructor(
    private readonly router: Router,
    private readonly notificationService: NotificationService,
  ) {
    this.notifications = this.notificationService.listar();
    this.unreadNotifications.set(this.notificationService.quantidadeNaoLidas());
    this.updateViewport();
    this.updateRouteContext(this.router.url);
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.updateRouteContext(event.urlAfterRedirects);
        this.closeNotificationPanel();
        if (this.isMobile()) this.sidebarOpen.set(false);
      });
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((value) => !value);
  }

  toggleNotificationPanel(): void {
    this.notificationPanelOpen.update((value) => !value);
    if (this.notificationPanelOpen()) {
      this.notificationService.marcarTodasComoLidas();
      this.unreadNotifications.set(0);
    }
  }

  closeNotificationPanel(): void {
    this.notificationPanelOpen.set(false);
  }

  formatNotificationDate(value: string): string {
    const [year, month, day] = value.split('-');
    return year && month && day ? `${day}/${month}/${year}` : value;
  }

  @HostListener('window:resize')
  onResize(): void {
    this.updateViewport();
  }

  @HostListener('document:keydown.escape')
  closePanelsWithEscape(): void {
    this.closeNotificationPanel();
  }

  private updateViewport(): void {
    const mobile = window.innerWidth <= 900;
    const changedRange = mobile !== this.isMobile();
    this.isMobile.set(mobile);
    if (changedRange) this.sidebarOpen.set(!mobile);
  }

  private updateRouteContext(url: string): void {
    const route = url.split('?')[0].split('#')[0];
    const contextByRoute: Record<string, { title: string; context: string }> = {
      '/': { title: 'Visão geral', context: 'Central operacional' },
      '/xml/ferramentas': { title: 'Ferramentas XML TISS', context: 'Processamento local' },
      '/bi/especialidade-medica': {
        title: 'Especialidade médica',
        context: 'Business Intelligence',
      },
      '/relatorios': { title: 'Central de relatórios', context: 'Consultas SGU' },
      '/ans/corretor-rede': { title: 'Corretor de rede RPS', context: 'Arquivos ANS' },
      '/fechamento/corretor': { title: 'Fechamento de produção', context: 'Em desenvolvimento' },
      '/usuarios': { title: 'Gerenciamento de usuários', context: 'Administração de acesso' },
      '/usuarios/novo': { title: 'Cadastrar usuário', context: 'Administração de acesso' },
      '/usuarios/permissoes': {
        title: 'Permissões dos usuários',
        context: 'Administração de acesso',
      },
      '/usuarios/resetar-senha': { title: 'Resetar senha', context: 'Administração de acesso' },
    };
    const current = contextByRoute[route] ?? contextByRoute['/'];
    this.pageTitle.set(current.title);
    this.pageContext.set(current.context);
  }
}
