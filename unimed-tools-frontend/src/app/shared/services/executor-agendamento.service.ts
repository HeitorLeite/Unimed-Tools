import { Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RelatorioAgendamentoResumo } from '../models/relatorio.model';
import {
  DiretorioAgendamentoErro,
  DiretorioAgendamentoService,
} from './diretorio-agendamento.service';
import { RelatorioService } from './relatorio.service';

export interface AvisoExecucaoAgendamento {
  tipo: 'sucesso' | 'erro';
  mensagem: string;
  agendamentoId?: string;
}

/** Verifica vencimentos enquanto a aplicação está aberta e garante uma execução por vez. */
@Injectable({ providedIn: 'root' })
export class ExecutorAgendamentoService {
  readonly aviso = signal<AvisoExecucaoAgendamento | null>(null);
  readonly processando = signal(false);

  private iniciado = false;
  private verificando = false;
  private temporizador?: ReturnType<typeof setInterval>;
  private temporizadorAviso?: ReturnType<typeof setTimeout>;

  constructor(
    private readonly relatorios: RelatorioService,
    private readonly diretorios: DiretorioAgendamentoService,
  ) {}

  iniciar(): void {
    if (this.iniciado) return;
    this.iniciado = true;
    this.relatorios.configuracaoAgendamento().subscribe({
      next: (configuracao) => {
        if (!configuracao.disponivel || !this.diretorios.suportado()) return;
        const intervalo = Math.max(15, configuracao.intervaloVerificacaoSegundos) * 1000;
        void this.verificarAgora();
        this.temporizador = setInterval(() => void this.verificarAgora(), intervalo);
      },
      error: () => {
        // Os demais módulos continuam disponíveis quando o agendador está indisponível.
      },
    });
  }

  async verificarAgora(): Promise<void> {
    if (this.verificando) return;
    this.verificando = true;
    try {
      const ids = await firstValueFrom(this.relatorios.listarAgendamentosPendentes());
      for (const id of ids) {
        await this.executar(id);
      }
    } catch {
      // A próxima verificação retoma falhas transitórias de rede ou autenticação.
    } finally {
      this.verificando = false;
      this.processando.set(false);
    }
  }

  limparAviso(): void {
    this.aviso.set(null);
    if (this.temporizadorAviso) clearTimeout(this.temporizadorAviso);
  }

  private async executar(id: string): Promise<void> {
    let reservado: RelatorioAgendamentoResumo | null = null;
    try {
      reservado = await firstValueFrom(this.relatorios.reservarAgendamento(id));
      this.processando.set(true);
      const resposta = await firstValueFrom(this.relatorios.baixarArquivoAgendado(id));
      const conteudo = resposta.body;
      if (!conteudo) throw new Error('Arquivo vazio.');
      if (!reservado.diretorioReferencia) {
        throw new DiretorioAgendamentoErro(
          'PASTA_INACESSIVEL',
          'A referência da pasta não está disponível para este usuário.',
        );
      }

      await this.diretorios.escrever(
        reservado.diretorioReferencia,
        `${reservado.nomeArquivoExecucao}.${reservado.formato}`,
        conteudo,
      );
      const conclusao = await firstValueFrom(this.relatorios.concluirAgendamento(id));
      this.notificar('sucesso', conclusao.message, id);
    } catch (erro) {
      if (!reservado) return;
      const codigo =
        erro instanceof DiretorioAgendamentoErro ? erro.codigo : 'EXPORTACAO_FALHOU';
      try {
        const falha = await firstValueFrom(this.relatorios.falharAgendamento(id, codigo));
        this.notificar('erro', falha.message, id);
      } catch {
        this.notificar(
          'erro',
          'O relatório agendado falhou e não foi possível atualizar seu estado. Abra a página de agendamentos.',
          id,
        );
      }
    }
  }

  private notificar(
    tipo: AvisoExecucaoAgendamento['tipo'],
    mensagem: string,
    agendamentoId: string,
  ): void {
    this.aviso.set({ tipo, mensagem, agendamentoId });
    if (this.temporizadorAviso) clearTimeout(this.temporizadorAviso);
    this.temporizadorAviso = setTimeout(() => this.aviso.set(null), 15_000);
  }
}
