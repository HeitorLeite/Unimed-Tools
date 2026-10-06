import { TestBed } from '@angular/core/testing';
import { AuthStateService } from './auth-state.service';

describe('AuthStateService', () => {
  it('mantém usuário em memória e verifica permissões', () => {
    const service = TestBed.inject(AuthStateService);
    service.setUser({
      id: 1,
      nome: 'Usuário de Teste',
      login: 'usuario.teste',
      email: null,
      perfil: 'USUARIO',
      deveTrocarSenha: false,
      permissoes: ['XML_ACESSAR'],
    });

    expect(service.authenticated()).toBe(true);
    expect(service.hasPermission('XML_ACESSAR')).toBe(true);
    expect(service.hasPermission('USUARIOS_CRIAR')).toBe(false);

    service.clear();
    expect(service.authenticated()).toBe(false);
  });
});
