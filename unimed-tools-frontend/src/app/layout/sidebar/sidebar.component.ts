/**
 * Menu lateral e metadados de navegação dos módulos disponíveis.
 */
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';

interface NavItem {
  label: string;
  route: string;
  tag: string;
  icon: string;
  ready: boolean;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgFor, NgIf],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  @Input() open = true;
  @Output() toggleSidebar = new EventEmitter<void>();

  items: NavItem[] = [
    {
      label: 'Central de relatórios',
      tag: 'Consultas SGU',
      route: '/relatorios',
      ready: true,
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M5 2.5h7l3 3v12H5v-15Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M12 2.5V6h3M7.5 9.5h5M7.5 12.5h5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>',
    },
    {
      label: 'XML TISS',
      tag: 'Correção de arquivos',
      route: '/xml/ferramentas',
      ready: true,
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M7 5 3 10l4 5M13 5l4 5-4 5M11.5 3 8.5 17" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    },
    {
      label: 'Especialidade médica',
      tag: 'Planilhas de BI',
      route: '/bi/especialidade-medica',
      ready: true,
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M4 16V9m4 7V5m4 11v-6m4 6V3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>',
    },
    {
      label: 'Corretor de rede ANS',
      tag: 'Arquivo posicional RPS',
      route: '/ans/corretor-rede',
      ready: true,
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M3 5.5h14M3 10h9M3 14.5h6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/><circle cx="15" cy="13" r="2.5" stroke="currentColor" stroke-width="1.3"/></svg>',
    },
    {
      label: 'Fechamento',
      tag: 'Em desenvolvimento',
      route: '/fechamento/corretor',
      ready: false,
      icon: '<svg viewBox="0 0 20 20" fill="none"><path d="M5 2.5h7l3 3v12H5v-15Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/><path d="M12 2.5V6h3M7.5 11h5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>',
    },
  ];
}
