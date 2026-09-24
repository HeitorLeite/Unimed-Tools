export interface AuthUser {
  id: number;
  nome: string;
  login: string;
  email: string | null;
  perfil: 'ADMINISTRADOR' | 'USUARIO';
  deveTrocarSenha: boolean;
  permissoes: string[];
}

export interface AuthFlowResponse {
  status: 'AUTENTICADO';
  usuario: AuthUser | null;
}

export interface NewUserRequest {
  nome: string;
  login: string;
  email: string | null;
  senhaTemporaria: string;
  perfilCodigo: 'ADMINISTRADOR' | 'USUARIO';
  permissoes: string[];
}

export interface CreatedUser {
  id: number;
  nome: string;
  login: string;
  email: string | null;
  perfil: 'ADMINISTRADOR' | 'USUARIO';
  status: string;
  deveTrocarSenha: boolean;
}

export interface ManagedUser extends CreatedUser {
  permissoes: string[];
}

export interface UpdateUserRequest {
  nome: string;
  email: string | null;
  perfilCodigo: 'ADMINISTRADOR' | 'USUARIO';
}

export interface AvailablePermission {
  codigo: string;
  modulo: string;
  descricao: string;
}

export interface OperationResponse {
  mensagem: string;
}
