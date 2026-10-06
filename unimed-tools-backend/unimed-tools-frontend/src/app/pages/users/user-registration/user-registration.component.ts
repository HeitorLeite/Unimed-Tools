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
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import {
  AvailablePermission,
  CreatedUser,
  NewUserRequest,
} from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';

function passwordMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('senhaTemporaria')?.value === group.get('confirmacao')?.value
    ? null
    : { passwordMismatch: true };
}

@Component({
  selector: 'app-user-registration',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './user-registration.component.html',
  styleUrl: './user-registration.component.scss',
})
export class UserRegistrationComponent {
  readonly loading = signal(false);
  readonly loadingPermissions = signal(true);
  readonly error = signal('');
  readonly created = signal<CreatedUser | null>(null);
  readonly showPassword = signal(false);
  readonly permissions = signal<AvailablePermission[]>([]);
  readonly selectedPermissions = signal<Set<string>>(new Set());

  readonly form = new FormGroup(
    {
      nome: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(3), Validators.maxLength(150)],
      }),
      login: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^[a-z0-9._-]{3,80}$/)],
      }),
      email: new FormControl('', {
        nonNullable: true,
        validators: [Validators.email, Validators.maxLength(254)],
      }),
      perfilCodigo: new FormControl<'USUARIO' | 'ADMINISTRADOR'>('USUARIO', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      senhaTemporaria: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)],
      }),
      confirmacao: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required],
      }),
    },
    { validators: passwordMatch },
  );

  constructor(private readonly auth: AuthService) {
    this.form.controls.perfilCodigo.valueChanges.subscribe((profile) => {
      if (profile === 'ADMINISTRADOR') this.selectedPermissions.set(new Set());
      this.created.set(null);
    });

    this.auth
      .listAvailablePermissions()
      .pipe(finalize(() => this.loadingPermissions.set(false)))
      .subscribe({
        next: (permissions) => this.permissions.set(permissions),
        error: (error: HttpErrorResponse) =>
          this.error.set(
            error.error?.message || 'Não foi possível carregar as ferramentas disponíveis.',
          ),
      });
  }

  isPermissionSelected(code: string): boolean {
    return this.selectedPermissions().has(code);
  }

  togglePermission(code: string): void {
    const next = new Set(this.selectedPermissions());
    if (next.has(code)) next.delete(code);
    else next.add(code);
    this.selectedPermissions.set(next);
    this.created.set(null);
  }

  selectAllPermissions(): void {
    this.selectedPermissions.set(new Set(this.permissions().map((permission) => permission.codigo)));
  }

  clearPermissions(): void {
    this.selectedPermissions.set(new Set());
  }

  generateTemporaryPassword(): void {
    const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*';
    const bytes = new Uint32Array(18);
    crypto.getRandomValues(bytes);
    const generated = Array.from(bytes, (value) => alphabet[value % alphabet.length]).join('');
    this.form.patchValue({
      senhaTemporaria: generated,
      confirmacao: generated,
    });
    this.showPassword.set(true);
    this.form.updateValueAndValidity();
  }

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
      permissoes:
        value.perfilCodigo === 'USUARIO' ? [...this.selectedPermissions()] : [],
    };

    this.auth
      .createUser(request)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (user) => {
          this.created.set(user);
          this.form.reset({ perfilCodigo: 'USUARIO' });
          this.selectedPermissions.set(new Set());
          this.showPassword.set(false);
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível cadastrar o usuário.'),
      });
  }
}
