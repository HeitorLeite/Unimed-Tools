import { of } from 'rxjs';
import { ManagedUser } from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';
import { UserListComponent } from './user-list.component';

describe('UserListComponent', () => {
  const admin: ManagedUser = {
    id: 1,
    nome: 'Administrador',
    login: 'admin',
    email: 'admin@exemplo.com',
    perfil: 'ADMINISTRADOR',
    status: 'ATIVO',
    deveTrocarSenha: false,
    permissoes: ['USUARIOS_EDITAR'],
  };
  const operacional: ManagedUser = {
    id: 2,
    nome: 'Usuário Operacional',
    login: 'operacional',
    email: null,
    perfil: 'USUARIO',
    status: 'ATIVO',
    deveTrocarSenha: false,
    permissoes: [],
  };

  it('carrega e filtra os usuários cadastrados', () => {
    const auth = {
      user: vi.fn(() => admin),
      listUsers: vi.fn(() => of([admin, operacional])),
    } as unknown as AuthService;
    const component = new UserListComponent(auth);

    component.search.set('operacional');

    expect(component.filteredUsers()).toEqual([operacional]);
  });

  it('envia dados editáveis sem novo MFA após o login administrativo', () => {
    const atualizado = {
      ...operacional,
      nome: 'Nome Atualizado',
      perfil: 'ADMINISTRADOR' as const,
    };
    const updateUser = vi.fn(() => of(atualizado));
    const auth = {
      user: vi.fn(() => admin),
      listUsers: vi.fn(() => of([admin, operacional])),
      updateUser,
    } as unknown as AuthService;
    const component = new UserListComponent(auth);
    component.openEdit(operacional);
    component.editForm.patchValue({
      nome: 'Nome Atualizado',
      email: 'NOVO@EXEMPLO.COM',
      perfilCodigo: 'ADMINISTRADOR',
    });

    component.saveEdit();

    expect(updateUser).toHaveBeenCalledWith(2, {
      nome: 'Nome Atualizado',
      email: 'NOVO@EXEMPLO.COM',
      perfilCodigo: 'ADMINISTRADOR',
    });
    expect(component.users().find((user) => user.id === 2)).toEqual(atualizado);
  });
});
