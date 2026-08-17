import { ChangeDetectorRef } from '@angular/core';

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
});
