/**
 * Menu lateral e metadados de navegação dos módulos disponíveis.
 */
import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  inject,
  Input,
  OnChanges,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import { AuthService } from '../../shared/services/auth.service';
import {
  ToolIconComponent,
  ToolIconName,
} from '../../shared/components/tool-icon/tool-icon.component';

interface NavItem {
  label: string;
  route: string;
  tag: string;
  icon: ToolIconName;
  ready: boolean;
  permission?: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgFor, NgIf, ToolIconComponent],
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
      icon: 'relatorios',
    },
    {
      label: 'XML TISS',
      tag: 'Correção de arquivos',
      route: '/xml/ferramentas',
      ready: true,
      permission: 'XML_ACESSAR',
      icon: 'xml',
    },
    {
      label: 'Especialidade médica',
      tag: 'Planilhas de BI',
      route: '/bi/especialidade-medica',
      ready: true,
      permission: 'BI_ACESSAR',
      icon: 'bi',
    },
    {
      label: 'Corretor de rede ANS',
      tag: 'Arquivo posicional RPS',
      route: '/ans/corretor-rede',
      ready: true,
      permission: 'ANS_ACESSAR',
      icon: 'ans',
    },
    {
      label: 'Fechamento',
      tag: 'Em desenvolvimento',
      route: '/fechamento/corretor',
      ready: false,
      permission: 'APLICACAO_ACESSAR',
      icon: 'fechamento',
    },
  ];

  get visibleItems(): NavItem[] {
    return this.items.filter(
      (item) => !item.permission || this.auth.hasPermission(item.permission),
    );
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
