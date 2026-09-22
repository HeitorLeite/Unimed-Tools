import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../shared/services/auth.service';
import { CustomReportComponent } from './custom-report.component';

describe('Ferramenta configurável — atualização assíncrona', () => {
  let fixture: ComponentFixture<CustomReportComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { user: () => null, hasPermission: () => true } },
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'teste' } } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CustomReportComponent);
    await fixture.whenStable();
    http
      .expectOne('/api/ferramentas')
      .flush([
        {
          id: 1,
          slug: 'teste',
          nome: 'Relatório de teste',
          descricao: 'Dados sintéticos',
          apiNome: 'teste',
          filtros: [],
          colunasPreview: [],
        },
      ]);
    http.expectOne('/api/ferramentas/nativas').flush([]);
    await fixture.whenStable();
  });
  afterEach(() => http.verify());

  it('mostra erro ao falhar a API de uma ferramenta existente', async () => {
    http
      .expectOne('/api/relatorios/sgu/listar')
      .flush({ message: 'API indisponível' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('API indisponível');
    expect(fixture.nativeElement.textContent).not.toContain('Carregando a configuração');
  });

  it('atualiza a prévia e o fim da exportação sem interação adicional', async () => {
    http
      .expectOne('/api/relatorios/sgu/listar')
      .flush({ content: [{ nome: 'teste', filtros: [] }] });
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.generate') as HTMLButtonElement).click();
    await fixture.whenStable();
    http
      .expectOne('/api/relatorios/sgu/executar/teste')
      .flush({ content: [{ TOTAL_TESTE: 1234 }] });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('1234');
    expect(fixture.nativeElement.textContent).not.toContain('Consultando o relatório');
    (fixture.nativeElement.querySelector('.download') as HTMLButtonElement).click();
    await fixture.whenStable();
    http
      .expectOne('/api/relatorios/sgu/exportar/teste?formato=xlsx')
      .flush(new Blob(), { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('temporariamente indisponível');
    expect(fixture.nativeElement.textContent).toContain('tente novamente');
    expect(fixture.nativeElement.querySelector('.download').disabled).toBe(false);
  });
});
