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

## Valorizar guias Fusex-SPA — Atual, execução de teste controlada

Rota: `/valorizar-guias-fusex-spa`.
Permissão própria: `FUSEX_SPA_VALORIZAR`, exigida na rota e no backend.
A execução continua protegida por sessão, CSRF e permissão específica.

### Fluxo SGU usado no teste

A ferramenta reaproveita o contrato existente em `SguRelatorioService`:
1. publica/atualiza uma API reservada por `ins_atu_query_api`;
2. chama essa API em `/api/procedure/p_prcssa_dados/{nome}`.

O watch mode usa `0090-valorizar-guias-fusex-spa-dev`; fora dele, o padrão é
`0090-valorizar-guias-fusex-spa`. A variável `FUSEX_SPA_API_NOME` pode
sobrescrever o nome.

```sql
UPDATE dbaunimed.guia_item i
SET
    i.guite_val_fat_honor = i.guite_val_inform_honor
WHERE
    i.guia_cod_id IN (<ids validados>)
    AND i.guite_val_inform_honor <> i.guite_val_fat_honor;

COMMIT;
```

O `ins_atu_query_api` exige que `filtros` contenha ao menos uma definição.
Por isso a publicação inclui um filtro técnico opcional chamado `controle`, com
`AND 1 = :controle`. Esse parâmetro não é enviado durante a execução, então o
filtro não é aplicado e não altera o conjunto de guias do UPDATE.

Os IDs são validados no frontend e novamente no backend, aceitando somente dígitos
de até 64 bits, removendo duplicados e limitando o lote a 1.000 guias. Antes de
entrar no `IN`, cada valor passa por `Long.parseLong` e é reserializado na
forma canônica, impedindo a entrada de fragmentos SQL arbitrários.

Como a API do SGU é mutável, publicação e execução ficam sob um lock único na JVM.
Não existe retry automático: em erro ou timeout, o estado das guias deve ser
conferido antes de uma nova tentativa. A auditoria registra executor, quantidade,
nome da API, etapa e resultado, sem registrar os IDs ou valores financeiros.

**Atual:** o bloqueio HTTP 501 foi removido e o backend tenta publicar e executar o
UPDATE + COMMIT pelo mesmo caminho SGU/Kong usado pelos relatórios.

**Pendente:** confirmar no ambiente autorizado se `ins_atu_query_api` aceita DML,
se `p_prcssa_dados` executa múltiplos comandos e se o `COMMIT` é efetivamente
aplicado. Uma resposta HTTP de sucesso comprova somente que o endpoint aceitou a
chamada; o resultado deve ser conferido no SGU antes de repetir.

A alteração não adiciona datasource Oracle, não contorna ACL/WAF/Kong, não expõe a
chave do SGU e não libera SQL arbitrário vindo do navegador.

## Verificação e instalação

Testes sintéticos cobrem cache, falha de publicação, exportação no ambiente dev,
concorrência durante a escrita, CSV/TXT/XLSX, arredondamento e preservação de
identificadores, validação de IDs, confirmação, permissão e CSRF.

Instalações existentes: migração `007_permissao_fusex_spa.sql`, após as anteriores,
ou sincronização idempotente do catálogo no início do backend. Instalações novas
usam `database/DBUNIMED.sql`. Não há mudança nos atalhos local/prod nem deploy
automático. O PR permanece sujeito à revisão humana antes do merge/publicação.
