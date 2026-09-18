const fs = require('fs');
const ts = require('./unimed-tools-frontend/node_modules/typescript');
const path = 'unimed-tools-frontend/src/app/pages/relatorios/relatorios.component.ts';
const original = fs.readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
const ast = ts.createSourceFile(path, original, ts.ScriptTarget.Latest, true);
const cls = ast.statements.find(ts.isClassDeclaration);
const groups = {
  'sql-lexico': ['extrairPrimeiraInstrucaoSql','removerComentariosFinaisSql','validarDelimitadoresSql','mascaraSqlSemTextosEComentarios','encontrarFechamentoParentesesSql','normalizarVariaveisBindSql','localizarConsultaPrincipal','tokensSqlNivelZero','removerComentariosSql','inserirClausulaSql'],
  'sql-filtros': ['filtroDetectadoDoSql','inferirTipoFiltroSql','detectarFiltrosFixosSimples','nomeFiltroPorColunaSql','nomeFiltroConhecidoPorColunaSql','criarFiltroDeCondicaoFixa','assinaturaFiltroFixo','extrairLiteraisSql','ehLiteralTextoSql','ehTrechoSqlExecutavel','converterParametrosFixosSql','converterFiltrosFixosCteSql','converterComparacoesFixasWhereCte'],
  'sql-estrutura': ['ajustarEstruturaSqlImportado'],
  'sgu-definicao': ['normalizarFiltros','filtroTecnicoSemFiltros','ehFiltroTecnicoSemFiltros','filtrosDeNegocio','removerFiltroTecnicoDaDefinicao','prepararDefinicaoParaSgu','nomeFiltroSgu','substituirBindSql','clonarDefinicaoApi','validarDefinicaoApi','detectarAliasDuplicadoSql','detectarAliasColunaDuplicadoSql','validarCorrespondenciaBind','filtroVazio'],
};
const members = new Map(cls.members.filter(ts.isMethodDeclaration).map(m => [m.name.getText(ast), m]));
// Falha antes de produzir alterações se algum método não existir.
for (const names of Object.values(groups)) for (const name of names) if (!members.has(name)) throw new Error(name);
const owner = new Map(Object.entries(groups).flatMap(([g,names]) => names.map(n => [n,g])));
const modelNames = ['StatusArquivoSql','FiltroFixoSqlDetectado','ArquivoSqlImportado','TokenSqlNivelZero','EstruturaConsultaPrincipal'];
const modelNodes = ast.statements.filter(n => n.name && modelNames.includes(n.name.getText(ast)));
const models = "import { SguFiltro } from '../../../shared/models/relatorio.model';\n\n" + modelNodes.map(n => 'export ' + n.getText(ast)).join('\n\n') + '\n';
const changes = modelNodes.map(n => ({start:n.getFullStart(), end:n.end, text:''}));
const files = new Map();
const modelImports = code => modelNames.filter(n => new RegExp('\\b'+n+'\\b').test(code));
function importsFor(code, group, prefix) {
  let text = '';
  for (const [other,names] of Object.entries(groups)) {
    const used = names.filter(n => new RegExp('\\b'+n+'\\b').test(code));
    if (other !== group && used.length) text += `import { ${used.join(', ')} } from '${prefix}${other}';\n`;
  }
  return text;
}
for (const [group,names] of Object.entries(groups)) {
  let code = names.map(name => {
    const m = members.get(name);
    changes.push({start:m.getFullStart(), end:m.end, text:''});
    return m.getFullText(ast).replace(/^  /gm,'').replace(/\bprivate\s+/,'export function ');
  }).join('\n');
  code = code.replace(/this\.([A-Za-z0-9_]+)/g, (full,n) => {
    if (owner.has(n) || n === 'nomeFiltroTecnicoSemFiltros' || n === 'gerarId') return n;
    throw new Error('Dependência não separada: '+full);
  });
  let imports = importsFor(code,group,'./');
  const types = ['SguFiltro','SguApiDefinicao'].filter(n => new RegExp('\\b'+n+'\\b').test(code));
  if (types.length) imports += `import { ${types.join(', ')} } from '../../../shared/models/relatorio.model';\n`;
  const modelsUsed = modelImports(code);
  if (modelsUsed.length) imports += `import { ${modelsUsed.join(', ')} } from './sql-importacao.model';\n`;
  if (/\bgerarId\b/.test(code)) code = '\n' + members.get('gerarId').getText(ast).replace('private ', 'function ').replaceAll('this.', '') + '\n' + code;
  if (/\bnomeFiltroTecnicoSemFiltros\b/.test(code)) code = "\nconst nomeFiltroTecnicoSemFiltros = 'filtrotecnico';\n" + code;
  files.set('unimed-tools-frontend/src/app/pages/relatorios/sql/'+group+'.ts',imports+code+'\n');
}
let updated = original;
for (const c of changes.sort((a,b) => b.start-a.start)) updated = updated.slice(0,c.start)+c.text+updated.slice(c.end);
for (const name of owner.keys()) updated = updated.replaceAll('this.'+name+'(', name+'(');
updated = updated.replace(/^  private readonly nomeFiltroTecnicoSemFiltros.*\n/m,'');
updated = updated.replace(/^\s*console\.error\(err\);\n/m,'\n');
const usedModels = modelImports(updated);
updated = importsFor(updated,'component','./sql/') + `import { ${usedModels.join(', ')} } from './sql/sql-importacao.model';\n` + updated;
let patch = '*** Begin Patch\n';
for (const [file,content] of [...files,['unimed-tools-frontend/src/app/pages/relatorios/sql/sql-importacao.model.ts',models]]) {
  if (!file.endsWith('/'+process.argv[2]+'.ts')) continue;
  patch += '*** Add File: '+file+'\n'+content.trimEnd().split('\n').map(l=>'+'+l).join('\n')+'\n';
}
if (process.argv[2] === 'component') {
  patch += '*** Update File: '+path+'\n';
  const oldLines = original.trimEnd().split('\n'), newLines = updated.trimEnd().split('\n');
  let i=0,j=0;
  while(i<oldLines.length || j<newLines.length) {
    if (oldLines[i] === newLines[j]) { i++; j++; continue; }
    const si=i,sj=j;
    let found=false;
    for (let distance=1; distance<6000 && !found; distance++) {
      for (let di=0;di<=distance;di++) {
        const dj=distance-di;
        if (i+di<oldLines.length && j+dj<newLines.length && oldLines[i+di]===newLines[j+dj] && oldLines[i+di].trim().length>5 && oldLines[i+di+1]===newLines[j+dj+1]) {i+=di;j+=dj;found=true;break;}
      }
    }
    if(!found){i=oldLines.length;j=newLines.length;}
    patch += '@@\n' + (si>0?' '+oldLines[si-1]+'\n':'') + oldLines.slice(si,i).map(l=>'-'+l+'\n').join('') + newLines.slice(sj,j).map(l=>'+'+l+'\n').join('') + (i<oldLines.length?' '+oldLines[i]+'\n':'');
  }
}
patch += '*** End Patch';
process.stdout.write(patch);
