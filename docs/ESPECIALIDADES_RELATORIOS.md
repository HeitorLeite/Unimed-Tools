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

A prioridade é aplicada sobre todas as linhas da guia:

1. nomes de especialidade aprovados;
2. descrições de alta confiança;
3. catálogo de CID seguro;
4. `CLINICO`.

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

O projeto não possuía um mapa CID → especialidade e o requisito não forneceu
associações aprovadas. Por segurança, o catálogo de CID começa vazio e fica
centralizado no resolvedor para futuras inclusões validadas. Nenhuma associação
por capítulo ou CID ambíguo é inventada; sem evidência anterior, aplica-se
`CLINICO`.

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
