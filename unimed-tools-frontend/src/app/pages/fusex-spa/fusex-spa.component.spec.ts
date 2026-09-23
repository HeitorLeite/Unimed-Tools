import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { FusexSpaComponent, normalizarGuias } from './fusex-spa.component';
import { FusexSpaService } from '../../shared/services/fusex-spa.service';

describe('Valorizar guias Fusex-SPA', () => {
  it('valida inteiros e remove duplicados sem perda de precisão', () => {
    expect(normalizarGuias('001, 2\r\n1\n9007199254740993')).toEqual(['1', '2', '9007199254740993']);
    for (const entrada of ['', ',\n', '1.2', '1e3', '-1', '1; UPDATE X', '9223372036854775808']) {
      expect(() => normalizarGuias(entrada)).toThrow();
    }
  });

  it('mostra revisão, bloqueia execução indisponível e invalida confirmação ao editar IDs', async () => {
    const service = {
      validar: vi.fn(() => of({ guias: ['1', '2'], quantidadeGuias: 2, execucaoDisponivel: false, mensagem: 'Nenhuma guia foi alterada.' })),
      executar: vi.fn(() => of(undefined)),
    };
    await TestBed.configureTestingModule({ imports: [FusexSpaComponent], providers: [{ provide: FusexSpaService, useValue: service }] }).compileComponents();
    const fixture = TestBed.createComponent(FusexSpaComponent);
    const component = fixture.componentInstance;
    component.alterarTexto('1,1,2');
    component.validar();
    component.confirmado = true;
    component.executar();
    fixture.detectChanges();
    expect(service.validar).toHaveBeenCalledWith('1,2');
    expect(service.executar).not.toHaveBeenCalled();
    const buttons = fixture.nativeElement.querySelectorAll('button');
    expect(buttons[1].disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('2 guia(s) informada(s)');
    component.alterarTexto('3');
    expect(component.validacao).toBeNull();
    expect(component.confirmado).toBe(false);
  });
});
