/** Testes das convenções compartilhadas de apresentação de arquivos. */
import { formatarTamanhoArquivo } from './file.utils';

describe('formatarTamanhoArquivo', () => {
  it('mantém bytes abaixo de um quilobyte', () => {
    expect(formatarTamanhoArquivo(512)).toBe('512 B');
  });

  it('formata quilobytes e megabytes com uma casa decimal', () => {
    expect(formatarTamanhoArquivo(1536)).toBe('1.5 KB');
    expect(formatarTamanhoArquivo(2 * 1024 * 1024)).toBe('2.0 MB');
  });
});
