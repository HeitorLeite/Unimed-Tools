import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';

import { RelatorioService } from '../../shared/services/relatorio.service';
import { RelatoriosComponent } from './relatorios.component';

describe('RelatoriosComponent - importação SQL', () => {
  const criarComponente = (): RelatoriosComponent => {
    const relatorioService = {} as RelatorioService;
    const cdr = { detectChanges: vi.fn() } as unknown as ChangeDetectorRef;
    return new RelatoriosComponent(relatorioService, cdr);
  };

  const criarArquivo = (consultaSQL: string): any => ({
    id: 'arquivo-1',
    arquivoNome: 'fx_etaria_gerar.sql',
    tamanhoBytes: consultaSQL.length,
    apiNome: '0090-fx-etaria-gerar',
    nomeExibicao: 'Faixa etária',
    descricao: '',
    consultaSQL,
    ordenacao: '',
    filtros: [],
    filtrosFixosDetectados: [],
    filtrosFixosIgnorados: [],
    ajustesAplicados: [],
    detalhesAbertos: false,
    status: 'pendente',
    erro: '',
  });

  it('bloqueia as ações do modo manual durante consulta ou download', () => {
    const component = criarComponente();

    expect(component.operacaoRelatorioEmAndamento).toBe(false);
    component.carregando = true;
    expect(component.operacaoRelatorioEmAndamento).toBe(true);
    component.carregando = false;
    component.exportando = 'xlsx';
    expect(component.operacaoRelatorioEmAndamento).toBe(true);
  });

  it('detecta a data compartilhada e a lista de empresas dentro da CTE', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      WITH base AS (
        SELECT trunc(months_between(TO_DATE('30/06/2026','DD/MM/YYYY'), pes.pes_dat_nasc) / 12) idade
        FROM dbaunimed.bnfrio b
        INNER JOIN dbaunimed.pessoa pes ON pes.pes_cod = b.bnf_cod_pessoa
        INNER JOIN dbaunimed.cntrat_venda ctv ON ctv.cv_nro = b.cv_nro
        LEFT JOIN dbaunimed.emp_contrt emp ON emp.empcn_cod = ctv.empcn_cod
        WHERE b.bnf_dat_inic_vigen <= TO_DATE('30/06/2026','DD/MM/YYYY')
          AND emp.empcn_cod_pessoa IN ()
          AND (b.bnf_dat_excl IS NULL OR b.bnf_dat_excl > TO_DATE('30/06/2026','DD/MM/YYYY'))
      ),
      dados AS (
        SELECT * FROM base
      )
      SELECT * FROM dados
    `);

    component.ajustarArquivoSql(arquivo);

    expect(arquivo.filtros.map((filtro: any) => filtro.nomeFiltro)).toEqual([
      'data_referencia',
      'empresas',
    ]);
    expect(arquivo.filtros[0]).toMatchObject({
      tipoDadoFiltro: 'DATE',
      mascaraFiltro: 'DD/MM/YYYY',
      conteudoFiltro: 'and :data_referencia is not null',
      obrigatorioFiltro: 'S',
    });
    expect(arquivo.filtros[1]).toMatchObject({
      tipoDadoFiltro: 'VARCHAR(4000)',
      conteudoFiltro: 'and :empresas is not null',
      obrigatorioFiltro: 'S',
    });
    expect(arquivo.consultaSQL.match(/:data_referencia/g)).toHaveLength(3);
    expect(arquivo.consultaSQL).toContain("replace(:empresas, ' ', '')");
    expect(arquivo.consultaSQL).toContain('to_char(emp.empcn_cod_pessoa)');
    expect(arquivo.consultaSQL).not.toContain("TO_DATE('30/06/2026','DD/MM/YYYY')");
    expect(arquivo.consultaSQL).not.toContain('emp.empcn_cod_pessoa IN ()');
    expect(component.erroArquivoSql(arquivo)).toBe('');

    const definicaoSgu = (component as any).definicaoDoArquivoSql(arquivo);
    expect(definicaoSgu.filtros.map((filtro: any) => filtro.nomeFiltro)).toEqual([
      'datareferencia',
      'empresas',
    ]);
    expect(definicaoSgu.consultaSQL).toContain(':datareferencia');
    expect(definicaoSgu.consultaSQL).not.toContain(':data_referencia');
    expect(definicaoSgu.filtros[0].conteudoFiltro).toBe('and :datareferencia is not null');
  });

  it('não unifica datas diferentes encontradas na mesma CTE', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      WITH base AS (
        SELECT *
        FROM dbaunimed.bnfrio b
        WHERE b.bnf_dat_inic_vigen <= TO_DATE('01/06/2026','DD/MM/YYYY')
          AND b.bnf_dat_excl > TO_DATE('30/06/2026','DD/MM/YYYY')
      )
      SELECT * FROM base
    `);

    component.ajustarArquivoSql(arquivo);

    expect(arquivo.filtros).toEqual([]);
    expect(arquivo.consultaSQL).toContain("TO_DATE('01/06/2026','DD/MM/YYYY')");
    expect(arquivo.consultaSQL).toContain("TO_DATE('30/06/2026','DD/MM/YYYY')");
  });

  it('detecta somente os filtros editáveis do relatório de despesas por grupo', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      WITH SOLA_UNICA AS (
        SELECT *
        FROM (
          SELECT SOLA.*, ROW_NUMBER() OVER (ORDER BY SOLA.GSOL_COD_SOLICITACAO DESC) RN
          FROM GUIA_SOLIC_AUTRIZ SOLA
        )
        WHERE RN = 1
      ), SOL_UNICA AS (
        SELECT *
        FROM (
          SELECT SOL.*, ROW_NUMBER() OVER (ORDER BY SOL.CD_SOLICITACAO DESC) RN
          FROM SOLICITACOES SOL
        )
        WHERE RN = 1
      )
      SELECT GB.GRBNF_COD, G.GUIA_NRO_COMPET, GI.GUITE_IND_STATUS
      FROM GRUPO_BENEFICIARIO GB
      JOIN GUIA G ON G.GRBNF_COD = GB.GRBNF_COD
      JOIN GUIA_ITEM GI ON GI.GUIA_ID = G.GUIA_ID
      WHERE GB.GRBNF_COD IN (1)
        AND G.GUIA_NRO_COMPET IN (202605)
        AND GI.GUITE_IND_STATUS = 'I'
    `);

    component.ajustarArquivoSql(arquivo);

    expect(arquivo.filtros.map((filtro: any) => filtro.nomeFiltro)).toEqual([
      'grbnf_cod',
      'competencia',
    ]);
    expect(arquivo.filtros.find((filtro: any) => filtro.nomeFiltro === 'grbnf_cod')).toMatchObject({
      tipoDadoFiltro: 'VARCHAR(4000)',
      conteudoFiltro: expect.stringContaining(':grbnf_cod'),
    });
    expect(arquivo.consultaSQL).toContain('WHERE RN = 1');
    expect(arquivo.consultaSQL).toContain("GI.GUITE_IND_STATUS = 'I'");
    expect(arquivo.consultaSQL).not.toContain(':rn');
    expect(arquivo.consultaSQL).not.toContain(':guite_ind_status');
    expect(component.erroArquivoSql(arquivo)).toBe('');

    const definicaoSgu = (component as any).definicaoDoArquivoSql(arquivo);
    expect(definicaoSgu.filtros.map((filtro: any) => filtro.nomeFiltro)).toEqual([
      'grbnfcod',
      'competencia',
    ]);
    expect(definicaoSgu.filtros[0].conteudoFiltro).toContain(':grbnfcod');
    expect(definicaoSgu.filtros[1].conteudoFiltro).toContain(':competencia');
    expect(definicaoSgu.consultaSQL).not.toContain(':grbnf_cod');
  });

  it('rejeita filtros diferentes que ficam iguais após a compactação do SGU', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      SELECT * FROM GUIA WHERE 1 = 1 /*FILTROS*/
    `);
    arquivo.filtros = [
      {
        nomeFiltro: 'codigo_empresa',
        conteudoFiltro: 'and EMPRESA = :codigo_empresa',
        tipoDadoFiltro: 'NUMBER',
        mascaraFiltro: '',
        obrigatorioFiltro: 'S',
      },
      {
        nomeFiltro: 'codigoempresa',
        conteudoFiltro: 'and OUTRA_EMPRESA = :codigoempresa',
        tipoDadoFiltro: 'NUMBER',
        mascaraFiltro: '',
        obrigatorioFiltro: 'S',
      },
    ];

    expect(component.erroArquivoSql(arquivo)).toContain(
      'resultam no mesmo nome aceito pelo SGU: “codigoempresa”',
    );
  });

  it('mantém constantes técnicas e converte filtros conhecidos dentro da CTE', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      WITH dados AS (
        SELECT GB.GRBNF_COD, G.GUIA_NRO_COMPET, GI.GUITE_IND_STATUS
        FROM GRUPO_BENEFICIARIO GB
        JOIN GUIA G ON G.GRBNF_COD = GB.GRBNF_COD
        JOIN GUIA_ITEM GI ON GI.GUIA_ID = G.GUIA_ID
        WHERE GB.GRBNF_COD = 1
          AND G.GUIA_NRO_COMPET IN (202605)
          AND GI.GUITE_IND_STATUS = 'I'
      )
      SELECT * FROM dados
    `);

    component.ajustarArquivoSql(arquivo);

    expect(arquivo.consultaSQL).toContain('GB.GRBNF_COD = :grbnf_cod');
    expect(arquivo.consultaSQL).toContain('G.GUIA_NRO_COMPET = :competencia');
    expect(arquivo.consultaSQL).toContain("GI.GUITE_IND_STATUS = 'I'");
    expect(arquivo.filtros.map((filtro: any) => filtro.nomeFiltro)).toEqual([
      'grbnf_cod',
      'competencia',
    ]);
    expect(component.erroArquivoSql(arquivo)).toBe('');
  });

  it('informa aliases duplicados no mesmo SELECT antes de enviar ao SGU', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      SELECT EC.EMPCN_COD_PESSOA
      FROM DBAUNIMED.CNTRAT_VENDA CNT
      LEFT JOIN DBAUNIMED.EMP_CONTRT EC ON EC.EMPCN_COD = CNT.EMPCN_COD
      LEFT JOIN DBAUNIMED.CNTRAT_VENDA CV ON CV.CV_NRO = CNT.CV_NRO
      LEFT JOIN DBAUNIMED.EMP_CONTRT EC ON EC.EMPCN_COD = CV.EMPCN_COD
      WHERE 1 = 1
    `);

    component.ajustarArquivoSql(arquivo);

    expect(component.erroArquivoSql(arquivo)).toContain(
      'O alias SQL “EC” foi declarado mais de uma vez no mesmo SELECT',
    );
  });

  it('permite reutilizar o mesmo alias em subconsultas diferentes', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      SELECT (
        SELECT P.PES_NOM_COMP FROM DBAUNIMED.PESSOA P WHERE P.PES_COD = 1
      ) NOME,
      (
        SELECT P.PES_IND FROM DBAUNIMED.PESSOA P WHERE P.PES_COD = 1
      ) TIPO
      FROM DUAL
      WHERE 1 = 1
    `);

    component.ajustarArquivoSql(arquivo);

    expect(component.erroArquivoSql(arquivo)).toBe('');
  });

  it('informa aliases de coluna duplicados antes de enviar ao SGU', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      SELECT
        SUBSTR(GI.ITEM_COD, 1, 4) AS GRUPO,
        PES.PES_NOM_COMP AS GRUPO
      FROM DBAUNIMED.GUIA_ITEM GI
      JOIN DBAUNIMED.PESSOA PES ON PES.PES_COD = GI.PES_COD
      WHERE 1 = 1
    `);

    component.ajustarArquivoSql(arquivo);

    expect(component.erroArquivoSql(arquivo)).toContain(
      'A coluna de saída “GRUPO” foi definida mais de uma vez no mesmo SELECT',
    );
    expect(component.erroArquivoSql(arquivo)).toContain('ORA-00918');
  });

  it('permite o mesmo alias de coluna em SELECTs independentes', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      WITH PRIMEIRO AS (
        SELECT P.PES_NOM_COMP AS NOME FROM DBAUNIMED.PESSOA P
      ), SEGUNDO AS (
        SELECT P.PES_NOM_COMP AS NOME FROM DBAUNIMED.PESSOA P
      )
      SELECT PRIMEIRO.NOME AS NOME_PRIMEIRO, SEGUNDO.NOME AS NOME_SEGUNDO
      FROM PRIMEIRO
      JOIN SEGUNDO ON 1 = 1
      WHERE 1 = 1
    `);

    component.ajustarArquivoSql(arquivo);

    expect(component.erroArquivoSql(arquivo)).toBe('');
  });

  it('ignora anotações após o ponto e vírgula da consulta principal', () => {
    const component = criarComponente();
    const resultado = (component as any).extrairPrimeiraInstrucaoSql(`
      SELECT 'texto; preservado' valor
      FROM dual
      -- comentário com ; preservado
      ORDER BY valor;

      /* consultas auxiliares e anotações da equipe */
      SELECT * FROM outra_tabela;
    `);

    expect(resultado.sql).toContain("SELECT 'texto; preservado' valor");
    expect(resultado.sql).toContain('ORDER BY valor');
    expect(resultado.sql).not.toContain('outra_tabela');
    expect(resultado.sql).not.toMatch(/;\s*$/);
    expect(resultado.conteudoPosteriorIgnorado).toBe(true);
  });

  it('remove comentários finais antes de posicionar o marcador do SGU', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      SELECT EMP.EMPCN_COD_PESSOA
      FROM DBAUNIMED.EMP_CONTRT EMP
      WHERE EMP.EMPCN_COD_PESSOA IN (90)

      /*
       * Consulta auxiliar mantida apenas como anotação da equipe.
       * SELECT * FROM OUTRA_TABELA;
       */
    `);

    component.ajustarArquivoSql(arquivo);

    expect(arquivo.consultaSQL).toContain('/*FILTROS*/');
    expect(arquivo.consultaSQL).not.toContain('Consulta auxiliar');
    expect(arquivo.consultaSQL).not.toContain('OUTRA_TABELA');
    expect(arquivo.ajustesAplicados).toContain(
      'Comentários e anotações após o fim da consulta foram removidos.',
    );
    expect(component.erroArquivoSql(arquivo)).toBe('');
  });

  it('impede o cadastro de SQL com comentário de bloco sem fechamento', () => {
    const component = criarComponente();
    const arquivo = criarArquivo(`
      SELECT *
      FROM DBAUNIMED.EMP_CONTRT
      WHERE 1 = 1
      /* anotação sem fechamento
    `);

    component.ajustarArquivoSql(arquivo);

    expect(component.erroArquivoSql(arquivo)).toContain('comentário /* sem fechamento');
  });

  it('atualiza a lista assim que a consulta de APIs termina', () => {
    const resposta$ = new Subject<any[]>();
    const relatorioService = {
      listarApis: vi.fn(() => resposta$.asObservable()),
    } as unknown as RelatorioService;
    const cdr = { detectChanges: vi.fn() } as unknown as ChangeDetectorRef;
    const component = new RelatoriosComponent(relatorioService, cdr);

    component.carregarApisCadastradas();
    expect(component.carregandoListaApis).toBe(true);

    resposta$.next([
      {
        nome: '0090-api-teste',
        consultaSQL: 'select 1 from dual',
        ordenacao: '',
        filtros: [],
      },
    ]);
    resposta$.complete();

    expect(component.carregandoListaApis).toBe(false);
    expect(component.apisDisponiveis).toHaveLength(1);
    expect(cdr.detectChanges).toHaveBeenCalledTimes(1);
  });

  it('atualiza a tela depois da conclusão assíncrona do cadastro em lote', async () => {
    const relatorioService = {
      listarApis: vi.fn(() => of([])),
      criarApi: vi.fn(() => of({})),
      salvarCatalogo: vi.fn(),
    } as unknown as RelatorioService;
    const cdr = { detectChanges: vi.fn() } as unknown as ChangeDetectorRef;
    const component = new RelatoriosComponent(relatorioService, cdr);
    component.arquivosSqlImportados = [criarArquivo('SELECT 1 AS VALOR FROM DUAL')];

    await component.criarApisDosArquivos();

    expect(component.criandoApisEmLote).toBe(false);
    expect(component.arquivosSqlImportados).toEqual([]);
    expect(component.sucesso).toContain('cadastrada e adicionada');
    expect(cdr.detectChanges).toHaveBeenCalled();
  });

  it('mostra imediatamente o erro lido de uma resposta Blob', async () => {
    const erroHttp = new HttpErrorResponse({
      status: 400,
      statusText: 'Bad Request',
      error: new Blob([JSON.stringify({ message: 'Falha de exportação.' })], {
        type: 'application/json',
      }),
    });
    const relatorioService = {
      exportar: vi.fn(() => throwError(() => erroHttp)),
    } as unknown as RelatorioService;
    const cdr = { detectChanges: vi.fn() } as unknown as ChangeDetectorRef;
    const component = new RelatoriosComponent(relatorioService, cdr);
    component.selecionado = {
      id: 'relatorio-1',
      nomeExibicao: 'Relatório de teste',
      descricao: '',
      apiNome: '0090-api-teste',
      filtros: [],
      criadoEm: '2026-08-20T00:00:00.000Z',
    };

    component.baixar();

    await vi.waitFor(() => {
      expect(component.erro).toBe('Erro 400: Falha de exportação.');
    });
    expect(component.exportando).toBeNull();
    expect(cdr.detectChanges).toHaveBeenCalled();
  });
});
