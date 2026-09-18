# Unimed Tools — Frontend

Interface Angular do Unimed Tools. A documentação funcional completa está no [README da raiz](../README.md).

## Pré-requisitos

- Node.js 22;
- npm compatível com o `package-lock.json`;
- backend Spring Boot para os fluxos que consultam SGU, banco ou processam arquivos no servidor.

## Execução local

```bash
npm ci
npm start
```

O `npm start` usa o proxy local de `/api` para `http://localhost:8080`.

## Validação

```bash
npm test -- --watch=false
npm run build
```

Para publicação no XAMPP:

```bash
npm run build:lan
```

## Organização

```text
src/app/
├── layout/          # navbar e shell autenticado
├── pages/           # páginas por área de negócio
└── shared/
    ├── components/
    ├── constants/
    ├── guards/
    ├── models/
    ├── services/
    └── utils/
```

A navegação das ferramentas nativas é centralizada em
`shared/constants/tools.constants.ts`. Home e menu Ferramentas devem consumir
essa mesma fonte; não duplique manualmente nomes, rotas ou permissões em vários componentes.

## Ferramentas principais

- Comercial;
- Assistencial;
- Revisão de Contas;
- Única;
- Hospital;
- Gestão de Risco;
- TI.

Ferramentas simples baseadas em uma API SGU existente também podem ser criadas
pela área TI. Elas são carregadas do backend pelo `ToolRegistryService`.

## Estado local

O navegador pode manter:

- modelos estruturais do Assistencial, sem valores de filtros;
- catálogo/templates/grupos legados de relatórios;
- ferramentas recentes;
- estado de leitura das notificações.

O frontend **não** persiste token de sessão em `localStorage` ou `sessionStorage`.

## Segurança

- o SGU nunca é acessado diretamente pelo Angular;
- a chave SGU permanece no backend;
- autenticação usa cookie de sessão `HttpOnly`;
- operações de escrita usam CSRF;
- guards melhoram a experiência, mas a autorização real é validada no backend;
- o fluxo atual de login não utiliza MFA/TOTP.

## Observações

- o processamento XML principal continua no navegador;
- rotas antigas são redirecionadas para as novas áreas para preservar favoritos;
- BI e Fechamento permanecem no código por compatibilidade, mas não fazem parte dos cards principais da Home.
