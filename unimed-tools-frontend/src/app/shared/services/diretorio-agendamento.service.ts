import { Injectable } from '@angular/core';

type PermissaoDiretorio = 'granted' | 'denied' | 'prompt';

interface ArquivoGravavelLocal {
  write(dados: Blob): Promise<void>;
  close(): Promise<void>;
  abort(): Promise<void>;
}

interface ArquivoHandleLocal {
  createWritable(opcoes?: { keepExistingData?: boolean }): Promise<ArquivoGravavelLocal>;
}

interface DiretorioHandleLocal {
  readonly kind: 'directory';
  readonly name: string;
  queryPermission(opcoes: { mode: 'readwrite' }): Promise<PermissaoDiretorio>;
  requestPermission(opcoes: { mode: 'readwrite' }): Promise<PermissaoDiretorio>;
  getFileHandle(nome: string, opcoes?: { create?: boolean }): Promise<ArquivoHandleLocal>;
  removeEntry?(nome: string): Promise<void>;
}

interface JanelaComSeletorDiretorio extends Window {
  showDirectoryPicker?: (opcoes: { mode: 'readwrite' }) => Promise<DiretorioHandleLocal>;
}

export interface DiretorioAgendamentoSelecionado {
  referencia: string;
  nome: string;
}

export class DiretorioAgendamentoErro extends Error {
  constructor(
    readonly codigo:
      | 'PASTA_INACESSIVEL'
      | 'PERMISSAO_REVOGADA'
      | 'ARQUIVO_JA_EXISTE'
      | 'GRAVACAO_FALHOU',
    message: string,
  ) {
    super(message);
  }
}

/** Mantém o handle somente no IndexedDB deste navegador; o caminho nunca vai ao backend. */
@Injectable({ providedIn: 'root' })
export class DiretorioAgendamentoService {
  private readonly bancoNome = 'unimed-tools-report-directories-v1';
  private readonly armazenamento = 'diretorios';

  suportado(): boolean {
    return (
      typeof window !== 'undefined' &&
      typeof indexedDB !== 'undefined' &&
      typeof (window as JanelaComSeletorDiretorio).showDirectoryPicker === 'function'
    );
  }

  async selecionar(): Promise<DiretorioAgendamentoSelecionado> {
    const seletor = (window as JanelaComSeletorDiretorio).showDirectoryPicker;
    if (!this.suportado() || !seletor) {
      throw new DiretorioAgendamentoErro(
        'PASTA_INACESSIVEL',
        'Este navegador não permite selecionar pastas. Use uma versão atual do Chrome ou Edge.',
      );
    }

    const handle = await seletor({ mode: 'readwrite' });
    const permissao = await handle.requestPermission({ mode: 'readwrite' });
    if (permissao !== 'granted') {
      throw new DiretorioAgendamentoErro(
        'PERMISSAO_REVOGADA',
        'A permissão de gravação na pasta não foi concedida.',
      );
    }

    const referencia = crypto.randomUUID();
    await this.salvarHandle(referencia, handle);
    return { referencia, nome: handle.name };
  }

  async escrever(referencia: string, nomeArquivo: string, conteudo: Blob): Promise<void> {
    const diretorio = await this.buscarHandle(referencia);
    if (!diretorio) {
      throw new DiretorioAgendamentoErro(
        'PASTA_INACESSIVEL',
        'A referência da pasta não existe neste navegador.',
      );
    }

    const permissao = await diretorio.queryPermission({ mode: 'readwrite' });
    if (permissao !== 'granted') {
      throw new DiretorioAgendamentoErro(
        'PERMISSAO_REVOGADA',
        'A permissão da pasta precisa ser concedida novamente.',
      );
    }

    try {
      await diretorio.getFileHandle(nomeArquivo);
      throw new DiretorioAgendamentoErro(
        'ARQUIVO_JA_EXISTE',
        'Já existe um arquivo com esse nome na pasta selecionada.',
      );
    } catch (erro) {
      if (erro instanceof DiretorioAgendamentoErro) throw erro;
      if (!(erro instanceof DOMException) || erro.name !== 'NotFoundError') {
        throw new DiretorioAgendamentoErro(
          'PASTA_INACESSIVEL',
          'Não foi possível consultar a pasta selecionada.',
        );
      }
    }

    let gravacao: ArquivoGravavelLocal | null = null;
    let criado = false;
    try {
      const arquivo = await diretorio.getFileHandle(nomeArquivo, { create: true });
      criado = true;
      gravacao = await arquivo.createWritable({ keepExistingData: false });
      await gravacao.write(conteudo);
      await gravacao.close();
    } catch (erro) {
      try {
        await gravacao?.abort();
        if (criado && diretorio.removeEntry) await diretorio.removeEntry(nomeArquivo);
      } catch {
        // A limpeza é uma tentativa segura; o erro original continua sendo reportado.
      }
      throw new DiretorioAgendamentoErro(
        'GRAVACAO_FALHOU',
        'Não foi possível gravar o arquivo na pasta selecionada.',
      );
    }
  }

  private async salvarHandle(referencia: string, handle: DiretorioHandleLocal): Promise<void> {
    const banco = await this.abrirBanco();
    await new Promise<void>((resolve, reject) => {
      const transacao = banco.transaction(this.armazenamento, 'readwrite');
      transacao.objectStore(this.armazenamento).put(handle, referencia);
      transacao.oncomplete = () => resolve();
      transacao.onerror = () => reject(transacao.error);
      transacao.onabort = () => reject(transacao.error);
    });
    banco.close();
  }

  private async buscarHandle(referencia: string): Promise<DiretorioHandleLocal | null> {
    if (typeof indexedDB === 'undefined') return null;
    const banco = await this.abrirBanco();
    const resultado = await new Promise<DiretorioHandleLocal | undefined>((resolve, reject) => {
      const requisicao = banco
        .transaction(this.armazenamento, 'readonly')
        .objectStore(this.armazenamento)
        .get(referencia);
      requisicao.onsuccess = () => resolve(requisicao.result as DiretorioHandleLocal | undefined);
      requisicao.onerror = () => reject(requisicao.error);
    });
    banco.close();
    return resultado ?? null;
  }

  private abrirBanco(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const requisicao = indexedDB.open(this.bancoNome, 1);
      requisicao.onupgradeneeded = () => {
        if (!requisicao.result.objectStoreNames.contains(this.armazenamento)) {
          requisicao.result.createObjectStore(this.armazenamento);
        }
      };
      requisicao.onsuccess = () => resolve(requisicao.result);
      requisicao.onerror = () => reject(requisicao.error);
    });
  }
}
