import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { ToolIconComponent } from '../../shared/components/tool-icon/tool-icon.component';
import { ApplicationNotification } from '../../shared/models/application-notification.model';
import { ToolDefinition } from '../../shared/models/tool.model';
import { AuthService } from '../../shared/services/auth.service';
import { NotificationService } from '../../shared/services/notification.service';
import { ToolRegistryService } from '../../shared/services/tool-registry.service';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, ToolIconComponent],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss'],
})
export class MainLayoutComponent {
  private readonly router = inject(Router);
  private readonly element = inject(ElementRef<HTMLElement>);
  readonly auth = inject(AuthService);
  private readonly notificationsService = inject(NotificationService);
  readonly registry = inject(ToolRegistryService);

  readonly toolsOpen = signal(false);
  readonly notificationOpen = signal(false);
  readonly userOpen = signal(false);
  readonly mobileOpen = signal(false);
  readonly unread = signal(0);
  readonly notifications: ApplicationNotification[] = this.notificationsService.listar();
  readonly user = this.auth.user;

  constructor() {
    this.unread.set(this.notificationsService.quantidadeNaoLidas());
    this.router.events.pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => this.closeMenus());
  }

  get tools(): ToolDefinition[] {
    return this.registry.listAccessible();
  }

  toggleTools(event: MouseEvent): void {
    event.stopPropagation();
    this.toolsOpen.update((value) => !value);
    this.notificationOpen.set(false);
    this.userOpen.set(false);
  }

  toggleNotifications(event: MouseEvent): void {
    event.stopPropagation();
    this.notificationOpen.update((value) => !value);
    this.toolsOpen.set(false);
    this.userOpen.set(false);
    if (this.notificationOpen()) {
      this.notificationsService.marcarTodasComoLidas();
      this.unread.set(0);
    }
  }

  toggleUser(event: MouseEvent): void {
    event.stopPropagation();
    this.userOpen.update((value) => !value);
    this.toolsOpen.set(false);
    this.notificationOpen.set(false);
  }

  toggleMobile(): void {
    this.mobileOpen.update((value) => !value);
  }

  openTool(tool: ToolDefinition): void {
    this.registry.recordOpened(tool.id);
    this.closeMenus();
    void this.router.navigateByUrl(tool.route);
  }

  logout(): void {
    this.closeMenus();
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => {
        this.auth.clear();
        void this.router.navigateByUrl('/login');
      },
    });
  }

  formatDate(value: string): string {
    const [year, month, day] = value.split('-');
    return year && month && day ? `${day}/${month}/${year}` : value;
  }

  initials(name: string): string {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  }

  closeMenus(): void {
    this.toolsOpen.set(false);
    this.notificationOpen.set(false);
    this.userOpen.set(false);
    this.mobileOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  closeOutside(event: MouseEvent): void {
    if (!this.element.nativeElement.contains(event.target as Node)) this.closeMenus();
  }

  @HostListener('document:keydown.escape')
  closeEscape(): void {
    this.closeMenus();
  }
}
