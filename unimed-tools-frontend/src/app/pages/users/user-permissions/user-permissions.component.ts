import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
    codigoMfaAdministrador: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  constructor(private readonly auth: AuthService) {
    this.form.controls.usuarioId.valueChanges.subscribe((id) => this.selectUser(id));
    this.load();
  }

  isSelected(code: string): boolean {
    return this.selectedPermissions().has(code);
  }

  toggle(code: string): void {
    const next = new Set(this.selectedPermissions());
    if (next.has(code)) next.delete(code); else next.add(code);
    this.selectedPermissions.set(next);
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
      .updateUserPermissions(
        userId,
        [...this.selectedPermissions()],
        this.form.controls.codigoMfaAdministrador.value,
      )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (response) => {
          const selected = [...this.selectedPermissions()];
          this.users.update((users) => users.map((user) =>
            user.id === userId
              ? { ...user, permissoes: selected.length ? ['APLICACAO_ACESSAR', ...selected] : [] }
              : user,
          ));
          this.form.controls.codigoMfaAdministrador.reset();
          this.success.set(response.mensagem);
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível atualizar as permissões.'),
      });
  }

  private load(): void {
    forkJoin({ users: this.auth.listUsers(), permissions: this.auth.listAvailablePermissions() })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ users, permissions }) => {
          this.users.set(users.filter((user) => user.perfil === 'USUARIO'));
          this.permissions.set(permissions);
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível carregar os usuários.'),
      });
  }

  private selectUser(id: number | null): void {
    const user = this.users().find((item) => item.id === id);
    const availableCodes = new Set(this.permissions().map((permission) => permission.codigo));
    this.selectedPermissions.set(
      new Set((user?.permissoes ?? []).filter((code) => availableCodes.has(code))),
    );
    this.form.controls.codigoMfaAdministrador.reset();
    this.error.set('');
    this.success.set('');
  }
}
