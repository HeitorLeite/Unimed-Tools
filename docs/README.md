# Documentação do Unimed Tools

Este diretório é o ponto de entrada para compreender, operar e alterar o sistema.
Antes de editar código, siga a ordem de leitura definida em [`AGENTS.md`](../AGENTS.md).

## Por onde começar

| Documento | Público principal | Conteúdo |
| --- | --- | --- |
| [README da raiz](../README.md) | Todos | Visão geral, ferramentas, requisitos e comandos principais |
| [Guia do usuário](GUIA_DO_USUARIO.md) | Usuários e suporte | Passo a passo das ferramentas, entradas, saídas e limitações |
| [Arquitetura](ARQUITETURA.md) | Desenvolvimento e arquitetura | Componentes, fronteiras, persistência, autenticação e integrações |
| [Mapa do código](MAPA_DO_CODIGO.md) | Desenvolvimento | Onde ficam rotas, páginas, controllers, services e regras de cada fluxo |
| [Guia de desenvolvimento](DESENVOLVIMENTO.md) | Desenvolvimento | Ambiente, execução, testes, depuração e processo seguro de alteração |
| [Segurança](../SEGURANCA.md) | Todos que alteram o sistema | Política obrigatória de segurança, privacidade e tratamento de dados |
| [Banco de dados](../database/README.md) | Backend e operação | Esquema, migrações, permissões e configuração do usuário do banco |
| [Frontend](../unimed-tools-frontend/README.md) | Frontend | Organização Angular, estado local e particularidades da interface |
| [Backend](../unimed-tools-backend/README.md) | Backend | Pacotes Java, grupos de endpoints e regras de implementação |

## Documentos especializados

- [Assistencial](ASSISTENCIAL.md): comportamento atual do construtor, cache de
  definição, análises e decimais.
- [Especialidades nos relatórios](ESPECIALIDADES_RELATORIOS.md): regra de
  resolução e propagação da especialidade por guia.
- [Exportação de relatórios](RELATORIOS_EXPORTACAO.md): integridade, paginação,
  formatos, lote e limites conhecidos.
- [`sql/0090-faixa-etaria-corrigida.sql`](sql/0090-faixa-etaria-corrigida.sql):
  referência SQL específica, não um script de migração do MariaDB.

## Status usados na documentação

- **Atual:** implementado e confirmado no código.
- **Parcial:** existe, mas há limitações funcionais relevantes.
- **Proposto:** solução descrita que ainda não foi implementada.
- **Pendente:** depende de especificação, decisão, homologação ou validação.

Ao alterar comportamento, atualize o documento mais próximo da regra e os links
de navegação quando necessário. Não replique uma regra detalhada em vários
arquivos: mantenha uma fonte principal e use links nas visões resumidas.
