import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService } from '../../../shared/services/auth.service';
import { ToolManagerComponent } from './tool-manager.component';

describe('Administração de ferramentas — atualização assíncrona', () => {
  let fixture: ComponentFixture<ToolManagerComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { user: () => ({ perfil: 'ADMINISTRADOR' }), hasPermission: () => true },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ToolManagerComponent);
    await fixture.whenStable();
    for (const request of http.match('/api/ferramentas')) request.flush([]);
    for (const request of http.match('/api/ferramentas/nativas')) request.flush([]);
    await fixture.whenStable();
  });
  afterEach(() => http.verify());

  it('apresenta os filtros carregados e libera a busca ao receber a resposta', async () => {
    const input = fixture.nativeElement.querySelector('.api-field input') as HTMLInputElement;
    input.value = 'api-teste';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.api-field button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Buscando…');
    http.expectOne('/api/relatorios/sgu/listar').flush({
      content: [
        {
          nome: 'api-teste',
          filtros: [
            { nomeFiltro: 'competencia', tipoDadoFiltro: 'NUMBER', obrigatorioFiltro: 'S' },
          ],
        },
      ],
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.filter-config').textContent).toContain(
      'Competencia',
    );
    expect(fixture.nativeElement.querySelector('.api-field button').disabled).toBe(false);
  });

  it('mostra erro ao salvar página nativa e encerra o carregamento', async () => {
    (
      fixture.nativeElement.querySelector('.native-grid footer button') as HTMLButtonElement
    ).click();
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.native-editor .save') as HTMLButtonElement).click();
    await fixture.whenStable();
    const request = http.expectOne(
      (req) => req.method === 'PUT' && req.url.startsWith('/api/ferramentas/nativas/'),
    );
    request.flush(
      { message: 'Falha simulada ao salvar' },
      { status: 503, statusText: 'Unavailable' },
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Falha simulada ao salvar');
    expect(fixture.nativeElement.querySelector('.native-editor .save').disabled).toBe(false);
  });
});
