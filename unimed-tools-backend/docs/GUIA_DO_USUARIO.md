# Guia do usuário — Unimed Tools

## 1. Acesso e navegação

O acesso usa login e senha. A sessão é mantida em cookie protegido e cada pessoa
visualiza somente as áreas concedidas pelo administrador. A Home reúne os cards
disponíveis; o menu **Ferramentas** usa o mesmo catálogo.

Cada página operacional possui o botão **Ajuda**, que abre um guia com
pré-requisitos, passo a passo, resultado esperado e cuidados específicos sem
sair da tarefa atual.

Se uma ferramenta não aparecer, confirme com um administrador se a conta possui
a permissão correspondente. Ocultar o card não substitui a autorização do
servidor.

## 2. Comercial — Atual

Finalidade: gerar beneficiários, receita, despesas e faixa etária por empresa.

1. Busque e selecione uma ou várias empresas.
2. Confira a competência sugerida e a data de referência da faixa etária.
3. Para faixa etária, escolha **Todos os códigos**, **Somente códigos ativos** ou
   **Somente códigos inativos**. A opção inicial é Todos.
4. Marque os relatórios necessários.
5. Gere as prévias e confira erros ou resultados vazios.
6. Escolha CSV, TXT ou XLSX.
7. Baixe um relatório ou o pacote completo.

Beneficiários, receita e despesas geram arquivos separados por empresa. Faixa
etária gera um único consolidado de todas as empresas selecionadas. Alterar
empresa, competência, data, situação dos códigos ou seleção invalida as prévias
anteriores. Códigos de carteirinha com quatro posições iniciados por `5` ou `9`
são inativos; os demais são ativos. Essa escolha não altera beneficiários,
receita nem despesas.

### Relatório final (XLSX) — Atual

Selecione uma empresa e os quatro relatórios do Comercial, informe a competência
e envie o XLSX do relatório final anterior. Use **Gerar relatório final (XLSX)**
para consolidar a competência atual com os onze meses anteriores do arquivo enviado.

A receita inclui mensalidades e o acréscimo `ACRÉS. RETROAT.REF 06/26`, inclusive
quando a origem apresenta `ACR�S.` e espaços ao final. A coparticipação continua
separada nas colunas próprias e somada nos indicadores que já a consideravam.

A aba gerada apresenta valores monetários e percentuais arredondados, sem casas
decimais (por exemplo, 725,98 aparece como 726). Os cálculos mantêm a precisão
original. Essa aba fica sem gráficos, inclusive quando já existe no modelo.
As abas históricas do arquivo anterior são preservadas.

A correção de grupos reconhece Vitallis como Sessões Multi e Clínica Vale
Histórico como Clínica Médica quando o grupo original é médico não cooperado.
Os relatórios individuais e o ZIP continuam disponíveis com seus formatos próprios.

## 3. Assistencial — Atual

Finalidade: montar relatórios personalizados com colunas e filtros autorizados.

1. Escolha as colunas e arraste-as para a ordem desejada.
2. Adicione os filtros e preencha seus valores; competência é obrigatória.
3. Se necessário, remova duplicados, separe métricas por mês ou aplique ranking.
4. Gere a prévia e confira paginação, valores e ordenação.
5. Exporte CSV, TXT ou XLSX.

Modelos salvos guardam colunas, tipos de filtro e opções estruturais. Valores
digitados nos filtros não são armazenados. Campos de beneficiário podem ser
protegidos visualmente na prévia.

## 4. Revisão de Contas — Atual

Finalidade: corrigir XMLs TISS em lote no próprio navegador.

1. Escolha todas as correções, apenas prefixos ou apenas blocos vazios/zerados.
2. Arraste ou selecione os arquivos XML.
3. Execute a análise.
4. Abra os detalhes e confira as alterações por arquivo.
5. Escolha a regra de nomes e baixe uma nova cópia ou o ZIP.

Os arquivos originais nunca são sobrescritos. As regras dependem das estruturas
e tags `ans:` esperadas pelo TISS; revise o resumo antes de encaminhar a saída.

## 5. Única — Atual

Finalidade: filtrar um TXT posicional da rede ANS a partir de apontamentos.

1. Escolha a origem dos filtros: planilha XLSX ou CSVs por categoria.
2. Adicione os arquivos de filtro aplicáveis.
3. Adicione o TXT posicional.
4. Processe e acompanhe linhas lidas, removidas e mantidas.
5. Baixe a nova cópia filtrada.

O TXT usa codificação ISO-8859-1. CNPJ, CNES e outros campos são lidos em
posições fixas; não altere manualmente o layout antes do processamento.

## 6. Hospital — Atual

Finalidade: consultar autorizações ainda não convertidas em guia.

1. Preencha somente os filtros necessários.
2. Clique em **Gerar relatório**.
3. Navegue pelas páginas e confira o total.
4. Escolha XLSX, CSV ou TXT e baixe o resultado completo.

O filtro de prestador aceita parte do nome. O download é habilitado após uma
prévia com registros.

## 7. Gestão de Risco — Atual

Finalidade: executar os relatórios de colonoscopia, consultas, mamografia,
ressonância e sangue oculto.

1. Confira a competência e preencha filtros adicionais exibidos.
2. Marque os relatórios necessários.
3. Gere e confira as prévias.
4. Baixe individualmente ou gere um ZIP.

Cada relatório usa uma API própria e pode terminar com sucesso, vazio ou erro
independentemente. O pacote informa falhas sem apresentá-las como sucesso.

## 8. Ferramentas configuradas pela TI — Atual

Essas páginas usam uma API já cadastrada. Preencha os filtros marcados com
asterisco, gere a prévia, navegue pelas páginas e exporte no formato desejado.
Nome, descrição, filtros visíveis e colunas preferenciais são definidos pela TI.

## 9. Área TI — Atual

A área é restrita a administradores e possui três seções:

- **Relatórios e APIs:** consulta, importação, teste e manutenção de definições;
- **Grupos de relatórios:** organização e execução em lote;
- **Páginas e ferramentas:** apresentação de páginas nativas e publicação de
  ferramentas simples baseadas em APIs existentes.

Ferramentas configuráveis são globais e persistidas no MariaDB. Catálogos,
templates e grupos da Central antiga continuam locais quando indicado. Nunca
insira chaves, senhas ou dados pessoais em nomes, descrições, SQL ou logs.

## 10. Administração de usuários — Atual

Administradores autorizados podem cadastrar contas, editar dados, conceder
permissões, redefinir senha e desativar usuários.

- usuários operacionais começam sem acesso a módulos;
- permissões são concedidas por ferramenta;
- o reset gera senha temporária válida por 24 horas, exige troca no próximo
  acesso e revoga as sessões existentes;
- alterações administrativas são auditadas.

## 11. Especialidade Médica — Parcial

A rotina cruza despesas e uma base de médicos, preservando especialidades já
preenchidas. Use XLSX para as duas entradas. Embora a interface aceite selecionar
CSV para despesas, o backend atual usa `XSSFWorkbook`; CSV não possui suporte
funcional completo.

## 12. Fechamento de Produção — Pendente

A tela demonstra o fluxo planejado, mas o endpoint `/api/fechamento/converter`
não existe no backend. Não use arquivos reais nem considere a conversão
disponível. A implementação depende de especificação de entrada, saída, regras e
critérios de aceite.

## 13. Problemas comuns

| Sintoma | Verificação |
| --- | --- |
| Ferramenta não aparece | Confirme a permissão da conta e se o card está visível |
| Sessão expirou | Entre novamente; sessões possuem limites de inatividade e duração |
| Erro 403 no relatório | Confirme permissão; o SGU/Kong também pode bloquear origem/IP |
| Prévia vazia | Revise filtros e período; vazio não significa necessariamente falha |
| Download não habilita | Gere novamente a prévia depois da última alteração |
| Ferramenta configurável indisponível | Confirme com a TI se card e API continuam ativos |

Não tente contornar bloqueios externos. Registre a mensagem apresentada, a
ferramenta, o horário e os filtros usados sem copiar dados pessoais para o chamado.

## 14. Dados e arquivos

- use somente dados necessários para a finalidade da rotina;
- não envie arquivos corporativos por canais não aprovados;
- não coloque dados reais em evidências de teste ou documentação;
- preserve os arquivos originais e valide a saída antes de utilizá-la;
- nunca compartilhe senha, cookie, token ou chave de API.
