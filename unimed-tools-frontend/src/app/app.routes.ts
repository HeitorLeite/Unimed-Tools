/** Rotas da aplicação organizadas pelas ferramentas exibidas ao usuário. */
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
        loadComponent: () =>
          import('./pages/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'comercial',
        canActivate: [permissionGuard],
        data: { permission: 'COMERCIAL_ACESSAR' },
        loadComponent: () =>
          import('./pages/comercial/comercial.component').then((m) => m.ComercialComponent),
      },
      {
        path: 'assistencial',
        canActivate: [permissionGuard],
        data: { permission: 'ASSISTENCIAL_ACESSAR' },
        loadComponent: () =>
          import('./pages/relatorios/relatorios-personalizados/relatorios-personalizados.component')
            .then((m) => m.RelatoriosPersonalizadosComponent),
      },
      {
        path: 'revisao-contas',
        canActivate: [permissionGuard],
        data: { permission: 'REVISAO_CONTAS_ACESSAR' },
        loadComponent: () =>
          import('./pages/xml/xml-tools/xml-tools.component').then((m) => m.XmlToolsComponent),
      },
      {
        path: 'unica',
        canActivate: [permissionGuard],
        data: { permission: 'UNICA_ACESSAR' },
        loadComponent: () =>
          import('./pages/ans/corretor-rede/corretor-rede.component').then(
            (m) => m.CorretorRedeComponent,
          ),
      },
      {
        path: 'hospital',
        canActivate: [permissionGuard],
        data: { permission: 'HOSPITAL_ACESSAR' },
        loadComponent: () =>
          import('./pages/hospital/hospital.component').then((m) => m.HospitalComponent),
      },
      {
        path: 'gestao-risco',
        canActivate: [permissionGuard],
        data: { permission: 'GESTAO_RISCO_ACESSAR' },
        loadComponent: () =>
          import('./pages/gestao-risco/gestao-risco.component').then(
            (m) => m.GestaoRiscoComponent,
          ),
      },
      {
        path: 'ti',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./pages/ti/ti.component').then((m) => m.TiComponent),
      },
      {
        path: 'ferramentas/:slug',
        canActivate: [permissionGuard],
        data: { permission: 'RELATORIOS_ACESSAR' },
        loadComponent: () =>
          import('./pages/tools/custom-report/custom-report.component').then(
            (m) => m.CustomReportComponent,
          ),
      },
      {
        path: 'perfil',
        loadComponent: () =>
          import('./pages/profile/profile.component').then((m) => m.ProfileComponent),
      },

      // Administração de contas.
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
        path: 'usuarios/cadastrados',
        canActivate: [adminGuard, permissionGuard],
        data: { permission: 'USUARIOS_EDITAR' },
        loadComponent: () =>
          import('./pages/users/user-list/user-list.component').then((m) => m.UserListComponent),
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

      // Ferramentas legadas ainda mantidas para compatibilidade.
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
        path: 'fechamento/corretor',
        canActivate: [permissionGuard],
        data: { permission: 'APLICACAO_ACESSAR' },
        loadComponent: () =>
          import('./pages/fechamento/corretor-fechamento/corretor-fechamento.component').then(
            (m) => m.CorretorFechamentoComponent,
          ),
      },

      // Redirecionamentos dos endereços anteriores.
      { path: 'relatorios', redirectTo: 'assistencial', pathMatch: 'full' },
      { path: 'xml/ferramentas', redirectTo: 'revisao-contas', pathMatch: 'full' },
      { path: 'xml/corretor', redirectTo: 'revisao-contas', pathMatch: 'full' },
      { path: 'xml/removedor', redirectTo: 'revisao-contas', pathMatch: 'full' },
      { path: 'ans/corretor-rede', redirectTo: 'unica', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];
