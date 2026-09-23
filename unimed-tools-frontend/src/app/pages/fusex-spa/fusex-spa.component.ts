import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { FusexSpaService, FusexSpaValidacao } from '../../shared/services/fusex-spa.service';

export function normalizarGuias(texto: string): string[] {
  if (!texto.trim() || texto.length > 20000) throw new Error('Informe os IDs das guias (até 20.000 caracteres).');
  const ids = texto.split(/[,\r\n]/).map(id => id.trim()).filter(Boolean);
  const unicos = new Set<string>();
  for (const id of ids) {
    if (!/^[0-9]+$/.test(id)) throw new Error('Use apenas IDs inteiros separados por vírgula ou quebra de linha.');
    const numero = BigInt(id);
    if (numero > 9223372036854775807n) throw new Error('Um ID excede o limite de inteiro de 64 bits.');
    unicos.add(numero.toString());
    if (unicos.size > 1000) throw new Error('Informe no máximo 1.000 guias por operação.');
  }
  if (!unicos.size) throw new Error('Informe pelo menos um ID de guia.');
  return [...unicos];
}

@Component({
  selector: 'app-fusex-spa',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './fusex-spa.component.html',
  styleUrl: './fusex-spa.component.scss',
})
export class FusexSpaComponent {
  private readonly service = inject(FusexSpaService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  texto = '';
  confirmado = false;
  processando = false;
  erro = '';
  sucesso = '';
  validacao: FusexSpaValidacao | null = null;

  alterarTexto(texto: string): void {
    this.texto = texto;
    this.validacao = null;
    this.confirmado = false;
    this.erro = '';
    this.sucesso = '';
  }

  validar(): void {
    if (this.processando) return;
    this.validacao = null;
    this.confirmado = false;
    this.erro = '';
    this.sucesso = '';
    let ids: string[];
    try { ids = normalizarGuias(this.texto); }
    catch (error) { this.erro = (error as Error).message; return; }

    this.processando = true;
    this.service.validar(ids.join(','))
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.processando = false;
        this.cdr.markForCheck();
      }))
      .subscribe({
        next: resposta => { this.validacao = resposta; },
        error: error => { this.erro = error?.error?.message ?? 'Não foi possível validar as guias.'; },
      });
  }

  executar(): void {
    if (this.processando || !this.confirmado || !this.validacao?.execucaoDisponivel) return;
    this.processando = true;
    this.erro = '';
    this.sucesso = '';
    const quantidade = this.validacao.quantidadeGuias;

    this.service.executar(this.validacao.guias.join(','))
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        this.processando = false;
        this.confirmado = false;
        this.cdr.markForCheck();
      }))
      .subscribe({
        next: () => {
          this.sucesso = `O endpoint do SGU respondeu com sucesso para ${quantidade} guia(s). Confira o resultado antes de repetir a operação.`;
          this.validacao = null;
        },
        error: error => {
          const detalhe = error?.error?.message ?? 'Não foi possível confirmar a operação no SGU.';
          this.erro = `${detalhe} Não repita automaticamente: confirme o estado das guias antes de uma nova tentativa.`;
        },
      });
  }
}
