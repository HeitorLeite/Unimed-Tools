import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
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
  private customTools: CustomReportTool[] = [];
  private nativeConfigs = new Map<string, NativeToolConfig>();
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
        this.customTools = custom.map((item) => this.fromBackend(item));
        this.nativeConfigs = new Map(
          native.map((item) => [item.id, this.fromNativeBackend(item)]),
        );
      }),
      map(() => [...this.customTools]),
    );
  }

  listAll(): ToolDefinition[] {
    const natives = CORE_TOOLS
      .filter((tool) => this.nativeConfigs.get(tool.id)?.ativo !== false)
      .map((tool) => this.applyNativeConfig(tool));
    return [...natives, ...this.customTools.map((tool) => this.toDefinition(tool))];
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
      config: this.nativeConfigs.get(original.id) ?? null,
    }));
  }

  saveNativeConfig(
    id: string,
    nome: string | null,
    descricao: string | null,
    ativo: boolean,
  ): Observable<NativeToolConfig> {
    return this.http.put<BackendNativeTool>(`${this.baseUrl}/nativas/${id}`, {
      nome,
      descricao,
      ativo,
    }).pipe(
      map((item) => this.fromNativeBackend(item)),
      tap((saved) => this.nativeConfigs.set(saved.id, saved)),
    );
  }

  resetNativeConfig(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/nativas/${id}`).pipe(
      tap(() => this.nativeConfigs.delete(id)),
    );
  }

  search(term: string): ToolDefinition[] {
    const normalized = this.normalize(term);
    if (!normalized) return this.listAccessible();
    return this.listAccessible().filter((tool) =>
      this.normalize([tool.nome, tool.categoria, tool.descricao, ...tool.keywords].join(' '))
        .includes(normalized),
    );
  }

  listCustom(): CustomReportTool[] {
    return [...this.customTools];
  }

  findCustom(slug: string): CustomReportTool | undefined {
    return this.customTools.find((tool) => tool.slug === slug);
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
        const exists = this.customTools.some((tool) => tool.id === saved.id);
        this.customTools = exists
          ? this.customTools.map((tool) => tool.id === saved.id ? saved : tool)
          : [...this.customTools, saved].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
      }),
    );
  }

  deleteCustom(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`).pipe(
      tap(() => this.customTools = this.customTools.filter((tool) => tool.id !== id)),
    );
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
    const config = this.nativeConfigs.get(tool.id);
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
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
      .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80);
  }

  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  }

  private read<T>(key: string, fallback: T): T {
    if (typeof localStorage === 'undefined') return fallback;
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) as T : fallback;
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
