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
  e por relatório. Códigos do catálogo pertencentes à mesma empresa permanecem
  reunidos no arquivo dessa empresa.
- Durante a preparação, TI/manual e Assistencial mostram atividade e tempo;
  porcentagem de transferência depende dos bytes recebidos, não de estimativa
  de conclusão da consulta.
- ZIP preserva o contrato de sucesso parcial, com manifesto e contadores.
  Arquivos vazios/com falha não entram como relatórios válidos. Comercial e Gestão
  de Risco também avisam quando o pacote contém falhas. Causas internas não são
  copiadas para o manifesto.
- Hospital exporta pela API do ambiente configurado, inclusive o sufixo `-dev`.

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
