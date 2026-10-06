import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { NativeToolAdminItem } from '../../../shared/models/tool.model';
import { ToolRegistryService } from '../../../shared/services/tool-registry.service';

@Component({
  selector: 'app-native-tool-manager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './native-tool-manager.component.html',
  styleUrl: './native-tool-manager.component.scss',
})
export class NativeToolManagerComponent implements OnInit {
  items: NativeToolAdminItem[] = [];
  editingId = '';
  nome = '';
  descricao = '';
  ativo = true;
  loading = true;
  saving = false;
  error = '';
  success = '';

  constructor(
    private readonly registry: ToolRegistryService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  edit(item: NativeToolAdminItem): void {
    this.editingId = item.tool.id;
    this.nome = item.config?.nome?.trim() || item.tool.nome;
    this.descricao = item.config?.descricao?.trim() || item.tool.descricao;
    this.ativo = item.config?.ativo ?? true;
    this.error = '';
    this.success = '';
  }

  cancel(): void {
    this.editingId = '';
    this.error = '';
  }

  save(): void {
    if (!this.editingId || this.saving) return;
    if (!this.nome.trim() || !this.descricao.trim()) {
      this.error = 'Nome e descrição não podem ficar vazios.';
      return;
    }
    this.saving = true;
    this.error = '';
    this.success = '';
    const id = this.editingId;
    this.registry
      .saveNativeConfig(id, this.nome.trim(), this.descricao.trim(), this.ativo)
      .pipe(
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.items = this.registry.listNativeAdmin();
          const current = this.items.find((item) => item.tool.id === id);
          this.success = current
            ? `Página “${current.tool.nome}” atualizada.`
            : 'Página atualizada.';
          this.editingId = '';
        },
        error: (error: any) =>
          (this.error =
            error?.error?.message || error?.message || 'Não foi possível atualizar a página.'),
      });
  }

  reset(item: NativeToolAdminItem): void {
    if (!item.config) return;
    if (
      !window.confirm(`Restaurar nome, descrição e visibilidade padrão de “${item.tool.nome}”?`)
    ) {
      return;
    }
    this.error = '';
    this.success = '';
    this.registry
      .resetNativeConfig(item.tool.id)
      .pipe(finalize(() => this.cdr.markForCheck()))
      .subscribe({
        next: () => {
          this.items = this.registry.listNativeAdmin();
          this.success = 'Configuração padrão restaurada.';
          if (this.editingId === item.tool.id) this.editingId = '';
        },
        error: (error: any) =>
          (this.error = error?.error?.message || 'Não foi possível restaurar a página.'),
      });
  }

  isEditing(id: string): boolean {
    return this.editingId === id;
  }

  permission(item: NativeToolAdminItem): string {
    if (item.tool.adminOnly) return 'Somente administrador';
    return (
      item.tool.permission?.replace(/_ACESSAR$/, '').replace(/_/g, ' ') || 'Usuário autenticado'
    );
  }

  private reload(): void {
    this.loading = true;
    this.registry
      .refresh()
      .pipe(
        finalize(() => {
          this.loading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => (this.items = this.registry.listNativeAdmin()),
        error: (error: any) =>
          (this.error =
            error?.error?.message || 'Não foi possível carregar as páginas principais.'),
      });
  }
}
