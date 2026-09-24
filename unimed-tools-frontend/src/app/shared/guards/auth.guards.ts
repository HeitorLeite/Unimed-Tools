import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const applicationGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  const user = auth.user();
  if (!user) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  if (user.deveTrocarSenha) return router.createUrlTree(['/alterar-senha']);
  return true;
};

export const authenticatedGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  return auth.user() ? true : router.createUrlTree(['/login']);
};

export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  const user = auth.user();
  if (!user) return true;
  return router.createUrlTree([user.deveTrocarSenha ? '/alterar-senha' : '/']);
};

export const permissionGuard: CanActivateFn = async (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  const permission = route.data['permission'] as string | undefined;
  if (!auth.user()) return router.createUrlTree(['/login']);
  return !permission || auth.hasPermission(permission)
    ? true
    : router.createUrlTree(['/']);
};

export const adminGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.initialize();
  const user = auth.user();
  if (!user) return router.createUrlTree(['/login']);
  return user.perfil === 'ADMINISTRADOR' ? true : router.createUrlTree(['/']);
};
