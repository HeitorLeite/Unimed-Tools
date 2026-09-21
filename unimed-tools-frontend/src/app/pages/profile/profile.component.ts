import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../shared/services/auth.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent {
  readonly user;

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {
    this.user = auth.user;
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

  permissionLabel(permission: string): string {
    const labels: Record<string, string> = {
      COMERCIAL_ACESSAR: 'Comercial',
      ASSISTENCIAL_ACESSAR: 'Assistencial',
      REVISAO_CONTAS_ACESSAR: 'Revisão de Contas',
      UNICA_ACESSAR: 'Única',
      HOSPITAL_ACESSAR: 'Hospital',
      GESTAO_RISCO_ACESSAR: 'Gestão de Risco',
      XML_ACESSAR: 'Revisão de Contas (compatibilidade)',
      ANS_ACESSAR: 'Única (compatibilidade)',
      BI_ACESSAR: 'Business Intelligence',
      RELATORIOS_ACESSAR: 'Relatórios (permissão técnica)',
      FERRAMENTAS_ADMINISTRAR: 'Administração de ferramentas',
      USUARIOS_VISUALIZAR: 'Visualização de usuários',
      USUARIOS_CRIAR: 'Cadastro de usuários',
      USUARIOS_EDITAR: 'Edição de usuários',
    };
    return labels[permission] ?? permission.replace(/_/g, ' ');
  }
}
