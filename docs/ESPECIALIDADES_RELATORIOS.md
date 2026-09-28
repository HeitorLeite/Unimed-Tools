# Especialidades nos relatórios — Atual

## Regra central

`EspecialidadeRelatorioResolver` atua no backend quando a resposta possui
`NOME_ESPECIALIDADE`. A especialidade pertence à guia inteira: o valor resolvido
é aplicado a procedimentos, exames, medicamentos, materiais, diárias, taxas e
demais itens sem remover linhas nem deduplicar registros.

A chave mínima é composta por `COD_BENEFICIARIO + NUMERO_GUIA + DATA_GUIA`.
Aliases equivalentes de código do beneficiário e número da guia são aceitos sem
diferenciar maiúsculas, minúsculas ou pontuação do cabeçalho. Se a origem omitir
qualquer parte da chave, cada linha recebe uma identidade isolada; registros de
guias possivelmente diferentes nunca são misturados.

## Resolução

A resolução principal já existente continua aplicada sobre todas as linhas da guia:

1. nomes de especialidade aprovados;
2. descrições de alta confiança;
3. `CLINICO`.

Nomes são comparados sem diferença de caixa, acentos, espaços repetidos e
pontuação. O conteúdo original de `DESCRICAO_ITEM` e `CID` não é modificado.
Especialidades específicas vencem `CLINICO` e `CLINICA MEDICA` dentro da mesma
guia. Entre especialidades específicas, a primeira evidência de nome na ordem
estável do relatório prevalece.

As descrições usam somente os termos explícitos de alta confiança. Termos
genéricos, como hemograma, medicamentos, materiais e diárias, não classificam a
guia. As regras preservam separadamente GINECOLOGIA/OBSTETRICIA,
NEUROLOGIA/NEUROCIRURGIA, NEUROLOGIA PEDIATRICA e
PEDIATRIA/CIRURGIA PEDIATRICA.

Somente quando essa resolução termina exatamente em `CLINICO`, uma segunda
camada tenta, nesta ordem:

1. CID aprovado;
2. termos residuais da descrição, em prioridade explícita;
3. `CLINICO`.

O catálogo CID aprovado contém apenas `K804`, `K573`, `K40`, `N390`, `K801`,
`S829`, `O809` e `S729`. Pontos, espaços e diferença de caixa são ignorados na
comparação, sem modificar o CID original. Códigos não cadastrados nunca são
inferidos por capítulo; o backend registra somente o código normalizado como não
mapeado para revisão e ainda permite que a descrição resolva a guia.

As regras residuais ficam em uma lista central e ordenada. Evidências específicas
como obstetrícia, cardiologia, oftalmologia, urologia, ortopedia, mastologia e
anatomia patológica são avaliadas antes de radiologia genérica. Indicadores curtos
como `TC`, `RM`, `RX` e `US` exigem limites de palavra, evitando coincidências em
outras palavras. `CIRURGICA` isolado, materiais cirúrgicos, medicamentos e exames
laboratoriais genéricos não classificam a guia.

## Prévia, exportação e desempenho

Relatórios comuns sem a coluna continuam paginados e gravados em fluxo. Quando a
coluna existe, o backend reúne as páginas do SGU, resolve as guias em
tempo aproximadamente O(n) com mapas e só então recorta a página da prévia ou
gera CSV, TXT e XLSX. Isso garante os mesmos valores na prévia e no download,
inclusive quando uma guia atravessa páginas externas.

No Assistencial, os campos necessários à resolução são acrescentados à consulta
como suporte e retirados do resultado quando o usuário não os selecionou.
Relatórios cadastrados manualmente precisam retornar a chave mínima para permitir
propagação entre itens; sem ela, a normalização segura permanece por linha.
