import { CommonModule } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ManagedUser } from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss',
})
export class UserManagementComponent {
  readonly loading = signal(true);
  readonly users = signal<ManagedUser[]>([]);

  readonly totalUsers = computed(() => this.users().length);
  readonly operationalUsers = computed(
    () => this.users().filter((user) => user.perfil === 'USUARIO').length,
  );
  readonly administrators = computed(
    () => this.users().filter((user) => user.perfil === 'ADMINISTRADOR').length,
  );

  constructor(private readonly auth: AuthService) {
    this.auth
      .listUsers()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({ next: (users) => this.users.set(users), error: () => this.users.set([]) });
  }
}
