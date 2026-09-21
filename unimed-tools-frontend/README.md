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

<<<<<<< HEAD
### Atualização da interface — Atual

O Angular 21 usa detecção de mudanças sem Zone.js. Estados assíncronos precisam
notificar a interface: o catálogo compartilhado de ferramentas usa `signal`,
e as páginas que mantêm campos comuns chamam `ChangeDetectorRef.markForCheck()`
após respostas, erros e finalização das operações. Nos lotes de prévias, cada
relatório concluído atualiza a tela mesmo enquanto os seguintes são consultados.
Não é necessário clicar na página para visualizar o resultado.

Ao adicionar fluxos assíncronos, use sinais consumidos pelo template ou notifique
o componente depois de alterar seu estado. `subscribe`, `finalize` e `await`
sozinhos não agendam a renderização. Os testes de regressão aguardam respostas
e `fixture.whenStable()` sem forçar `detectChanges()` depois da resposta.

### Apresentação e downloads — Atual

- Home, navegação, relatórios e administração usam textos mais legíveis e painéis
  que se adaptam à largura disponível, preservando as cores institucionais.
- Comercial e Gestão de Risco exibem prévias apenas depois de iniciar a consulta;
  resultado vazio e erro não são apresentados como estado inicial.
- “Baixar todos selecionados” é a ação principal, informa a quantidade selecionada
  e mantém o formato ZIP com arquivos XLSX. Durante a geração do pacote, o botão
  mostra o carregamento e impede envios duplicados.
- Downloads individuais continuam disponíveis. XML e grupos de relatórios usam
  o mesmo destaque visual para a ação de baixar o conjunto.

### Conferência visual — Pendente

Validar no navegador em desktop e celular: menus do topo, Home, Assistencial,
Comercial, Hospital, Gestão de Risco e TI. Conferir rolagem das tabelas, foco pelo
teclado, mensagens de carregamento/erro e hierarquia das ações de download.
Os testes automatizados usam respostas sintéticas; a integração real com SGU e
a revisão visual em navegador não foram executadas nesta alteração.

### Persistência no navegador

=======
>>>>>>> 7a10fbdb854a7ebb88a1acd410328f2bb29ac0f8
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
