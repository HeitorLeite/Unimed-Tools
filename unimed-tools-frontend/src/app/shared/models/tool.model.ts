import { ToolIconName } from '../components/tool-icon/tool-icon.component';

export type ToolKind = 'native' | 'custom-report';

export interface ToolDefinition {
  id: string;
  nome: string;
  categoria: string;
  descricao: string;
  route: string;
  icon: ToolIconName;
  permission?: string;
  adminOnly?: boolean;
  keywords: string[];
  kind: ToolKind;
}

export interface CustomReportTool {
  id: string;
  slug: string;
  nome: string;
  descricao: string;
  apiNome: string;
  filtros: string[];
  colunasPreview: string[];
  criadoEm: string;
  atualizadoEm: string;
}
