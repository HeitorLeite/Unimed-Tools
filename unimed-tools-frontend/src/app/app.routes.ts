/** Mapa central de rotas públicas, autenticadas e autorizadas por permissão. */
import { Routes } from '@angular/router';
import {
  applicationGuard,
  adminGuard,
  authenticatedGuard,
  guestGuard,
  permissionGuard,
} from './shared/guards/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./pages/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'alterar-senha',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./pages/auth/change-password/change-password.component').then(
        (m) => m.ChangePasswordComponent,
      ),
  },
  {
    path: '',
    canActivate: [applicationGuard],
    loadComponent: () =>
      import('./layout/main-layout/main-layout.component').then((m) => m.MainLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'xml/ferramentas',
        canActivate: [permissionGuard],
        data: { permission: 'XML_ACESSAR' },
        loadComponent: () =>
          import('./pages/xml/xml-tools/xml-tools.component').then((m) => m.XmlToolsComponent),
      },
      {
        path: 'fechamento/corretor',
        canActivate: [permissionGuard],
        data: { permission: 'APLICACAO_ACESSAR' },
        loadComponent: () =>
          import('./pages/fechamento/corretor-fechamento/corretor-fechamento.component').then(
            (m) => m.CorretorFechamentoComponent,
          ),
      },
      {
        path: 'ans/corretor-rede',
        canActivate: [permissionGuard],
        data: { permission: 'ANS_ACESSAR' },
        loadComponent: () =>
          import('./pages/ans/corretor-rede/corretor-rede.component').then(
            (m) => m.CorretorRedeComponent,
          ),
      },
      {
        path: 'bi/especialidade-medica',
        canActivate: [permissionGuard],
        data: { permission: 'BI_ACESSAR' },
        loadComponent: () =>
          import('./pages/bi/especialidade-medica/especialidade-medica.component').then(
            (m) => m.EspecialidadeMedicaComponent,
          ),
      },
      {
        path: 'relatorios',
        canActivate: [permissionGuard],
        data: { permission: 'RELATORIOS_ACESSAR' },
        loadComponent: () =>
          import('./pages/relatorios/relatorios.component').then((m) => m.RelatoriosComponent),
      },
      {
        path: 'usuarios',
        canActivate: [adminGuard, permissionGuard],
        data: { permission: 'USUARIOS_VISUALIZAR' },
        loadComponent: () =>
          import('./pages/users/user-management/user-management.component').then(
            (m) => m.UserManagementComponent,
          ),
      },
      {
        path: 'usuarios/novo',
        canActivate: [adminGuard, permissionGuard],
        data: { permission: 'USUARIOS_CRIAR' },
        loadComponent: () =>
          import('./pages/users/user-registration/user-registration.component').then(
            (m) => m.UserRegistrationComponent,
          ),
      },
      {
        path: 'usuarios/permissoes',
        canActivate: [adminGuard, permissionGuard],
        data: { permission: 'USUARIOS_EDITAR' },
        loadComponent: () =>
          import('./pages/users/user-permissions/user-permissions.component').then(
            (m) => m.UserPermissionsComponent,
          ),
      },
      {
        path: 'usuarios/resetar-senha',
        canActivate: [adminGuard, permissionGuard],
        data: { permission: 'USUARIOS_EDITAR' },
        loadComponent: () =>
          import('./pages/users/user-password-reset/user-password-reset.component').then(
            (m) => m.UserPasswordResetComponent,
          ),
      },
      { path: 'xml/corretor', redirectTo: 'xml/ferramentas' },
      { path: 'xml/removedor', redirectTo: 'xml/ferramentas' },
    ],
  },
  { path: '**', redirectTo: '' },
];
