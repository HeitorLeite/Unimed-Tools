/** Layout autenticado que coordena navegação, contexto da rota e conta atual. */
import { NgIf } from '@angular/common';
import { Component, HostListener, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, SidebarComponent, NgIf],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss'],
})
export class MainLayoutComponent {
  sidebarOpen = signal(true);
  isMobile = signal(false);
  pageTitle = signal('Visão geral');
  pageContext = signal('Central operacional');

  constructor(
    private readonly router: Router,
  ) {
    this.updateViewport();
    this.updateRouteContext(this.router.url);
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.updateRouteContext(event.urlAfterRedirects);
        if (this.isMobile()) this.sidebarOpen.set(false);
      });
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((value) => !value);
  }

  @HostListener('window:resize')
  onResize(): void {
    this.updateViewport();
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
      '/bi/especialidade-medica': { title: 'Especialidade médica', context: 'Business Intelligence' },
      '/relatorios': { title: 'Central de relatórios', context: 'Consultas SGU' },
      '/ans/corretor-rede': { title: 'Corretor de rede RPS', context: 'Arquivos ANS' },
      '/fechamento/corretor': { title: 'Fechamento de produção', context: 'Em desenvolvimento' },
      '/usuarios': { title: 'Gerenciamento de usuários', context: 'Administração de acesso' },
      '/usuarios/novo': { title: 'Cadastrar usuário', context: 'Administração de acesso' },
      '/usuarios/permissoes': { title: 'Permissões dos usuários', context: 'Administração de acesso' },
      '/usuarios/resetar-senha': { title: 'Resetar senha', context: 'Administração de acesso' },
    };
    const current = contextByRoute[route] ?? contextByRoute['/'];
    this.pageTitle.set(current.title);
    this.pageContext.set(current.context);
  }
}
