/* Acessibilidade no navegador (Chrome sem janela): roda o axe-core (WCAG 2.1 AA) nas telas do escopo
   e confere o painel de acessibilidade (classes aplicadas e persistência no navegador).
   Precisa do site em http://localhost:3000 (docker compose up), do Google Chrome e do pacote axe-core (npm install).
   Sem eles, o teste é pulado.
   Rodar: npm test   (outro endereço: TASKGO_SITE=http://... npm test) */
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');

const SITE=process.env.TASKGO_SITE||'http://localhost:3000';
const CHROME=[process.env.CHROME_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome','/usr/bin/chromium','/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'].find(c=>c&&fs.existsSync(c));
const AXE_ARQ=path.join(__dirname,'..','node_modules','axe-core','axe.min.js');
const espera=ms=>new Promise(r=>setTimeout(r,ms));
let chrome,ws,perfil,id=0;const pend={};
let pular=null;

/* Rotas do escopo: públicas, login/cadastro, área do cliente e do profissional (modo demonstração).
   Ficam de fora o painel da equipe e a administração (fora do escopo desta rodada). */
const ROTAS=['#/home','#/servicos','#/como-funciona','#/profissionais','#/planos','#/sobre','#/time','#/faq','#/ajuda',
  '#/contato','#/seguranca','#/entrar','#/acessibilidade','#/legal/termos','#/legal/privacidade','#/contratar',
  '?demo=1&role=cliente#/app/inicio','?demo=1&role=cliente#/app/contratar','?demo=1&role=cliente#/app/pedidos',
  '?demo=1&role=cliente#/app/mensagens','?demo=1&role=cliente#/painel/config',
  '?demo=1&role=profissional#/app/inicio','?demo=1&role=profissional#/app/oportunidades','?demo=1&role=profissional#/app/agenda'];

async function cmd(method,params={}){return new Promise(r=>{const i=++id;pend[i]=r;ws.send(JSON.stringify({id:i,method,params}));});}
async function js(expr){const r=await cmd('Runtime.evaluate',{expression:expr,awaitPromise:true,returnByValue:true});if(r.result.exceptionDetails)throw new Error(r.result.exceptionDetails.exception?.description||r.result.exceptionDetails.text);return r.result.result.value;}
async function ir(url){
  /* 2,6 s: as animações de entrada do topo (até 1,4 s) terminam antes da medição de contraste. */
  await cmd('Page.navigate',{url:SITE+'/index.html'+url});await espera(2600);
  for(let i=0;i<100;i++){if(await js("document.readyState==='complete'&&document.body.innerText.trim().length>0"))return;await espera(200);}
  throw new Error('a página não desenhou em 20 s: '+url);
}

before(async()=>{
  if(!CHROME){pular='Google Chrome não encontrado (defina CHROME_PATH).';return;}
  if(!fs.existsSync(AXE_ARQ)){pular='axe-core não instalado (rode npm install).';return;}
  try{const r=await fetch(SITE,{signal:AbortSignal.timeout(3000)});if(!r.ok)throw 0;}catch{pular='Site fora do ar em '+SITE+' (rode docker compose up).';return;}
  const porta=9400+Math.floor(Math.random()*400);
  perfil=fs.mkdtempSync(path.join(os.tmpdir(),'taskgo-chrome-'));
  chrome=spawn(CHROME,['--headless=new','--remote-debugging-port='+porta,'--user-data-dir='+perfil,'--window-size=1366,900','--no-first-run','about:blank'],{stdio:'ignore'});
  let alvo;for(let i=0;i<60&&!alvo;i++){try{alvo=(await (await fetch('http://127.0.0.1:'+porta+'/json')).json()).find(t=>t.type==='page');}catch{}if(!alvo)await espera(250);}
  ws=new WebSocket(alvo.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
  ws.onmessage=m=>{const d=JSON.parse(m.data);if(d.id&&pend[d.id]){pend[d.id](d);delete pend[d.id];}};
  await cmd('Runtime.enable');await cmd('Page.enable');
});

after(async()=>{try{ws&&ws.close();}catch{}if(chrome){chrome.kill();await espera(400);}try{perfil&&fs.rmSync(perfil,{recursive:true,force:true});}catch{}});

test('axe-core sem violações WCAG 2.1 AA nas telas do escopo',{timeout:600000},async t=>{
  if(pular){t.skip(pular);return;}
  const axe=fs.readFileSync(AXE_ARQ,'utf8');
  const problemas=[];
  for(const rota of ROTAS){
    await ir(rota.startsWith('?')?rota:'#'+rota.slice(1).replace(/^#/,'')); /* aceita "#/rota" ou "?demo=1...#/rota" */
    await js(axe);
    const r=await js("axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21a','wcag21aa']}}).then(r=>r.violations.map(v=>({id:v.id,nos:v.nodes.length,alvo:(v.nodes[0]&&v.nodes[0].target[0])||''})))");
    for(const v of r)problemas.push(rota+' -> '+v.id+' ('+v.nos+' elementos, ex.: '+v.alvo+')');
  }
  assert.deepEqual(problemas,[],'violações encontradas:\n'+problemas.join('\n'));
});

test('painel de acessibilidade: classes aplicadas e preferências mantidas após recarregar',{timeout:120000},async t=>{
  if(pular){t.skip(pular);return;}
  await ir('#/home');
  await js("localStorage.removeItem('taskgo-acessibilidade')");
  await js("a11yAcao('aumentar');a11yAcao('contraste');a11yAcao('semAnimacao');a11yAcao('links');a11yAcao('espaco')");
  const marcadas=await js("['a11y-contraste','a11y-sem-animacao','a11y-links','a11y-espaco'].every(c=>document.documentElement.classList.contains(c))");
  assert.equal(marcadas,true,'classes do painel não foram aplicadas');
  assert.equal(await js("document.documentElement.style.fontSize"),'115%');
  await cmd('Page.reload');await espera(1500);
  /* Depois de recarregar, a escolha vem do localStorage e já está aplicada (a aplicação é feita no <head>). */
  assert.equal(await js("document.documentElement.classList.contains('a11y-contraste')"),true,'alto contraste não foi mantido');
  assert.equal(await js("document.documentElement.style.fontSize"),'115%','tamanho do texto não foi mantido');
  await js("a11yAcao('restaurar')");
  assert.equal(await js("document.documentElement.classList.contains('a11y-contraste')"),false,'restaurar não limpou as classes');
  assert.equal(await js("localStorage.getItem('taskgo-acessibilidade')") !== null, true);
});
