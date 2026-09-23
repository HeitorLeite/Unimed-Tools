# Assistencial

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

## Verificação

Testes sintéticos cobrem cache, falha de publicação, exportação no ambiente dev,
concorrência durante a escrita, CSV/TXT/XLSX, arredondamento e preservação de
identificadores. Não há mudança nos atalhos local/prod nem deploy automático.
