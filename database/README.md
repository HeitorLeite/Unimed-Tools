# Banco de dados do Unimed Tools

O banco `DBUNIMED` mantém identidade, sessões, permissões, auditoria e a configuração global das ferramentas criadas pela TI.

## Instalação nova

Importe:

`database/DBUNIMED.sql`

O esquema atual contém:

- `perfil_acesso`;
- `permissao`;
- `perfil_permissao`;
- `usuario`;
- `usuario_permissao`;
- `sessao_usuario`;
- `ferramenta_configuravel`;
- `ferramenta_nativa_configuracao`;
- `auditoria_acesso`.

O fluxo atual **não utiliza MFA/TOTP** e instalações novas não criam campos ou tabelas para segundo fator.

O primeiro administrador é criado pelo backend quando a tabela `usuario` está vazia e as variáveis `AUTH_BOOTSTRAP_ADMIN_*` estão configuradas.

## Banco existente

Não apague o banco.

Faça backup e execute somente as migrações ainda não aplicadas, em ordem:

1. `002_permissoes_por_usuario.sql` — permissões individuais;
2. `003_ferramentas_configuraveis.sql` — cards/ferramentas publicados pela TI;
3. `004_remove_mfa.sql` — remove campos TOTP e a antiga tabela de desafios;
4. `005_configuracao_ferramentas_nativas.sql` — permite personalizar nome, descrição e visibilidade dos cards nativos;
5. `006_permissoes_ferramentas_atuais.sql` — adiciona permissões alinhadas às seis ferramentas operacionais atuais e migra concessões antigas.

A migração 004 preserva:

- contas;
- senhas;
- perfis;
- permissões;
- sessões;
- bloqueios;
- auditoria.

Depois das migrações, reinicie o backend.

## Usuário do banco

Não use `root` para a aplicação.

Exemplo, substituindo a senha antes da execução:

```sql
CREATE USER 'unimed_tools_app'@'localhost' IDENTIFIED BY 'SENHA_FORTE_AQUI';
GRANT SELECT, INSERT, UPDATE, DELETE ON DBUNIMED.*
TO 'unimed_tools_app'@'localhost';
FLUSH PRIVILEGES;
```

Configure `DB_USERNAME` e `DB_PASSWORD` no ambiente do backend. Valores reais nunca devem ser versionados.


## Permissões atuais

O gerenciamento operacional trabalha com:

- `COMERCIAL_ACESSAR`;
- `ASSISTENCIAL_ACESSAR`;
- `REVISAO_CONTAS_ACESSAR`;
- `UNICA_ACESSAR`;
- `HOSPITAL_ACESSAR`;
- `GESTAO_RISCO_ACESSAR`;
- `FUSEX_SPA_VALORIZAR` (migração 007; concessão explícita para operadores).

O backend mantém automaticamente as permissões técnicas necessárias para os endpoints existentes.
