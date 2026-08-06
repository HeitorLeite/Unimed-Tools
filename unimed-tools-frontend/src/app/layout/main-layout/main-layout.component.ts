/**
 * Layout compartilhado que coordena menu lateral, conteúdo e estado da rota inicial.
 */
import { Component, HostListener, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { NgIf } from '@angular/common';
import { filter } from 'rxjs/operators';

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

  constructor(private router: Router) {
    this.updateViewport();
    this.updateRouteContext(this.router.url);

    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.updateRouteContext(event.urlAfterRedirects);

        // Em telas menores, fechar o menu depois da navegação devolve o foco ao conteúdo.
        if (this.isMobile()) this.sidebarOpen.set(false);
      });
  }

  toggleSidebar() {
    this.sidebarOpen.update((v) => !v);
  }

  @HostListener('window:resize')
  onResize() {
    this.updateViewport();
  }

  private updateViewport() {
    const mobile = window.innerWidth <= 900;
    const mudouDeFaixa = mobile !== this.isMobile();
    this.isMobile.set(mobile);

    if (mudouDeFaixa) this.sidebarOpen.set(!mobile);
  }

  private updateRouteContext(url: string) {
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
    };
    const current = contextByRoute[route] ?? contextByRoute['/'];

    this.pageTitle.set(current.title);
    this.pageContext.set(current.context);
  }
}
