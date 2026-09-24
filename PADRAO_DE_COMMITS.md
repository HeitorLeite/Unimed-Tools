# Padronização de Commits — Unimed Tools

Este documento define o padrão de mensagens de commit do projeto **Unimed Tools**. O objetivo é manter o histórico do Git claro, pesquisável e compreensível para qualquer pessoa que trabalhe no projeto.

O padrão adotado é inspirado no [Conventional Commits 1.0.0](https://www.conventionalcommits.org/pt-br/v1.0.0/).

## 1. Estrutura da mensagem

Utilize o seguinte formato:

```text
<tipo>(<escopo opcional>): <descrição curta>

[corpo opcional]

[rodapé opcional]
```

Exemplo:

```text
feat(xml): adiciona remoção de despesas vazias
```

## 2. Tipos permitidos

| Tipo       | Utilização                                               |
| ---------- | -------------------------------------------------------- |
| `feat`     | Adição de uma nova funcionalidade.                       |
| `fix`      | Correção de um erro ou comportamento incorreto.          |
| `docs`     | Alterações somente na documentação.                      |
| `refactor` | Reorganização do código sem alterar seu comportamento.   |
| `test`     | Criação ou alteração de testes.                          |
| `style`    | Formatação do código, sem mudança de comportamento.      |
| `perf`     | Melhoria de desempenho.                                  |
| `build`    | Alterações no processo de compilação ou dependências.    |
| `ci`       | Alterações em integração ou entrega contínua.            |
| `chore`    | Tarefas de manutenção que não alteram regras de negócio. |
| `revert`   | Reversão de um commit anterior.                          |

## 3. Escopos recomendados

O escopo é opcional e identifica a área afetada pela mudança. Utilize nomes em letras minúsculas e sem acentos.

| Escopo       | Área do projeto                                  |
| ------------ | ------------------------------------------------ |
| `frontend`   | Aplicação Angular e componentes compartilhados.  |
| `backend`    | Aplicação Spring Boot e recursos compartilhados. |
| `xml`        | Ferramentas de processamento de XML TISS.        |
| `bi`         | Preenchimento de especialidades médicas.         |
| `ans`        | Correção e filtragem da rede ANS.                |
| `relatorios` | Central de Relatórios e integração com o SGU.    |
| `fechamento` | Conversão de arquivos de fechamento.             |
| `config`     | Configurações do projeto e ambientes.            |
| `deps`       | Dependências e respectivas versões.              |
| `docs`       | Documentação geral do projeto.                   |

Quando a mudança afetar várias áreas e não existir um escopo predominante, o escopo pode ser omitido.

## 4. Regras para a descrição

- escrever a descrição em português;
- iniciar com letra minúscula;
- usar uma frase curta e objetiva;
- não terminar a descrição com ponto final;
- informar o que o commit realiza, e não apenas o arquivo alterado;
- manter cada commit concentrado em uma única mudança lógica.

Exemplos adequados:

```text
feat(ans): adiciona processamento de múltiplos arquivos csv
fix(xml): corrige remoção de prefixos em códigos TISS
docs: adiciona diagrama de atividades do projeto
refactor(relatorios): separa geração de arquivos por formato
chore(deps): atualiza dependências do frontend
```

Exemplos que devem ser evitados:

```text
alterações
correção
atualiza arquivos
commit final
feat: coisas novas
```

## 5. Corpo da mensagem

O corpo é opcional. Utilize-o quando a descrição curta não for suficiente para explicar a mudança.

```text
fix(bi): corrige leitura da planilha de médicos

Ajusta a identificação das colunas obrigatórias e apresenta uma
mensagem clara quando a estrutura da planilha não é reconhecida.
```

O corpo deve explicar, quando necessário:

- o motivo da alteração;
- o comportamento anterior;
- o novo comportamento;
- impactos ou limitações relevantes.

## 6. Alterações incompatíveis

Uma alteração que quebra compatibilidade deve utilizar `!` depois do tipo ou escopo e pode apresentar os detalhes no rodapé `BREAKING CHANGE`.

```text
feat(relatorios)!: altera o formato da requisição de exportação

BREAKING CHANGE: o campo formatoSaida foi substituído pelo campo formato.
```

Esse tipo de commit deve ser usado com atenção, pois indica que outras partes do sistema podem precisar de ajustes.

## 7. Referência a issues

Quando a mudança estiver relacionada a uma issue, adicione a referência no rodapé.

```text
fix(ans): corrige contagem de linhas removidas

Refs #42
```

Quando o commit concluir completamente a issue:

```text
feat(xml): adiciona download consolidado em zip

Closes #57
```

## 8. Exemplos para o Unimed Tools

```text
feat(frontend): adiciona indicador visual de processamento
feat(backend): adiciona tratamento global para arquivos inválidos
fix(xml): preserva codificação original do arquivo processado
fix(bi): corrige preenchimento de especialidades já existentes
fix(ans): corrige seleção do endpoint para arquivos csv
feat(relatorios): adiciona exportação de relatório em xlsx
refactor(relatorios): extrai serviço de geração do manifesto
docs: atualiza levantamento de requisitos
test(backend): adiciona testes para validação de arquivos
style(frontend): padroniza espaçamento dos formulários
```

## 9. Checklist antes do commit

Antes de criar um commit, confirme:

- [ ] a alteração está relacionada a um único objetivo;
- [ ] o tipo representa corretamente a mudança;
- [ ] o escopo está correto ou foi omitido conscientemente;
- [ ] a descrição está curta, clara e em português;
- [ ] não existem arquivos temporários, credenciais ou dados sensíveis;
- [ ] o código foi executado ou testado quando aplicável;
- [ ] a documentação foi atualizada quando o comportamento mudou.

## 10. Modelo rápido

```bash
git add <arquivos>
git commit -m "tipo(escopo): descrição curta"
```

Exemplo:

```bash
git add unimed-tools-unimed-tools-frontend/src/app/xml-tools
git commit -m "fix(xml): corrige validação de arquivos selecionados"
```

---

**Projeto:** Unimed Tools

**Responsável:** Heitor Leite

**Versão do documento:** 1.0
**Status:** padrão inicial para adoção no projeto
