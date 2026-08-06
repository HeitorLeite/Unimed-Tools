# Instruções para IAs — Unimed Tools

Este arquivo define as regras que uma inteligência artificial ou agente de programação deve seguir antes de analisar, alterar, testar ou documentar o projeto **Unimed Tools**.

As instruções se aplicam a todo o repositório, salvo quando existir outro `AGENTS.md` mais específico dentro de um subdiretório.

## 1. Objetivo principal

A IA deve compreender o comportamento existente antes de modificar o projeto. Nenhuma alteração deve ser feita apenas por suposição, preferência técnica ou tentativa de “melhorar” algo fora do escopo solicitado.

Princípios obrigatórios:

- ler a documentação e o código relacionado antes de editar;
- preservar regras de negócio e contratos existentes;
- distinguir comportamento atual de melhoria proposta;
- realizar alterações pequenas, rastreáveis e verificáveis;
- não esconder limitações, erros ou decisões pendentes;
- atualizar a documentação quando o comportamento mudar;
- proteger credenciais, dados pessoais e arquivos corporativos.

## 2. Ordem obrigatória de leitura

Antes de alterar qualquer arquivo, a IA deve seguir esta ordem:

1. Ler este `AGENTS.md` por completo.
2. Ler o `README.md` da raiz.
3. Ler `SEGURANCA.md` por completo.
4. Ler `PADRAO_DE_COMMITS.md`, quando o arquivo estiver disponível.
5. Verificar a pasta `docs/` e ler os documentos relacionados à tarefa.
6. Identificar o módulo, a rota, o componente ou o endpoint afetado.
7. Ler todos os arquivos diretamente envolvidos no fluxo.
8. Procurar referências, chamadas e dependências desses arquivos no restante do projeto.
9. Ler testes, configurações e contratos relacionados.
10. Somente depois disso propor ou realizar alterações.

Não é suficiente ler apenas o arquivo mencionado no pedido. A IA deve acompanhar o fluxo completo da funcionalidade.

## 3. Fontes de informação e conflitos

Para compreender o estado atual, considere:

- o código e os testes como evidência do comportamento implementado;
- a documentação aprovada como fonte dos requisitos e das regras de negócio;
- o `README.md` como orientação operacional e visão geral;
- issues e solicitações do usuário como definição do objetivo da alteração.

Quando código, documentação e solicitação apresentarem informações diferentes:

1. não escolher silenciosamente uma interpretação;
2. identificar claramente o conflito;
3. verificar histórico, testes e contratos relacionados;
4. solicitar decisão quando a escolha alterar regra de negócio, formato, integração ou compatibilidade;
5. atualizar a documentação depois que a decisão for confirmada.

## 4. Estrutura principal do projeto

```text
corretor-de-arquivos/
├── unimed-tools-frontend/       # Frontend Angular
├── unimed-tools-backend/    # Backend Spring Boot
├── docs/                    # Documentação de engenharia de software
├── README.md
├── SEGURANCA.md
├── PADRAO_DE_COMMITS.md
├── AGENTS.md
└── render.yaml
```

### Frontend

Diretório principal:

```text
unimed-tools-frontend/
```

Tecnologias e características atuais:

- Angular 21;
- TypeScript 5.9;
- componentes standalone;
- carregamento de páginas por rotas;
- RxJS;
- SCSS;
- npm.

### Backend

Diretório principal:

```text
unimed-tools-backend/
```

Tecnologias e características atuais:

- Java 21;
- Spring Boot 3.3;
- Maven;
- Apache POI;
- Apache Commons CSV;
- integração HTTP com SGU Suite/Kong;
- processamento de arquivos de até 100 MB.

## 5. Contexto funcional que deve ser preservado

### XML TISS

- O fluxo principal de XML é processado no navegador pelo frontend.
- Existem serviços de XML em TypeScript e Java com responsabilidades semelhantes.
- A IA não deve migrar, unificar ou remover uma das implementações sem uma decisão arquitetural explícita.
- As regras dependem de estruturas e tags com prefixo `ans:`.
- Arquivos originais nunca devem ser sobrescritos.

### BI — Especialidade Médica

- O backend atual processa planilhas XLSX com `XSSFWorkbook`.
- A interface pode permitir a seleção de CSV, mas isso não significa que CSV esteja funcionalmente suportado.
- Não declarar suporte completo a CSV sem implementar leitor, testes, validações e documentação correspondentes.
- Linhas que já possuem especialidade devem continuar preservadas, salvo nova regra aprovada.

### ANS — Corretor de Rede

- O fluxo recebe uma fonte de filtros e um arquivo TXT posicional.
- O TXT utiliza codificação ISO-8859-1.
- CNPJ, CNES e outros campos dependem de posições fixas.
- Alterações de posição, preenchimento com zeros ou critérios de remoção são regras de negócio e exigem validação.
- A saída deve preservar a codificação esperada.

### Central de Relatórios

- A execução depende do backend e do SGU Suite/Kong.
- Catálogo, templates e grupos são armazenados no `localStorage` do navegador.
- Não migrar esses dados para banco ou servidor como parte de outra tarefa.
- O SGU/Kong pode responder `403 Forbidden` quando acessado pelo ambiente hospedado.
- A IA não deve contornar ACL, WAF, restrições de IP ou mecanismos de segurança.
- A chave do SGU deve existir somente no backend e nas variáveis de ambiente.
- Exportações podem utilizar CSV, TXT e XLSX e percorrem páginas do serviço externo.

### Fechamento de Produção

- A interface existe, mas o backend de conversão ainda não está implementado.
- O endpoint `/api/fechamento/converter` representa um contrato planejado.
- Não criar controller, serviço, DTO ou regra de conversão sem especificação funcional, exemplos de entrada e saída e critérios de aceite.
- Não apresentar a funcionalidade como concluída apenas porque a tela existe.

### Autenticação e persistência

- A aplicação não possui autenticação própria no estado atual.
- O backend não utiliza persistência JPA para os catálogos da aplicação.
- Autenticação, autorização, auditoria e persistência central são evoluções propostas, não comportamentos existentes.
- Não introduzir essas mudanças incidentalmente em outra tarefa.

## 6. Processo obrigatório antes de alterar o código

Antes de editar, a IA deve registrar mentalmente ou apresentar, quando solicitado:

1. qual é o objetivo da alteração;
2. qual comportamento atual foi confirmado;
3. quais arquivos participam do fluxo;
4. quais regras de negócio podem ser afetadas;
5. quais contratos de entrada e saída existem;
6. quais riscos de regressão foram identificados;
7. como a alteração será validada.

Também deve:

- pesquisar pelo nome de classes, métodos, rotas, endpoints, campos e chaves alterados;
- verificar se a mesma regra aparece no frontend e no backend;
- identificar consumidores externos e internos;
- conferir mudanças locais já existentes antes de editar;
- preservar alterações do usuário que não pertençam à tarefa;
- evitar refatorações paralelas que aumentem o escopo sem necessidade.

## 7. Regras para alterações

### Escopo

- Alterar apenas o necessário para atender ao pedido.
- Não reorganizar pastas, renomear APIs ou substituir tecnologias sem autorização.
- Não adicionar funcionalidades que não foram solicitadas.
- Não corrigir regras de negócio com base apenas em intuição.
- Preferir mudanças incrementais e reversíveis.

### Compatibilidade

- Preservar rotas, nomes de campos, formatos de arquivos e contratos HTTP sempre que possível.
- Mudanças incompatíveis devem ser identificadas explicitamente.
- Quando um contrato precisar mudar, atualizar frontend, backend, testes e documentação na mesma entrega.
- Não remover suporte existente sem confirmar os consumidores afetados.

### Dependências

- Não adicionar biblioteca quando a solução puder ser implementada adequadamente com as dependências atuais.
- Justificar toda nova dependência.
- Verificar licença, manutenção, compatibilidade e impacto no build.
- Não atualizar versões de dependências fora do escopo da tarefa.

## 8. Organização do código

### Frontend Angular

- Manter componentes de página dentro de `unimed-tools-frontend/src/app/pages/`.
- Manter componentes reutilizáveis, modelos e serviços nas áreas compartilhadas existentes.
- Evitar regra de negócio complexa em templates HTML.
- Separar comunicação HTTP e transformação reutilizável em serviços.
- Preservar o padrão de componentes standalone e carregamento por rota.
- Ao adicionar uma página, revisar rota, página inicial e menu lateral.
- Tratar estados de carregamento, sucesso, vazio e erro.
- Não acessar diretamente o SGU nem expor sua chave no frontend.
- Versionar cuidadosamente chaves do `localStorage` quando o formato dos dados mudar.

### Backend Spring Boot

- Controllers devem tratar o contrato HTTP e delegar processamento.
- Services devem concentrar regras de negócio e integração.
- DTOs devem tornar contratos de entrada e saída explícitos.
- Reutilizar o tratamento global de exceções quando aplicável.
- Validar arquivos, parâmetros multipart, formatos e limites antes do processamento.
- Não registrar conteúdo sensível ou credenciais nos logs.
- Utilizar variáveis de ambiente para configurações e segredos.
- Preservar limites de paginação, tamanho e memória nas exportações.
- Evitar carregar arquivos grandes desnecessariamente mais de uma vez na memória.

## 9. Padrão de comentários

Comentários devem explicar o motivo de uma decisão, uma regra de negócio ou uma limitação que não seja evidente pelo código.

Comentar quando houver:

- regra de negócio específica da Unimed, TISS, ANS ou SGU;
- posição fixa em arquivo;
- codificação obrigatória;
- comportamento externo não controlado pelo projeto;
- solução temporária com motivo e condição de remoção;
- decisão que pareça estranha, mas seja necessária por compatibilidade.

Evitar comentários que apenas repetem o código:

```java
// Incrementa o contador
contador++;
```

Preferir comentários que expliquem a razão:

```java
// O TXT da ANS usa CNES com sete posições; zeros à esquerda fazem parte da chave.
String cnesNormalizado = completarComZeros(cnes, 7);
```

Regras adicionais:

- escrever comentários e documentação em português;
- manter termos técnicos, nomes de APIs e identificadores conforme o código;
- remover ou atualizar comentários que deixarem de representar a implementação;
- não manter blocos grandes de código comentado;
- utilizar nomes claros antes de recorrer a comentários extensos.

## 10. Arquivos, dados e segurança

- Aplicar integralmente a política definida em `SEGURANCA.md`.
- Nunca sobrescrever o arquivo original enviado pelo usuário.
- Gerar saída com nome diferente e claramente identificável.
- Preservar codificação, delimitadores, posições e estrutura quando fizerem parte do contrato.
- Validar o resultado antes de apresentá-lo como concluído.
- Não adicionar arquivos reais de beneficiários, guias ou produção ao repositório.
- Não registrar nomes, documentos, carteirinhas, chaves ou conteúdo corporativo nos logs.
- Não criar exemplos com dados pessoais reais.
- Não salvar API keys, tokens, senhas ou URLs privadas com credenciais.
- Não expor `SGU_API_KEY` no Angular, no README, em testes ou no histórico do Git.
- Ao encontrar uma credencial exposta, interromper o uso e informar o responsável.

## 11. Testes e validações

Toda alteração deve executar as verificações relevantes disponíveis no projeto.

### Frontend

```bash
cd unimed-tools-frontend
npm test -- --watch=false
npm run build
```

Quando os testes não existirem ou não cobrirem o fluxo, realizar ao menos o build e descrever a validação manual necessária.

### Backend

```bash
cd unimed-tools-backend
mvn test
mvn clean package
```

Para alterações em endpoints, também verificar:

- método HTTP;
- rota;
- parâmetros e multipart;
- status de sucesso e erro;
- headers de resposta, incluindo `X-Stats` quando aplicável;
- nome, tipo e conteúdo do arquivo retornado.

### Validação por módulo

- XML: testar arquivo sem ocorrência, com uma ocorrência, com várias ocorrências e lote com nomes repetidos.
- BI: testar colunas válidas, colunas ausentes, linhas já preenchidas e solicitante sem correspondência.
- ANS: testar cada origem de filtro, codificação, posições fixas, linhas mantidas e removidas.
- Relatórios: testar filtros obrigatórios, paginação, resultado vazio, exportação e falha externa.
- Fechamento: não simular conclusão enquanto o backend permanecer ausente.

Se algum comando não puder ser executado, informar exatamente qual validação ficou pendente e por quê.

## 12. Atualização da documentação

A IA deve atualizar a documentação quando a alteração modificar:

- requisito funcional ou regra de negócio;
- fluxo de atividade ou caso de uso;
- classe, serviço, responsabilidade ou relacionamento importante;
- rota ou item de navegação;
- endpoint, parâmetro, DTO, header ou formato de resposta;
- formato, codificação ou estrutura de arquivo;
- variável de ambiente, procedimento de instalação ou deploy;
- limitação conhecida;
- status de uma funcionalidade.

Ao documentar, classificar claramente cada item como:

- **Atual:** implementado e confirmado no código;
- **Parcial:** existe apenas em parte do fluxo;
- **Proposto:** melhoria ainda não implementada;
- **Pendente:** depende de decisão ou validação.

Não atualizar diagramas ou requisitos para representar uma solução futura como se ela já existisse.

## 13. Git e commits

- Seguir `PADRAO_DE_COMMITS.md`.
- Utilizar mensagens inspiradas em Conventional Commits.
- Manter cada commit relacionado a uma única mudança lógica.
- Não criar commit, tag, branch, push ou pull request sem solicitação explícita.
- Não reescrever histórico compartilhado.
- Não usar comandos destrutivos para descartar alterações do usuário.
- Revisar o diff antes de considerar o trabalho concluído.
- Não incluir arquivos temporários, resultados de build ou dados sensíveis.

Exemplo:

```text
fix(ans): preserva codificação do arquivo filtrado
```

## 14. Ações proibidas sem autorização explícita

A IA não deve:

- implementar o backend de Fechamento sem especificação aprovada;
- declarar suporte a CSV no BI apenas alterando a interface;
- remover ou unificar os serviços XML sem decisão arquitetural;
- substituir o `localStorage` por persistência central incidentalmente;
- adicionar autenticação ou alterar a estratégia de segurança fora do escopo;
- contornar o bloqueio 403 do SGU/Kong ou qualquer controle externo;
- mover credenciais para o frontend;
- alterar posições e codificações dos arquivos ANS sem validação;
- sobrescrever arquivos originais;
- apagar código, documentação ou configuração sem confirmar o impacto;
- realizar refatoração ampla junto de uma correção pequena;
- afirmar que testes passaram quando não foram executados.

## 15. Critérios de conclusão

Uma alteração só pode ser considerada concluída quando:

- o pedido foi atendido sem expansão indevida do escopo;
- o comportamento atual e o comportamento final estão claros;
- regras de negócio e contratos foram preservados ou alterados conscientemente;
- testes e builds relevantes foram executados;
- erros e cenários alternativos foram considerados;
- a documentação necessária foi atualizada;
- o diff foi revisado;
- não foram incluídos segredos, dados pessoais ou arquivos temporários;
- limitações e validações pendentes foram informadas.

Ao finalizar, a IA deve apresentar:

1. resumo objetivo do que foi alterado;
2. arquivos modificados;
3. testes e verificações executados;
4. documentação atualizada;
5. riscos, limitações ou pendências restantes.

---

**Projeto:** Unimed Tools

**Responsável:** Heitor Leite

**Versão do documento:** 1.0
**Status:** instruções obrigatórias para agentes de programação
