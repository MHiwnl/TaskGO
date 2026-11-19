/* O catálogo do site (js/core/catalogo.js) e o do servidor (CatalogoServicos.java) têm os mesmos serviços. */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');const vm=require('node:vm');
const raiz=path.join(__dirname,'..');
test('catálogo amplo, igual no site e no servidor',()=>{
  const ctx={};vm.createContext(ctx);vm.runInContext(fs.readFileSync(path.join(raiz,'js/core/catalogo.js'),'utf8')+';this.S=SERVICES;this.G=GRUPOS_SERVICOS;this.T=TERMOS_SERVICO;',ctx);
  const java=fs.readFileSync(path.join(raiz,'backend/src/main/java/com/taskgo/api/CatalogoServicos.java'),'utf8');
  const idsJava=[...java.matchAll(/add\("([^"]+)"/g)].map(m=>m[1]);
  const idsSite=JSON.parse(JSON.stringify(ctx.S.map(s=>s.id)));
  assert.ok(idsSite.length>=100,'pelo menos 100 serviços');
  assert.deepEqual(idsSite,idsJava);
  assert.equal(new Set(idsSite).size,idsSite.length,'ids repetidos');
  assert.deepEqual(JSON.parse(JSON.stringify(ctx.G.flatMap(g=>g[1]))),idsSite,'todo serviço está num grupo');
  for(const s of ctx.S){assert.ok(ctx.T[s.id]&&ctx.T[s.id].length,'termos de busca de '+s.id);assert.ok(s.min>0&&s.max>=s.min,'faixa de '+s.id);}
  for(const id of ['eletrica','ar','hidraulica','limpeza','pintura','montagem','reforma','jardim','marido','eletro','frete','dedetiza','ti','cftv'])assert.ok(idsSite.includes(id),'serviço antigo mantido: '+id);
});

test('todo serviço do catálogo tem exemplo de descrição próprio',()=>{
  const ctx={};vm.createContext(ctx);vm.runInContext(fs.readFileSync(path.join(raiz,'js/core/catalogo.js'),'utf8')+';'+fs.readFileSync(path.join(raiz,'js/core/exemplos.js'),'utf8')+';this.S=SERVICES;this.E=EXEMPLO_SERVICO;',ctx);
  const ex=Object.values(ctx.E);
  for(const s of ctx.S)assert.ok(ctx.E[s.id]&&ctx.E[s.id].startsWith('Ex.: '),'exemplo de '+s.id);
  assert.equal(new Set(ex).size,ex.length,'exemplos repetidos');
});
