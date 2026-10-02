# Mapa do código

Este documento responde “onde devo procurar?” antes de uma alteração.

## 1. Pontos de entrada

| Assunto | Arquivo principal |
| --- | --- |
| Rotas Angular | `unimed-tools-frontend/src/app/app.routes.ts` |
| Catálogo da Home/navbar | `shared/constants/tools.constants.ts` |
| Ajuda das ferramentas | `shared/constants/tool-help.constants.ts` |
| Estado e chamadas de autenticação | `shared/services/auth-state.service.ts` e `auth.service.ts` |
| Chamadas de relatório | `shared/services/relatorio.service.ts` |
| Segurança HTTP backend | `config/SecurityConfig.java` |
| Sessão por requisição | `config/SessaoAuthenticationFilter.java` e `auth/SessaoService.java` |
| Tratamento de erros | `exception/GlobalExceptionHandler.java` |
| Configuração de ambiente | `unimed-tools-backend/src/main/resources/application.properties` |
| Esquema do banco | `database/DBUNIMED.sql` e `database/migrations/` |

Os caminhos frontend abaixo são relativos a `unimed-tools-frontend/src/app/` e
os caminhos backend a
`unimed-tools-backend/src/main/java/com/unimedlorena/tools/`.

## 2. Fluxos funcionais

| Área | Rota e página Angular | Backend e regra principal | Persistência/integração |
| --- | --- | --- | --- |
| Comercial | `/comercial` — `pages/comercial/`; catálogo em `pages/relatorios/relatorios-automaticos/empresa-catalogo.ts` | `RelatorioController`, `ExportacaoRelatorioService`, `FaixaEtariaConsolidator`, `GrupoPrestadorComercialNormalizer` | SGU/Kong; arquivos temporários na exportação; SQL de faixa etária em `docs/sql/` |
| Assistencial | `/assistencial` — `pages/relatorios/relatorios-personalizados/` | `RelatorioPersonalizadoService`, `RelatorioPersonalizadoSqlBuilder`, `EspecialidadeRelatorioResolver` | SGU/Kong; modelos estruturais no navegador |
| Revisão de Contas | `/revisao-contas` — `pages/xml/xml-tools/` | Fluxo atual em `shared/services/xml.service.ts`; implementação Java separada em `XmlController`/`XmlService` | Processamento principal local, sem sobrescrever originais |
| Única | `/unica` — `pages/ans/corretor-rede/` | `AnsController` e `AnsService` | Upload multipart; TXT ISO-8859-1 |
| Hospital | `/hospital` — `pages/hospital/` | `RelatorioController` e `HospitalRelatorioService` | SGU/Kong |
| Gestão de Risco | `/gestao-risco` — `pages/gestao-risco/` | Endpoints SGU genéricos e serviços de exportação | SGU/Kong |
| TI | `/ti` — `pages/ti/` e `pages/relatorios/` | `RelatorioController`, `FerramentaController`, `FerramentaConfiguravelService` | MariaDB para ferramentas; parte do catálogo legado no navegador |
| Ferramenta configurável | `/ferramentas/:slug` — `pages/tools/custom-report/` | Endpoints SGU genéricos | Definição em `ferramenta_configuravel`; execução no SGU |
| Usuários | `/usuarios/**` — `pages/users/` | `UsuarioController`, `UsuarioService`, `AuthRepository` | MariaDB; auditoria e revogação de sessões |
| Autenticação | `/login`, `/alterar-senha`, `/perfil` | `AuthController`, `AuthService`, `SessaoService` | MariaDB; cookie opaco e hash de sessão |
| Especialidade Médica | `/bi/especialidade-medica` — `pages/bi/` | `EspecialidadeController` e `EspecialidadeService` | XLSX no backend; suporte CSV parcial/não confirmado |
| Fechamento | `/fechamento/corretor` — `pages/fechamento/` | Não existe controller/service de conversão | Contrato visual pendente |

## 3. Organização do frontend

- `layout/`: shell autenticado e navegação global;
- `pages/`: componentes de página, agrupados por área;
- `shared/components/`: componentes reaproveitáveis, como upload, prévia, ícone
  e ajuda;
- `shared/constants/`: catálogos estáticos e conteúdo compartilhado;
- `shared/guards/`: proteção de rotas para experiência do usuário;
- `shared/models/`: contratos TypeScript;
- `shared/services/`: HTTP, estado e regras reutilizáveis;
- `shared/utils/`: transformação e download sem estado.

Os componentes são standalone e as páginas são carregadas pelas rotas. O projeto
usa Angular sem Zone.js; atualizações assíncronas devem usar signals ou notificar
explicitamente a detecção de mudanças conforme o padrão já presente.

## 4. Organização do backend

- `auth/`: login, sessão, senha, usuários, permissões e auditoria;
- `config/`: Spring Security, CORS, CSRF, autenticação por sessão e execução
  assíncrona;
- `controller/`: contrato HTTP e delegação;
- `dto/`: entradas e saídas explícitas;
- `exception/`: erros públicos e tradução global;
- `service/`: regras de negócio, arquivos, integração SGU e exportações.

O projeto usa JDBC direto para identidade e catálogos da aplicação. Não introduza
JPA incidentalmente.

## 5. Endpoint para código

| Grupo HTTP | Controller | Consumidor principal |
| --- | --- | --- |
| `/api/auth/**` | `AuthController` | `AuthService` Angular |
| `/api/usuarios/**` | `UsuarioController` | páginas `users/` |
| `/api/relatorios/personalizado/**` | `RelatorioController` | Assistencial |
| `/api/relatorios/hospital/**` | `RelatorioController` | Hospital |
| `/api/relatorios/sgu/**` | `RelatorioController` | Comercial, Gestão de Risco, TI e ferramentas configuráveis |
| `/api/ferramentas/**` | `FerramentaController` | registro de ferramentas, Home e TI |
| `/api/ans/**` | `AnsController` | Única |
| `/api/bi/especialidade` | `EspecialidadeController` | Especialidade Médica |
| `/api/xml/**` | `XmlController` | implementação Java preservada; fluxo principal atual é local |

## 6. Onde uma nova ferramenta deve ser registrada

Para uma nova página nativa:

1. criar a página em `pages/`;
2. adicionar a rota em `app.routes.ts`;
3. registrar nome, descrição, ícone, rota e permissão em `CORE_TOOLS`;
4. definir e proteger o endpoint backend, quando necessário;
5. adicionar a permissão ao catálogo, migração e gerenciamento de usuários;
6. adicionar conteúdo em `tool-help.constants.ts` e o componente de ajuda;
7. atualizar guia do usuário, arquitetura e este mapa;
8. testar frontend, backend e autorização negativa.

Para uma página simples baseada em relatório existente, avalie primeiro a
ferramenta configurável da área TI.

## 7. Buscas úteis

```bash
rg -n "NOME_DA_PERMISSAO|/api/rota|nomeDoMetodo" .
rg -n "@.*Mapping" unimed-tools-backend/src/main/java
rg -n "loadComponent|permission:" unimed-tools-frontend/src/app
rg -n "localStorage|sessionStorage" unimed-tools-frontend/src/app
```

Sempre acompanhe o caminho completo: template → componente → service Angular →
endpoint → controller → service Java → repositório/banco/SGU → resposta e testes.
