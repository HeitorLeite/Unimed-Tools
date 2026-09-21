import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { ToolRegistryService } from './tool-registry.service';

describe('ToolRegistryService', () => {
  let service: ToolRegistryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ToolRegistryService,
        {
          provide: AuthService,
          useValue: {
            user: () => ({
              perfil: 'ADMINISTRADOR',
            }),
            hasPermission: () => true,
          },
        },
      ],
    });

    service = TestBed.inject(ToolRegistryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('aplica nome configurado e oculta ferramenta nativa desativada', () => {
    let completed = false;
    service.refresh().subscribe(() => completed = true);

    http.expectOne('/api/ferramentas').flush([]);
    http.expectOne('/api/ferramentas/nativas').flush([
      {
        id: 'comercial',
        nome: 'Empresas',
        descricao: 'Área comercial configurada pela TI.',
        ativo: true,
        atualizadoEm: '2026-09-18T12:00:00',
      },
      {
        id: 'hospital',
        nome: null,
        descricao: null,
        ativo: false,
        atualizadoEm: '2026-09-18T12:00:00',
      },
    ]);

    expect(completed).toBe(true);
    const tools = service.listAccessible();
    expect(tools.find((tool) => tool.id === 'comercial')?.nome).toBe('Empresas');
    expect(tools.find((tool) => tool.id === 'hospital')).toBeUndefined();
    expect(tools.find((tool) => tool.id === 'ti')).toBeDefined();
  });

  it('atualiza a configuração nativa depois de salvar', () => {
    let ativo = false;
    service.saveNativeConfig(
      'assistencial',
      'Assistência',
      'Descrição configurada.',
      true,
    ).subscribe(() => ativo = true);

    http.expectOne('/api/ferramentas/nativas/assistencial').flush({
      id: 'assistencial',
      nome: 'Assistência',
      descricao: 'Descrição configurada.',
      ativo: true,
      atualizadoEm: '2026-09-18T12:00:00',
    });

    expect(ativo).toBe(true);
    expect(
      service.listNativeAdmin().find((item) => item.tool.id === 'assistencial')?.tool.nome,
    ).toBe('Assistência');
  });
});
