import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AvailablePermission, ManagedUser } from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';

@Component({
  selector: 'app-user-permissions',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './user-permissions.component.html',
  styleUrl: './user-permissions.component.scss',
})
export class UserPermissionsComponent {
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly users = signal<ManagedUser[]>([]);
  readonly permissions = signal<AvailablePermission[]>([]);
  readonly selectedPermissions = signal<Set<string>>(new Set());

  readonly form = new FormGroup({
    usuarioId: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  private readonly initialUserId: number | null;

  constructor(
    private readonly auth: AuthService,
    route: ActivatedRoute,
  ) {
    const rawUser = Number(route.snapshot.queryParamMap.get('usuario'));
    this.initialUserId = Number.isFinite(rawUser) && rawUser > 0 ? rawUser : null;

    this.form.controls.usuarioId.valueChanges.subscribe((id) => this.selectUser(id));
    this.load();
  }

  get selectedUser(): ManagedUser | undefined {
    return this.users().find((user) => user.id === this.form.controls.usuarioId.value);
  }

  isSelected(code: string): boolean {
    return this.selectedPermissions().has(code);
  }

  toggle(code: string): void {
    if (!this.form.controls.usuarioId.value) return;
    const next = new Set(this.selectedPermissions());
    if (next.has(code)) next.delete(code);
    else next.add(code);
    this.selectedPermissions.set(next);
    this.success.set('');
  }

  selectAll(): void {
    this.selectedPermissions.set(new Set(this.permissions().map((permission) => permission.codigo)));
    this.success.set('');
  }

  clearAll(): void {
    this.selectedPermissions.set(new Set());
    this.success.set('');
  }

  submit(): void {
    const userId = this.form.controls.usuarioId.value;
    if (!userId || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set('');
    this.success.set('');

    this.auth
      .updateUserPermissions(userId, [...this.selectedPermissions()])
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (response) => {
          const selected = [...this.selectedPermissions()];
          this.users.update((users) =>
            users.map((user) =>
              user.id === userId ? { ...user, permissoes: selected } : user,
            ),
          );
          this.success.set(response.mensagem);
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível atualizar as permissões.'),
      });
  }

  private load(): void {
    forkJoin({
      users: this.auth.listUsers(),
      permissions: this.auth.listAvailablePermissions(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ users, permissions }) => {
          const operational = users.filter((user) => user.perfil === 'USUARIO');
          this.users.set(operational);
          this.permissions.set(permissions);

          if (this.initialUserId && operational.some((user) => user.id === this.initialUserId)) {
            this.form.controls.usuarioId.setValue(this.initialUserId);
          }
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível carregar os acessos.'),
      });
  }

  private selectUser(id: number | null): void {
    const user = this.users().find((item) => item.id === id);
    const availableCodes = new Set(this.permissions().map((permission) => permission.codigo));
    this.selectedPermissions.set(
      new Set((user?.permissoes ?? []).filter((code) => availableCodes.has(code))),
    );
    this.error.set('');
    this.success.set('');
  }
}
