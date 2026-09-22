# Unimed Tools

Aplicação interna da Unimed Lorena para centralizar relatórios, correções de arquivos e rotinas operacionais em uma interface única.

A interface foi reorganizada por **área de trabalho** em vez de por tecnologia. O usuário encontra a rotina pelo nome do setor ou da atividade, enquanto detalhes como API SGU, XML TISS e arquivos posicionais ficam em segundo plano.

## Ferramentas principais

| Ferramenta | Rota | Finalidade | Permissão |
| --- | --- | --- | --- |
| Home | `/` | Busca, cards e acessos recentes | usuário autenticado |
| Comercial | `/comercial` | Beneficiários, receita, despesas e faixa etária por empresa | `COMERCIAL_ACESSAR` |
| Assistencial | `/assistencial` | Relatório personalizado por colunas e filtros | `ASSISTENCIAL_ACESSAR` |
| Revisão de Contas | `/revisao-contas` | Correção e conferência de XML TISS | `REVISAO_CONTAS_ACESSAR` |
| Única | `/unica` | Correção de rede ANS / arquivo RPS | `UNICA_ACESSAR` |
| Hospital | `/hospital` | Autorizações ainda não convertidas em guia | `HOSPITAL_ACESSAR` |
| Gestão de Risco | `/gestao-risco` | Relatórios de rastreio e acompanhamento | `GESTAO_RISCO_ACESSAR` |
| TI | `/ti` | APIs, grupos e criação de ferramentas | administrador |
| Meu perfil | `/perfil` | Dados da conta e permissões | usuário autenticado |
| Usuários | `/usuarios` | Contas, permissões e reset de senha | administrador |

Rotas antigas como `/xml/ferramentas`, `/ans/corretor-rede` e `/relatorios` permanecem com redirecionamento para evitar quebra de favoritos existentes.

## Navegação

O layout principal usa uma barra superior única:

- marca e nome do sistema à esquerda;
- **Início** e menu **Ferramentas** ao centro;
- notificações e menu da conta à direita;
- administradores recebem atalhos para usuários e TI.

A Home usa um registro central de ferramentas. Nome, rota, descrição, categoria e permissão são definidos uma única vez para alimentar os cards e o menu.

## Comercial

O Comercial usa somente:

- `0090-beneficiario-empresa`;
- `0090-receita-empresa-com-grupo`;
- `0090-despesa-empresas`;
- `0090-faixa-etaria`.

O usuário pode selecionar **uma ou várias empresas** pelo nome. A interface resolve os códigos internos do catálogo e envia apenas os parâmetros exigidos pela API.

Quando mais de uma empresa é selecionada, a prévia mostra somente a primeira empresa para manter a consulta rápida. O download, porém, usa todas as empresas escolhidas e consolida os resultados nos arquivos exportados.

Fluxo:

1. uma ou várias empresas;
2. competência;
3. data de referência da faixa etária;
4. seleção dos relatórios;
5. prévia da primeira empresa;
6. download completo individual ou pacote ZIP.

## Assistencial

O Assistencial substitui a antiga entrada de relatório personalizado.

Fluxo:

1. escolher colunas;
2. conferir a ordem das colunas;
3. adicionar filtros;
4. gerar a prévia;
5. exportar CSV, TXT ou XLSX.

Modelos de estrutura podem ser salvos no navegador para reutilização. Eles guardam colunas, filtros escolhidos e opções de estrutura, mas **não guardam os valores digitados nos filtros**.

O SQL do relatório personalizado continua montado exclusivamente no backend por allowlist. SQL arbitrário não é aceito do navegador.

## Hospital

O backend mantém uma API reservada chamada:

`0090-hospital-autorizacoes`

A consulta retorna:

- convênio;
- nome do beneficiário;
- data de autorização;
- código da guia;
- procedimento;
- descrição;
- prestador;
- status.

Filtros disponíveis:

- convênio;
- nome do beneficiário;
- data inicial e final de autorização;
- código da guia;
- procedimento;
- descrição;
- nome do prestador (busca parcial);
- status.

**Atual:** o prestador vem de `GSOL.GSOL_NOM_PROFIS`, aparece na prévia e na
exportação como `PRESTADOR`. O filtro opcional `prestador` usa `LIKE` com bind
`%texto%` e normalização para maiúsculas; não é necessário digitar os curingas.
A data de validade e as demais regras do relatório permanecem preservadas.

A definição SQL e os filtros permitidos ficam no backend. O frontend nunca recebe a chave do SGU nem SQL executável.

## Gestão de Risco

Centraliza:

- `0090-colonoscopia-ramiro`;
- `0090-consultas-ramiro`;
- `0090-mamografia-ramiro`;
- `0090-ressonancia-ramiro`;
- `0090-sangue-oculto-ramiro`.

A tela usa competência e também detecta outros filtros obrigatórios cadastrados em cada API.

## TI

A área TI reúne três atividades:

1. **Relatórios e APIs** — catálogo, criação/importação e teste de APIs SGU.
2. **Grupos de relatórios** — geração em lote.
3. **Ferramentas e cards** — publicação de novas páginas orientadas a relatório.

Ferramentas configuráveis são persistidas em `DBUNIMED.ferramenta_configuravel`. Um card criado pela TI fica disponível para todos os usuários autorizados, e não apenas no navegador onde foi criado.

Cada ferramenta configurável define:

- nome e descrição;
- slug/rota;
- API SGU;
- filtros exibidos;
- colunas preferenciais da prévia.

A mesma área também gerencia a apresentação das páginas nativas. Para Comercial,
Assistencial, Revisão de Contas, Única, Hospital e Gestão de Risco, a TI pode
alterar nome, descrição e visibilidade na Home/menu. Rota e permissão permanecem
fixas e são controladas pelo código. A própria área TI não pode ser ocultada.

## Autenticação e segurança

A autenticação atual usa:

- login e senha;
- BCrypt;
- bloqueio temporário após tentativas inválidas;
- sessão opaca em cookie `HttpOnly`;
- token de sessão persistido somente por hash SHA-256;
- expiração por inatividade e duração absoluta;
- CSRF nas operações de escrita;
- autorização no backend por permissão;
- auditoria de eventos administrativos;
- troca obrigatória de senha temporária.

**Não há autenticação de dois fatores/TOTP no fluxo atual.**

Tokens de sessão não são armazenados em `localStorage` ou `sessionStorage`.

## Banco de dados

Banco: MariaDB/MySQL, esquema `DBUNIMED`.

Para uma instalação nova, importe:

`database/DBUNIMED.sql`

Para instalações existentes, aplique somente as migrações ainda não executadas, em ordem:

1. `002_permissoes_por_usuario.sql`;
2. `003_ferramentas_configuraveis.sql`;
3. `004_remove_mfa.sql`;
4. `005_configuracao_ferramentas_nativas.sql`;
5. `006_permissoes_ferramentas_atuais.sql`.

O backend também sincroniza de forma idempotente as permissões das ferramentas atuais ao iniciar, para que instalações existentes passem a exibir Comercial, Assistencial, Revisão de Contas, Única, Hospital e Gestão de Risco no gerenciamento de acessos.

Faça backup antes de qualquer migração.

## Inicializador local

O atalho `Iniciar Unimed Tools.cmd` chama `scripts/iniciar-unimed-tools.ps1`.

Em toda execução ele:

1. valida Java/Maven/npm e as variáveis necessárias;
2. garante que o MariaDB esteja ativo;
3. testa e gera um novo build do frontend;
4. testa e empacota novamente o backend;
5. remove arquivos MFA/TOTP legados que possam ter sobrado fisicamente de versões antigas;
6. encerra o backend anterior;
7. encerra e reinicia o Apache que serve o frontend;
8. remove a publicação antiga em `C:\xampp\htdocs\unimed-tools`;
9. publica o novo frontend do zero;
10. inicia o novo backend;
11. valida os dois endereços:
   - `http://localhost/unimed-tools/`
   - `http://192.168.3.242/unimed-tools/`

Assim, localhost e o endereço da rede usam exatamente o mesmo build publicado e
não permanecem com arquivos antigos depois de clicar novamente no atalho.

## Desenvolvimento

### Frontend

Requisitos:

- Node.js 22;
- npm compatível com o lockfile.

```bash
cd unimed-tools-frontend
npm ci
npm start
```

Validação:

```bash
npm test -- --watch=false
npm run build
```

### Backend

Requisitos:

- Java 21;
- Maven;
- MariaDB.

```bash
cd unimed-tools-backend
mvn spring-boot:run
```

Validação:

```bash
mvn clean package
```

## Variáveis de ambiente

### Banco

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

### Sessão

- `AUTH_COOKIE_SECURE`
- `AUTH_COOKIE_SAME_SITE`
- `AUTH_SESSION_IDLE_MINUTES`
- `AUTH_SESSION_ABSOLUTE_HOURS`
- `AUTH_LOGIN_MAX_ATTEMPTS`
- `AUTH_LOGIN_BLOCK_MINUTES`

### Primeiro administrador

Usadas somente quando a tabela `usuario` estiver vazia:

- `AUTH_BOOTSTRAP_ADMIN_NAME`
- `AUTH_BOOTSTRAP_ADMIN_LOGIN`
- `AUTH_BOOTSTRAP_ADMIN_EMAIL`
- `AUTH_BOOTSTRAP_ADMIN_PASSWORD`

Após criar a primeira conta, remova a senha de bootstrap do ambiente.

### SGU

- `SGU_API_BASE_URL`
- `SGU_API_KEY`
- `SGU_API_KEY_HEADERS`
- `SGU_API_PROCEDURE_PATH`
- `SGU_API_EXECUTION_PATH`

A chave do SGU existe somente no backend.

### Exportações extensas e falhas temporárias (Atual)

- A exportação usa páginas de 1.000 registros por padrão (`SGU_EXPORT_PAGE_SIZE`).
- Uma página que receber HTTP 502, 503 ou 504 do SGU tem uma única nova tentativa,
  após um segundo. Apenas a consulta é repetida, antes de gravar suas linhas;
  páginas já concluídas não são repetidas. Criação, alteração e exclusão de APIs
  não recebem novas tentativas automáticas.
- Se a falha persistir, o backend preserva o status 502/503/504 e devolve uma
  mensagem segura em JSON, sem HTML remoto. Isso não elimina o timeout do gateway.
- Logs de exportação medem consulta por página, escrita e empacotamento XLSX,
  sem filtros, SQL ou conteúdo dos registros. Esses tempos permitem identificar
  o gargalo antes de mudar tamanho de página ou otimizar a consulta no SGU.
- CSV/TXT já transmitidos não permitem substituir a resposta HTTP por JSON
  em caso de falha posterior: a transferência pode ser interrompida e a saída
  parcial não deve ser tratada como relatório completo.

**Pendente:** medir a `0090-bi-despesas` no ambiente corporativo. O número de
linhas, isoladamente, não permite afirmar que 1.400 segundos sejam necessários.

## Estrutura

```text
unimed-tools-frontend/
  src/app/
    layout/
    pages/
    shared/

unimed-tools-backend/
  src/main/java/com/unimedlorena/tools/
    auth/
    config/
    controller/
    dto/
    service/

database/
  DBUNIMED.sql
  migrations/

docs/
  ARQUITETURA.md
```

## CI

O workflow `.github/workflows/ci.yml` executa em `main` e branches `refactor/**`:

- `npm ci`;
- testes Angular;
- build Angular;
- `mvn clean package`.

Uma alteração só deve ser considerada pronta para merge depois que frontend e backend concluírem com sucesso.

## Observações

- O processamento principal de XML continua no navegador.
- O SGU/Kong pode impor restrições externas de rede/IP; o projeto não tenta contorná-las.
- A página de BI e o Fechamento permanecem no código por compatibilidade, mas não fazem parte dos cards principais da nova Home.
- Dados reais de beneficiários não devem ser incluídos em testes, documentação ou repositório.


## Gerenciamento de usuários

As permissões operacionais exibidas ao administrador correspondem diretamente às áreas atuais da Home:

- Comercial;
- Assistencial;
- Revisão de Contas;
- Única;
- Hospital;
- Gestão de Risco.

As permissões técnicas antigas (`RELATORIOS_ACESSAR`, `XML_ACESSAR` e `ANS_ACESSAR`) continuam sendo aplicadas internamente quando uma ferramenta precisa delas, mas não aparecem mais como opções de negócio na tela de permissões.

No cadastro de usuário operacional, as ferramentas já podem ser escolhidas junto com a criação da conta.
