# Unimed Tools — Frontend

Interface web do Unimed Tools, desenvolvida com Angular e componentes standalone. A documentação funcional e operacional completa está no [`README.md`](../README.md) da raiz.

## Pré-requisitos

- Node.js compatível com o Angular 21;
- npm 11 ou versão compatível com o `package-lock.json`;
- backend executando em `http://localhost:8080` para os fluxos que dependem do servidor.

## Execução local

```bash
npm ci
npm start
```

A aplicação fica disponível em `http://localhost:4200`. O comando `npm start` também ativa o proxy de desenvolvimento de `/api` para o backend local.

## Comandos

| Comando                     | Finalidade                                               |
| --------------------------- | -------------------------------------------------------- |
| `npm start`                 | Executa o servidor de desenvolvimento com o proxy local. |
| `npm run build`             | Gera o build otimizado em `dist/unimed-tools-frontend/`. |
| `npm run build:lan`         | Gera o build para XAMPP no caminho `/unimed-tools/`.      |
| `npm run watch`             | Mantém um build de desenvolvimento em observação.        |
| `npm test -- --watch=false` | Executa os testes uma vez.                               |

## Organização

```text
src/app/
├── layout/       # Estrutura visual compartilhada entre as páginas
├── pages/        # Funcionalidades agrupadas por domínio
└── shared/       # Componentes, modelos e serviços reutilizáveis
```

Novas páginas devem seguir os padrões descritos no [`AGENTS.md`](../AGENTS.md). As mensagens de commit seguem o [`PADRAO_DE_COMMITS.md`](../PADRAO_DE_COMMITS.md).

## Observações importantes

- O processamento XML principal ocorre no navegador.
- A API do SGU nunca deve ser acessada diretamente pelo frontend.
- A Central de Relatórios mantém catálogos e configurações no `localStorage` no estado atual.
- A página de Fechamento ainda não possui implementação correspondente no backend.
