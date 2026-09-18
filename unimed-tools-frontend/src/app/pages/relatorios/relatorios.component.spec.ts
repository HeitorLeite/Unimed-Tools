import { RelatorioService } from '../../shared/services/relatorio.service';
import { RelatoriosComponent } from './relatorios.component';

describe('RelatoriosComponent - orquestração', () => {
  it('carrega o resumo e atualiza o catálogo ao entrar no modo automático', () => {
    const listarCatalogo = vi
      .fn()
      .mockReturnValueOnce([])
      .mockReturnValueOnce([
        {
          id: 'relatorio-1',
          nomeExibicao: 'Relatório de teste',
          descricao: '',
          apiNome: 'api-teste',
          filtros: [],
          criadoEm: '2026-09-18T00:00:00.000Z',
        },
      ]);
    const relatorioService = {
      listarCatalogo,
      listarGruposAutomaticos: vi.fn(() => [{ id: 'grupo-1' }]),
    } as unknown as RelatorioService;

    const component = new RelatoriosComponent(relatorioService);
    component.ngOnInit();

    expect(component.modoPagina).toBe('selecao');
    expect(component.quantidadeGruposAutomaticos).toBe(1);
    expect(component.relatorios).toEqual([]);

    component.selecionarModoPagina('automatico');

    expect(component.modoPagina).toBe('automatico');
    expect(component.relatorios).toHaveLength(1);
    expect(listarCatalogo).toHaveBeenCalledTimes(2);
  });
});
