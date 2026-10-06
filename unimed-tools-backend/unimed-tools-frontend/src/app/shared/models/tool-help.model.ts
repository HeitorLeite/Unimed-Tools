export interface ToolHelpStep {
  title: string;
  description: string;
}

export interface ToolHelpContent {
  title: string;
  summary: string;
  prerequisites?: readonly string[];
  steps: readonly ToolHelpStep[];
  result: string;
  tips?: readonly string[];
  warnings?: readonly string[];
  status?: 'Atual' | 'Parcial' | 'Pendente';
}
