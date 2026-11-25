/* Celular simulado da página inicial (js/pages/celular.js), rodado sem navegador. */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');const vm=require('node:vm');
const raiz=path.join(__dirname,'..');

/* Página mínima: só o que celular.js toca. Os timers ficam numa fila que o teste dispara na mão. */
function montar(){
  const fila=new Map();let id=0;
  const caixa={innerHTML:'',querySelector:()=>null,querySelectorAll:()=>[],contains:()=>false};
  const voz={textContent:''};
  const ctx={
    document:{getElementById:i=>i==='celApp'?caixa:i==='celVoz'?voz:null,querySelector:s=>s==='.cel'?{}:null,activeElement:null},
    setTimeout:(f)=>{fila.set(++id,f);return id;},clearTimeout:(i)=>{fila.delete(i);},
    matchMedia:()=>({matches:true}),ic:()=>'',esc:s=>s,avatar:()=>'',
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(raiz,'js/pages/celular.js'),'utf8'),ctx);
  const disparar=()=>{for(const [i,f] of [...fila]){fila.delete(i);f();}};
  return {ctx,voz,disparar};
}

test('Recomeçar logo depois de concluir não quebra quando a avaliação chega',()=>{
  const {ctx,disparar}=montar();
  vm.runInContext("celIr('pedido','p1');celAceitar();celAvancar();celAvancar();celAvancar();celAvancar();",ctx);
  assert.equal(vm.runInContext('cel.status',ctx),4,'chegou a Concluído');
  vm.runInContext('celRecomecar()',ctx);
  assert.doesNotThrow(disparar,'o timer da avaliação não pode rodar sobre o celular recomeçado');
  assert.equal(vm.runInContext('cel.aceito',ctx),null);
});

test('tocar aba travada avisa e o leitor de tela recebe o aviso',()=>{
  const {ctx,voz}=montar();
  vm.runInContext('celTravada()',ctx);
  assert.match(voz.textContent,/Aceite um pedido/);
});
