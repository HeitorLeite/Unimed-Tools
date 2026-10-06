import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../shared/services/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: { login: ReturnType<typeof vi.fn> };
  const navigateByUrl = vi.fn();

  beforeEach(async () => {
    auth = {
      login: vi.fn().mockReturnValue(
        of({
          status: 'AUTENTICADO',
          usuario: {
            id: 1,
            nome: 'Administrador',
            login: 'admin.teste',
            email: null,
            perfil: 'ADMINISTRADOR',
            deveTrocarSenha: false,
            permissoes: [],
          },
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: Router, useValue: { navigateByUrl } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => null } } },
        },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('autentica com login e senha e segue para o inicio', () => {
    component.credentialsForm.setValue({
      login: 'admin.teste',
      senha: 'SenhaSegura!123',
    });

    component.submitCredentials();

    expect(auth.login).toHaveBeenCalledWith('admin.teste', 'SenhaSegura!123');
    expect(navigateByUrl).toHaveBeenCalledWith('/');
  });
});
