# Arquitetura do Unimed Tools

Este documento apresenta a organização técnica implementada no repositório. Requisitos e regras de negócio devem continuar sendo consultados antes de alterar qualquer fluxo.

## Visão geral

```mermaid
flowchart LR
    U[Usuário interno] --> L[Login Angular]
    L -->|senha + TOTP administrativo| I[Identidade Spring Security]
    I -->|sessão opaca HttpOnly| F[Frontend Angular]
    F -->|HTTP /api| B[Backend Spring Boot]
    B -->|usuários, permissões, sessões e auditoria| D[(MariaDB DBUNIMED)]
    B -->|Catálogo, execução e exportação| S[SGU Suite / Kong]
    F -->|XML local| X[Processamento no navegador]
    B -->|ANS, BI e exportações| A[Processamento de arquivos]
    B -->|Relatório personalizado| P[Allowlist de filtros e colunas]
    P --> S
```

O frontend concentra interação e estado de tela. O backend recebe arquivos,
aplica regras de negócio e integra a Central de Relatórios ao SGU. O relatório
personalizado é montado exclusivamente no backend a partir de fragmentos SQL
aprovados; o Angular recebe apenas metadados de campos, filtros e limites. O
processamento XML principal permanece local no navegador; a implementação Java
existente é mantida porque sua remoção ou unificação depende de decisão
arquitetural específica.

## Identidade e acesso

**Status: Atual.** A primeira rota exibida é `/login`. O backend valida senha
com BCrypt e cria uma sessão opaca; somente o hash SHA-256 do token é persistido
em `sessao_usuario`, enquanto o navegador recebe o valor em cookie `HttpOnly`,
`SameSite=Strict` e `Secure` fora do perfil local. Requisições de escrita também
exigem o token CSRF mantido em cookie separado, sem capacidade de autenticação.

Administradores precisam configurar e validar TOTP. O segredo compartilhado é
criptografado com AES-256-GCM antes de chegar ao banco, usando
`AUTH_MFA_ENCRYPTION_KEY`. O primeiro administrador é criado por bootstrap
somente quando `usuario` está vazia; os próximos usuários são cadastrados em
`POST /api/usuarios` por uma sessão com `USUARIOS_CRIAR` e um novo código TOTP.

O perfil `USUARIO` não herda permissões operacionais. As concessões individuais
ficam em `usuario_permissao` e são combinadas às permissões do perfil a cada
requisição. A interface administrativa permite somente a allowlist de módulos;
permissões de administração e dados sensíveis permanecem fora desse fluxo.
Alterações de acesso e redefinições de senha exigem step-up TOTP e são auditadas.

```mermaid
sequenceDiagram
    actor U as Usuário
    participant A as Angular
    participant B as Spring Security
    participant D as MariaDB
    U->>A: login e senha
    A->>B: POST /api/auth/login + CSRF
    B->>D: valida conta, hash e bloqueio
    alt administrador
        B-->>A: desafio MFA temporário
        U->>A: código TOTP
        A->>B: POST /api/auth/mfa/verificar
    end
    B->>D: grava somente hash da sessão
    B-->>A: cookie HttpOnly
    A->>B: requisição de módulo
    B->>D: valida sessão e permissões atuais
    B-->>A: resposta autorizada
```

## Frontend

Diretório: `unimed-tools-frontend/`

| Área                         | Responsabilidade                                      |
| ---------------------------- | ----------------------------------------------------- |
| `src/app/layout/`            | Estrutura visual e navegação compartilhadas.          |
| `src/app/pages/`             | Componentes agrupados por módulo funcional.           |
| `src/app/shared/components/` | Elementos visuais reutilizáveis.                      |
| `src/app/shared/models/`     | Contratos de dados da aplicação.                      |
| `src/app/shared/services/`   | HTTP, armazenamento local e regras reutilizáveis.     |
| `src/app/shared/guards/`     | Entrada autenticada e autorização de rotas.           |
| `src/app/shared/constants/`  | Identificadores que precisam permanecer consistentes. |
| `src/app/shared/utils/`      | Funções puras e utilitários sem estado.               |
| `src/environments/`          | Endereço da API por ambiente de build.                |

As rotas utilizam carregamento sob demanda e permanecem centralizadas em `src/app/app.routes.ts`.

A rota `/relatorios` agrega quatro componentes de tela:

| Componente                       | Responsabilidade                                        |
| -------------------------------- | ------------------------------------------------------- |
| `relatorios-inicio`              | Seleção entre os modos disponíveis.                     |
| `relatorios`                     | Orquestração do modo selecionado.                       |
| `relatorios-automaticos`         | Grupos e exportações em lote.                           |
| `relatorios-personalizados`      | Filtros, colunas, prévia paginada e exportação guiada.  |

`RelatorioService` concentra a comunicação HTTP e o acesso aos catálogos do
`localStorage`. O modo personalizado não persiste filtros digitados: eles ficam
somente no estado da página atual.

## Backend

Diretório: `unimed-tools-backend/`

| Pacote       | Responsabilidade                              |
| ------------ | --------------------------------------------- |
| `config`     | Configurações transversais, como CORS.        |
| `controller` | Contratos HTTP e montagem das respostas.      |
| `dto`        | Objetos de entrada, saída e estatísticas.     |
| `exception`  | Tratamento uniforme de erros.                 |
| `service`    | Regras de negócio, arquivos e integração SGU. |
| `auth`       | Senhas, MFA, sessões, usuários e auditoria.    |

Controllers não devem incorporar parsing de arquivos ou regras de negócio. Services não devem conhecer detalhes visuais do frontend.

### Componentes do relatório personalizado

**Status: Atual.**

| Componente                            | Responsabilidade                                                       |
| ------------------------------------- | ---------------------------------------------------------------------- |
| `RelatorioController`                 | Expõe configuração, execução e exportação e protege a API reservada.   |
| `RelatorioPersonalizadoRequest`       | Contrato de colunas, filtros, `distinct`, paginação e nome do arquivo. |
| `RelatorioPersonalizadoService`       | Valida entradas, coordena publicação/execução e projeta a resposta.    |
| `RelatorioPersonalizadoSqlBuilder`    | Mantém a allowlist e monta SQL somente com fragmentos aprovados.       |
| `SguRelatorioService`                 | Envia a definição e os parâmetros ao SGU sem expor a chave ao cliente. |
| `ExportacaoRelatorioService`          | Percorre páginas, infere tipos e gera CSV, TXT ou XLSX.                 |

A API `0090-relatorio-personalizado` é reservada ao construtor. Ela é ocultada
da listagem do catálogo manual e os endpoints genéricos impedem sua criação,
execução, exportação e exclusão direta.

### Fluxo do relatório personalizado

```mermaid
sequenceDiagram
    actor U as Usuário
    participant F as Angular
    participant C as RelatorioController
    participant R as RelatorioPersonalizadoService
    participant Q as SqlBuilder
    participant S as SGU/Kong

    F->>C: GET /personalizado/configuracao
    C->>R: configuracao()
    R-->>F: 50 colunas, 23 filtros e limites
    U->>F: informa filtros, seleciona colunas e define distinct
    F->>C: POST /personalizado/executar
    C->>R: request tipado
    R->>R: valida e normaliza entradas
    R->>Q: gerar(colunas, filtros ativos, distinct)
    Q-->>R: SQL, ordenação e filtros SGU
    alt estrutura diferente da última publicação
        R->>S: ins_atu_query_api
    end
    R->>S: executar API reservada
    S-->>R: página de registros
    R->>R: projeta somente colunas autorizadas
    R-->>F: conteúdo, paginação e colunas
```

Na exportação, o mesmo lock permanece adquirido enquanto todas as páginas são
carregadas. Isso impede que outra requisição na mesma JVM substitua a definição
da API entre páginas. A definição mais recente fica em cache na memória do
processo e só é republicada quando mudam as colunas ou os filtros ativos.

### Contratos e limites

- fonte atual: **Despesas por item de guia**;
- 50 colunas autorizadas, com rótulo, grupo, seleção padrão e indicador de
  sensibilidade;
- 23 filtros autorizados; competências inicial e final são obrigatórias;
- intervalo máximo de 12 meses;
- prévia de 1 a 100 linhas por página;
- filtros desconhecidos, repetidos ou maiores que 240 caracteres são rejeitados;
- IDs internos aceitam underscore e, por compatibilidade, hífen;
- na fronteira SGU, nomes e binds são compactados para caracteres alfanuméricos;
- `distinct=true` aplica `SELECT DISTINCT` somente à projeção externa autorizada
  e ordena pelas próprias colunas projetadas para preservar compatibilidade com
  o Oracle;
- a resposta e a exportação contêm apenas as colunas solicitadas e validadas;
- a prévia apresenta o total informado pelo SGU e a exportação devolve a
  quantidade materializada no header CORS `X-Total-Registros`;
- o XLSX grava datas, números e textos em células tipadas; identificadores são
  preservados como texto, enquanto CSV e TXT recebem representação compatível
  com a importação no Excel sem alterar sua natureza textual;
- SQL, aliases técnicos e `SGU_API_KEY` não são enviados ao navegador.

Os endpoints específicos são:

| Método | Endpoint                                             | Responsabilidade                   |
| ------ | ---------------------------------------------------- | ---------------------------------- |
| GET    | `/api/relatorios/personalizado/configuracao`         | Catálogo seguro para montar a tela |
| POST   | `/api/relatorios/personalizado/executar`              | Prévia paginada                    |
| POST   | `/api/relatorios/personalizado/exportar?formato=...`  | Exportação completa                |

## Estado e persistência

- Catálogo, templates e grupos de relatórios permanecem no `localStorage`.
- As chaves são centralizadas e versionadas; qualquer mudança exige migração explícita.
- Filtros do relatório personalizado não são persistidos; o catálogo autorizado vem do backend.
- O cache da última definição personalizada publicada existe apenas na memória da instância Spring Boot.
- Usuários, perfis, permissões, desafios, sessões e auditorias ficam no MariaDB.
- O backend usa JDBC parametrizado e não introduz JPA nos catálogos existentes.
- Token de sessão, senha e segredo TOTP nunca são persistidos em claro.
- O estado de autenticação do Angular fica apenas em memória; o cookie de sessão
  não é acessível pelo JavaScript.

## Implantação local em rede

**Status: Atual.**

O build `lan` do Angular usa base `/unimed-tools/` e API relativa `/api`. No
XAMPP, o Apache serve somente os artefatos compilados e atua como proxy reverso
para o Spring Boot restrito a `127.0.0.1:8080`.

```mermaid
flowchart LR
    U[Computador da rede] -->|HTTP porta 80| A[Apache / XAMPP]
    A -->|Arquivos estáticos| F[htdocs/unimed-tools]
    A -->|Proxy /api| B[Spring Boot 127.0.0.1:8080]
    B -->|HTTPS + API key| S[SGU / Kong]
```

O código-fonte não deve ser copiado para `htdocs`. O fluxo de atualização é:

1. alterar e testar o projeto em `unimed-tools-frontend/`;
2. executar `npm run build:lan`;
3. copiar somente `dist/unimed-tools-frontend/browser/` para o diretório público;
4. manter `.htaccess` para o fallback das rotas Angular.

Essa topologia mantém a porta Java restrita e agora inclui autenticação. Como há
tráfego de credenciais e sessão, o Apache deve publicar a aplicação por HTTPS
com certificado aprovado, sem desativar a validação TLS.

## Limites arquiteturais conhecidos

- Fechamento possui interface, mas não possui implementação no backend.
- O módulo BI usa `XSSFWorkbook`; seleção de CSV na interface não comprova suporte funcional a CSV.
- O SGU/Kong pode bloquear o ambiente hospedado com HTTP 403 por regras externas de rede.
- A API personalizada é compartilhada e o lock coordena apenas threads da mesma JVM; múltiplas réplicas exigem coordenação distribuída.
- O indicador de coluna sensível orienta a interface, mas não substitui autorização, pois a aplicação ainda não possui login próprio.
- Uploads grandes são limitados a 100 MB e exigem atenção ao consumo de memória.
- Serviços XML em TypeScript e Java não devem ser fundidos ou removidos incidentalmente.
- Recuperação de senha e recuperação de MFA ainda não possuem fluxo próprio;
  dependem de procedimento administrativo futuro e aprovado.
- A chave de criptografia MFA precisa permanecer disponível e protegida durante
  todo o ciclo de vida; perdê-la impede validar os segredos TOTP existentes.

## Como evoluir a estrutura

1. Preserve rotas, endpoints, campos e formatos já consumidos.
2. Extraia código reutilizável para `shared` somente quando houver mais de um consumidor real.
3. Mantenha tipos específicos junto do domínio; mova-os para modelos compartilhados apenas quando atravessarem módulos.
4. Adicione testes antes de alterar parsers posicionais, SQL, XML ou paginação do SGU.
5. Atualize este documento e o `README.md` quando uma fronteira arquitetural mudar.
6. Preserve a allowlist do relatório personalizado; novos campos exigem expressão SQL aprovada, classificação de sensibilidade e testes.
