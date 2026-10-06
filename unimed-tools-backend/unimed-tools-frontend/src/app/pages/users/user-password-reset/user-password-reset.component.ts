import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
    },
    { validators: passwordsMatch },
  );

  private readonly initialUserId: number | null;

  constructor(
    private readonly auth: AuthService,
    route: ActivatedRoute,
  ) {
    const rawUser = Number(route.snapshot.queryParamMap.get('usuario'));
    this.initialUserId = Number.isFinite(rawUser) && rawUser > 0 ? rawUser : null;
    this.load();
  }

  get selectedUser(): ManagedUser | undefined {
    return this.users().find((user) => user.id === this.form.controls.usuarioId.value);
  }

  generateTemporaryPassword(): void {
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*';
    const bytes = new Uint32Array(18);
    crypto.getRandomValues(bytes);
    const generated = Array.from(bytes, (value) => alphabet[value % alphabet.length]).join('');
    this.form.patchValue({ senhaTemporaria: generated, confirmacao: generated });
    this.showPasswords.set(true);
    this.form.updateValueAndValidity();
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
      .resetUserPassword(value.usuarioId, value.senhaTemporaria)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (response) => {
          this.success.set(response.mensagem);
          this.form.controls.senhaTemporaria.reset();
          this.form.controls.confirmacao.reset();
          this.form.markAsUntouched();
          this.showPasswords.set(false);
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível redefinir a senha.'),
      });
  }

  private load(): void {
    const currentUserId = this.auth.user()?.id;
    this.auth
      .listUsers()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (users) => {
          const available = users.filter((user) => user.id !== currentUserId);
          this.users.set(available);
          if (this.initialUserId && available.some((user) => user.id === this.initialUserId)) {
            this.form.controls.usuarioId.setValue(this.initialUserId);
          }
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível carregar os usuários.'),
      });
  }
}
