import { Injectable, computed, signal } from '@angular/core';
import { AuthUser } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly currentUser = signal<AuthUser | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly authenticated = computed(() => this.currentUser() !== null);

  setUser(user: AuthUser | null): void {
    this.currentUser.set(user);
  }

  clear(): void {
    this.currentUser.set(null);
  }

  hasPermission(permission: string): boolean {
    return this.currentUser()?.permissoes.includes(permission) ?? false;
  }
}
