const fs = require('fs');
const ts = require('./unimed-tools-frontend/node_modules/typescript');
const read = p => fs.readFileSync(p,'utf8').replace(/\r\n/g,'\n');
const patch = (p,old,next) => '*** Begin Patch\n'+(old === null ? '*** Add File: '+p+'\n' : '*** Update File: '+p+'\n@@\n'+old.trimEnd().split('\n').map(l=>'-'+l).join('\n')+'\n')+next.trimEnd().split('\n').map(l=>'+'+l).join('\n')+'\n*** End Patch';
const xml = 'unimed-tools-frontend/src/app/shared/services/xml.service.ts';
const auto = 'unimed-tools-frontend/src/app/pages/relatorios/relatorios-automaticos/relatorios-automaticos.component.ts';
if (process.argv[2].startsWith('xml')) {
 const old = read(xml), ast=ts.createSourceFile(xml,old,ts.ScriptTarget.Latest,true), cls=ast.statements.find(ts.isClassDeclaration);
 const names=['criarZip','dataHoraDos','crc32'];
 const members=cls.members.filter(n=>n.name && names.includes(n.name.getText(ast)));
 const zip="import { ArquivoZip } from '../models/xml.models';\n"+members.map(n=>n.getFullText(ast).replace(/^  /gm,'').replace('private ','export function ').replaceAll('this.','')).join('\n');
 if (process.argv[2]==='xml-zip') process.stdout.write(patch('unimed-tools-frontend/src/app/shared/utils/xml-zip.utils.ts',null,zip));
 else {
 let updated=old;
 const nodes=[...ast.statements.filter(ts.isInterfaceDeclaration),...members].sort((a,b)=>b.getFullStart()-a.getFullStart());
 for(const n of nodes) updated=updated.slice(0,n.getFullStart())+updated.slice(n.end);
 updated="import { ArquivoResultado, ArquivoZip, CorretorXmlError, RemovedorBlock, OutrasDespesasVazia } from '../models/xml.models';\nexport type { ArquivoResultado, ArquivoZip, CorretorXmlError, RemovedorBlock, OutrasDespesasVazia } from '../models/xml.models';\nimport { criarZip } from '../utils/xml-zip.utils';\n"+updated.replace('this.criarZip(', 'criarZip(');
 process.stdout.write(patch(xml,old,updated));
 }
} else {
 const old=read(auto), ast=ts.createSourceFile(auto,old,ts.ScriptTarget.Latest,true), cls=ast.statements.find(ts.isClassDeclaration);
 const names=['chaveLogicaFiltro','filtrosNegocio','valoresPreenchidos','nomeCurtoRelatorio','sanitizarNomeArquivo','novoValorFiltro','rotuloChaveFiltro'];
 const members=cls.members.filter(n=>n.name && names.includes(n.name.getText(ast)));
 const interfaces=ast.statements.filter(n=>ts.isInterfaceDeclaration(n)&&n.name.getText(ast)!=='NotificacaoExecucao');
 let updated=old;
 for (const n of [...members,...interfaces].sort((a,b)=>b.getFullStart()-a.getFullStart())) updated=updated.slice(0,n.getFullStart())+updated.slice(n.end);
 for (const name of names) updated=updated.replaceAll('this.'+name+'(',name+'(');
 const start=updated.indexOf('    const mapa = new Map<string, FiltroGrupoExecucao>();');
 const end=updated.indexOf('    this.empresasExecucao = this.filtroEmpresa',start);
 updated=updated.slice(0,start)+'    this.filtrosExecucao = montarFiltrosGrupo(grupo, this.relatorios);\n\n'+updated.slice(end);
 updated="import { ContextoEmpresa, EmpresaGrupoExecucao, FiltroGrupoExecucao, ValorFiltroGrupo } from './grupo-execucao.model';\nimport { montarFiltrosGrupo, "+names.filter(n=>n!=='rotuloChaveFiltro').join(', ')+" } from './grupo-filtros.utils';\n"+updated;
 process.stdout.write(patch(auto,old,updated));
}
