import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../shared/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  readonly loading = signal(false);
  readonly showPassword = signal(false);
  readonly error = signal('');

  readonly credentialsForm = new FormGroup({
    login: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    senha: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(128)],
    }),
  });

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
  ) {}

  submitCredentials(): void {
    if (this.credentialsForm.invalid || this.loading()) {
      this.credentialsForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set('');
    const { login, senha } = this.credentialsForm.getRawValue();
    this.auth.login(login, senha)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          if (!response.usuario) {
            this.error.set('O servidor não devolveu os dados da conta.');
            return;
          }
          const requested = this.route.snapshot.queryParamMap.get('returnUrl');
          const returnUrl =
            requested?.startsWith('/') && !requested.startsWith('//') ? requested : '/';
          void this.router.navigateByUrl(
            response.usuario.deveTrocarSenha ? '/alterar-senha' : returnUrl,
          );
        },
        error: (error) => this.error.set(this.messageFrom(error)),
      });
  }

  private messageFrom(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message || 'Não foi possível entrar. Verifique a conexão com o servidor.';
    }
    return 'Não foi possível entrar. Tente novamente.';
  }
}
