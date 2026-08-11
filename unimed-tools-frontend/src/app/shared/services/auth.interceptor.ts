import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthStateService } from './auth-state.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);
  const state = inject(AuthStateService);
  const authenticatedRequest = request.clone({ withCredentials: true });

  return next(authenticatedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      const publicAuthCall =
        request.url.endsWith('/auth/login') ||
        request.url.endsWith('/auth/mfa/verificar') ||
        request.url.endsWith('/auth/csrf') ||
        request.url.endsWith('/auth/me');

      if (error.status === 401 && !publicAuthCall) {
        state.clear();
        const returnUrl = router.url.startsWith('/login') ? '/' : router.url;
        void router.navigate(['/login'], { queryParams: { returnUrl } });
      }
      return throwError(() => error);
    }),
  );
};
