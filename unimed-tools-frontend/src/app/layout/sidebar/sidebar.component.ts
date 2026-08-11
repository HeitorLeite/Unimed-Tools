/**
 * Menu lateral e metadados de navegação dos módulos disponíveis.
 */
import { Component, ElementRef, EventEmitter, HostListener, inject, Input, OnChanges, Output, signal, SimpleChanges } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import { AuthService } from '../../shared/services/auth.service';

interface NavItem {
  label: string;
  route: string;
  tag: string;
  icon: string;
  ready: boolean;
  permission?: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgFor, NgIf],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnChanges {
  @Input() open = true;
  @Output() toggleSidebar = new EventEmitter<void>();

  readonly auth = inject(AuthService);
  readonly user = this.auth.user;
  readonly accountOpen = signal(false);

  constructor(
    private readonly router: Router,
    private readonly elementRef: ElementRef<HTMLElement>,
  ) {}

  items: NavItem[] = [
    {
      label: 'Central de relatórios',
      tag: 'Consultas SGU',
      route: '/relatorios',
      ready: true,
      permission: 'RELATORIOS_ACESSAR',
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M5 2.5h7l3 3v12H5v-15Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M12 2.5V6h3M7.5 9.5h5M7.5 12.5h5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>',
    },
    {
      label: 'XML TISS',
      tag: 'Correção de arquivos',
      route: '/xml/ferramentas',
      ready: true,
      permission: 'XML_ACESSAR',
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M7 5 3 10l4 5M13 5l4 5-4 5M11.5 3 8.5 17" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    },
    {
      label: 'Especialidade médica',
      tag: 'Planilhas de BI',
      route: '/bi/especialidade-medica',
      ready: true,
      permission: 'BI_ACESSAR',
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M4 16V9m4 7V5m4 11v-6m4 6V3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>',
    },
    {
      label: 'Corretor de rede ANS',
      tag: 'Arquivo posicional RPS',
      route: '/ans/corretor-rede',
      ready: true,
      permission: 'ANS_ACESSAR',
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M3 5.5h14M3 10h9M3 14.5h6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/><circle cx="15" cy="13" r="2.5" stroke="currentColor" stroke-width="1.3"/></svg>',
    },
    {
      label: 'Fechamento',
      tag: 'Em desenvolvimento',
      route: '/fechamento/corretor',
      ready: false,
      permission: 'APLICACAO_ACESSAR',
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M5 2.5h7l3 3v12H5v-15Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M12 2.5V6h3M7.5 11h5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>',
    },
  ];

  get visibleItems(): NavItem[] {
    return this.items.filter((item) => !item.permission || this.auth.hasPermission(item.permission));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && !this.open) this.accountOpen.set(false);
  }

  toggleAccount(event: MouseEvent): void {
    event.stopPropagation();
    if (!this.open) this.toggleSidebar.emit();
    this.accountOpen.update((value) => !value);
  }

  closeAccount(): void {
    this.accountOpen.set(false);
  }

  logout(): void {
    this.accountOpen.set(false);
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => {
        this.auth.clear();
        void this.router.navigateByUrl('/login');
      },
    });
  }

  @HostListener('document:click', ['$event'])
  closeWhenClickingOutside(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target as Node)) this.accountOpen.set(false);
  }
}
