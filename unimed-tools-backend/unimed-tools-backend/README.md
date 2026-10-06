# Unimed Tools — Backend

API Spring Boot responsável por autenticação, autorização, persistência da
aplicação, processamento de arquivos no servidor e integração com SGU/Kong.

## Tecnologias

- Java 21;
- Spring Boot 3.3;
- Spring Security;
- JDBC direto com MariaDB;
- Apache POI e Apache Commons CSV;
- Maven.

## Organização

```text
src/main/java/com/unimedlorena/tools/
├── auth/        # sessão, login, senha, usuários, permissões e auditoria
├── config/      # segurança, CORS, CSRF e exportação assíncrona
├── controller/  # contratos HTTP
├── dto/         # entradas e saídas
├── exception/   # erros públicos e tratamento global
└── service/     # regras de negócio, arquivos, SGU e exportações
```

Veja o relacionamento completo entre páginas e classes em
[`docs/MAPA_DO_CODIGO.md`](../docs/MAPA_DO_CODIGO.md).

## Grupos de endpoints

- `/api/auth/**`: login, sessão, CSRF, senha e logout;
- `/api/usuarios/**`: contas, permissões e reset administrativo;
- `/api/relatorios/personalizado/**`: Assistencial;
- `/api/relatorios/hospital/**`: Hospital;
- `/api/relatorios/sgu/**`: catálogo, execução e exportação de relatórios;
- `/api/ferramentas/**`: ferramentas configuráveis e apresentação das nativas;
- `/api/ans/**`: processamento da rede ANS;
- `/api/bi/especialidade`: preenchimento de especialidade em XLSX;
- `/api/xml/**`: implementação Java de XML preservada separadamente;
- `/health`: verificação básica de disponibilidade.

Não existe endpoint de Fechamento de Produção.

## Execução

```powershell
$env:PORT = '8081'
mvn spring-boot:run
```

A porta 8081 corresponde ao proxy do frontend de desenvolvimento. Sem `PORT`, a
aplicação usa 8080.

As configurações vêm de variáveis de ambiente. Consulte
[`docs/DESENVOLVIMENTO.md`](../docs/DESENVOLVIMENTO.md) e
[`application.properties`](src/main/resources/application.properties).

## Testes e pacote

```bash
mvn test
mvn clean package
```

O Maven deve usar JDK 21. Testes não podem depender de dados pessoais ou de
arquivos reais de produção.

## Regras importantes

- controllers tratam HTTP e delegam processamento;
- services concentram regras e integração;
- autorização é validada no servidor;
- sessão é opaca e somente seu hash é persistido;
- consultas usam parâmetros/allowlists, nunca SQL arbitrário do navegador;
- a chave SGU fica somente no backend;
- arquivos originais não são sobrescritos;
- logs não recebem credenciais, arquivos ou conteúdo pessoal;
- exportações extensas usam paginação e arquivo temporário antes da resposta.

Antes de alterar código, leia `AGENTS.md`, `SEGURANCA.md`, o README da raiz e os
documentos específicos do fluxo.
