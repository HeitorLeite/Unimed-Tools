import { Component, Input, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RelatorioService } from '../../shared/services/relatorio.service';
import { RelatoriosManualComponent } from '../relatorios/relatorios-manual/relatorios-manual.component';
import { RelatoriosAutomaticosComponent } from '../relatorios/relatorios-automaticos/relatorios-automaticos.component';
import { ToolManagerComponent } from './tool-manager/tool-manager.component';
import { TiComponent } from './ti.component';

@Component({ selector: 'app-relatorios-manual', template: '' })
class ManualStub { @Input() embedded = false; }
@Component({ selector: 'app-relatorios-automaticos', template: '' })
class GruposStub { @Input() embedded = false; @Input() relatorios: unknown[] = []; }
@Component({ selector: 'app-tool-manager', template: '' })
class FerramentasStub {}

describe('TI — navegação', () => {
  it('exibe uma seção por vez e atualiza o catálogo ao abrir grupos', async () => {
    const listarCatalogo = vi.fn(() => []);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: RelatorioService, useValue: { listarCatalogo } },
      ],
    }).overrideComponent(TiComponent, {
      remove: { imports: [RelatoriosManualComponent, RelatoriosAutomaticosComponent, ToolManagerComponent] },
      add: { imports: [ManualStub, GruposStub, FerramentasStub] },
    });
    const fixture = TestBed.createComponent(TiComponent);
    await fixture.whenStable();
    const buttons = fixture.nativeElement.querySelectorAll('.admin-nav button') as NodeListOf<HTMLButtonElement>;
    expect(buttons[0].getAttribute('aria-current')).toBe('page');
    expect(fixture.nativeElement.querySelector('app-relatorios-manual')).not.toBeNull();
    buttons[1].click();
    await fixture.whenStable();
    expect(listarCatalogo).toHaveBeenCalledTimes(2);
    expect(buttons[1].getAttribute('aria-current')).toBe('page');
    expect(fixture.nativeElement.querySelector('app-relatorios-manual')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-relatorios-automaticos')).not.toBeNull();
    buttons[2].click();
    await fixture.whenStable();
    expect(buttons[2].getAttribute('aria-current')).toBe('page');
    expect(fixture.nativeElement.querySelector('app-relatorios-automaticos')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-tool-manager')).not.toBeNull();
  });
});
