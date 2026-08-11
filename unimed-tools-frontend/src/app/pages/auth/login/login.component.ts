import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthFlowResponse } from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';

type LoginStage = 'CREDENCIAIS' | 'MFA_CONFIGURACAO' | 'MFA_VALIDACAO';

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
  readonly stage = signal<LoginStage>('CREDENCIAIS');
  readonly error = signal('');
  readonly mfaSecret = signal('');

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

  readonly mfaForm = new FormGroup({
    codigo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });

  private challengeToken = '';

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
    this.auth
      .login(login, senha)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => this.handleFlow(response),
        error: (error) => this.error.set(this.messageFrom(error)),
      });
  }

  submitMfa(): void {
    if (this.mfaForm.invalid || !this.challengeToken || this.loading()) {
      this.mfaForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.auth
      .verifyMfa(this.challengeToken, this.mfaForm.controls.codigo.value)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => this.handleFlow(response),
        error: (error) => this.error.set(this.messageFrom(error)),
      });
  }

  restart(): void {
    this.challengeToken = '';
    this.mfaForm.reset();
    this.mfaSecret.set('');
    this.error.set('');
    this.stage.set('CREDENCIAIS');
  }

  private handleFlow(response: AuthFlowResponse): void {
    if (response.status === 'AUTENTICADO' && response.usuario) {
      const requested = this.route.snapshot.queryParamMap.get('returnUrl');
      const returnUrl = requested?.startsWith('/') && !requested.startsWith('//') ? requested : '/';
      void this.router.navigateByUrl(response.usuario.deveTrocarSenha ? '/alterar-senha' : returnUrl);
      return;
    }
    this.challengeToken = response.desafioToken ?? '';
    this.mfaSecret.set(response.segredoMfa ?? '');
    this.mfaForm.reset();
    if (response.status !== 'AUTENTICADO') this.stage.set(response.status);
  }

  private messageFrom(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.message || 'Não foi possível entrar. Verifique a conexão com o servidor.';
    }
    return 'Não foi possível entrar. Tente novamente.';
  }
}
