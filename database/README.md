# Banco de dados do Unimed Tools

## Banco já existente

Como a primeira conta administrativa já foi criada, não exclua o banco. No
phpMyAdmin, selecione `DBUNIMED`, abra a aba **Importar** e execute, nesta ordem,
somente as migrações ainda não aplicadas:

1. `migrations/002_permissoes_por_usuario.sql`;
2. `migrations/003_agendamento_relatorios.sql`;
3. `migrations/004_recorrencia_agendamento_relatorios.sql`.

A migração 002:

- cria `usuario_permissao` sem apagar contas, sessões ou auditorias;
- remove as permissões herdadas pelo perfil `USUARIO`;
- mantém todas as permissões do perfil `ADMINISTRADOR`;
- faz usuários operacionais começarem sem acesso até uma concessão individual.

A edição e a exclusão lógica de usuários não exigem outra migração. O esquema
atual já possui `atualizado_por`, `desativado_por` e `desativado_em`; a aplicação
usa esses campos para preservar a trilha de auditoria sem manter a conta ativa.

Faça backup antes de aplicar uma migração em um ambiente com dados importantes.
Depois da importação, reinicie o backend para que a versão nova do código passe
a usar a tabela criada.

A migração 003 cria `relatorio_agendamento`, vincula cada agenda ao usuário
criador e mantém filtros e opções somente no campo criptografado pelo backend.
Também registra reservas temporárias, falhas e a retenção do histórico por 90
dias, sem mover o catálogo local de relatórios para o banco.

A migração 004 preserva as agendas existentes como execução única e adiciona
recorrência diária, semanal e mensal, dias da semana, dia do mês, fuso horário
e contador de execuções concluídas.

## Instalação nova

O arquivo `DBUNIMED.sql` contém o esquema completo atual de autenticação,
sessões, MFA, permissões individuais e auditoria. Em uma instalação vazia:

1. importe `DBUNIMED.sql` no phpMyAdmin;
2. crie uma conta de banco exclusiva para a aplicação, sem usar `root`;
3. conceda a essa conta apenas `SELECT`, `INSERT`, `UPDATE` e `DELETE` no banco
   `DBUNIMED`;
4. configure `DB_USERNAME` e `DB_PASSWORD` no ambiente do backend.

Exemplo para criar a conta, substituindo a senha antes de executar e sem salvar
o valor real no repositório:

```sql
CREATE USER 'unimed_tools_app'@'localhost' IDENTIFIED BY 'SENHA_FORTE_AQUI';
GRANT SELECT, INSERT, UPDATE, DELETE ON DBUNIMED.*
TO 'unimed_tools_app'@'localhost';
FLUSH PRIVILEGES;
```

O script não cria usuário da aplicação. O primeiro administrador é criado pelo
backend somente quando `usuario` estiver vazia e as variáveis
`AUTH_BOOTSTRAP_ADMIN_*` estiverem definidas. Consulte o `README.md` da raiz
para o procedimento completo.
