import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../shared/services/auth.service';

function samePassword(group: AbstractControl): ValidationErrors | null {
  const password = group.get('novaSenha')?.value;
  const confirmation = group.get('confirmacao')?.value;
  return password && confirmation && password !== confirmation ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss',
})
export class ChangePasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly showPasswords = signal(false);
  readonly user = this.auth.user;

  readonly form = new FormGroup(
    {
      senhaAtual: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      novaSenha: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)],
      }),
      confirmacao: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    },
    { validators: samePassword },
  );

  submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');
    const { senhaAtual, novaSenha } = this.form.getRawValue();
    this.auth
      .changePassword(senhaAtual, novaSenha)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/'),
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível alterar a senha.'),
      });
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login'),
      error: () => {
        this.auth.clear();
        void this.router.navigateByUrl('/login');
      },
    });
  }
}
