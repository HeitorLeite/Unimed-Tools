import { ToolHelpContent } from '../models/tool-help.model';

export const TOOL_HELP_CONTENT = {
  comercial: {
    title: 'Como usar o Comercial',
    summary:
      'Gere relatórios de beneficiários, receita, despesas e faixa etária para uma ou várias empresas.',
    prerequisites: [
      'Selecionar ao menos uma empresa.',
      'Informar uma competência no formato AAAAMM.',
    ],
    steps: [
      {
        title: 'Escolha as empresas',
        description: 'Use a busca e marque todas as empresas que entrarão na consulta.',
      },
      {
        title: 'Defina o período',
        description:
          'Confira a competência e, para faixa etária, a data de referência e se deseja códigos ativos, inativos ou todos.',
      },
      {
        title: 'Selecione os relatórios',
        description: 'Marque somente os arquivos necessários para a rotina.',
      },
      {
        title: 'Gere e confira',
        description: 'Gere as prévias e verifique registros, avisos e resultados vazios.',
      },
      {
        title: 'Exporte',
        description: 'Escolha CSV, TXT ou XLSX e baixe individualmente ou em um pacote ZIP.',
      },
    ],
    result:
      'Os relatórios comuns geram um arquivo por empresa. A faixa etária gera um único consolidado das empresas selecionadas.',
    tips: [
      'Na faixa etária, códigos de quatro posições iniciados por 5 ou 9 são inativos; os demais são ativos.',
      'Alterar empresa, competência, data, situação dos códigos ou seleção invalida a prévia anterior.',
      'Uma prévia vazia é válida, mas não produz arquivo de exportação.',
    ],
    warnings: ['A consulta depende do backend e da disponibilidade do SGU/Kong.'],
    status: 'Atual',
  },
  assistencial: {
    title: 'Como usar o Assistencial',
    summary:
      'Monte um relatório sob medida escolhendo colunas, filtros, análises e a ordem de saída.',
    prerequisites: ['Escolher ao menos uma coluna.', 'Preencher a competência obrigatória.'],
    steps: [
      {
        title: 'Escolha as colunas',
        description: 'Marque os campos e arraste-os para definir a ordem da prévia e do arquivo.',
      },
      {
        title: 'Configure filtros',
        description: 'Adicione os filtros necessários e preencha seus valores.',
      },
      {
        title: 'Ajuste as análises',
        description: 'Opcionalmente remova duplicados, separe métricas por mês ou aplique ranking.',
      },
      {
        title: 'Gere a prévia',
        description:
          'Confira dados, ordenação, paginação e o tratamento visual de campos protegidos.',
      },
      {
        title: 'Exporte ou salve o modelo',
        description: 'Baixe CSV, TXT ou XLSX e salve a estrutura para reutilização.',
      },
    ],
    result: 'A exportação respeita as colunas e a ordem conferidas na prévia.',
    tips: [
      'Modelos guardam a estrutura, mas nunca os valores digitados nos filtros.',
      'Use nomes claros para diferenciar modelos semelhantes.',
    ],
    warnings: [
      'Relatórios avançados podem levar mais tempo porque percorrem todas as páginas do serviço externo.',
    ],
    status: 'Atual',
  },
  revisaoContas: {
    title: 'Como usar a Revisão de Contas',
    summary: 'Analise e corrija XMLs TISS no navegador sem sobrescrever os arquivos originais.',
    prerequisites: ['Arquivos XML válidos com as estruturas TISS esperadas.'],
    steps: [
      {
        title: 'Escolha a operação',
        description:
          'Selecione todas as correções, apenas prefixos ou apenas blocos vazios/zerados.',
      },
      {
        title: 'Adicione os XMLs',
        description: 'Arraste ou selecione um ou vários arquivos; nomes repetidos são ignorados.',
      },
      {
        title: 'Analise o lote',
        description: 'Aguarde o processamento local e abra os detalhes de cada arquivo.',
      },
      {
        title: 'Revise as alterações',
        description: 'Confira correções, remoções e avisos antes de baixar.',
      },
      {
        title: 'Baixe novas cópias',
        description: 'Escolha a regra de nomes e baixe um XML ou o lote em ZIP.',
      },
    ],
    result: 'São geradas novas cópias corrigidas; os arquivos enviados permanecem inalterados.',
    tips: ['Para rastreabilidade, prefira nomes com o sufixo “_corrigido”.'],
    warnings: [
      'As regras dependem das tags e estruturas com prefixo ans:. Revise o resumo antes de usar os arquivos.',
    ],
    status: 'Atual',
  },
  unica: {
    title: 'Como usar a Única',
    summary:
      'Filtre um TXT posicional de rede ANS usando uma planilha XLSX ou arquivos CSV de referência.',
    prerequisites: ['Uma fonte de filtros.', 'Um arquivo TXT posicional no padrão esperado.'],
    steps: [
      {
        title: 'Escolha a fonte',
        description: 'Use uma planilha XLSX ou informe uma ou mais categorias CSV.',
      },
      {
        title: 'Adicione os filtros',
        description: 'Selecione os arquivos correspondentes às categorias desejadas.',
      },
      { title: 'Adicione o TXT', description: 'Informe o arquivo de rede que será filtrado.' },
      {
        title: 'Processe',
        description: 'Acompanhe o log e confira quantas linhas foram lidas, removidas e mantidas.',
      },
      { title: 'Baixe o resultado', description: 'Salve a nova cópia do TXT filtrado.' },
    ],
    result:
      'A saída preserva o formato posicional e a codificação ISO-8859-1 esperada pelo fluxo ANS.',
    tips: ['Nos CSVs, envie somente as categorias que fazem parte da ocorrência recebida.'],
    warnings: [
      'CNPJ, CNES e outros campos dependem de posições fixas; não edite manualmente o TXT antes de processar.',
    ],
    status: 'Atual',
  },
  hospital: {
    title: 'Como usar o Hospital',
    summary:
      'Consulte autorizações que ainda não possuem guia gerada e exporte o resultado completo.',
    prerequisites: ['Acesso ao módulo Hospital.', 'Backend e SGU disponíveis.'],
    steps: [
      {
        title: 'Preencha os filtros',
        description: 'Use somente os campos necessários; filtros vazios não restringem a consulta.',
      },
      {
        title: 'Gere o relatório',
        description: 'Consulte a primeira página e confira o total retornado.',
      },
      {
        title: 'Navegue pela prévia',
        description: 'Use a paginação para revisar outros registros.',
      },
      {
        title: 'Exporte',
        description: 'Escolha XLSX, CSV ou TXT e baixe o conjunto completo filtrado.',
      },
    ],
    result: 'O arquivo contém todas as colunas configuradas no backend, incluindo prestador.',
    tips: ['O filtro de prestador aceita parte do nome; não é necessário digitar curingas.'],
    warnings: ['O download só é habilitado depois que a prévia retorna registros.'],
    status: 'Atual',
  },
  gestaoRisco: {
    title: 'Como usar a Gestão de Risco',
    summary: 'Consulte e exporte os cinco relatórios de rastreio em uma única rotina.',
    prerequisites: [
      'Informar a competência no formato AAAAMM.',
      'Preencher filtros adicionais obrigatórios quando exibidos.',
    ],
    steps: [
      {
        title: 'Defina os filtros',
        description: 'Confira a competência e complete os campos detectados nas APIs.',
      },
      {
        title: 'Escolha os relatórios',
        description: 'Marque colonoscopia, consultas, mamografia, ressonância e/ou sangue oculto.',
      },
      {
        title: 'Gere as prévias',
        description: 'Aguarde cada relatório e confira resultados vazios ou erros individuais.',
      },
      {
        title: 'Exporte',
        description: 'Baixe um relatório por vez ou gere um ZIP com todos os selecionados.',
      },
    ],
    result: 'O pacote contém os relatórios concluídos e informa eventuais falhas sem ocultá-las.',
    tips: ['Alterar filtros ou seleção exige gerar as prévias novamente.'],
    warnings: [
      'Cada relatório depende de uma API SGU própria e pode terminar com resultado diferente dos demais.',
    ],
    status: 'Atual',
  },
  ti: {
    title: 'Como usar a área TI',
    summary:
      'Administre relatórios, grupos e páginas publicadas sem expor credenciais do SGU no navegador.',
    prerequisites: ['Conta administradora com as permissões técnicas necessárias.'],
    steps: [
      {
        title: 'Relatórios e APIs',
        description: 'Consulte, importe, teste e mantenha definições autorizadas do SGU.',
      },
      {
        title: 'Grupos de relatórios',
        description: 'Organize relatórios existentes para execução e download em lote.',
      },
      {
        title: 'Páginas e ferramentas',
        description:
          'Personalize cards nativos ou publique uma ferramenta baseada em API existente.',
      },
      {
        title: 'Valide antes de publicar',
        description: 'Teste filtros, prévia, formato de saída e acesso com uma conta apropriada.',
      },
    ],
    result:
      'Ferramentas configuráveis ficam globais no banco; catálogos e grupos legados continuam locais quando indicado pela tela.',
    tips: ['Prefira o construtor de ferramentas para páginas simples baseadas em APIs existentes.'],
    warnings: [
      'Não cole chaves, senhas ou dados pessoais em SQL, nomes, descrições ou logs. Rotas e permissões nativas não são editáveis pela interface.',
    ],
    status: 'Atual',
  },
  customReport: {
    title: 'Como usar esta ferramenta',
    summary: 'Execute uma ferramenta publicada pela TI sobre uma API de relatório já configurada.',
    prerequisites: ['Preencher todos os filtros marcados com asterisco.'],
    steps: [
      {
        title: 'Informe os filtros',
        description: 'Preencha os campos obrigatórios e, se necessário, os opcionais.',
      },
      {
        title: 'Gere a prévia',
        description: 'Confira as colunas e os registros retornados pela API.',
      },
      {
        title: 'Navegue',
        description: 'Use os controles de página para revisar outros resultados.',
      },
      { title: 'Exporte', description: 'Escolha XLSX, CSV ou TXT e baixe o relatório completo.' },
    ],
    result: 'O arquivo segue a API, os filtros e as colunas de apresentação definidos pela TI.',
    warnings: [
      'Se a ferramenta estiver indisponível, confirme com a TI se o card e a API continuam ativos.',
    ],
    status: 'Atual',
  },
  especialidade: {
    title: 'Como usar Especialidade Médica',
    summary:
      'Complete especialidades em uma planilha de despesas usando uma planilha de médicos como referência.',
    prerequisites: [
      'Planilha de despesas XLSX.',
      'Planilha de médicos XLSX com as colunas esperadas.',
    ],
    steps: [
      {
        title: 'Adicione as despesas',
        description: 'Selecione a planilha principal que receberá os preenchimentos.',
      },
      {
        title: 'Adicione os médicos',
        description: 'Selecione a base de referência com nome e especialidade.',
      },
      {
        title: 'Processe',
        description:
          'Acompanhe as contagens de linhas preenchidas, preservadas e sem correspondência.',
      },
      {
        title: 'Baixe a cópia',
        description: 'Salve o arquivo identificado com o sufixo _PREENCHIDO.',
      },
    ],
    result: 'Linhas que já possuíam especialidade são preservadas.',
    warnings: [
      'O backend atual processa XLSX. A seleção visual de CSV não representa suporte funcional completo.',
    ],
    status: 'Parcial',
  },
  fechamento: {
    title: 'Sobre o Fechamento de Produção',
    summary:
      'Esta tela demonstra um fluxo planejado, mas a conversão ainda não foi implementada no backend.',
    steps: [
      {
        title: 'Não use em produção',
        description:
          'A seleção de arquivo e o botão representam somente o contrato visual previsto.',
      },
      {
        title: 'Aguarde a especificação',
        description:
          'A implementação depende de exemplos anonimizados, regras de transformação e critérios de aceite.',
      },
    ],
    result:
      'Nenhum resultado operacional é garantido enquanto o endpoint /api/fechamento/converter não existir.',
    warnings: [
      'Não apresente esta funcionalidade como concluída e não envie arquivos reais para tentativa de conversão.',
    ],
    status: 'Pendente',
  },
} as const satisfies Record<string, ToolHelpContent>;
