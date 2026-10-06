import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToolHelpComponent } from './tool-help.component';

describe('ToolHelpComponent', () => {
  let fixture: ComponentFixture<ToolHelpComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ToolHelpComponent] }).compileComponents();
    fixture = TestBed.createComponent(ToolHelpComponent);
    fixture.componentRef.setInput('content', {
      title: 'Ajuda de teste',
      summary: 'Resumo',
      steps: [{ title: 'Passo', description: 'Descrição' }],
      result: 'Resultado',
      status: 'Atual',
    });
    fixture.detectChanges();
  });

  it('abre e fecha o guia sem navegar para outra página', () => {
    const dialog = fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
    const trigger = fixture.nativeElement.querySelector('.help-trigger') as HTMLButtonElement;

    trigger.click();
    expect(dialog.hasAttribute('open')).toBe(true);

    const close = fixture.nativeElement.querySelector('.help-close') as HTMLButtonElement;
    close.click();
    expect(dialog.hasAttribute('open')).toBe(false);
  });
});
