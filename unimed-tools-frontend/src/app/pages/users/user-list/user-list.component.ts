import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ManagedUser, UpdateUserRequest } from '../../../shared/models/auth.model';
import { AuthService } from '../../../shared/services/auth.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss',
})
export class UserListComponent {
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly search = signal('');
  readonly users = signal<ManagedUser[]>([]);
  readonly editingUser = signal<ManagedUser | null>(null);
  readonly deletingUser = signal<ManagedUser | null>(null);
  readonly currentUserId: number | null;

  readonly filteredUsers = computed(() => {
    const term = this.search().trim().toLocaleLowerCase('pt-BR');
    if (!term) return this.users();
    return this.users().filter((user) =>
      [user.nome, user.login, user.email ?? '', this.profileLabel(user.perfil)].some((value) =>
        value.toLocaleLowerCase('pt-BR').includes(term),
      ),
    );
  });

  readonly editForm = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3), Validators.maxLength(150)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.email, Validators.maxLength(254)],
    }),
    perfilCodigo: new FormControl<'ADMINISTRADOR' | 'USUARIO'>('USUARIO', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  constructor(private readonly auth: AuthService) {
    this.currentUserId = this.auth.user()?.id ?? null;
    this.load();
  }

  openEdit(user: ManagedUser): void {
    this.editingUser.set(user);
    this.error.set('');
    this.success.set('');
    this.editForm.reset({
      nome: user.nome,
      email: user.email ?? '',
      perfilCodigo: user.perfil,
    });
    if (user.id === this.currentUserId) this.editForm.controls.perfilCodigo.disable();
    else this.editForm.controls.perfilCodigo.enable();
  }

  closeEdit(): void {
    if (this.saving()) return;
    this.editingUser.set(null);
    this.editForm.controls.perfilCodigo.enable();
  }

  saveEdit(): void {
    const user = this.editingUser();
    if (!user || this.editForm.invalid || this.saving()) {
      this.editForm.markAllAsTouched();
      return;
    }
    const value = this.editForm.getRawValue();
    const request: UpdateUserRequest = {
      nome: value.nome,
      email: value.email || null,
      perfilCodigo: value.perfilCodigo,
    };
    this.saving.set(true);
    this.error.set('');
    this.auth
      .updateUser(user.id, request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (updated) => {
          this.users.update((users) =>
            users.map((item) => (item.id === updated.id ? updated : item)),
          );
          this.editingUser.set(null);
          this.success.set('Informações do usuário atualizadas com sucesso.');
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível atualizar o usuário.'),
      });
  }

  openDelete(user: ManagedUser): void {
    if (user.id === this.currentUserId) return;
    this.deletingUser.set(user);
    this.error.set('');
    this.success.set('');
  }

  closeDelete(): void {
    if (this.deleting()) return;
    this.deletingUser.set(null);
  }

  confirmDelete(): void {
    const user = this.deletingUser();
    if (!user || this.deleting()) return;
    this.deleting.set(true);
    this.error.set('');
    this.auth
      .deleteUser(user.id)
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: (response) => {
          this.users.update((users) => users.filter((item) => item.id !== user.id));
          this.deletingUser.set(null);
          this.success.set(response.mensagem);
        },
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível excluir o usuário.'),
      });
  }

  profileLabel(profile: ManagedUser['perfil']): string {
    return profile === 'ADMINISTRADOR' ? 'Administrador' : 'Operacional';
  }

  statusLabel(status: string): string {
    if (status === 'BLOQUEADO') return 'Bloqueado';
    if (status === 'PENDENTE_ATIVACAO') return 'Pendente';
    return 'Ativo';
  }

  trackByUserId(_index: number, user: ManagedUser): number {
    return user.id;
  }

  private load(): void {
    this.auth
      .listUsers()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (users) => this.users.set(users),
        error: (error: HttpErrorResponse) =>
          this.error.set(error.error?.message || 'Não foi possível carregar os usuários.'),
      });
  }
}
