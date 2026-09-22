import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, throwError } from 'rxjs';
import { RelatorioService } from './relatorio.service';

describe('RelatorioService - falhas temporárias de download', () => {
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
