import { HttpClient, HttpErrorResponse, HttpResponse, HttpEventType } from '@angular/common/http';
import { firstValueFrom, lastValueFrom, of, throwError, toArray } from 'rxjs';
import { RelatorioService } from './relatorio.service';

describe('RelatorioService - falhas temporárias de download', () => {
  it.each(['csv', 'txt', 'xlsx'] as const)('aceita o arquivo %s somente no evento final', async (formato) => {
    const blob = new Blob([formato === 'xlsx' ? new Uint8Array([80, 75, 3, 4, 1]) : 'ID;VALOR\r\n001;1,50\r\n']);
    const resposta = new HttpResponse({ body: blob });
    const service = new RelatorioService({ post: () => of({ type: HttpEventType.Sent }, resposta) } as unknown as HttpClient);
    const eventos = await lastValueFrom(service.exportar('teste', formato, {}, 'teste').pipe(toArray()));
    expect(eventos).toEqual([{ type: HttpEventType.Sent }, resposta]);
  });

  it.each(['csv', 'txt', 'xlsx'] as const)('rejeita marca de codificação sem registros em %s', async (formato) => {
    const service = new RelatorioService({ post: () => of(new HttpResponse({ body: new Blob(['\uFEFF']) })) } as unknown as HttpClient);
    await expect(lastValueFrom(service.exportar('teste', formato, {}, 'teste'))).rejects.toThrow();
  });

  it('decodifica erros JSON recebidos como Blob no Assistencial, Hospital e lote', async () => {
    const erro = new HttpErrorResponse({ status: 422,
      error: new Blob([JSON.stringify({ codigo: 'ORDENACAO_AUSENTE', message: 'Revise a ordenação da API.' })], { type: 'application/json' }) });
    const service = new RelatorioService({ post: () => throwError(() => erro) } as unknown as HttpClient);
    const request: any = {};
    for (const consulta of [service.exportarPersonalizado('csv', request), service.exportarHospital('txt', request), service.exportarLote(request)]) {
      await expect(lastValueFrom(consulta)).rejects.toMatchObject({ status: 422, error: { message: 'Revise a ordenação da API.' } });
    }
  });

  it('não salva HTML recebido com HTTP 200 como planilha', async () => {
    const service = new RelatorioService({ post: () => of(new HttpResponse({ body: new Blob(['<html>erro</html>'], { type: 'text/html' }) })) } as unknown as HttpClient);
    await expect(lastValueFrom(service.exportar('teste', 'xlsx', {}, 'teste'))).rejects.toThrow('relatório válido');
  });
  it.each([502, 503, 504])('traduz HTTP %s sem expor HTML e sem repetir o download inteiro', async (status) => {
    const post = vi.fn(() => throwError(() => new HttpErrorResponse({
      status, error: new Blob(['<html>detalhe-interno</html>'], { type: 'text/html' }),
    })));
    const service = new RelatorioService({ post } as unknown as HttpClient);
    await expect(firstValueFrom(service.exportar('api-teste', 'xlsx', {}, 'teste')))
      .rejects.toMatchObject({ status, error: { message: expect.stringContaining('tente novamente') } });
    expect(post).toHaveBeenCalledTimes(1);
  });

  it('preserva erros que não são temporários', async () => {
    const erro = new HttpErrorResponse({ status: 400, error: { message: 'Filtro inválido.' } });
    const service = new RelatorioService({ post: () => throwError(() => erro) } as unknown as HttpClient);
    await expect(firstValueFrom(service.exportar('api-teste', 'xlsx', {}, 'teste'))).rejects.toBe(erro);
  });
});
