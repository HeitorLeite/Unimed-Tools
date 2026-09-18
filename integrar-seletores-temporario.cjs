const fs=require('fs');
const dir='unimed-tools-frontend/src/app/pages/relatorios/relatorios-personalizados/';
const ext=process.argv[2];
const p=dir+'relatorios-personalizados.component.'+ext;
const old=fs.readFileSync(p,'utf8').replace(/\r\n/g,'\n');
let next=old;
if(ext==='html') {
 const start=next.indexOf('          <div class="filter-group"');
 const end=next.indexOf('        </div>\n      </section>',start);
 next=next.slice(0,start)+`          <app-filtros-relatorio [filtros]="configuracao.filtros" [valores]="valoresFiltro"
            [bloqueado]="operacaoRelatorioEmAndamento" [versaoLimpeza]="versaoLimpezaFiltros"
            (valoresChange)="atualizarFiltros($event)" />\n`+next.slice(end);
 const startCols=next.indexOf('          <section class="selected-column-order"');
 const endCols=next.indexOf('        </div>\n      </aside>',startCols);
 next=next.slice(0,startCols)+`          <app-colunas-relatorio [colunas]="configuracao.colunas" [ordem]="ordemColunasSelecionadas"
            [maximo]="configuracao.limites.maximoColunas" [bloqueado]="operacaoRelatorioEmAndamento"
            (alternar)="alternarColuna($event)" (alternarGrupo)="alternarGrupo($event)"
            (mover)="moverColuna($event.id, $event.deslocamento)" />\n`+next.slice(endCols);
} else {
 next=next.slice(0,next.indexOf('.filter-group +'))+next.slice(next.indexOf('.distinct-option {'));
 next=next.slice(0,next.indexOf('.selected-column-order {'))+next.slice(next.indexOf('.result-panel {'));
 next=next.replace(/  \.column-groups \{[^}]*\}\n  \.column-group \+ \.column-group \{[^}]*\}\n/,'');
 next=next.replace(/  \.fields-grid,\n  \.column-groups \{[^}]*\}\n/,'');
}
process.stdout.write('*** Begin Patch\n*** Update File: '+p+'\n@@\n'+old.trimEnd().split('\n').map(l=>'-'+l).join('\n')+'\n'+next.trimEnd().split('\n').map(l=>'+'+l).join('\n')+'\n*** End Patch');
