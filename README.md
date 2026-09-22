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

O Assistencial substitui a antiga entrada de relatório personalizado e usa como base as regras aprovadas do relatório de despesas.

Fluxo:

1. **Escolher colunas** — as colunas selecionadas podem ser reorganizadas por drag and drop;
2. **Filtros e análises** — período obrigatório, filtros opcionais, empresas do mesmo catálogo usado no Comercial, remoção de duplicidades, separação mensal e ranking;
3. **Gerar e conferir** — prévia completa, drag and drop dos cabeçalhos, ordenação por uma coluna e exportação CSV, TXT ou XLSX.

A ordem alterada na prévia permanece sincronizada com a etapa 1. Reordenar visualmente as colunas não dispara uma nova consulta; uma nova consulta só ocorre quando a ordenação dos registros é alterada ou o usuário pede outra página.

### Empresas

O filtro **Nome da empresa** usa o catálogo de empresas do Comercial. É possível pesquisar e selecionar mais de uma empresa; a interface resolve os códigos automaticamente. O filtro técnico por código continua disponível para necessidades avançadas.

### Separar meses

Ao selecionar um intervalo de competências, a opção **Separar meses** permite escolher uma ou mais colunas numéricas. A competência deixa de ser exibida como uma coluna comum e cada métrica passa a ter uma coluna por mês, por exemplo:

`Despesa total JAN/2026 | Despesa total FEV/2026 | Receita JAN/2026 | Receita FEV/2026`

O intervalo continua limitado a 12 meses.

### Maiores e menores gastadores

Quando existe uma métrica do grupo **Valores** selecionada, o usuário pode montar um ranking escolhendo:

- maiores ou menores;
- quantidade entre 1 e 1000;
- dimensão, como beneficiário, empresa, contrato ou prestador;
- métrica financeira usada no ranking.

### Dados de beneficiário

Além dos campos já existentes de UF, município, CEP, idade e titular, o catálogo inclui **Região do beneficiário** e **Beneficiário ativo**. As regras de endereço/titular foram alinhadas ao relatório `Despesa_Empresa_com_grupo.sql`.

Modelos de estrutura podem ser salvos no navegador para reutilização. Eles guardam colunas, filtros escolhidos e opções de estrutura, mas **não guardam os valores digitados nos filtros**.

O SQL do relatório personalizado continua montado exclusivamente no backend por allowlist. Antes de publicar a definição dinâmica no SGU, a consulta é compactada e a ordenação é mantida curta para respeitar os limites da rotina `ins_atu_query_api`. SQL arbitrário não é aceito do navegador.

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

## Ambientes local e de produção

Os ambientes são separados para que alterações na pasta de trabalho não sejam promovidas automaticamente para a rede.

### Ambiente local / teste

O atalho `Iniciar Unimed Tools.cmd` inicia o **Watch Mode** e mantém a janela aberta.

Endereços locais:

- frontend: `http://localhost:4200/`;
- backend: `http://127.0.0.1:8081`.

O Angular usa seu dev server em modo watch. Alterações em `src` e `public` são recompiladas automaticamente. O inicializador também observa o código do backend; quando detecta alteração em `src` ou `pom.xml`, compila um novo JAR e reinicia somente o backend local.

Se uma compilação do backend falhar, o processo local anterior não é derrubado antes do build e o erro aparece na janela. Nenhum `git pull`, `git fetch` ou atualização do GitHub é feito pelo inicializador.

Use `Ctrl+C` para encerrar o ambiente local. Isso não encerra a produção.

### Produção / rede

A produção continua disponível em:

`http://192.168.3.242/unimed-tools/`

Ela **não é atualizada pelo Watch Mode**.

Para promover deliberadamente a versão que está na pasta local, execute:

`Publicar Unimed Tools - Producao.cmd`

A publicação manual:

1. instala as dependências do frontend a partir do lockfile;
2. executa os testes do frontend;
3. gera o build `build:lan`;
4. executa `mvn clean package` com os testes do backend;
5. somente depois das validações encerra os serviços de produção;
6. republica o frontend no XAMPP;
7. reinicia o backend de produção na porta 8080;
8. reinicia/valida o Apache;
9. valida o endereço da rede.

Assim, atualizar arquivos em `C:\Users\pcti02\Downloads\Unimed Tools\Aplicacao` atualiza o ambiente de teste automaticamente, mas a rede só recebe uma versão quando o atalho de publicação é executado explicitamente.

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
