import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../shared/services/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let auth: { login: ReturnType<typeof vi.fn>; verifyMfa: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    auth = {
      login: vi.fn(),
      verifyMfa: vi.fn().mockReturnValue(
        of({
          status: 'AUTENTICADO',
          desafioToken: null,
          segredoMfa: null,
          uriMfa: null,
          usuario: {
            id: 1,
            nome: 'Administrador',
            login: 'admin.teste',
            email: null,
            perfil: 'ADMINISTRADOR',
            deveTrocarSenha: true,
            permissoes: [],
          },
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(),
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
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

  it('envia o MFA pelo ngSubmit sem recarregar a página', () => {
    (component as unknown as { handleFlow(response: unknown): void }).handleFlow({
      status: 'MFA_CONFIGURACAO',
      desafioToken: 'desafio-teste',
      segredoMfa: 'SEGREDOBASE32',
      uriMfa: 'otpauth://teste',
      usuario: null,
    });
    component.mfaForm.controls.codigo.setValue('123456');
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    const event = new Event('submit', { bubbles: true, cancelable: true });
    const dispatched = form.dispatchEvent(event);

    expect(dispatched).toBe(false);
    expect(event.defaultPrevented).toBe(true);
    expect(auth.verifyMfa).toHaveBeenCalledWith('desafio-teste', '123456');
  });
});
