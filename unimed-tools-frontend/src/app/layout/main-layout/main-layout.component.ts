/**
 * Layout compartilhado que coordena menu lateral, conteúdo e estado da rota inicial.
 */
import { Component, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { NgIf } from '@angular/common';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, NgIf],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss'],
})
export class MainLayoutComponent {
  sidebarOpen = signal(true);
  isHome = signal(true);

  constructor(private router: Router) {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.isHome.set(event.urlAfterRedirects === '/' || event.urlAfterRedirects === '');
        // A página inicial usa toda a largura; páginas de ferramenta abrem a navegação.
        this.sidebarOpen.set(!this.isHome());
      });
  }

  toggleSidebar() {
    this.sidebarOpen.update((v) => !v);
  }
}
