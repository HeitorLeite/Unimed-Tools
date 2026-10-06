import { ArquivoZip } from '../models/xml.models';


export function criarZip(arquivos: ArquivoZip[]): Blob {
  if (arquivos.length > 65535) {
    throw new Error('O lote possui arquivos demais para um ZIP padrão.');
  }

  const encoder = new TextEncoder();
  const registrosLocais: Uint8Array[] = [];
  const registrosCentrais: Uint8Array[] = [];
  const agora = new Date();
  const { dataDos, horaDos } = dataHoraDos(agora);
  let deslocamentoLocal = 0;
  let tamanhoCentral = 0;

  for (const arquivo of arquivos) {
    const nomeBytes = encoder.encode(arquivo.nome);
    const conteudoBytes = encoder.encode(arquivo.conteudo);
    const crc = crc32(conteudoBytes);

    // Cabeçalho local do arquivo — método 0 (armazenado, sem compressão).
    const local = new Uint8Array(30 + nomeBytes.length + conteudoBytes.length);
    const localView = new DataView(local.buffer);
    localView.setUint32(0, 0x04034b50, true);
    localView.setUint16(4, 20, true);
    localView.setUint16(6, 0x0800, true); // Nome em UTF-8.
    localView.setUint16(8, 0, true);
    localView.setUint16(10, horaDos, true);
    localView.setUint16(12, dataDos, true);
    localView.setUint32(14, crc, true);
    localView.setUint32(18, conteudoBytes.length, true);
    localView.setUint32(22, conteudoBytes.length, true);
    localView.setUint16(26, nomeBytes.length, true);
    localView.setUint16(28, 0, true);
    local.set(nomeBytes, 30);
    local.set(conteudoBytes, 30 + nomeBytes.length);
    registrosLocais.push(local);

    // Entrada correspondente no diretório central.
    const central = new Uint8Array(46 + nomeBytes.length);
    const centralView = new DataView(central.buffer);
    centralView.setUint32(0, 0x02014b50, true);
    centralView.setUint16(4, 20, true);
    centralView.setUint16(6, 20, true);
    centralView.setUint16(8, 0x0800, true);
    centralView.setUint16(10, 0, true);
    centralView.setUint16(12, horaDos, true);
    centralView.setUint16(14, dataDos, true);
    centralView.setUint32(16, crc, true);
    centralView.setUint32(20, conteudoBytes.length, true);
    centralView.setUint32(24, conteudoBytes.length, true);
    centralView.setUint16(28, nomeBytes.length, true);
    centralView.setUint16(30, 0, true);
    centralView.setUint16(32, 0, true);
    centralView.setUint16(34, 0, true);
    centralView.setUint16(36, 0, true);
    centralView.setUint32(38, 0, true);
    centralView.setUint32(42, deslocamentoLocal, true);
    central.set(nomeBytes, 46);
    registrosCentrais.push(central);

    deslocamentoLocal += local.length;
    tamanhoCentral += central.length;
  }

  const fimDiretorio = new Uint8Array(22);
  const fimView = new DataView(fimDiretorio.buffer);
  fimView.setUint32(0, 0x06054b50, true);
  fimView.setUint16(4, 0, true);
  fimView.setUint16(6, 0, true);
  fimView.setUint16(8, arquivos.length, true);
  fimView.setUint16(10, arquivos.length, true);
  fimView.setUint32(12, tamanhoCentral, true);
  fimView.setUint32(16, deslocamentoLocal, true);
  fimView.setUint16(20, 0, true);

  const tamanhoTotal = deslocamentoLocal + tamanhoCentral + fimDiretorio.length;
  const zip = new Uint8Array(tamanhoTotal);
  let cursor = 0;

  for (const registro of registrosLocais) {
    zip.set(registro, cursor);
    cursor += registro.length;
  }

  for (const registro of registrosCentrais) {
    zip.set(registro, cursor);
    cursor += registro.length;
  }

  zip.set(fimDiretorio, cursor);
  return new Blob([zip.buffer], { type: 'application/zip' });
}


export function dataHoraDos(data: Date): { dataDos: number; horaDos: number } {
  const ano = Math.min(Math.max(data.getFullYear(), 1980), 2107);
  const dataDos = ((ano - 1980) << 9) | ((data.getMonth() + 1) << 5) | data.getDate();
  const horaDos =
    (data.getHours() << 11) | (data.getMinutes() << 5) | Math.floor(data.getSeconds() / 2);

  return { dataDos, horaDos };
}


export function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;

  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) {
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    }
  }

  return (crc ^ 0xffffffff) >>> 0;
}
