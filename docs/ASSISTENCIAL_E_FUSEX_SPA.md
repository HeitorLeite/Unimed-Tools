# Assistencial e valorização Fusex-SPA

## Assistencial — Atual

- A última estrutura publicada (colunas, conjunto de filtros, distinct e ordenação)
  é comparada antes de reconstruir o SQL. Paginação e troca somente dos valores
  dos filtros reutilizam a definição, mas consultam dados novos no SGU.
- O cache guarda apenas a definição, nunca resultados ou valores de filtros.
  Uma publicação com falha invalida o cache, inclusive quando o resultado remoto
  é incerto por timeout. Não há repetição automática da publicação.
- O lock continua cobrindo publicação e todas as páginas da consulta. Projeção,
  ranking, pivot mensal, ordenação e geração de arquivo acontecem após liberar
  o lock. Foram removidas cópias redundantes dos registros na análise sem pivot.
- Prévia, análise e exportação usam `relatorios.personalizado.api-nome`; o
  desenvolvimento mantém o sufixo `-dev`. A exportação simples não usa mais
  o nome fixo de produção.
- O SQL, os filtros, a paginação e as regras de agregação/ranking permanecem.
  O lock/cache é local à JVM: continua necessário um único backend publicador
  por nome de API. Múltiplas réplicas exigiriam coordenação externa.

### Decimais

O catálogo informa `casasDecimais: 2` para VALOR_FATOR, VALOR_PG_PROCEDIMENTO,
VALOR_PG_FILME, VALOR_PG_CO, VALOR_TOTAL, VALOR_TOTAL_21, RECEITA,
SINISTRALIDADE e VALOR_RECEBER. As colunas mensais herdam os metadados da base.

A prévia, CSV e TXT usam duas casas e vírgula decimal; XLSX mantém células
numéricas e máscara `#,##0.00`. O arredondamento é HALF_UP na apresentação e
exportação, após ranking, soma, distinct e ordenação. Nulos permanecem vazios
nos arquivos e como travessão na prévia. IDs, códigos, idade e contagens não
recebem essa máscara. Outros relatórios mantêm sua inferência de tipos atual.

**Pendente:** medir o ganho de tempo em relatórios corporativos representativos.
Os testes comprovam redução de reconstruções/publicações e liberação do lock
antes da escrita, mas não medem a execução do Oracle/SGU. Análises avançadas
ainda carregam todas as páginas a cada solicitação; não há cache de dados sensíveis.

## Valorizar guias Fusex-SPA — Parcial, execução bloqueada

Rota: `/valorizar-guias-fusex-spa`.
Permissão própria: `FUSEX_SPA_VALORIZAR`, exigida na rota e no backend.
Card, menu, configurações nativas da TI e gestão de permissões usam os registros
existentes. A permissão é concedida ao perfil Administrador; usuários operacionais
precisam de concessão explícita. Não se herda acesso de Relatórios/Assistencial.

### Inspeção do contrato SGU

`SguRelatorioService` publica JSON em `ins_atu_query_api`, consulta definições em
`lista_query_api`, exclui em `apaga_query_api` e executa relatórios por nome sob
`/api/procedure/p_prcssa_dados`. O nome “procedure” na URL não comprova aceitação
de SQL DML arbitrário, execução de múltiplos comandos ou commit.

Não há neste repositório contrato de operação para atualizar GUIA_ITEM, retorno
de linhas afetadas, confirmação de commit, rollback, idempotência ou recuperação
de timeout. As transações Spring/JDBC existentes são do MariaDB DBUNIMED
(identidade, permissões, auditoria e catálogos); não controlam transações Oracle
executadas por HTTP no SGU/Kong. Não há datasource Oracle ou execução JDBC de
procedures Oracle. Nenhuma chamada DML foi testada em produção.

Portanto, a ausência de suporte comprovado gera bloqueio obrigatório no servidor.
Não se publica UPDATE como relatório, não se envia COMMIT separado, não se cria
procedure fictícia nem se usa a conexão de identidade como conexão do SGU.

### Interface e endpoints

- `POST /api/fusex-spa/validar`: recebe `{ "guias": "101,102\n103" }`, valida
  inteiros não negativos de 64 bits, remove duplicados e retorna IDs como strings,
  quantidade informada, `execucaoDisponivel: false` e a explicação do bloqueio.
  Não consulta existência, convênio ou itens das guias.
- Limites: 20.000 caracteres e 1.000 IDs distintos por solicitação. O lote limita
  custo de validação e prepara um limite conservador para eventual IN Oracle.
  IDs ficam em strings no navegador para não perder precisão acima de 2^53.
- A revisão mostra a operação e exige confirmação explícita. Editar IDs invalida
  a revisão e a confirmação. O botão de execução fica desabilitado nesta versão.
- `POST /api/fusex-spa/executar`: exige a mesma lista e `confirmado: true`.
  Sem confirmação retorna 400; com confirmação retorna **501** e código
  `SGU_DML_NAO_SUPORTADO`. Validação inválida retorna 400, ausência de sessão 401,
  falta de permissão ou CSRF 403. Não há retry automático.
- A tentativa confirmada bloqueada gera auditoria com executor, quantidade e
  motivo, sem registrar IDs ou dados financeiros. Não retorna contagens de
  afetados fictícias nem representa bloqueio como sucesso.

### Operação pretendida e condição de liberação — Pendente

```sql
UPDATE DBAUNIMED.GUIA_ITEM
SET GUITE_VAL_FAT_HONOR = GUITE_VAL_INFORM_HONOR
WHERE GUIA_COD_ID IN (:id1, :id2 /* demais binds validados */)
  AND GUITE_VAL_INFORM_HONOR <> GUITE_VAL_FAT_HONOR;
COMMIT;
```

O predicado solicitado exclui comparações envolvendo NULL; não foi substituído
por NVL. O nome da ferramenta não acrescenta filtro de convênio não solicitado.
Para liberar, a TI deve fornecer e homologar um endpoint/procedure suportado,
com autorização mínima, parâmetros tipados, atomicidade, rollback, confirmação
do commit, sem repetição cega após timeout e contagens de itens/guias quando
disponíveis. Uma nova implementação deverá adaptar a resposta da UI e os testes
ao contrato real; não existe flag para habilitar execução sem implementação.

## Verificação e instalação

Testes sintéticos cobrem cache, falha de publicação, exportação no ambiente dev,
concorrência durante a escrita, CSV/TXT/XLSX, arredondamento e preservação de
identificadores, validação de IDs, confirmação, permissão e CSRF.

Instalações existentes: migração `007_permissao_fusex_spa.sql`, após as anteriores,
ou sincronização idempotente do catálogo no início do backend. Instalações novas
usam `database/DBUNIMED.sql`. Não há mudança nos atalhos local/prod nem deploy
automático. O PR permanece sujeito à revisão humana antes do merge/publicação.
