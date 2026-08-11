import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ManagedUser } from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('senhaTemporaria')?.value === group.get('confirmacao')?.value
    ? null
    : { passwordMismatch: true };
}

@Component({
  selector: 'app-user-password-reset',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './user-password-reset.component.html',
  styleUrl: './user-password-reset.component.scss',
})
export class UserPasswordResetComponent {
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly users = signal<ManagedUser[]>([]);
  readonly showPasswords = signal(false);

  readonly form = new FormGroup(
    {
      usuarioId: new FormControl<number | null>(null, { validators: [Validators.required] }),
      senhaTemporaria: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)],
      }),
      confirmacao: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      codigoMfaAdministrador: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
      }),
    },
    { validators: passwordsMatch },
  );

  constructor(private readonly auth: AuthService) {
    this.load();
  }

  submit(): void {
    const value = this.form.getRawValue();
    if (!value.usuarioId || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');
    this.success.set('');
    this.auth
      .resetUserPassword(value.usuarioId, value.senhaTemporaria, value.codigoMfaAdministrador)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (response) => {
          this.success.set(response.mensagem);
          this.form.controls.senhaTemporaria.reset();
          this.form.controls.confirmacao.reset();
          this.form.controls.codigoMfaAdministrador.reset();
          this.form.markAsUntouched();
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível redefinir a senha.'),
      });
  }

  private load(): void {
    const currentUserId = this.auth.user()?.id;
    this.auth.listUsers()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (users) => this.users.set(users.filter((user) => user.id !== currentUserId)),
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível carregar os usuários.'),
      });
  }
}
