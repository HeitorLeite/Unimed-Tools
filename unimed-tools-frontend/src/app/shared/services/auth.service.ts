import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthFlowResponse,
  AuthUser,
  AvailablePermission,
  CreatedUser,
  ManagedUser,
  NewUserRequest,
  OperationResponse,
  UpdateUserRequest,
} from '../models/auth.model';
import { AuthStateService } from './auth-state.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiUrl}/auth`;
  private initialization?: Promise<void>;
  private readonly http = inject(HttpClient);
  private readonly state = inject(AuthStateService);

  readonly user = this.state.user;
  readonly authenticated = this.state.authenticated;

  initialize(): Promise<void> {
    if (!this.initialization) {
      this.initialization = firstValueFrom(this.http.get(`${this.baseUrl}/csrf`))
        .then(() => firstValueFrom(this.http.get<AuthUser>(`${this.baseUrl}/me`)))
        .then((user) => this.state.setUser(user))
        .catch(() => this.state.clear());
    }
    return this.initialization;
  }

  login(login: string, senha: string): Observable<AuthFlowResponse> {
    return this.http
      .post<AuthFlowResponse>(`${this.baseUrl}/login`, { login, senha })
      .pipe(tap((response) => this.applyResponse(response)));
  }

  verifyMfa(desafioToken: string, codigo: string): Observable<AuthFlowResponse> {
    return this.http
      .post<AuthFlowResponse>(`${this.baseUrl}/mfa/verificar`, { desafioToken, codigo })
      .pipe(tap((response) => this.applyResponse(response)));
  }

  changePassword(senhaAtual: string, novaSenha: string): Observable<AuthFlowResponse> {
    return this.http
      .post<AuthFlowResponse>(`${this.baseUrl}/senha`, { senhaAtual, novaSenha })
      .pipe(tap((response) => this.applyResponse(response)));
  }

  createUser(request: NewUserRequest): Observable<CreatedUser> {
    return this.http.post<CreatedUser>(`${environment.apiUrl}/usuarios`, request);
  }

  listUsers(): Observable<ManagedUser[]> {
    return this.http.get<ManagedUser[]>(`${environment.apiUrl}/usuarios`);
  }

  updateUser(userId: number, request: UpdateUserRequest): Observable<ManagedUser> {
    return this.http.put<ManagedUser>(`${environment.apiUrl}/usuarios/${userId}`, request).pipe(
      tap((updated) => {
        const current = this.state.user();
        if (current?.id === updated.id) {
          this.state.setUser({
            ...current,
            nome: updated.nome,
            email: updated.email,
            perfil: updated.perfil,
            permissoes: updated.permissoes,
          });
        }
      }),
    );
  }

  deleteUser(userId: number): Observable<OperationResponse> {
    return this.http.delete<OperationResponse>(`${environment.apiUrl}/usuarios/${userId}`);
  }

  listAvailablePermissions(): Observable<AvailablePermission[]> {
    return this.http.get<AvailablePermission[]>(
      `${environment.apiUrl}/usuarios/permissoes-disponiveis`,
    );
  }

  updateUserPermissions(userId: number, permissoes: string[]): Observable<OperationResponse> {
    return this.http.put<OperationResponse>(`${environment.apiUrl}/usuarios/${userId}/permissoes`, {
      permissoes,
    });
  }

  resetUserPassword(userId: number, senhaTemporaria: string): Observable<OperationResponse> {
    return this.http.post<OperationResponse>(
      `${environment.apiUrl}/usuarios/${userId}/resetar-senha`,
      { senhaTemporaria },
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, {}).pipe(
      tap(() => {
        this.state.clear();
        this.initialization = undefined;
      }),
    );
  }

  hasPermission(permission: string): boolean {
    return this.state.hasPermission(permission);
  }

  clear(): void {
    this.state.clear();
    this.initialization = undefined;
  }

  private applyResponse(response: AuthFlowResponse): void {
    if (response.status === 'AUTENTICADO' && response.usuario) {
      this.state.setUser(response.usuario);
    }
  }
}
