/* Pix no painel de pagamento (js/painel/pagamento.js), isolado do navegador:
   - "Gerar outro" reinicia a contagem com o Pix novo (antes ficava em 00:00 com o objeto antigo);
   - pedido de novo Pix recusado com 409 mostra a saída certa, anunciada ao leitor de tela;
   - Pix vencido diz "expirou; se já pagou, aguarde a confirmação".
   Rodar: npm test */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');
const vm=require('node:vm');

/* Carrega o arquivo num contexto com DOM e API falsos; devolve o contexto para o teste. */
function carregar(apiJSON){
  const elementos={};
  const ctx={
    console,Date,Math,Number,String,Promise,JSON,Object,Array,Set,
    route:{sub:'pagar',id:'p1'},
    DEMO_MODE:false,
    $:sel=>elementos[sel]||null,
    render:()=>{ctx.renders=(ctx.renders||0)+1;},
    money:v=>'R$ '+Number(v).toFixed(2),
    esc:s=>String(s),
    ic:()=>'',
    apiJSON,
    toast:()=>{},
    erroLegivel:(e,p)=>(e&&e.message)||p,
    setInterval:(fn)=>{ctx.intervalos.push(fn);return ctx.intervalos.length;},
    clearInterval:()=>{},
    setTimeout:()=>0,clearTimeout:()=>{},
    intervalos:[],elementos
  };
  ctx.window=ctx;ctx.document={getElementById:()=>null};
  vm.createContext(ctx);
  const codigo=fs.readFileSync(path.join(__dirname,'..','js','painel','pagamento.js'),'utf8');
  vm.runInContext(codigo+'\n;this.__ok=1;',ctx);
  return ctx;
}

test('Gerar outro: a contagem usa o Pix novo, não o antigo',async()=>{
  const ctx=carregar(async()=>({}));
  const pixEl={textContent:''};ctx.elementos['#pixExpira']=pixEl;
  const agora=Date.now();
  vm.runInContext("PAGAR={id:'p1',timer:null,pixAtual:null};",ctx);
  /* Pix antigo, com 100 s de validade. */
  vm.runInContext('ligarEsperaPix(a1)',Object.assign(ctx,{a1:{id:'c1',expiraEm:agora+100000}}));
  /* Gerou outro: 1 hora de validade. */
  vm.runInContext('ligarEsperaPix(a2)',Object.assign(ctx,{a2:{id:'c2',expiraEm:agora+3600000}}));
  const tick=ctx.intervalos[ctx.intervalos.length-1];
  await tick();
  assert.match(pixEl.textContent,/^(59|60):\d\d$/,'a contagem mostrou '+pixEl.textContent+' em vez de ~60:00');
});

test('Gerar outro recusado com 409: mensagem de confirmação e botão continua',async()=>{
  const erro409=Object.assign(new Error('Cancelamento pendente'),{status:409});
  const ctx=carregar(async(caminho,metodo)=>{if(metodo==='POST')throw erro409;return {falta:10,aberta:null};});
  vm.runInContext("PAGAR={id:'p1',timer:null,enviando:false,resumo:null,erro:'',pixPendente:false};",ctx);
  await vm.runInContext('gerarPix()',ctx);
  assert.equal(vm.runInContext('PAGAR.pixPendente',ctx),true,'o 409 não foi guardado para a tela');
  const r={falta:10,aberta:{metodo:'pix',id:'c1',expiraEm:Date.now()-1000,valor:10,pixCopiaECola:'x'}};
  const html=vm.runInContext('painelPix(r)',Object.assign(ctx,{r}));
  assert.match(html,/Estamos confirmando o Pix anterior\. Tente de novo em alguns minutos; se você já pagou, aguarde a confirmação\./);
  assert.match(html,/role="alert"/);
  assert.match(html,/onclick="gerarPix\(\)"/,'o botão para gerar outro sumiu');
});

test('Pix vencido diz que expirou e que quem já pagou deve aguardar',()=>{
  const ctx=carregar(async()=>({}));
  vm.runInContext("PAGAR={id:'p1',timer:null,pixPendente:false};",ctx);
  const r={falta:10,aberta:{metodo:'pix',id:'c1',expiraEm:Date.now()-1000,valor:10,pixCopiaECola:'x'}};
  const html=vm.runInContext('painelPix(r)',Object.assign(ctx,{r}));
  assert.match(html,/expirou; se já pagou, aguarde a confirmação/);
});

test('Aparelho com a hora adiantada: Pix ainda válido no servidor continua com o QR',()=>{
  const ctx=carregar(async()=>({}));
  ctx.taskgoDesvioRelogio=-3600000;   // aparelho 1 h à frente do servidor
  const r={falta:10,aberta:{metodo:"pix",id:"c1",expiraEm:0,valor:10,pixCopiaECola:"x"}};
  /* No relógio do aparelho já venceu há 30 min; no do servidor faltam 30 min. */
  r.aberta.expiraEm=Date.now()-1800000;
  const html=vm.runInContext('painelPix(r)',Object.assign(ctx,{r}));
  assert.doesNotMatch(html,/expirou/);
  assert.match(html,/Expira em <b id="pixExpira">(29:5\d|30:00)<\/b>/);
});
