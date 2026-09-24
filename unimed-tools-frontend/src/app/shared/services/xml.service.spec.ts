/** Testes de regressão das regras XML executadas localmente no navegador. */
import { TestBed } from '@angular/core/testing';

import { XmlService } from './xml.service';

describe('XmlService', () => {
  let service: XmlService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(XmlService);
  });

  it('remove o prefixo TISS e diferencia guias repetidas entre arquivos', async () => {
    const criarXml = () => `
      <ans:guia>
        <ans:numeroGuiaPrestador>123</ans:numeroGuiaPrestador>
        <ans:codigoTabela>00</ans:codigoTabela>
        <ans:codigoProcedimento>181234</ans:codigoProcedimento>
      </ans:guia>`;
    const arquivos = [
      new File([criarXml()], 'primeiro.xml', { type: 'application/xml' }),
      new File([criarXml()], 'segundo.xml', { type: 'application/xml' }),
    ];

    const resultados = await service.processarLote(arquivos, true, false);

    expect(resultados[0].guiasRenomeadas).toEqual([{ original: '123', novo: '123a' }]);
    expect(resultados[1].guiasRenomeadas).toEqual([{ original: '123', novo: '123b' }]);
    expect(resultados[0].prefixos[0]).toMatchObject({ original: '181234', corrected: '1234' });
    expect(resultados[0].correctedContent).toContain(
      '<ans:codigoProcedimento>1234</ans:codigoProcedimento>',
    );
  });

  it('remove despesas zeradas e o contêiner que fica vazio', async () => {
    const xml = `
      <ans:guia>
        <ans:numeroGuiaPrestador>GUIA-1</ans:numeroGuiaPrestador>
        <ans:outrasDespesas>
          <ans:despesa>
            <ans:dataExecucao>2026-08-05</ans:dataExecucao>
            <ans:codigoTabela>00</ans:codigoTabela>
            <ans:codigoProcedimento>1234</ans:codigoProcedimento>
            <ans:descricaoProcedimento>Teste</ans:descricaoProcedimento>
            <ans:valorTotal>0.00</ans:valorTotal>
          </ans:despesa>
        </ans:outrasDespesas>
      </ans:guia>`;

    const [resultado] = await service.processarLote(
      [new File([xml], 'despesas.xml', { type: 'application/xml' })],
      false,
      true,
    );

    expect(resultado.blocos).toHaveLength(1);
    expect(resultado.outrasDespesasVazias).toHaveLength(1);
    expect(resultado.outrasDespesasVazias[0].numeroGuia).toBe('GUIA-1');
    expect(resultado.correctedContent).not.toContain('<ans:despesa>');
    expect(resultado.correctedContent).not.toContain('<ans:outrasDespesas>');
  });

  it('preserva despesas com valor diferente de zero', () => {
    const xml = `
      <ans:despesa>
        <ans:valorTotal>10.50</ans:valorTotal>
      </ans:despesa>`;

    const resultado = service.analisarRemovedor(xml);

    expect(resultado.blocks).toHaveLength(0);
    expect(resultado.correctedContent).toContain('<ans:valorTotal>10.50</ans:valorTotal>');
  });
});
