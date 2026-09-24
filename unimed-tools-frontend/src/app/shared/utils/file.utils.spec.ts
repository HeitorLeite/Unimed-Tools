import { baixarBlob, formatarTamanhoArquivo } from './file.utils';

describe('formatarTamanhoArquivo', () => {
  it('mantém bytes abaixo de um quilobyte', () => {
    expect(formatarTamanhoArquivo(512)).toBe('512 B');
  });
  it('formata quilobytes e megabytes com uma casa decimal', () => {
    expect(formatarTamanhoArquivo(1536)).toBe('1.5 KB');
    expect(formatarTamanhoArquivo(2 * 1024 * 1024)).toBe('2.0 MB');
  });
});

describe('download no navegador', () => {
  it('anexa o link e preserva a URL enquanto o navegador inicia a transferência', () => {
    vi.useFakeTimers();
    const criar = vi.fn(() => 'blob:teste');
    const revogar = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: criar });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revogar });
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.isConnected).toBe(true);
      expect(this.download).toBe('relatorio.csv');
    });
    try {
      baixarBlob(new Blob(['ID\r\n001']), 'relatorio.csv');
      expect(clique).toHaveBeenCalledOnce();
      expect(revogar).not.toHaveBeenCalled();
      vi.advanceTimersByTime(60_000);
      expect(revogar).toHaveBeenCalledWith('blob:teste');
    } finally {
      clique.mockRestore();
      vi.useRealTimers();
    }
  });
});
