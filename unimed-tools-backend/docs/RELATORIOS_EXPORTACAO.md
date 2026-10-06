# Exportação de relatórios — Atual

## Integridade da entrega

CSV, TXT e XLSX individuais da Central/Comercial/Gestão de Risco/ferramentas
configuráveis são gerados em arquivo temporário, página a página. Só após sucesso
o controller define Content-Type, Content-Disposition, Content-Length e
X-Total-Registros e transfere o arquivo. O temporário é removido em `finally`,
inclusive em falha do SGU ou desconexão. Temporários do POI também são descartados
quando há exceção. Não é necessário guardar todas as páginas em memória nesse fluxo.

Falha de consulta, ausência de ordenação, resposta malformada, contagem incoerente
ou limite atingido não resultam em um CSV com apenas BOM nem em um arquivo parcial
com status de sucesso. Exportação sem registros devolve 422 com mensagem pública.
Prévia vazia continua permitida. Assistencial e Hospital já geram antes de responder.

O navegador só salva a resposta final; rejeita conteúdo vazio, HTML/JSON no lugar
de relatório e assinatura incompatível com XLSX/ZIP. Erros JSON recebidos como Blob
são decodificados em todos os caminhos, incluindo Assistencial, Hospital e lote.
O link fica anexado ao documento e sua URL só é revogada após o navegador poder
iniciar a transferência. Isso não permite afirmar que o usuário salvou o arquivo
em disco: o navegador ainda controla o diálogo e o destino.

O contexto da sessão opaca fica em atributo da requisição durante o despacho
assíncrono. Não são liberadas rotas/dispatches anônimos nem alterados CSRF,
permissões ou duração da sessão. Testes cobrem sucesso assíncrono e negação
sem autenticação, sem permissão e sem CSRF.

## Ordenação e paginação

Ordenar só pela primeira coluna não resolve empates. Assistencial e Hospital
incluem todas as colunas visíveis como desempates, respeitando a prioridade e
direção escolhidas pelo usuário. Importações continuam inferindo os aliases da
projeção quando isso é seguro; ordenações manuais curtas são preservadas.

O SGU rejeitou uma ordenação de 152 caracteres com ORA-06502 em teste sintético.
Para listas extensas de aliases, `OrdenacaoRelatorio` mantém os critérios completos
em `ROW_NUMBER() OVER (ORDER BY ...)`, envolvendo a consulta original sem mudar
seus filtros, agregações ou DISTINCT. O campo `ordenacao` recebe apenas
`UT_EXPORT_ORD`. Essa coluna técnica é retirada da resposta de execução e dos
arquivos. Expressões extensas fora da forma suportada são rejeitadas explicitamente.
Não se corta a lista de critérios para caber no SGU.

Referência da semântica SQL: [Oracle — SELECT e ORDER BY](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/SELECT.html).

O leitor reconhece o contrato real do SGU: `totalPage` e `numberOfElements`
(total geral), inclusive quando são strings. Confere a sequência RNUM e a
quantidade final; aceita também `last` e `totalElements`. `last=false` prevalece
sobre o tamanho aparente da página. Uma última página cheia não exige consulta
extra quando os metadados já confirmam o término.

Linhas iguais permanecem iguais e não são deduplicadas. Somente no fallback sem
metadados/RNUM o leitor interrompe páginas integralmente repetidas para evitar
loop infinito. APIs sem metadados com páginas legítimas inteiramente iguais
precisam corrigir seu contrato de paginação. Mudanças de colunas durante escrita
paginada são rejeitadas, em vez de descartar campos silenciosamente.

## Conteúdo e experiência

- CSV/TXT: UTF-8 com BOM, ponto e vírgula, aspas conforme necessário e CRLF.
- XLSX: códigos preservados como texto, datas e números tipados; fórmulas externas
  nunca são criadas a partir dos valores recebidos. A linha de títulos não recebe
  autofiltro nem tabela automática: contém somente os nomes das colunas.
- Decimal SGU `1.005` significa 1,005, não 1005. Decimais em páginas posteriores
  não recebem indevidamente a máscara de inteiro da primeira página.
- O Assistencial preserva a ordem de colunas ajustada na prévia também na
  exportação simples, além da análise avançada.
- Comercial, Hospital, Gestão de Risco e ferramentas configuráveis oferecem os
  três formatos, além das telas de TI e Assistencial que já os ofereciam.
- Quando o Comercial recebe várias empresas, o ZIP contém um arquivo por empresa
  e por relatório, exceto faixa etária, que contém um único consolidado das
  empresas selecionadas. Códigos do catálogo pertencentes à mesma empresa
  permanecem reunidos no arquivo dessa empresa nos demais relatórios.
- Nas APIs do Comercial, `GrupoPrestadorComercialNormalizer` corrige somente
  registros cujo `GRUPO_PRESTADOR` corresponde a médico(a)(s) não cooperado(a)(s).
  A prioridade é OPME, recurso próprio, sessões multi, clínica de imagem, clínica
  médica e reembolso. O tratamento ocorre por página antes da prévia e da escrita
  de CSV/TXT/XLSX; `NOME_PRESTADOR`, `TIPO_PRESTADOR` e a quantidade de linhas são
  preservados. Grupos já classificados e prestadores desconhecidos não mudam.
  **Atual:** `VITALLIS SERVICOS EM SAUDE LTDA` (incluindo o identificador legado
  `VTALLIS`) é classificado como `SESSOES MULTI`; `CLINICA VALE HISTORICO LTDA`
  é classificado como `CLINICA MEDICA`. Acentos, caixa e pontuação são normalizados.
- Durante a preparação, TI/manual e Assistencial mostram atividade e tempo;
  porcentagem de transferência depende dos bytes recebidos, não de estimativa
  de conclusão da consulta.
- ZIP preserva o contrato de sucesso parcial, com manifesto e contadores.
  Arquivos vazios/com falha não entram como relatórios válidos. Comercial e Gestão
  de Risco também avisam quando o pacote contém falhas. Causas internas não são
  copiadas para o manifesto.
- Hospital exporta pela API do ambiente configurado, inclusive o sufixo `-dev`.

## Faixa etária consolidada — Atual

A API `0090-faixa-etaria` mantém o SQL por contrato no SGU. O backend consome
todas as páginas antes de devolver a prévia ou o arquivo. Somente linhas das dez
faixas conhecidas alimentam as somas; cabeçalhos CONTRATO e TOTAL GERAL da origem
são descartados para não contar os mesmos beneficiários duas vezes.

### Situação dos códigos de carteirinha — Atual

A tela oferece **Todos os códigos** (padrão), **Somente códigos ativos** e
**Somente códigos inativos**. A escolha afeta somente `0090-faixa-etaria`.
O catálogo guarda `BNF_COD_CNTRAT_CART` sem zeros decorativos à esquerda. Para
classificar a situação, o código é considerado com quatro posições: os iniciados
por `5` ou `9` são inativos; os demais são ativos.

| Empresa | Ativos | Inativos |
| --- | --- | --- |
| Yakult | 2128 | 9128 |
| Grupo Canção Nova | 2152, 2154, 2156, 2158, 2160, 2162 | 9152 |
| Órica | 422 | 5002 |
| Saint Gobain | 99 | — |
| Grupo Biondi | 2173, 2174, 2175, 2176, 2177, 2178, 2179, 2180 | 9173 |
| Biemme | 45 | 5045 |
| Liceu | 364, 365, 366, 367, 369, 2190 | 5364, 5365 |
| Apolo | 156, 2260 | 5156 |
| Comunidade Canção Nova | 2191 | — |
| Grupo EPPO | 2416, 2417 | — |
| AKG | 2401 | — |
| Instituto Santa Teresa | 92 | 5092 |
| AECI Latam | 2022, 2303 | — |
| ICE do Brasil | 2142 | — |
| Serra Dourada | 2311, 2314 | — |

O frontend envia cada código em uma combinação separada. Isso é obrigatório
porque o SGU interpola filtros `NUMBER` no bloco PL/SQL; uma lista com vírgulas
causaria `PLS-00103`. A opção Todos cria combinações para todos os códigos das
empresas selecionadas. Se não existir código da situação, é enviado `0`.

A saída segue a aba GERAL do modelo informado pelo Comercial:

| Coluna | Cálculo por faixa |
| --- | --- |
| DEP | DEP_MASC + DEP_FEM |
| TIT | TIT_MASC + TIT_FEM |
| FEM | DEP_FEM + TIT_FEM |
| MASC | DEP_MASC + TIT_MASC |
| TOTAL | DEP + TIT, sem agregados |

O cabeçalho é `FAIXA_ETARIA;DEP;TIT;FEM;MASC;TOTAL`, seguido diretamente pelas dez
faixas em ordem crescente e TOTAL GERAL, sem linha de identificação GERAL vazia. XLSX usa uma única aba
Geral, com contagens numéricas. CSV e TXT mantêm o contrato de codificação existente.
Faixas ausentes são preenchidas com zero quando há dados válidos em outras faixas.
Resposta completamente vazia permite prévia vazia e impede exportação com 422.
Contagem ausente, negativa, fracionária ou inválida e faixa desconhecida impedem
a consolidação com 502, em vez de produzir totais incompletos.

Os endpoints existentes continuam protegidos por sessão, permissão e CSRF:

- `POST /api/relatorios/sgu/executar/0090-faixa-etaria`: aceita os filtros simples
  anteriores ou `{ "combinacoesFiltros": [{...}], "page": 1, "size": 20 }`.
  A paginação entregue refere-se à tabela consolidada; cada consulta da origem
  sempre percorre suas páginas com os limites e retries existentes.
- `POST /api/relatorios/sgu/exportar/0090-faixa-etaria`: o campo `filtros` pode
  conter `{ "combinacoesFiltros": [{...}] }`. Retorna um único arquivo no formato
  solicitado, sem ZIP. Os headers e o temporário seguem o exportador existente.
- `POST /api/relatorios/sgu/exportar-lote`: o Comercial envia um único item para
  faixa etária. Suas combinações são acumuladas antes da escrita. Se uma delas
  falhar, o consolidado inteiro fica fora do ZIP e a falha entra no manifesto;
  os demais relatórios mantêm o contrato de sucesso parcial.

O envelope de combinações é interpretado somente para a API de faixa etária e
nunca é enviado como filtro ao SGU. A seleção de várias empresas usa códigos
únicos, unidos em uma lista quando o filtro aceita VARCHAR ou divididos em
combinações quando exige NUMBER. As contagens são acumuladas em memória fixa
de dez faixas, sem reunir todas as linhas de origem.

`codigoscarteirinha` deve permanecer como filtro obrigatório `NUMBER` no SGU. O
SQL de `docs/sql/0090-faixa-etaria-corrigida.sql` compara diretamente um código
por execução com `b.bnf_cod_cntrat_cart`. Depois de atualizar frontend ou SQL,
homologar as três opções no navegador autenticado. Testes locais usam somente
dados sintéticos.

## Desempenho e limites

O tamanho padrão passa a 5.000 registros por página, configurável por
`SGU_EXPORT_PAGE_SIZE`. O limite de páginas configurado e o retry restrito a uma
consulta que falhou temporariamente continuam preservados. Pode-se reduzir o
tamanho por configuração quando uma consulta pesada exigir.

Em 24/09/2026, a API de despesas com competência 202608 e empresas informadas no
caso de validação retornou 3.631 registros e 54 colunas. Consulta mais geração dos
três formatos levou aproximadamente 99,5s com páginas de 1.000, contra 36,8s com
páginas de 5.000. Os CSVs tiveram o mesmo SHA-256. É uma medição desse caso e dessas
condições; não é garantia de ganho em todas as APIs.

O arquivo correto de referência contém 342 repetições legítimas. O arquivo antigo
incorreto tinha a mesma contagem total, mas 1.257 ocorrências faltantes e 1.257
excedentes. Comparação usa multiconjunto, sem eliminar repetições.

O relatório consultado após a correção preservou todos os registros de referência,
consideradas duas diferenças explicadas: o exportador antigo substituiu caracteres
acentuados por espaços; uma idade aumentou em um ano e o SQL usa SYSDATE no cálculo.
Não se alteraram os valores atuais para imitar essas diferenças históricas.
CSV e XLSX foram reabertos e comparados nas 54 colunas, sem divergências; TXT é
idêntico ao CSV. Dados reais não fazem parte dos testes ou do repositório.

## Pendente / limites de validação

### Relatório final do Comercial — leitura do histórico (Atual)

Na busca por títulos e tabelas do XLSX anterior, fórmulas com resultado em erro
(por exemplo, sinistralidade de uma região sem receita) não são lidas como
números. Isso permite carregar o modelo legado sem falhar ao procurar rankings.
Erros em células numéricas efetivamente usadas como receita, sinistro ou outro
valor histórico continuam impedindo a geração, com identificação da aba e célula;
não são substituídos silenciosamente por zero.

A coluna `CODIGO_CARTAO` da receita é reconhecida como identificador do
beneficiário. Vidas por região são contadas por identificador único nas
mensalidades, incluindo mensalidades repetidas sem multiplicar a quantidade de
vidas. A soma financeira mantém todas as linhas legítimas.

**Atual:** a população de beneficiários do resumo é reconstruída no último dia
da competência: cadastro futuro e exclusão na data de referência ou antes dela
ficam fora. O campo ATIVO atual só serve de fallback quando não há datas de
vigência, pois pode refletir mudanças posteriores ao período. Depois da seleção,
a carteirinha de quatro posições classifica iniciados por 5/9 como inativos e os
demais como ativos, com deduplicação por identificador. Rankings mantêm a situação
pelo código, inclusive para beneficiários que tiveram custos e não estão mais
vigentes. A coluna de vidas no mês atual contém ativos + inativos; as contagens
históricas permanecem como fornecidas pelo modelo anterior, sem reconstituir
populações históricas não disponíveis. Isso deve ser considerado ao interpretar
variações de vidas em modelos que usavam outro critério no histórico.

**Atual:** despesas regionais usam REGIAO_PREST/REGIAO_PRESTADOR; na ausência,
usam a região do beneficiário. Prestadores Central Nacional e Módulo Coração são
separados; o rateio da Central usa a região do beneficiário. Conforme o modelo,
Central também aparece na região do prestador, compensada no residual Local.
Receita e vidas nas
mensalidades continuam por região do beneficiário. O cabeçalho regional é
identificado por Região na coluna A, sem confundir Sinistro por Região com outra
tabela; Sudeste (Fora Vale) não é classificado como Vale do Paraíba.

O consolidado regional inclui os custos locais no Vale e os rateios da Central e
Módulo. Home Care permanece discriminado, mas não é somado novamente ao custo já
incluído na despesa. Local e o rateio local da Central são residuais em relação
aos totais históricos, evitando propagar diferenças de arredondamento do modelo.
Receita nos indicadores é mensalidade + acréscimo retroativo de junho/2026 + coparticipação classificada;
a tabela inicial continua discriminando as duas parcelas. Linhas sem tipo não são
reclassificadas por suposição. Centavos das fontes são preservados.

**Atual:** a receita da competência inclui descrições com MENSALIDADE e
`ACRÉS. RETROAT.REF 06/26`, aceitando também `ACR�S. RETROAT.REF 06/26`
e espaços ao final. O acréscimo entra nos totais e na receita por região, sem
acrescentar vidas. A coparticipação permanece discriminada e compõe os
indicadores que já a somavam. Valores históricos continuam vindo do XLSX anterior.

**Atual:** valores monetários, totais e percentuais da competência gerada são exibidos
arredondados, sem casas decimais. Os cálculos mantêm a precisão original; o
arredondamento ocorre na apresentação no Excel.

**Atual:** fórmulas da competência gerada são calculadas antes da gravação.
A aplicação não cria gráficos e remove os gráficos da aba dessa competência
quando ela já existe no modelo. Imagens, outros desenhos e abas históricas são
preservados. As tabelas históricas de receita, tipo de guia e grupo de prestador
são localizadas pelos cabeçalhos, permitindo modelos com linhas diferentes.
Modelos sem tabelas regionais permitem gerar o mês atual; o acumulado regional
é indicado como indisponível. Rankings também aceitam os títulos
MAIORES CUSTOS BENEFICIARIOS/ESPECIALIDADES e as colunas Cód. Beneficiário e Sinistro.
A competência gerada é a aba ativa e abre no início, com a primeira linha congelada.

**Validação:** testes sintéticos cobrem vigência, classificação, duplicidade,
região de prestador, rateio, consolidação, coparticipação, as duas grafias do
acréscimo retroativo e remoção de gráficos, inclusive em duas gerações consecutivas.
Também verificam uma competência nova sem gráficos e a preservação dos gráficos
históricos. Em 06/10/2026, o build Maven e os 245 testes do backend passaram.

**Pendente:** publicar a versão atualizada do backend para disponibilizar essas
regras na produção. Alterar o código na pasta Aplicacao não atualiza o JAR já em execução.

**Pendente:** a consulta de faixa etária ao SGU permanece independente. Uma
conferência offline pode comparar as faixas do modelo com idade na referência,
sexo e sufixo da carteirinha dos CSVs, mas não comprova os filtros enviados nem a
resposta do SGU na instalação. Rankings acumulados a partir de tabelas truncadas
do histórico não comprovam os custos individuais completos dos doze meses.
Não são inseridos dados reais em testes ou documentação.

SGU não fornece um snapshot transacional compartilhado entre chamadas: a
ordenação estabiliza dados que não mudam durante a exportação. Alterações
simultâneas com a mesma contagem total ainda exigem suporte da origem para
garantir um instante único. Não há cache persistente de resultados sensíveis.

O caso real de despesas foi validado com o exportador Java e o SGU. Os caminhos
HTTP, segurança, interfaces e lotes são cobertos por testes sintéticos. Cada
relatório possui seus próprios filtros e regras; não houve homologação de dados
reais de todos os catálogos nem clique autenticado em navegador para cada tela.

A publicação de produção continua sendo uma ação separada do atalho de teste.
Atualizar arquivos da pasta Aplicacao não publica automaticamente no XAMPP.
