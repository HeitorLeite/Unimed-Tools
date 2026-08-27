import { ChangeDetectorRef } from '@angular/core';
import { DiretorioAgendamentoService } from '../../../shared/services/diretorio-agendamento.service';
import { ExecutorAgendamentoService } from '../../../shared/services/executor-agendamento.service';
import { RelatorioService } from '../../../shared/services/relatorio.service';
import { RelatoriosAgendadosComponent } from './relatorios-agendados.component';

describe('RelatoriosAgendadosComponent', () => {
  function criarComponente(): RelatoriosAgendadosComponent {
    return new RelatoriosAgendadosComponent(
      {} as RelatorioService,
      {} as DiretorioAgendamentoService,
      {} as ExecutorAgendamentoService,
      { detectChanges: vi.fn() } as unknown as ChangeDetectorRef,
    );
  }

  it('seleciona por padrão o dia da primeira execução na recorrência semanal', () => {
    const componente = criarComponente();
    componente.agendadoPara = '2026-08-31T08:00';
    componente.recorrencia = 'SEMANAL';

    componente.alterarRecorrencia();

    expect(componente.diasSemanaSelecionados).toEqual([1]);
  });

  it('permite escolher vários dias sem duplicidade', () => {
    const componente = criarComponente();

    componente.alternarDiaSemana(5, true);
    componente.alternarDiaSemana(1, true);
    componente.alternarDiaSemana(5, true);

    expect(componente.diasSemanaSelecionados).toEqual([1, 5]);
  });
});
