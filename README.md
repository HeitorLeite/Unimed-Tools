# Unimed Tools — Corretor de Arquivos

Aplicação interna desenvolvida para centralizar rotinas de correção, validação, transformação e exportação de arquivos utilizados pela Unimed Lorena.

O projeto reúne ferramentas para arquivos XML TISS, planilhas de BI, arquivos posicionais da ANS, consultas do SGU e futuras rotinas de fechamento de produção.

---

## Sumário

- [Visão geral](#visão-geral)
- [Status das páginas](#status-das-páginas)
- [Arquitetura](#arquitetura)
- [Tecnologias](#tecnologias)
- [Como cada página funciona](#como-cada-página-funciona)
  - [Página inicial](#1-página-inicial)
  - [Ferramentas XML TISS](#2-ferramentas-xml-tiss)
  - [BI — Especialidade Médica](#3-bi--especialidade-médica)
  - [Central de Relatórios](#4-central-de-relatórios)
  - [ANS — Corretor de Rede RPS](#5-ans--corretor-de-rede-rps)
  - [Fechamento de Produção](#6-fechamento-de-produção)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Como executar localmente](#como-executar-localmente)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Endpoints do backend](#endpoints-do-backend)
- [Deploy](#deploy)
- [Limitações conhecidas](#limitações-conhecidas)
- [Solução de problemas](#solução-de-problemas)
- [Segurança](#segurança)

---

## Visão geral

A aplicação é dividida em dois projetos:

- **Frontend Angular:** interface, navegação, upload de arquivos, visualização de resultados e downloads.
- **Backend Spring Boot:** processamento de planilhas e arquivos posicionais, integração com o SGU e geração de arquivos para exportação.

A página de XML é uma exceção importante: o processamento principal dos XMLs ocorre diretamente no navegador. As outras ferramentas utilizam o backend.

### Fluxo geral

```mermaid
flowchart LR
    U[Usuário] --> F[Frontend Angular]

    F -->|XML TISS| X[Processamento no navegador]
    X --> D1[XML ou ZIP corrigido]

    F -->|Uploads e parâmetros| B[Backend Spring Boot]
    B --> BI[Processamento XLSX com Apache POI]
    B --> ANS[Filtragem de TXT posicional]
    B --> EXP[Exportação CSV, TXT e XLSX]
    B --> SGU[SGU Suite / Kong]

    BI --> D2[Planilha preenchida]
    ANS --> D3[TXT filtrado]
    EXP --> D4[Relatório exportado]
```

---

## Status das páginas

| Página                     | Rota                       |                Status | Processamento                  |
| -------------------------- | -------------------------- | --------------------: | ------------------------------ |
| Página inicial             | `/`                        |            Disponível | Frontend                       |
| Ferramentas XML TISS       | `/xml/ferramentas`         |            Disponível | Navegador                      |
| BI — Especialidade Médica  | `/bi/especialidade-medica` |            Disponível | Backend                        |
| Central de Relatórios      | `/relatorios`              | Disponível localmente | Backend + SGU                  |
| ANS — Corretor de Rede RPS | `/ans/corretor-rede`       |            Disponível | Backend                        |
| Fechamento de Produção     | `/fechamento/corretor`     |    Em desenvolvimento | Backend ainda não implementado |

### Situação atual da Central de Relatórios

A integração com o SGU funciona quando o backend é executado localmente. No ambiente hospedado no Render, o SGU/Kong atualmente retorna `HTTP 403 Forbidden`.

Isso indica uma restrição externa de IP, ACL, WAF ou autorização de origem. Até que o SGU/Kong libere o tráfego de saída do Render, a Central de Relatórios deve ser utilizada localmente.

---

## Arquitetura

### Frontend

O frontend utiliza componentes standalone do Angular e carregamento de páginas por rota.

Principais responsabilidades:

- apresentar as ferramentas disponíveis;
- validar os arquivos selecionados;
- enviar arquivos ao backend;
- acompanhar progresso e mensagens;
- exibir estatísticas;
- gerar downloads;
- montar dinamicamente filtros e tabelas de relatórios;
- armazenar o catálogo pessoal de relatórios no navegador.

Em desenvolvimento, o frontend usa um proxy:

```text
/api → http://localhost:8080
```

O proxy possui tempo limite de 120 segundos e é carregado pelo comando `npm start`.

### Backend

O backend recebe requisições HTTP, processa arquivos e devolve os resultados para download.

Principais responsabilidades:

- ler e escrever planilhas XLSX;
- filtrar arquivos TXT posicionais;
- expor endpoints para análise e correção de XML;
- comunicar-se com a API do SGU;
- paginar relatórios;
- exportar relatórios completos em CSV, TXT e XLSX;
- devolver estatísticas por meio do header `X-Stats`.

### Limite de upload

O backend está configurado para aceitar requisições de até:

```text
100 MB
```

---

## Tecnologias

### Frontend

- Angular 21
- TypeScript 5.9
- RxJS 7.8
- SCSS
- npm

### Backend

- Java 21
- Spring Boot 3.3
- Maven
- Apache POI
- Apache Commons CSV
- Docker

### Infraestrutura utilizada

- GitHub para versionamento
- Vercel para o frontend
- Render para o backend
- SGU Suite/Kong para consultas externas

---

# Como cada página funciona

## 1. Página inicial

**Rota:** `/`

A página inicial apresenta os módulos da aplicação em cartões.

Cada cartão informa:

- nome da ferramenta;
- setor ou categoria;
- descrição;
- formatos aceitos;
- disponibilidade atual.

As ferramentas marcadas como disponíveis abrem sua respectiva página. Ferramentas ainda não concluídas aparecem como indisponíveis ou “Em breve”.

### Categorias exibidas

- XML — TISS
- BI — Business Intelligence
- Relatórios — Consultas SGU
- Fechamento — Produção
- ANS — Agência Nacional de Saúde Suplementar

A navegação interna também pode ser feita pelo menu lateral.

---

## 2. Ferramentas XML TISS

**Rota:** `/xml/ferramentas`

A página processa um ou vários arquivos XML TISS no próprio navegador.

Nenhum upload para o backend é necessário para o fluxo principal dessa página.

### Arquivos aceitos

```text
.xml
```

### Formas de envio

- seleção pelo explorador de arquivos;
- arrastar e soltar;
- processamento em lote de vários XMLs.

Arquivos com nomes repetidos não são adicionados novamente ao lote.

### Operações disponíveis

#### Todas as correções

Executa em conjunto:

- correção de prefixos;
- remoção de despesas zeradas;
- remoção de contêineres vazios;
- detecção e renomeação de guias duplicadas.

#### Corretor de prefixo

Localiza procedimentos com:

```xml
<ans:codigoTabela>00</ans:codigoTabela>
```

e remove os prefixos incorretos `18`, `19` ou `20` do início de:

```xml
<ans:codigoProcedimento>
```

Exemplo:

```xml
<ans:codigoProcedimento>1810101039</ans:codigoProcedimento>
```

é transformado em:

```xml
<ans:codigoProcedimento>10101039</ans:codigoProcedimento>
```

#### Removedor de blocos zerados

Localiza blocos:

```xml
<ans:despesa>...</ans:despesa>
```

e remove aqueles cujo:

```xml
<ans:valorTotal>
```

é igual a zero.

Também remove contêineres vazios nas formas:

```xml
<ans:outrasDespesas/>
```

ou:

```xml
<ans:outrasDespesas></ans:outrasDespesas>
```

#### Guias duplicadas

A ferramenta analisa os valores de:

```xml
<ans:numeroGuiaPrestador>
```

em todo o lote.

Quando o mesmo número aparece em mais de um arquivo, são adicionados sufixos sequenciais:

```text
12345a
12345b
12345c
```

### Resultado exibido

Para cada arquivo, a tela pode mostrar:

- prefixos encontrados e corrigidos;
- posição da ocorrência;
- valor original e valor final;
- blocos zerados removidos;
- data de execução;
- tabela e código do procedimento;
- descrição do procedimento;
- tags `outrasDespesas` vazias;
- linha e coluna da tag;
- número da guia relacionada;
- guias duplicadas e novos números.

A página também apresenta totais gerais do lote.

### Download

É possível baixar:

- um arquivo individual;
- todos os arquivos em ZIP.

As opções de nome são:

- ordem numérica: `1.xml`, `2.xml`, `3.xml`;
- nome original com sufixo: `arquivo_corrigido.xml`;
- nomes personalizados.

No download em lote, o arquivo padrão é:

```text
arquivos_xml_corrigidos.zip
```

### Observações

A lógica procura tags que utilizam o prefixo `ans:`. XMLs com estrutura ou namespace diferente podem não ser reconhecidos.

Sempre valide o arquivo corrigido antes de enviá-lo para produção.

---

## 3. BI — Especialidade Médica

**Rota:** `/bi/especialidade-medica`

Essa página preenche automaticamente a especialidade médica em uma planilha de despesas.

### Entradas

São necessários dois arquivos:

#### Planilha de despesas

Formato recomendado:

```text
.xlsx
```

A interface também permite selecionar CSV, mas o backend atual utiliza `XSSFWorkbook`. Portanto, o fluxo confiável e oficialmente suportado no momento é XLSX.

O sistema procura uma aba cujo nome contenha:

```text
despesa
```

Se nenhuma aba for encontrada, a primeira aba da planilha é utilizada.

#### Planilha de médicos

Formato:

```text
.xlsx
```

A primeira aba deve conter obrigatoriamente as colunas:

```text
PES_NOM_COMP
ESPMD_DES
```

### Colunas reconhecidas na planilha de despesas

#### Especialidade

Um destes nomes:

```text
NOME ESPECIALIDADE
NOME_ESPECIALIDADE
```

#### Solicitante ou prestador

Um destes nomes:

```text
NOME_PRESTADOR_SOLIC
NOME SOLICITANTE
NOME_SOLICITANTE
NOME_PRESTADOR
```

#### Código TUSS opcional

Um destes nomes:

```text
COD_TUSS
CODIGO_TUSS
```

### Regras de preenchimento

1. Linhas que já possuem especialidade não são alteradas.
2. Quando a especialidade está vazia, o sistema procura o nome do solicitante na planilha de médicos.
3. A comparação é feita com o texto sem diferenças entre maiúsculas e minúsculas.
4. Quando o solicitante está vazio, o sistema pode usar um mapa fixo de códigos TUSS.

### Mapa TUSS atual

| Código     | Especialidade  |
| ---------- | -------------- |
| `10101039` | CLÍNICA MÉDICA |
| `50000349` | FISIOTERAPIA   |
| `50000470` | PSICOLOGIA     |
| `50000560` | NUTRICIONISTA  |

### Estatísticas exibidas

Ao final, a página mostra:

- aba processada;
- total de linhas;
- especialidades preenchidas;
- linhas que já estavam preenchidas;
- linhas sem informação suficiente.

Esses dados são enviados pelo backend no header:

```text
X-Stats
```

### Saída

O backend sempre gera uma planilha XLSX com nome semelhante a:

```text
arquivo_PREENCHIDO.xlsx
```

---

## 4. Central de Relatórios

**Rota:** `/relatorios`

A Central de Relatórios permite cadastrar, localizar, executar, visualizar e exportar consultas publicadas como APIs no SGU.

**Status:** Atual e disponível no ambiente local.

A página oferece três modos:

- **Manual:** catálogo local de APIs do SGU, com filtros e exportação sob demanda;
- **Automático:** execução e exportação em lote dos grupos salvos no navegador;
- **Personalizado:** construtor guiado com filtros e colunas previamente autorizados pelo backend.

### Relatório personalizado

**Status:** Atual.

O modo de relatório personalizado utiliza exclusivamente a API reservada:

```text
0090-relatorio-personalizado
```

A fonte atual é **Despesas por item de guia**. O catálogo controlado pelo
backend contém 50 colunas e 23 filtros, organizados nos grupos Beneficiário,
Contrato e empresa, Prestador, Guia, Procedimento, Valores e Período. As
competências inicial e final são obrigatórias e o intervalo aceita no máximo 12
meses. A prévia permite até 100 linhas por página; a exportação percorre todas
as páginas e gera CSV, TXT ou XLSX apenas com as colunas selecionadas.

Fluxo atual:

1. o frontend solicita ao backend os rótulos, tipos, grupos, limites e marcações
   de dados sensíveis;
2. o usuário informa os filtros e escolhe ao menos uma coluna;
3. o backend valida a allowlist de campos, tipos, intervalos e paginação;
4. o SQL é montado somente com expressões aprovadas no código;
5. a definição é publicada na API reservada e executada no SGU;
6. a resposta é projetada novamente no backend para devolver somente as
   colunas solicitadas;
7. a interface apresenta a prévia ou baixa a exportação completa.

A cada execução, o backend monta a consulta somente com colunas e filtros do
catálogo aprovado. A API é publicada no SGU na primeira consulta e sempre que a
estrutura mudar; paginações ou repetições idênticas reutilizam a definição já
publicada para evitar uma chamada administrativa desnecessária. Em seguida, o
backend executa a consulta. Os identificadores usados entre Angular e Spring
Boot permanecem com underscore, como `competencia_inicio`. Na fronteira HTTP
com o SGU, o backend remove os separadores e envia um identificador alfanumérico
em minúsculas, como `competenciainicio`. O mesmo identificador é utilizado no bind
`:competenciainicio`: ele não contém o underscore rejeitado pelo SGU nem o hífen
inválido em parâmetros nomeados do Oracle. `nomeFiltro`, `conteudoFiltro` e os
parâmetros de execução ficam, portanto, com exatamente o mesmo nome, sem
comentários ou condições artificiais no SQL. O backend continua aceitando IDs
com underscore ou hífen na requisição para preservar compatibilidade, mas nunca
monta fragmentos SQL enviados pelo usuário.

A consulta publicada calcula os campos filtráveis em uma consulta interna. O
marcador `/*FILTROS*/` fica no `WHERE` da consulta externa e cada
`conteudoFiltro` usa somente o alias calculado, um operador e o bind compacto,
por exemplo `and RP.F_CODIGO_BENEFICIARIO = :codigobeneficiario`. Funções,
expressões `CASE`, normalização de texto e formatação não são enviadas no
`conteudoFiltro`: os cálculos permanecem no SQL-base aprovado e os valores de
busca são normalizados pelo backend. Aliases técnicos usados na filtragem e na
ordenação não são devolvidos ao navegador.

A API reservada não aparece na listagem do catálogo manual e não pode ser
alterada, executada ou removida pelos endpoints genéricos. Como ela é um
recurso mutável compartilhado no SGU, o backend usa um lock durante publicação,
execução e exportação. A última definição publicada também fica em cache na
memória do processo: uma mudança nas colunas ou nos filtros ativos republica a
API; uma nova página com a mesma estrutura reutiliza a definição existente.

Validações atuais do construtor:

- pelo menos uma coluna autorizada deve ser selecionada;
- competência usa `AAAAMM`, possui mês válido e intervalo máximo de 12 meses;
- datas inicial e final precisam formar um intervalo válido;
- valor máximo não pode ser menor que o mínimo;
- filtros numéricos e decimais são convertidos no backend;
- CPF, código de beneficiário, CID e buscas textuais são normalizados antes da
  integração;
- filtros desconhecidos, duplicados ou acima de 240 caracteres são rejeitados;
- nomes de arquivo são sanitizados antes do download.

No frontend Angular sem Zone.js, o componente solicita explicitamente a
atualização da view ao iniciar e concluir a chamada HTTP. Assim, o indicador de
carregamento, a tabela, a paginação e as mensagens aparecem automaticamente
quando a resposta chega, sem depender de redimensionamento ou abertura do F12.

### Dependências

Para funcionar, são necessários:

- backend Spring Boot em execução;
- acesso à API do SGU;
- API key válida;
- permissão do SGU/Kong para o IP de origem do backend.

### Catálogo local

Os relatórios adicionados à interface são armazenados no navegador por meio do `localStorage`.

Chave utilizada:

```text
unimed-tools.relatorios.v1
```

Isso significa que:

- o catálogo é específico de cada navegador;
- limpar os dados do navegador remove o catálogo local;
- adicionar um relatório em um computador não o adiciona automaticamente em outro;
- a API continua existindo no SGU mesmo que o item seja removido apenas do catálogo local.

### Adicionar uma API existente

1. Clique em **Novo relatório**.
2. Escolha **Usar API existente**.
3. Informe o nome cadastrado no SGU.
4. Faça a busca.
5. Informe o nome de exibição e uma descrição opcional.
6. Salve no catálogo.

O backend chama:

```text
lista_query_api
```

para localizar a definição e os filtros da API.

### Criar uma nova API

A página também permite cadastrar uma consulta no SGU.

São informados:

- nome da API;
- nome de exibição;
- descrição;
- SQL;
- ordenação;
- filtros.

A criação utiliza:

```text
ins_atu_query_api
```

### Regras dos filtros

O nome do filtro deve:

- estar em minúsculo;
- não conter espaços;
- usar apenas letras, números ou underscore.

Exemplo:

```text
competencia
```

O conteúdo precisa começar exatamente com:

```text
and
```

em letras minúsculas.

Também precisa conter o parâmetro com dois-pontos:

```text
:competencia
```

Tipos aceitos:

```text
NUMBER
DATE
VARCHAR(tamanho)
```

Exemplo:

```json
{
  "nomeFiltro": "competencia",
  "conteudoFiltro": "and G.GUIA_NRO_COMPET = :competencia",
  "tipoDadoFiltro": "NUMBER",
  "mascaraFiltro": "",
  "obrigatorioFiltro": "S"
}
```

Para filtros com vários códigos separados por vírgula, utilize `VARCHAR`, não `NUMBER`.

### Gerar relatório

Ao selecionar um relatório:

1. a tela monta automaticamente os campos;
2. os filtros obrigatórios são validados;
3. filtros `NUMBER` são enviados como número;
4. filtros `DATE` e `VARCHAR` são enviados como texto;
5. o backend acrescenta `page` e `size`;
6. o SGU retorna os registros;
7. a tabela é criada dinamicamente com base nas colunas da resposta.

O usuário pode escolher:

```text
25, 50 ou 100 linhas por página
```

### Exportações

Formatos disponíveis:

- CSV;
- TXT;
- XLSX.

A exportação não utiliza somente a página visível. O backend percorre as páginas do SGU até obter o relatório completo ou atingir o limite configurado.

#### CSV

- codificação UTF-8;
- BOM para facilitar abertura no Excel;
- separador `;`.
- datas são normalizadas como `DD/MM/AAAA`;
- valores decimais usam vírgula como separador decimal;
- campos textuais potencialmente interpretados como fórmula são neutralizados.

#### TXT

- codificação UTF-8;
- BOM;
- colunas separadas por tabulação.
- datas e valores decimais seguem a mesma representação do CSV;
- campos textuais potencialmente interpretados como fórmula são neutralizados.

#### XLSX

- cabeçalho em destaque;
- primeira linha congelada;
- filtro automático;
- colunas com largura ajustada ao conteúdo, limitada a 60 caracteres;
- células gravadas com tipo e formato de texto, número inteiro, decimal ou data;
- códigos, documentos, guias, contratos, competências e outros identificadores
  permanecem como texto para preservar zeros à esquerda;
- geração por workbook de streaming.

O XLSX é o formato indicado quando a tipagem das células precisa ser preservada.
CSV e TXT são formatos textuais e, por definição, não armazenam tipos de coluna;
nesses formatos a aplicação normaliza a representação para facilitar a importação
no Excel, mas o resultado final ainda depende das opções de importação e da
configuração regional do programa.

### Remover relatório

Há duas opções:

- remover apenas do catálogo do navegador;
- remover do catálogo e também apagar a API no SGU.

A exclusão no SGU utiliza:

```text
apaga_query_api
```

### Limitação atual no Render

A mesma API key retorna sucesso em chamadas locais, mas o SGU/Kong responde `403 Forbidden` quando a requisição se origina no Render.

O código do frontend e do backend não deve tentar contornar essa restrição.

As soluções possíveis são:

- liberar no SGU/Kong os IPs de saída do Render;
- utilizar hospedagem com IP fixo autorizado;
- utilizar proxy com IP autorizado;
- executar o backend dentro da rede corporativa;
- continuar utilizando o módulo localmente.

---

## 5. ANS — Corretor de Rede RPS

**Rota:** `/ans/corretor-rede`

Essa página remove linhas de um TXT posicional com base em registros de erro fornecidos pela ANS.

### Entradas obrigatórias

- um arquivo TXT posicional;
- pelo menos uma fonte de erros.

### Modos disponíveis

#### Planilha XLSX com múltiplas abas

Envie:

- uma planilha XLSX;
- o arquivo TXT que será filtrado.

Abas reconhecidas:

```text
ECnes
CNES
Municpio
Municipio
Prestador
Aviso
Erros
erros_*
misto*
```

As abas `Erros` podem conter mensagens em texto livre. O backend tenta identificar automaticamente o tipo de ocorrência.

#### Arquivos individuais

É possível fornecer arquivos separados para:

- ECnes;
- CNES;
- Município;
- Prestador;
- Aviso.

Todos são opcionais individualmente, mas pelo menos um deve ser informado junto com o TXT.

### Critérios de filtragem

#### ECnes

Remove por:

```text
CNPJ/CPF
```

#### CNES

Remove pela combinação:

```text
CNPJ/CPF + CNES
```

#### Município

Remove pela combinação:

```text
CNPJ/RAZÃO + CNES
```

#### Prestador — erro 8521

Pode remover por:

- combinação exata de CNPJ e CNES;
- somente CNPJ, quando o CNES está vazio, funcionando como curinga.

#### Aviso

Remove pelo CNES.

### Estrutura do TXT

O backend lê o arquivo com codificação:

```text
ISO-8859-1
```

Posições utilizadas:

| Informação | Posição inicial | Posição final exclusiva |
| ---------- | --------------: | ----------------------: |
| CNPJ       |               0 |                      14 |
| CNES 1     |              94 |                     101 |
| CNES 2     |             109 |                     116 |

O sistema completa:

- CNPJ com zeros à esquerda até 14 posições;
- CNES com zeros à esquerda até 7 posições.

### Resultado

A página exibe:

- quantidade de chaves carregadas por categoria;
- linhas lidas;
- linhas removidas;
- linhas mantidas;
- log da execução.

As estatísticas chegam pelo header:

```text
X-Stats
```

### Saída

```text
nome_original_filtrado.txt
```

O arquivo de saída mantém a codificação ISO-8859-1.

---

## 6. Fechamento de Produção

**Rota:** `/fechamento/corretor`

A interface dessa página foi preparada para converter uma planilha de eventos em CSV para fechamento de produção.

### Fluxo previsto

1. selecionar uma planilha XLSX;
2. enviar o arquivo ao backend;
3. acompanhar o progresso;
4. receber o arquivo convertido;
5. baixar como:

```text
nome_original_convertido.csv
```

### Endpoint previsto

```text
POST /api/fechamento/converter
```

### Status atual

O cartão está marcado como não disponível na página inicial e no menu lateral.

O frontend existe, mas o backend atual não contém um controller ou serviço para `fechamento/converter`. Portanto, essa página deve ser considerada **em desenvolvimento**.

---

## Estrutura do projeto

```text
corretor-de-arquivos/
├── unimed-tools-frontend/             # Frontend Angular
│   ├── public/
│   ├── src/
│   │   ├── app/
│   │   │   ├── layout/
│   │   │   │   ├── main-layout/
│   │   │   │   └── sidebar/
│   │   │   ├── pages/
│   │   │   │   ├── home/
│   │   │   │   ├── xml/
│   │   │   │   ├── bi/
│   │   │   │   ├── relatorios/
│   │   │   │   ├── ans/
│   │   │   │   └── fechamento/
│   │   │   ├── shared/
│   │   │   │   ├── components/
│   │   │   │   ├── models/
│   │   │   │   └── services/
│   │   │   └── app.routes.ts
│   │   ├── environments/
│   │   ├── main.ts
│   │   └── styles.scss
│   ├── angular.json
│   ├── package.json
│   └── proxy.conf.json
│
├── unimed-tools-backend/              # Backend Spring Boot
│   ├── src/main/java/com/unimedlorena/tools/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── service/
│   │   ├── config/
│   │   ├── exception/
│   │   └── UnimedToolsApplication.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── Dockerfile
│   └── pom.xml
│
├── docs/
│   └── ARQUITETURA.md                  # Fronteiras e responsabilidades técnicas
│
├── .gitignore
├── AGENTS.md                           # Orientações para agentes de IA
├── SEGURANCA.md               # Política de desenvolvimento seguro e privacidade
├── PADRAO_DE_COMMITS.md                # Conventional Commits do projeto
├── render.yaml
└── README.md
```

---

## Como executar localmente

### Pré-requisitos

Instale:

- Git;
- Node.js compatível com o Angular 21;
- npm;
- Java 21;
- Maven 3.9 ou superior.

### 1. Clonar o projeto

```bash
git clone https://github.com/HeitorLeite/corretor-de-arquivos.git
cd corretor-de-arquivos
```

### 2. Iniciar o backend

#### PowerShell

```powershell
cd unimed-tools-backend

$env:SGU_API_BASE_URL="https://api.lorena.sgusuite.com.br"
$env:SGU_API_KEY="SUA_CHAVE_REAL"
$env:SGU_API_PROCEDURE_PATH="/api/procedure/p_prcssa_dados"
$env:SGU_API_EXECUTION_PATH="/api/procedure/p_prcssa_dados"

mvn spring-boot:run
```

#### Linux ou macOS

```bash
cd unimed-tools-backend

export SGU_API_BASE_URL="https://api.lorena.sgusuite.com.br"
export SGU_API_KEY="SUA_CHAVE_REAL"
export SGU_API_PROCEDURE_PATH="/api/procedure/p_prcssa_dados"
export SGU_API_EXECUTION_PATH="/api/procedure/p_prcssa_dados"

mvn spring-boot:run
```

O backend será iniciado em:

```text
http://localhost:8080
```

Teste:

```text
http://localhost:8080/health
```

Resposta esperada:

```text
ok
```

> A chave do SGU é necessária apenas para a Central de Relatórios. As outras ferramentas podem ser utilizadas sem ela.

### 3. Iniciar o frontend

Em outro terminal:

```bash
cd unimed-tools-frontend
npm ci
npm start
```

Abra:

```text
http://localhost:4200
```

Use `npm start`, pois esse comando ativa o proxy definido em `proxy.conf.json`.

Executar somente `ng serve` sem o proxy pode fazer as chamadas `/api` serem enviadas para a porta errada.

### Acesso pela rede local com XAMPP

**Atual:** o build `lan` publica a aplicação no caminho `/unimed-tools/` e usa
o mesmo domínio do Apache para acessar `/api`. O Apache funciona como proxy
reverso para o Spring Boot em `127.0.0.1:8080`; portanto, somente a porta 80
precisa ficar acessível aos demais computadores.

1. Inicie o backend na máquina que executa o XAMPP, restringindo a porta Java
   ao próprio computador:

```powershell
cd unimed-tools-backend
$env:SERVER_ADDRESS="127.0.0.1"
mvn spring-boot:run
```

   Para usar a Central de Relatórios, defina também `SGU_API_KEY` como variável
   de ambiente com uma chave válida e nunca a grave no repositório.
2. Gere o frontend específico para a rede local:

```powershell
cd unimed-tools-frontend
npm run build:lan
```

3. Publique **somente** o conteúdo de
   `dist/unimed-tools-frontend/browser/` em
   `C:\xampp\htdocs\unimed-tools\`. Não copie o repositório, fontes,
   configurações do backend ou arquivos `.env` para `htdocs`.
4. No Apache, mantenha `mod_proxy`, `mod_proxy_http` e `mod_rewrite` ativos e
   adicione a configuração abaixo em
   `C:\xampp\apache\conf\extra\httpd-vhosts.conf`:

```apache
ProxyRequests Off
ProxyTimeout 3600
ProxyPass        /api http://127.0.0.1:8080/api
ProxyPassReverse /api http://127.0.0.1:8080/api
```

5. Valide a configuração com `httpd.exe -t`, reinicie o Apache e abra, em
   outro computador da mesma rede:

```text
http://IP_DA_MAQUINA/unimed-tools/
```

O arquivo `.htaccess` incluído no build direciona rotas como
`/unimed-tools/relatorios` para o `index.html`. Se o Windows bloquear o acesso,
libere no Firewall apenas a porta TCP 80 para o perfil de rede privada. O
backend deve permanecer restrito à própria máquina, atrás do proxy do Apache.

#### Atualizar o frontend publicado no XAMPP

O código-fonte deve ser alterado em `unimed-tools-frontend/`. A pasta
`C:\xampp\htdocs\unimed-tools\` recebe somente o resultado compilado e não deve
ser editada manualmente.

```powershell
cd unimed-tools-frontend
npm test -- --watch=false
npm run build:lan

Copy-Item `
  -Path ".\dist\unimed-tools-frontend\browser\*" `
  -Destination "C:\xampp\htdocs\unimed-tools" `
  -Recurse `
  -Force
```

Não é necessário executar `npm start` nem reiniciar o Apache após copiar um
novo build. Nos demais computadores, atualize a página com `Ctrl + F5`.

### Build do frontend

```bash
npm run build
```

### Build do backend

```bash
mvn clean package
```

O JAR será criado dentro de:

```text
unimed-tools-backend/target/
```

---

## Executar o backend com Docker

```bash
cd unimed-tools-backend

docker build -t unimed-tools-backend .
```

```bash
docker run --rm \
  -p 8080:8080 \
  -e PORT=8080 \
  -e SGU_API_BASE_URL=https://api.lorena.sgusuite.com.br \
  -e SGU_API_KEY=SUA_CHAVE_REAL \
  -e SGU_API_PROCEDURE_PATH=/api/procedure/p_prcssa_dados \
  -e SGU_API_EXECUTION_PATH=/api/procedure/p_prcssa_dados \
  unimed-tools-backend
```

No PowerShell, adapte as quebras de linha ou execute o comando em uma linha única.

---

## Variáveis de ambiente

| Variável                 |     Obrigatória | Padrão                               | Função                                              |
| ------------------------ | --------------: | ------------------------------------ | --------------------------------------------------- |
| `PORT`                   |             Não | `8080`                               | Porta do backend                                    |
| `SERVER_ADDRESS`         |             Não | `0.0.0.0`                            | Interface de rede; use `127.0.0.1` atrás do XAMPP   |
| `SGU_API_BASE_URL`       | Para relatórios | `https://api.lorena.sgusuite.com.br` | URL principal do SGU                                |
| `SGU_API_KEY`            | Para relatórios | vazio                                | Credencial enviada somente pelo backend             |
| `SGU_API_KEY_HEADERS`    |             Não | `apikey,x-api-key`                   | Headers que recebem a chave; use `apikey` se exigido |
| `SGU_API_PROCEDURE_PATH` |             Não | `/api/procedure/p_prcssa_dados`      | Caminho das rotinas administrativas                 |
| `SGU_API_EXECUTION_PATH` |             Não | `/api/procedure/p_prcssa_dados`      | Caminho de execução dos relatórios                  |
| `SGU_EXPORT_PAGE_SIZE`   |             Não | `1000`                               | Registros solicitados por página durante exportação |
| `SGU_EXPORT_MAX_PAGES`   |             Não | `1000`                               | Limite de páginas de uma exportação                 |

### Regras para `SGU_API_KEY`

Informe somente o valor da chave.

Correto:

```text
abc123...
```

Incorreto:

```text
apikey: abc123...
```

```text
SGU_API_KEY=abc123...
```

Nunca salve a chave no:

- `application.properties`;
- `environment.prod.ts`;
- código Java;
- código Angular;
- README;
- histórico do Git.

---

## Endpoints do backend

### Saúde

| Método | Endpoint  | Descrição                        |
| ------ | --------- | -------------------------------- |
| GET    | `/health` | Verifica se o backend está ativo |

### XML

| Método | Endpoint            | Descrição                |
| ------ | ------------------- | ------------------------ |
| POST   | `/api/xml/analisar` | Analisa um XML           |
| POST   | `/api/xml/corrigir` | Corrige e devolve um XML |

Parâmetros multipart:

- `file`;
- `corretor`;
- `removedor`.

> A interface atual de XML realiza seu processamento principal no navegador. Esses endpoints permanecem disponíveis no backend.

### BI

| Método | Endpoint                | Descrição                                       |
| ------ | ----------------------- | ----------------------------------------------- |
| POST   | `/api/bi/especialidade` | Preenche especialidades na planilha de despesas |

Multipart:

- `despesas`;
- `medicos`.

### ANS

| Método | Endpoint                | Descrição                              |
| ------ | ----------------------- | -------------------------------------- |
| POST   | `/api/ans/filtrar-xlsx` | Filtra TXT usando planilha XLSX        |
| POST   | `/api/ans/filtrar-csvs` | Filtra TXT usando arquivos individuais |

### Relatórios

| Método | Endpoint                                                   | Descrição                                  |
| ------ | ---------------------------------------------------------- | ------------------------------------------ |
| GET    | `/api/relatorios/personalizado/configuracao`               | Retorna campos e limites autorizados       |
| POST   | `/api/relatorios/personalizado/executar`                    | Gera uma página do relatório personalizado |
| POST   | `/api/relatorios/personalizado/exportar?formato=xlsx`       | Exporta o relatório personalizado completo |
| POST   | `/api/relatorios/sgu/listar`                                | Localiza API cadastrada no SGU             |
| POST   | `/api/relatorios/sgu/criar`                                 | Cria ou atualiza API no SGU                |
| DELETE | `/api/relatorios/sgu/{nome}`                                | Apaga API no SGU                           |
| POST   | `/api/relatorios/sgu/executar/{nome}`                       | Executa relatório do catálogo manual       |
| POST   | `/api/relatorios/sgu/exportar/{nome}?formato=xlsx`          | Exporta um relatório completo              |
| POST   | `/api/relatorios/sgu/exportar-lote`                         | Exporta grupo automático em arquivo ZIP    |

Contrato do relatório personalizado:

```json
{
  "colunas": ["COD_BENEFICIARIO", "PERIODO", "VALOR_TOTAL"],
  "filtros": {
    "competencia_inicio": "202601",
    "competencia_fim": "202601"
  },
  "pagina": 1,
  "tamanhoPagina": 50,
  "nomeArquivo": "relatorio_personalizado"
}
```

O endpoint de configuração não devolve SQL nem credenciais. Na execução, a
resposta do SGU é acrescida da lista `colunas` e o conteúdo é projetado conforme
a seleção validada. Na exportação, `pagina` e `tamanhoPagina` não limitam o
arquivo final, pois o backend materializa todas as páginas dentro dos limites
globais configurados.

Formatos de exportação:

```text
csv
txt
xlsx
```

### Fechamento

```text
POST /api/fechamento/converter
```

Endpoint planejado, ainda não implementado no backend atual.

---

## Deploy

## Frontend — Vercel

O ambiente de produção utiliza:

```typescript
apiUrl: "https://corretor-de-arquivos.onrender.com/api";
```

Ao mudar o endereço do backend, atualize:

```text
unimed-tools-frontend/src/environments/environment.prod.ts
```

Configuração típica:

```text
Root Directory: unimed-tools-frontend
Build Command: npm run build
```

Como o projeto é uma SPA Angular, configure fallback de rotas para `index.html` quando necessário.

## Backend — Render

O arquivo `render.yaml` configura:

- serviço web em Docker;
- pasta raiz `unimed-tools-backend`;
- Dockerfile `./Dockerfile`;
- porta `10000`;
- variáveis do SGU.

A aplicação deve escutar em:

```properties
server.port=${PORT:8080}
server.address=0.0.0.0
```

### CORS

O backend libera atualmente:

```text
http://localhost:4200
http://localhost:3000
https://corretor-de-arquivos.onrender.com
https://corretor-de-arquivos.vercel.app
https://*.vercel.app
```

Se o frontend for publicado em outro domínio, atualize:

```text
unimed-tools-backend/src/main/java/com/unimedlorena/tools/CorsConfig.java
```

### Limitação do SGU no Render

A implantação do backend pode ficar online, mas a Central de Relatórios continuará falhando caso o SGU/Kong não permita requisições originadas pelos IPs do Render.

Sintoma:

```text
HTTP 403 Forbidden
```

Essa limitação não afeta as páginas de BI e ANS, pois elas processam os arquivos no próprio backend.

---

## Limitações conhecidas

### Central de Relatórios no Render

O SGU/Kong bloqueia atualmente a origem do Render com `403 Forbidden`.

### BI e arquivos CSV

A interface aceita `.csv` para despesas, mas o backend atual abre o arquivo com `XSSFWorkbook`.

Use `.xlsx` até que o backend implemente um leitor específico para CSV.

### Fechamento

A interface existe, mas o endpoint correspondente ainda não foi implementado.

### Catálogo de relatórios

O catálogo é salvo apenas no `localStorage`. Não existe sincronização entre navegadores ou usuários.

Essa limitação vale para os modos manual e automático. O catálogo de campos do
relatório personalizado é definido no backend e não é persistido pelo navegador;
os valores digitados permanecem apenas no estado atual da página.

### Concorrência do relatório personalizado

A API `0090-relatorio-personalizado` é compartilhada e mutável. O processo
Spring Boot serializa publicação e execução com um lock local. Essa proteção não
coordena múltiplas instâncias do backend; uma implantação com mais de uma réplica
exigirá coordenação distribuída ou uma API independente por execução.

### XML

A correção utiliza padrões relacionados às tags `ans:`. XMLs com outro namespace podem exigir adaptação.

### Autenticação da aplicação

A aplicação não possui, no código atual, um sistema próprio de login e controle de usuários.

Como ela foi criada para uso interno, recomenda-se protegê-la por rede corporativa, proxy autenticado, VPN ou outro controle de acesso antes de disponibilizá-la publicamente.

### Tratamento de erros

O backend utiliza um handler global que pode transformar exceções em respostas HTTP 500. Consulte os logs do backend para encontrar a causa original.

---

## Solução de problemas

### O frontend abre, mas o backend não responde

Confirme:

```text
http://localhost:8080/health
```

Resposta:

```text
ok
```

Em desenvolvimento, inicie o frontend com:

```bash
npm start
```

e não apenas com `ng serve`.

Na publicação LAN com XAMPP, não execute `npm start`. Confirme que o Apache está
ativo e que o proxy alcança o backend por meio de:

```text
http://IP_DA_MAQUINA/api/relatorios/personalizado/configuracao
```

### Erro `status 0` ou `Unknown Error`

Normalmente está relacionado a:

- CORS;
- URL incorreta do backend;
- backend offline;
- preflight `OPTIONS` bloqueado;
- problema de certificado ou rede.

Abra o navegador em:

```text
F12 → Network
```

e examine a requisição.

### Erro `403 Forbidden` nos relatórios

Se a chamada direta ao SGU funciona, mas a chamada pelo Render falha, o bloqueio é externo.

Verifique com a equipe do SGU/Kong:

- allowlist de IP;
- ACL do consumidor;
- WAF;
- restrição por ambiente;
- rota liberada para a API key;
- IP de saída da hospedagem.

### Erro `URI is not absolute`

Confira:

```text
SGU_API_BASE_URL=https://api.lorena.sgusuite.com.br
```

No painel da hospedagem, a chave e o valor devem ser cadastrados em campos separados.

### Erro `401`

Um `401` devolvido pelo SGU confirma que o backend alcançou o serviço externo,
mas a autenticação foi rejeitada.

Verifique:

- valor de `SGU_API_KEY`;
- espaços no início ou no fim;
- aspas;
- ambiente correto;
- chave revogada;
- headers exigidos pelo ambiente em `SGU_API_KEY_HEADERS`.

Nunca resolva o erro gravando a chave em `application.properties`; defina-a no
ambiente do processo e reinicie o backend.

### Planilha de BI não é processada

Verifique:

- formato XLSX;
- existência da coluna de especialidade;
- existência da coluna do solicitante;
- colunas `PES_NOM_COMP` e `ESPMD_DES` na referência;
- planilha sem senha ou proteção incompatível;
- tamanho inferior ao limite configurado.

### ANS não remove linhas

Verifique:

- codificação ISO-8859-1;
- posições fixas do TXT;
- nomes das abas;
- texto das mensagens de erro;
- quantidade de caracteres de CNPJ e CNES;
- estatísticas exibidas na tela.

### XML não encontra ocorrências

Verifique:

- existência do prefixo `ans:`;
- código de tabela `00`;
- prefixo do procedimento;
- valor de `valorTotal`;
- estrutura exata das tags;
- codificação UTF-8.

---

## Segurança

As regras completas e obrigatórias para desenvolvimento seguro, uso de IA, autenticação, variáveis de ambiente, LGPD e referências internacionais estão em [`SEGURANCA.md`](SEGURANCA.md).

- Não envie a API key do SGU para o Angular.
- O frontend deve chamar somente o backend.
- O backend adiciona a chave ao acessar o SGU.
- Não grave chaves em arquivos versionados.
- Rotacione imediatamente qualquer credencial exposta.
- Não registre a chave completa nos logs.
- Não envie arquivos reais de beneficiários para repositórios públicos.
- Revise os logs antes de compartilhá-los.
- Proteja a aplicação com autenticação ou acesso de rede antes do uso corporativo em produção.

---

## Desenvolvimento

### Documentação e padrões

Antes de modificar o projeto, consulte:

- [`AGENTS.md`](AGENTS.md): ordem de leitura, limites arquiteturais, segurança e critérios para alterações realizadas com auxílio de IA;
- [`SEGURANCA.md`](SEGURANCA.md): controles de segurança, privacidade, LGPD, autenticação, segredos, APIs, SaaS e cadeia de entrega;
- [`PADRAO_DE_COMMITS.md`](PADRAO_DE_COMMITS.md): padrão Conventional Commits adotado no histórico;
- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md): responsabilidades do frontend, backend e integrações;
- este `README.md`: instalação, módulos, contratos, deploy e diagnóstico.

Comentários no código devem explicar regras de negócio, decisões de compatibilidade e limitações externas. Evite comentários que apenas traduzam uma instrução óbvia para português.

### Verificações antes de enviar alterações

Frontend:

```bash
cd unimed-tools-frontend
npm ci
npm test -- --watch=false
npm run build
```

Backend:

```bash
cd unimed-tools-backend
mvn test
mvn clean package
```

Também confirme que `git status` não contém `node_modules/`, `dist/`, `target/`, arquivos `.env`, credenciais, planilhas reais ou configurações locais de editor.

### Adicionar uma nova página

1. Crie o componente dentro de:

```text
unimed-tools-frontend/src/app/pages/
```

2. Adicione a rota em:

```text
unimed-tools-frontend/src/app/app.routes.ts
```

3. Adicione o item na página inicial:

```text
unimed-tools-frontend/src/app/pages/home/home.component.ts
```

4. Adicione o item no menu lateral:

```text
unimed-tools-frontend/src/app/layout/sidebar/sidebar.component.ts
```

5. Quando houver processamento no servidor, crie:

```text
controller
service
dto
```

no backend.

6. Documente o endpoint e as variáveis necessárias.

### Recomendações para futuras melhorias

- implementar autenticação e autorização;
- concluir o backend de Fechamento;
- implementar suporte real a CSV no módulo de BI;
- persistir o catálogo de relatórios no servidor;
- criar testes automatizados para cada serviço;
- padronizar respostas de erro e códigos HTTP;
- incluir auditoria das execuções;
- adicionar limites por tipo de arquivo;
- criar histórico de downloads;
- liberar ou substituir a infraestrutura usada na integração SGU.

---

## Uso interno

Este projeto foi desenvolvido para apoiar rotinas internas da Unimed Lorena.

Antes de usar arquivos em produção:

1. mantenha uma cópia do arquivo original;
2. valide o resultado gerado;
3. confira totais e registros;
4. teste com uma amostra representativa;
5. respeite as políticas internas de segurança e proteção de dados.
