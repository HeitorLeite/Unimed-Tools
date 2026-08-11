import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { CreatedUser, NewUserRequest } from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';

function passwordMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('senhaTemporaria')?.value === group.get('confirmacao')?.value
    ? null
    : { passwordMismatch: true };
}

@Component({
  selector: 'app-user-registration',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './user-registration.component.html',
  styleUrl: './user-registration.component.scss',
})
export class UserRegistrationComponent {
  readonly loading = signal(false);
  readonly error = signal('');
  readonly created = signal<CreatedUser | null>(null);
  readonly showPassword = signal(false);

  readonly form = new FormGroup(
    {
      nome: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(3), Validators.maxLength(150)] }),
      login: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[a-z0-9._-]{3,80}$/)] }),
      email: new FormControl('', { nonNullable: true, validators: [Validators.email, Validators.maxLength(254)] }),
      perfilCodigo: new FormControl<'USUARIO' | 'ADMINISTRADOR'>('USUARIO', { nonNullable: true, validators: [Validators.required] }),
      senhaTemporaria: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)] }),
      confirmacao: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      codigoMfaAdministrador: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{6}$/)] }),
    },
    { validators: passwordMatch },
  );

  constructor(private readonly auth: AuthService) {}

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.created.set(null);
    const value = this.form.getRawValue();
    const request: NewUserRequest = {
      nome: value.nome,
      login: value.login,
      email: value.email || null,
      senhaTemporaria: value.senhaTemporaria,
      perfilCodigo: value.perfilCodigo,
      codigoMfaAdministrador: value.codigoMfaAdministrador,
    };
    this.auth
      .createUser(request)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (user) => {
          this.created.set(user);
          this.form.reset({ perfilCodigo: 'USUARIO' });
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível cadastrar o usuário.'),
      });
  }
}
