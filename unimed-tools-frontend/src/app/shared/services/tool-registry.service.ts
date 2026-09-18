import { Injectable } from '@angular/core';
import { CORE_TOOLS } from '../constants/tools.constants';
import { CustomReportTool, ToolDefinition } from '../models/tool.model';
import { AuthService } from './auth.service';

const CUSTOM_TOOLS_KEY = 'unimed-tools.ferramentas-personalizadas.v1';
const RECENT_TOOLS_KEY = 'unimed-tools.ferramentas-recentes.v1';

@Injectable({ providedIn: 'root' })
export class ToolRegistryService {
  constructor(private readonly auth: AuthService) {}

  listAll(): ToolDefinition[] {
    return [...CORE_TOOLS, ...this.listCustom().map((tool) => this.toDefinition(tool))];
  }

  listAccessible(): ToolDefinition[] {
    const user = this.auth.user();
    return this.listAll().filter((tool) => {
      if (tool.adminOnly && user?.perfil !== 'ADMINISTRADOR') return false;
      return !tool.permission || this.auth.hasPermission(tool.permission);
    });
  }

  search(term: string): ToolDefinition[] {
    const normalized = this.normalize(term);
    if (!normalized) return this.listAccessible();
    return this.listAccessible().filter((tool) =>
      this.normalize([tool.nome, tool.categoria, tool.descricao, ...tool.keywords].join(' ')).includes(normalized),
    );
  }

  listCustom(): CustomReportTool[] {
    return this.read<CustomReportTool[]>(CUSTOM_TOOLS_KEY, []);
  }

  findCustom(slug: string): CustomReportTool | undefined {
    return this.listCustom().find((tool) => tool.slug === slug);
  }

  saveCustom(input: Omit<CustomReportTool, 'id' | 'criadoEm' | 'atualizadoEm'> & { id?: string }): CustomReportTool {
    const slug = this.slugify(input.slug || input.nome);
    if (!slug) throw new Error('Informe um nome válido para a ferramenta.');
    const now = new Date().toISOString();
    const existing = this.listCustom().find((tool) => tool.id === input.id);
    const duplicate = this.listCustom().find((tool) => tool.slug === slug && tool.id !== input.id);
    if (duplicate || CORE_TOOLS.some((tool) => tool.route === `/ferramentas/${slug}`)) {
      throw new Error('Já existe uma ferramenta com esse endereço.');
    }
    const saved: CustomReportTool = {
      id: existing?.id ?? this.id(),
      slug,
      nome: input.nome.trim(),
      descricao: input.descricao.trim(),
      apiNome: input.apiNome.trim(),
      filtros: [...new Set(input.filtros.map((item) => item.trim()).filter(Boolean))],
      colunasPreview: [...new Set(input.colunasPreview.map((item) => item.trim()).filter(Boolean))],
      criadoEm: existing?.criadoEm ?? now,
      atualizadoEm: now,
    };
    const next = existing
      ? this.listCustom().map((tool) => tool.id === saved.id ? saved : tool)
      : [...this.listCustom(), saved];
    this.write(CUSTOM_TOOLS_KEY, next);
    return saved;
  }

  deleteCustom(id: string): void {
    this.write(CUSTOM_TOOLS_KEY, this.listCustom().filter((tool) => tool.id !== id));
  }

  recordOpened(id: string): void {
    const next = [id, ...this.read<string[]>(RECENT_TOOLS_KEY, []).filter((item) => item !== id)].slice(0, 5);
    this.write(RECENT_TOOLS_KEY, next);
  }

  recent(): ToolDefinition[] {
    const byId = new Map(this.listAccessible().map((tool) => [tool.id, tool]));
    return this.read<string[]>(RECENT_TOOLS_KEY, [])
      .map((id) => byId.get(id))
      .filter((tool): tool is ToolDefinition => Boolean(tool))
      .slice(0, 3);
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

  private id(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
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
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, JSON.stringify(value));
  }
}
