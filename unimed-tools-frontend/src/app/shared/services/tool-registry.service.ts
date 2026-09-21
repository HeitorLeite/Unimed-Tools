import { HttpClient } from '@angular/common/http';
<<<<<<< HEAD
import { Injectable, signal } from '@angular/core';
=======
import { Injectable } from '@angular/core';
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
import { Observable, forkJoin, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CORE_TOOLS } from '../constants/tools.constants';
import {
  CustomReportTool,
  NativeToolAdminItem,
  NativeToolConfig,
  ToolDefinition,
} from '../models/tool.model';
import { AuthService } from './auth.service';

const RECENT_TOOLS_KEY = 'unimed-tools.ferramentas-recentes.v1';

interface BackendTool {
  id: number;
  slug: string;
  nome: string;
  descricao: string;
  apiNome: string;
  filtros: string[];
  colunasPreview: string[];
  criadoEm: string;
  atualizadoEm: string;
}

interface BackendNativeTool {
  id: string;
  nome: string | null;
  descricao: string | null;
  ativo: boolean;
  atualizadoEm: string;
}

@Injectable({ providedIn: 'root' })
export class ToolRegistryService {
<<<<<<< HEAD
  private readonly customTools = signal<CustomReportTool[]>([]);
  private readonly nativeConfigs = signal(new Map<string, NativeToolConfig>());
=======
  private customTools: CustomReportTool[] = [];
  private nativeConfigs = new Map<string, NativeToolConfig>();
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  private readonly baseUrl = `${environment.apiUrl}/ferramentas`;

  constructor(
    private readonly auth: AuthService,
    private readonly http: HttpClient,
  ) {}

  refresh(): Observable<CustomReportTool[]> {
    return forkJoin({
      custom: this.http.get<BackendTool[]>(this.baseUrl),
      native: this.http.get<BackendNativeTool[]>(`${this.baseUrl}/nativas`),
    }).pipe(
      tap(({ custom, native }) => {
<<<<<<< HEAD
        this.customTools.set(custom.map((item) => this.fromBackend(item)));
        this.nativeConfigs.set(
          new Map(native.map((item) => [item.id, this.fromNativeBackend(item)])),
        );
      }),
      map(() => [...this.customTools()]),
=======
        this.customTools = custom.map((item) => this.fromBackend(item));
        this.nativeConfigs = new Map(
          native.map((item) => [item.id, this.fromNativeBackend(item)]),
        );
      }),
      map(() => [...this.customTools]),
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    );
  }

  listAll(): ToolDefinition[] {
<<<<<<< HEAD
    const natives = CORE_TOOLS.filter(
      (tool) => this.nativeConfigs().get(tool.id)?.ativo !== false,
    ).map((tool) => this.applyNativeConfig(tool));
    return [...natives, ...this.customTools().map((tool) => this.toDefinition(tool))];
=======
    const natives = CORE_TOOLS
      .filter((tool) => this.nativeConfigs.get(tool.id)?.ativo !== false)
      .map((tool) => this.applyNativeConfig(tool));
    return [...natives, ...this.customTools.map((tool) => this.toDefinition(tool))];
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  listAccessible(): ToolDefinition[] {
    const user = this.auth.user();
    return this.listAll().filter((tool) => {
      if (tool.adminOnly && user?.perfil !== 'ADMINISTRADOR') return false;
      return !tool.permission || this.auth.hasPermission(tool.permission);
    });
  }

  listNativeAdmin(): NativeToolAdminItem[] {
    return CORE_TOOLS.map((original) => ({
      tool: this.applyNativeConfig(original),
<<<<<<< HEAD
      config: this.nativeConfigs().get(original.id) ?? null,
=======
      config: this.nativeConfigs.get(original.id) ?? null,
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    }));
  }

  saveNativeConfig(
    id: string,
    nome: string | null,
    descricao: string | null,
    ativo: boolean,
  ): Observable<NativeToolConfig> {
<<<<<<< HEAD
    return this.http
      .put<BackendNativeTool>(`${this.baseUrl}/nativas/${id}`, {
        nome,
        descricao,
        ativo,
      })
      .pipe(
        map((item) => this.fromNativeBackend(item)),
        tap((saved) =>
          this.nativeConfigs.update((configs) => new Map(configs).set(saved.id, saved)),
        ),
      );
=======
    return this.http.put<BackendNativeTool>(`${this.baseUrl}/nativas/${id}`, {
      nome,
      descricao,
      ativo,
    }).pipe(
      map((item) => this.fromNativeBackend(item)),
      tap((saved) => this.nativeConfigs.set(saved.id, saved)),
    );
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  resetNativeConfig(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/nativas/${id}`).pipe(
<<<<<<< HEAD
      tap(() =>
        this.nativeConfigs.update((configs) => {
          const next = new Map(configs);
          next.delete(id);
          return next;
        }),
      ),
=======
      tap(() => this.nativeConfigs.delete(id)),
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    );
  }

  search(term: string): ToolDefinition[] {
    const normalized = this.normalize(term);
    if (!normalized) return this.listAccessible();
    return this.listAccessible().filter((tool) =>
<<<<<<< HEAD
      this.normalize(
        [tool.nome, tool.categoria, tool.descricao, ...tool.keywords].join(' '),
      ).includes(normalized),
=======
      this.normalize([tool.nome, tool.categoria, tool.descricao, ...tool.keywords].join(' '))
        .includes(normalized),
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    );
  }

  listCustom(): CustomReportTool[] {
<<<<<<< HEAD
    return [...this.customTools()];
  }

  findCustom(slug: string): CustomReportTool | undefined {
    return this.customTools().find((tool) => tool.slug === slug);
=======
    return [...this.customTools];
  }

  findCustom(slug: string): CustomReportTool | undefined {
    return this.customTools.find((tool) => tool.slug === slug);
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  saveCustom(
    input: Omit<CustomReportTool, 'id' | 'criadoEm' | 'atualizadoEm'> & { id?: string },
  ): Observable<CustomReportTool> {
    const slug = this.slugify(input.slug || input.nome);
    if (!slug) throw new Error('Informe um nome válido para a ferramenta.');

    const body = {
      slug,
      nome: input.nome.trim(),
      descricao: input.descricao.trim(),
      apiNome: input.apiNome.trim(),
      filtros: [...new Set(input.filtros.map((item) => item.trim()).filter(Boolean))],
      colunasPreview: [...new Set(input.colunasPreview.map((item) => item.trim()).filter(Boolean))],
    };
    const request = input.id
      ? this.http.put<BackendTool>(`${this.baseUrl}/${input.id}`, body)
      : this.http.post<BackendTool>(this.baseUrl, body);

    return request.pipe(
      map((item) => this.fromBackend(item)),
      tap((saved) => {
<<<<<<< HEAD
        this.customTools.update((tools) =>
          tools.some((tool) => tool.id === saved.id)
            ? tools.map((tool) => (tool.id === saved.id ? saved : tool))
            : [...tools, saved].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
        );
=======
        const exists = this.customTools.some((tool) => tool.id === saved.id);
        this.customTools = exists
          ? this.customTools.map((tool) => tool.id === saved.id ? saved : tool)
          : [...this.customTools, saved].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
      }),
    );
  }

  deleteCustom(id: string): Observable<void> {
<<<<<<< HEAD
    return this.http
      .delete<void>(`${this.baseUrl}/${id}`)
      .pipe(tap(() => this.customTools.update((tools) => tools.filter((tool) => tool.id !== id))));
=======
    return this.http.delete<void>(`${this.baseUrl}/${id}`).pipe(
      tap(() => this.customTools = this.customTools.filter((tool) => tool.id !== id)),
    );
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  recordOpened(id: string): void {
    const next = [
      id,
      ...this.read<string[]>(RECENT_TOOLS_KEY, []).filter((item) => item !== id),
    ].slice(0, 5);
    this.write(RECENT_TOOLS_KEY, next);
  }

  recent(): ToolDefinition[] {
    const byId = new Map(this.listAccessible().map((tool) => [tool.id, tool]));
    return this.read<string[]>(RECENT_TOOLS_KEY, [])
      .map((id) => byId.get(id))
      .filter((tool): tool is ToolDefinition => Boolean(tool))
      .slice(0, 3);
  }

  private applyNativeConfig(tool: ToolDefinition): ToolDefinition {
<<<<<<< HEAD
    const config = this.nativeConfigs().get(tool.id);
=======
    const config = this.nativeConfigs.get(tool.id);
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    if (!config) return { ...tool };
    return {
      ...tool,
      nome: config.nome?.trim() || tool.nome,
      descricao: config.descricao?.trim() || tool.descricao,
    };
  }

  private fromNativeBackend(item: BackendNativeTool): NativeToolConfig {
    return {
      id: item.id,
      nome: item.nome,
      descricao: item.descricao,
      ativo: item.ativo,
      atualizadoEm: item.atualizadoEm,
    };
  }

  private fromBackend(item: BackendTool): CustomReportTool {
    return {
      id: String(item.id),
      slug: item.slug,
      nome: item.nome,
      descricao: item.descricao,
      apiNome: item.apiNome,
      filtros: item.filtros ?? [],
      colunasPreview: item.colunasPreview ?? [],
      criadoEm: item.criadoEm,
      atualizadoEm: item.atualizadoEm,
    };
  }

  private toDefinition(tool: CustomReportTool): ToolDefinition {
    return {
      id: `custom-${tool.id}`,
      nome: tool.nome,
      categoria: 'Relatório configurado pela TI',
      descricao: tool.descricao,
      route: `/ferramentas/${tool.slug}`,
      icon: 'relatorios',
      permission: 'RELATORIOS_ACESSAR',
      keywords: [tool.apiNome, ...tool.filtros, ...tool.colunasPreview],
      kind: 'custom-report',
    };
  }

  private slugify(value: string): string {
<<<<<<< HEAD
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 80);
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
=======
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
      .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80);
  }

  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
  }

  private read<T>(key: string, fallback: T): T {
    if (typeof localStorage === 'undefined') return fallback;
    try {
      const raw = localStorage.getItem(key);
<<<<<<< HEAD
      return raw ? (JSON.parse(raw) as T) : fallback;
=======
      return raw ? JSON.parse(raw) as T : fallback;
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
    } catch {
      return fallback;
    }
  }

  private write(key: string, value: unknown): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(key, JSON.stringify(value));
    }
  }
}
