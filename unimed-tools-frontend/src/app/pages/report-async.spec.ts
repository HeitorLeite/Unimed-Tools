import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection, Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComercialComponent } from './comercial/comercial.component';
import { GestaoRiscoComponent } from './gestao-risco/gestao-risco.component';
import { HospitalComponent } from './hospital/hospital.component';

// As respostas chegam depois da primeira renderização. Não usar detectChanges:
// isso esconderia a regressão em que a tela só atualizava após outro clique.
describe.each([
  ['Comercial', ComercialComponent],
  ['Gestão de Risco', GestaoRiscoComponent],
] as const)('%s — atualização assíncrona', (_name, componentType) => {
  let fixture: ComponentFixture<ComercialComponent | GestaoRiscoComponent>;
  let http: HttpTestingController;
  const text = () => fixture.nativeElement.textContent as string;
  const button = (selector: string) =>
    fixture.nativeElement.querySelector(selector) as HTMLButtonElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(
      componentType as Type<ComercialComponent | GestaoRiscoComponent>,
    );
    await fixture.whenStable();
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  async function loadDefinitions(): Promise<void> {
    for (const request of http.match('/api/relatorios/sgu/listar')) {
      request.flush({ content: [{ nome: request.request.body.nome, filtros: [] }] });
    }
    await fixture.whenStable();
    if (fixture.componentInstance instanceof ComercialComponent) {
      const company = fixture.nativeElement.querySelector(
        '.company-option input[type="checkbox"]',
      ) as HTMLInputElement;
      company.click();
      await fixture.whenStable();
    }
  }

  it('carrega definições, mostra cada prévia e encerra o carregamento sem clique extra', async () => {
    expect(text()).toContain('Carregando relatórios disponíveis');
    await loadDefinitions();
    expect(text()).not.toContain('Carregando relatórios disponíveis');
    expect(fixture.nativeElement.querySelector('app-report-preview')).toBeNull();

    button('button.primary, button.generate').click();
    await fixture.whenStable();
    expect(text()).toContain('Consultando');

    for (const [index, report] of fixture.componentInstance.selectedReports.entries()) {
      http
        .expectOne(`/api/relatorios/sgu/executar/${report.api}`)
        .flush({ content: [{ TOTAL_TESTE: 1234 + index }] });
      await Promise.resolve();
      await fixture.whenStable();
      expect(text()).toContain(String(1234 + index));
    }
    expect(button('button.primary, button.generate').disabled).toBe(false);
    expect(text()).not.toContain('Consultando');
  });

  it('distingue resultado vazio de falha e mantém as demais prévias', async () => {
    await loadDefinitions();
    button('button.primary, button.generate').click();
    await fixture.whenStable();
    for (const [index, report] of fixture.componentInstance.selectedReports.entries()) {
      const request = http.expectOne(`/api/relatorios/sgu/executar/${report.api}`);
      if (index === 0)
        request.flush(
          { message: 'Falha simulada na consulta' },
          { status: 503, statusText: 'Unavailable' },
        );
      else request.flush({ content: [] });
      await Promise.resolve();
      await fixture.whenStable();
    }
    expect(text()).toContain('Falha simulada na consulta');
    expect(text()).toContain('Nenhum registro encontrado');
    expect(fixture.nativeElement.querySelector('.start-state, .start')).toBeNull();
    expect(button('button.primary, button.generate').disabled).toBe(false);
  });

  it('destaca o ZIP, evita duplicação e libera a ação após erro sem clique extra', async () => {
    await loadDefinitions();
    const download = button('.batch-download');
    expect(download.classList.contains('btn-primary')).toBe(true);
    download.click();
    await fixture.whenStable();
    expect(download.disabled).toBe(true);
    expect(download.textContent).toContain('Preparando ZIP');
    fixture.componentInstance.downloadAll();
    const request = http.expectOne('/api/relatorios/sgu/exportar-lote');
    expect(request.request.body.itens.length).toBe(
      fixture.componentInstance.selectedReports.length,
    );
    request.flush(new Blob(), { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(download.disabled).toBe(false);
    expect(text()).toContain('Não foi possível gerar o pacote');
  });
});

describe('Hospital — atualização assíncrona', () => {
  let fixture: ComponentFixture<HospitalComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HospitalComponent);
    await fixture.whenStable();
  });
  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('exibe prestador e envia o filtro na prévia e no download', async () => {
    http.expectOne('/api/relatorios/hospital/configuracao').flush({
      colunas: ['PRESTADOR'],
      filtros: [{ id: 'prestador', rotulo: 'Prestador', tipo: 'text', placeholder: 'Digite parte do nome do prestador', opcoes: [] }],
    });
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('.fields input') as HTMLInputElement;
    input.value = 'prestador teste';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.generate') as HTMLButtonElement).click();
    await fixture.whenStable();
    const previa = http.expectOne('/api/relatorios/hospital/executar');
    expect(previa.request.body.filtros).toEqual({ prestador: 'prestador teste' });
    previa.flush({ content: [{ prestador: 'Prestador teste sintético' }], last: true });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('table').textContent).toContain('Prestador teste sintético');
    (fixture.nativeElement.querySelector('.download') as HTMLButtonElement).click();
    await fixture.whenStable();
    const download = http.expectOne('/api/relatorios/hospital/exportar?formato=xlsx');
    expect(download.request.body.filtros).toEqual({ prestador: 'prestador teste' });
    download.flush(new Blob(), { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.clear') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(input.value).toBe('');
  });

  it('exibe configuração, prévia, paginação e erro de exportação sem forçar a renderização', async () => {
    http
      .expectOne('/api/relatorios/hospital/configuracao')
      .flush({ colunas: ['TOTAL_TESTE'], filtros: [] });
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('.generate') as HTMLButtonElement).click();
    await fixture.whenStable();
    http.expectOne('/api/relatorios/hospital/executar').flush({
      content: Array.from({ length: 25 }, (_, i) => ({ TOTAL_TESTE: i + 1 })),
      totalElements: 26,
      last: false,
    });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(25);
    (
      fixture.nativeElement.querySelector('.pagination button:last-child') as HTMLButtonElement
    ).click();
    await fixture.whenStable();
    const nextPage = http.expectOne('/api/relatorios/hospital/executar');
    expect(nextPage.request.body.pagina).toBe(2);
    nextPage.flush({ content: [{ TOTAL_TESTE: 26 }], totalElements: 26, last: true });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Página 2');
    expect(fixture.nativeElement.querySelector('.pagination button:last-child').disabled).toBe(
      true,
    );

    (fixture.nativeElement.querySelector('.download') as HTMLButtonElement).click();
    await fixture.whenStable();
    http
      .expectOne('/api/relatorios/hospital/exportar?formato=xlsx')
      .flush(new Blob(), { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('temporariamente indisponível');
    expect(fixture.nativeElement.querySelector('.download').disabled).toBe(false);
  });

  it('apresenta falha de configuração sem manter o carregamento', async () => {
    http
      .expectOne('/api/relatorios/hospital/configuracao')
      .flush({ message: 'Configuração indisponível' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Configuração indisponível');
    expect(fixture.nativeElement.textContent).not.toContain('Preparando a ferramenta');
  });
});
