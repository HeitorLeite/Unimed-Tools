import { HttpErrorResponse } from '@angular/common/http';
import { isUnauthenticatedError } from './auth.interceptor';

describe('authInterceptor', () => {
  it('redireciona somente quando a sessão não está autenticada', () => {
    const error = new HttpErrorResponse({
      status: 401,
      error: { codigo: 'NAO_AUTENTICADO', message: 'Faça login para continuar.' },
    });

    expect(isUnauthenticatedError(error)).toBe(true);
  });

  it('mantém a sessão quando o código MFA administrativo é inválido', () => {
    const error = new HttpErrorResponse({
      status: 401,
      error: { codigo: 'MFA_INVALIDO', message: 'Use um código novo e válido.' },
    });

    expect(isUnauthenticatedError(error)).toBe(false);
  });
});
