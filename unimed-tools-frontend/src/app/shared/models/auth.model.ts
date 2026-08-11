export interface AuthUser {
  id: number;
  nome: string;
  login: string;
  email: string | null;
  perfil: 'ADMINISTRADOR' | 'USUARIO';
  deveTrocarSenha: boolean;
  permissoes: string[];
}

export type AuthFlowStatus = 'AUTENTICADO' | 'MFA_CONFIGURACAO' | 'MFA_VALIDACAO';

export interface AuthFlowResponse {
  status: AuthFlowStatus;
  desafioToken: string | null;
  segredoMfa: string | null;
  uriMfa: string | null;
  usuario: AuthUser | null;
}

export interface NewUserRequest {
  nome: string;
  login: string;
  email: string | null;
  senhaTemporaria: string;
  perfilCodigo: 'ADMINISTRADOR' | 'USUARIO';
  codigoMfaAdministrador: string;
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
  codigoMfaAdministrador: string;
}

export interface AvailablePermission {
  codigo: string;
  modulo: string;
  descricao: string;
}

export interface OperationResponse {
  mensagem: string;
}
