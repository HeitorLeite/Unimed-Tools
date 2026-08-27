import { of } from 'rxjs';
import { RelatorioAgendamentoResumo } from '../models/relatorio.model';
import {
  DiretorioAgendamentoErro,
  DiretorioAgendamentoService,
} from './diretorio-agendamento.service';
import { ExecutorAgendamentoService } from './executor-agendamento.service';
import { RelatorioService } from './relatorio.service';

describe('ExecutorAgendamentoService', () => {
  const agendamento: RelatorioAgendamentoResumo = {
    id: '00000000-0000-0000-0000-000000000001',
    usuarioId: 1,
    tipoRelatorio: 'MANUAL',
    tituloRelatorio: 'Teste',
    formato: 'csv',
    nomeArquivo: 'teste',
    nomeArquivoExecucao: 'teste_20260828_0800',
    diretorioReferencia: '00000000-0000-0000-0000-000000000002',
    diretorioNome: 'Relatorios',
    incluirCabecalho: true,
    agendadoPara: new Date().toISOString(),
    recorrencia: 'DIARIA',
    diasSemana: [],
    fusoHorario: 'America/Sao_Paulo',
    status: 'EM_EXECUCAO',
    tentativas: 1,
    execucoesConcluidas: 0,
    criadoEm: new Date().toISOString(),
  };

  it('salva o arquivo na pasta autorizada e conclui o agendamento', async () => {
    const relatorios = {
      listarAgendamentosPendentes: vi.fn().mockReturnValue(of([agendamento.id])),
      reservarAgendamento: vi.fn().mockReturnValue(of(agendamento)),
      baixarArquivoAgendado: vi.fn().mockReturnValue(of({ body: new Blob(['teste']) })),
      concluirAgendamento: vi
        .fn()
        .mockReturnValue(of({ message: 'Arquivo salvo.', agendamento })),
    } as unknown as RelatorioService;
    const diretorios = {
      escrever: vi.fn().mockResolvedValue(undefined),
    } as unknown as DiretorioAgendamentoService;
    const service = new ExecutorAgendamentoService(relatorios, diretorios);

    await service.verificarAgora();

    expect(diretorios.escrever).toHaveBeenCalledWith(
      agendamento.diretorioReferencia,
      'teste_20260828_0800.csv',
      expect.any(Blob),
    );
    expect(relatorios.concluirAgendamento).toHaveBeenCalledWith(agendamento.id);
    expect(service.aviso()?.tipo).toBe('sucesso');
  });

  it('registra falha de pasta para permitir a troca do diretório', async () => {
    const relatorios = {
      listarAgendamentosPendentes: vi.fn().mockReturnValue(of([agendamento.id])),
      reservarAgendamento: vi.fn().mockReturnValue(of(agendamento)),
      baixarArquivoAgendado: vi.fn().mockReturnValue(of({ body: new Blob(['teste']) })),
      falharAgendamento: vi.fn().mockReturnValue(
        of({
          message: 'Selecione outro diretório.',
          agendamento: { ...agendamento, status: 'FALHA' },
        }),
      ),
    } as unknown as RelatorioService;
    const diretorios = {
      escrever: vi
        .fn()
        .mockRejectedValue(
          new DiretorioAgendamentoErro('PERMISSAO_REVOGADA', 'Permissão revogada.'),
        ),
    } as unknown as DiretorioAgendamentoService;
    const service = new ExecutorAgendamentoService(relatorios, diretorios);

    await service.verificarAgora();

    expect(relatorios.falharAgendamento).toHaveBeenCalledWith(
      agendamento.id,
      'PERMISSAO_REVOGADA',
    );
    expect(service.aviso()?.tipo).toBe('erro');
  });
});
