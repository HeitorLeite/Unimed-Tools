# Guia de desenvolvimento e operação local

## 1. Pré-requisitos

- Node.js 22 e npm compatível com o lockfile;
- JDK 21;
- Maven;
- MariaDB/MySQL com o esquema `DBUNIMED`;
- acesso autorizado ao SGU/Kong apenas quando a tarefa exigir integração real.

No Windows, confirme que o Maven está usando o JDK correto:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

## 2. Preparação do banco

Instalação nova: importe `database/DBUNIMED.sql`.

Instalação existente: faça backup e aplique somente as migrações ainda não
executadas, na ordem descrita em [`database/README.md`](../database/README.md).
Não reaplique migrações de concessão de permissões como rotina de inicialização.

Crie um usuário de aplicação com acesso mínimo ao esquema. Não use `root` e não
versione credenciais.

## 3. Configuração

As propriedades estão em
`unimed-tools-backend/src/main/resources/application.properties` e recebem seus
valores do ambiente.

Grupos principais:

- banco: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_*`;
- sessão: `AUTH_COOKIE_*`, `AUTH_SESSION_*`, `AUTH_LOGIN_*`;
- primeiro administrador: `AUTH_BOOTSTRAP_ADMIN_*`;
- SGU: `SGU_API_*`;
- exportação: `SGU_EXPORT_*`, `REPORT_EXPORT_ASYNC_*`;
- APIs internas mutáveis: `RELATORIO_PERSONALIZADO_API_NOME` e
  `RELATORIO_HOSPITAL_API_NOME`.

Segredos devem vir do mecanismo corporativo aprovado. Nunca coloque valores
reais no Angular, em arquivos versionados, argumentos de comando, testes ou logs.

## 4. Execução

Frontend:

```bash
cd unimed-tools-frontend
npm ci
npm start
```

Backend:

```powershell
cd unimed-tools-backend
$env:PORT = '8081'
mvn spring-boot:run
```

O proxy do `npm start` aponta para `http://localhost:8081`. Sem definir `PORT`, o
backend usa 8080, que é a porta reservada pelo fluxo local de produção.

Os atalhos da raiz usam scripts PowerShell:

- `Iniciar Unimed Tools.cmd`: ambiente local em watch mode;
- `Publicar Unimed Tools - Producao.cmd`: testa, compila e publica no ambiente
  local de produção/XAMPP; execute somente quando houver solicitação explícita.

O watcher não atualiza o Git nem publica em produção.

## 5. Fluxo de uma requisição autenticada

1. o Angular solicita `/api/auth/csrf`;
2. o login cria uma sessão opaca;
3. o navegador recebe somente o cookie `HttpOnly`;
4. `SessaoAuthenticationFilter` valida o hash persistido a cada requisição;
5. `SecurityConfig` confere rota, método e autoridade;
6. o controller valida o contrato e delega ao service;
7. alterações administrativas e eventos sensíveis aplicáveis são auditados.

Guards Angular melhoram a navegação, mas não são uma fronteira de segurança.

## 6. Testes obrigatórios

Frontend:

```bash
cd unimed-tools-frontend
npm test -- --watch=false
npm run build
```

Backend:

```bash
cd unimed-tools-backend
mvn test
mvn clean package
```

Use dados sintéticos. Para endpoints, cubra sucesso, validação, ausência de
autenticação, ausência de permissão e CSRF nas operações de escrita. Para
arquivos, confira nome, tipo, codificação, conteúdo e preservação do original.

## 7. Alterações comuns

### Página ou ferramenta nativa

Consulte a sequência no [Mapa do código](MAPA_DO_CODIGO.md). Rotas, registro
central, permissão, ajuda, testes e documentação devem avançar juntos.

### Endpoint

- mantenha contrato no controller e regra no service;
- use DTO para entradas e saídas;
- valide tamanho, tipo e parâmetros antes de processar;
- atualize `SecurityConfig` com o método e a autoridade exatos;
- não devolva causas internas nem registre conteúdo sensível.

### Permissão

- negue por padrão;
- atualize catálogo, migração, perfil administrador e tela de gerenciamento;
- preserve permissões técnicas necessárias sem transformá-las repetidamente em
  concessões operacionais;
- teste mudanças de privilégio e reinicialização do backend.

### Relatório SGU

- mantenha SQL, API key e publicação no backend;
- valide filtros por allowlist;
- preserve paginação determinística;
- não deduplique linhas por suposição;
- teste vazio, falha externa e todos os formatos aplicáveis.

### Ajuda contextual

O componente fica em `shared/components/tool-help/` e o conteúdo central em
`shared/constants/tool-help.constants.ts`. Atualize a ajuda sempre que entradas,
etapas, saídas ou limitações de uma ferramenta mudarem.

## 8. Depuração

Antes de alterar código, identifique em qual fronteira ocorre a falha:

- navegador/template;
- service HTTP Angular;
- autenticação/CSRF/autorização;
- validação do controller;
- regra do service;
- MariaDB;
- SGU/Kong;
- geração ou transferência do arquivo.

Não resolva `403` externo contornando ACL, WAF, restrição de IP ou autenticação.
Registre horário, endpoint, status e identificador de correlação sem copiar
credenciais, filtros sensíveis ou registros de beneficiários.

## 9. Documentação e entrega

Antes de finalizar:

1. revise `git status` e o diff;
2. execute testes e builds relevantes;
3. atualize a fonte documental da regra;
4. classifique itens como Atual, Parcial, Proposto ou Pendente;
5. confirme que nenhum segredo, dado real ou artefato de build entrou no diff;
6. informe validações não executadas e riscos restantes;
7. não crie commit, branch, tag, push ou deploy sem solicitação explícita.
