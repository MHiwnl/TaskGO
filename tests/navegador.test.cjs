/* Site no navegador (Chrome sem janela): percorre as telas no modo demonstração, que não depende do banco.
   Confere conteúdo, erros de JavaScript e se o layout cabe no celular (sem rolagem lateral).
   Precisa do site em http://localhost:3000 (docker compose up) e do Google Chrome. Sem eles, o teste é pulado.
   Rodar: npm test   (outro endereço: TASKGO_SITE=http://... npm test) */
const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');

const SITE=process.env.TASKGO_SITE||'http://localhost:3000';
const CHROME=[process.env.CHROME_PATH,'C:/Program Files/Google/Chrome/Application/chrome.exe','C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome','/usr/bin/chromium','/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'].find(c=>c&&fs.existsSync(c));
const espera=ms=>new Promise(r=>setTimeout(r,ms));
let chrome,ws,perfil,erros=[],id=0;const pend={};
let pular=null;

async function cmd(method,params={}){return new Promise(r=>{const i=++id;pend[i]=r;ws.send(JSON.stringify({id:i,method,params}));});}
async function js(expr){const r=await cmd('Runtime.evaluate',{expression:expr,awaitPromise:true,returnByValue:true});if(r.result.exceptionDetails)throw new Error(r.result.exceptionDetails.exception?.description||r.result.exceptionDetails.text);return r.result.result.value;}
/* Espera o tempo pedido e, na primeira carga (a frio), também a página terminar de carregar e desenhar. */
async function ir(url,ms=1800){
  await cmd('Page.navigate',{url});await espera(ms);
  for(let i=0;i<25;i++){if(await js("document.readyState==='complete'&&document.body.innerText.trim().length>0"))break;await espera(200);}
}
async function tamanho(w,h){await cmd('Emulation.setDeviceMetricsOverride',{width:w,height:h,deviceScaleFactor:1,mobile:w<600});await espera(400);}
const texto=()=>js("document.querySelector('main, #app, body').innerText.replace(/\\s+/g,' ')");
const semRolagemLateral=()=>js("document.documentElement.scrollWidth<=window.innerWidth+1");

before(async()=>{
  if(!CHROME){pular='Google Chrome não encontrado (defina CHROME_PATH).';return;}
  try{const r=await fetch(SITE,{signal:AbortSignal.timeout(3000)});if(!r.ok)throw 0;}catch{pular='Site fora do ar em '+SITE+' (rode docker compose up).';return;}
  const porta=9400+Math.floor(Math.random()*400);
  perfil=fs.mkdtempSync(path.join(os.tmpdir(),'taskgo-chrome-'));
  chrome=spawn(CHROME,['--headless=new','--remote-debugging-port='+porta,'--user-data-dir='+perfil,'--window-size=1280,900','--no-first-run','about:blank'],{stdio:'ignore'});
  let alvo;for(let i=0;i<60&&!alvo;i++){try{alvo=(await (await fetch('http://127.0.0.1:'+porta+'/json')).json()).find(t=>t.type==='page');}catch{}if(!alvo)await espera(250);}
  ws=new WebSocket(alvo.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
  ws.onmessage=m=>{const d=JSON.parse(m.data);if(d.id&&pend[d.id]){pend[d.id](d);delete pend[d.id];}
    if(d.method==='Runtime.exceptionThrown')erros.push(d.params.exceptionDetails.exception?.description||d.params.exceptionDetails.text);};
  await cmd('Runtime.enable');await cmd('Page.enable');
});
after(async()=>{try{ws&&ws.close();}catch{}if(chrome){chrome.kill();await espera(400);}try{perfil&&fs.rmSync(perfil,{recursive:true,force:true});}catch{}});

const t=(nome,fn)=>test(nome,async ctx=>{if(pular)return ctx.skip(pular);erros=[];await fn();assert.deepEqual(erros,[],'erros de JavaScript na página');});

t('páginas públicas abrem sem erro e cabem no celular',async()=>{
  for(const [rota,deve] of [['#/','TaskGO'],['#/como-funciona','Pagamento'],['#/entrar','Entrar'],['#/seguranca','Pagamento depois do serviço'],['#/faq','pagamento']]){
    await tamanho(1280,900);await ir(SITE+'/'+rota);
    assert.match(await texto(),new RegExp(deve,'i'),rota);
    await tamanho(390,844);
    assert.ok(await semRolagemLateral(),'rolagem lateral no celular em '+rota);
  }
  await tamanho(1280,900);
});

t('cliente (demonstração): início, contratar com a escolha de modo, pedidos e carteira',async()=>{
  await ir(SITE+'/?demo=1&role=cliente#/app/inicio',2500);
  assert.match(await texto(),/Contratar/);
  await ir(SITE+'/?demo=1&role=cliente#/app/contratar');
  assert.match(await texto(),/Agendar/);assert.match(await texto(),/AGORA/i);
  await js("W=W_VAZIO();W.modo='agendar';W.service='eletrica';W.step=4;render();1");await espera(500);
  assert.match(await texto(),/Quanto você oferece/);
  assert.equal(await js("!!document.getElementById('wOferta')&&document.getElementById('wOferta').value"),'','o valor começa vazio, sem sugestão');
  await ir(SITE+'/?demo=1&role=cliente#/app/pedidos');
  assert.match(await texto(),/pedidos/i);
  await tamanho(390,844);assert.ok(await semRolagemLateral(),'rolagem lateral em Meus pedidos no celular');await tamanho(1280,900);
});

t('contratar: local separado dos detalhes (agendar, obra e recorrente)',async()=>{
  await ir(SITE+'/?demo=1&role=cliente#/app/contratar',2500);
  const tem=sel=>js("!!document.querySelector('"+sel+"')");
  const passo=()=>js('W.step');
  /* Agendar: passo 2 é só o local. */
  /* "Outro endereço": campo livre, começando vazio. */
  await js("W=W_VAZIO();W.modo='agendar';W.service='eletrica';W.addrId='outro';W.step=2;render();1");await espera(500);
  assert.match(await texto(),/Onde é o atendimento/);
  assert.ok(await tem('#wAddr'),'endereço no Local');assert.ok(await tem('#mapaContratar'),'mapa no Local');
  assert.ok(!(await tem('#wQuando')),'Quando não fica no Local');
  await tamanho(390,844);assert.ok(await semRolagemLateral(),'rolagem lateral no Local no celular');await tamanho(1280,900);
  /* Sem endereço e ponto no mapa, não sai do Local. */
  await js("wGo(3);1");await espera(300);assert.equal(await passo(),2,'saiu do Local sem endereço');
  await js("W.addr='Rua das Flores, 10';wGo(3);1");await espera(300);assert.equal(await passo(),2,'saiu do Local sem ponto no mapa');
  await js("W.lat=-8.05;W.lng=-34.9;wGo(3);1");await espera(500);
  /* Detalhes: Quando, mais de um dia, descrição e fotos; sem endereço nem mapa. */
  assert.equal(await passo(),3);assert.match(await texto(),/Conte os detalhes/);
  assert.ok(await tem('#wQuando'),'Quando nos Detalhes');assert.ok(await tem('#wNotas'),'descrição nos Detalhes');
  assert.ok(!(await tem('#wAddr'))&&!(await tem('#mapaContratar')),'endereço e mapa saíram dos Detalhes');
  await tamanho(390,844);assert.ok(await semRolagemLateral(),'rolagem lateral nos Detalhes no celular');await tamanho(1280,900);
  /* Dia e hora escolhidos são obrigatórios ao sair dos Detalhes. */
  await js("W.urgency='agendar';W.date='';render();wGo(4);1");await espera(300);assert.equal(await passo(),3,'saiu dos Detalhes sem data');
  await js("W.date='2030-01-10T09:00';wGo(4);1");await espera(500);
  assert.match(await texto(),/Quanto você oferece/);
  await js("W.oferta=150;W.notes='Tomada da cozinha sem energia';wGo(5);1");await espera(500);
  assert.match(await texto(),/Confira e envie/);
  /* Mudar: endereço volta ao Local; Quando e Descrição, aos Detalhes; valor, ao Valor. */
  const mudar=rotulo=>js("(()=>{const d=[...document.querySelectorAll('.resumo-pedido>div')].find(x=>x.querySelector('dt').textContent==='"+rotulo+"');return d?d.querySelector('.resumo-mudar').getAttribute('onclick'):null})()");
  assert.match(await mudar('Endereço'),/W\.step=2;/);assert.match(await mudar('Quando'),/W\.step=3;/);
  assert.match(await mudar('Descrição'),/W\.step=3;/);assert.match(await mudar('Você oferece'),/W\.step=4;/);
  await js("W.step=2;render();1");await espera(400);assert.match(await texto(),/Onde é o atendimento/);
  /* Obra: mesma divisão. */
  await js("W=W_VAZIO();W.modo='obra';W.service='reforma';W.addrId='outro';W.step=2;render();1");await espera(500);
  assert.match(await texto(),/Onde é o serviço/);assert.ok(await tem('#wAddr'));assert.ok(!(await tem('#wInicioObra')));
  await js("W.addr='Rua das Obras, 5';W.lat=-8.05;W.lng=-34.9;wGo(3);1");await espera(500);
  assert.match(await texto(),/Conte sobre a obra/);assert.ok(await tem('#wInicioObra'));assert.ok(!(await tem('#wAddr')),'endereço saiu dos detalhes da obra');
  await js("W.notes='curta';wGo(4);1");await espera(300);assert.equal(await passo(),3,'obra saiu dos detalhes com descrição curta');
  await js("W.notes='Reformar a cozinha: trocar 12 m² de piso e a bancada';wGo(4);1");await espera(500);
  assert.match(await texto(),/Até quanto pretende gastar/);
  await js("W.oferta=8000;wGo(5);1");await espera(500);assert.match(await texto(),/Confira e envie/);
  assert.match(await mudar('Endereço'),/W\.step=2;/);assert.match(await mudar('Quer começar'),/W\.step=3;/);
  assert.match(await mudar('O que fazer'),/W\.step=3;/);assert.match(await mudar('Até quanto gastar'),/W\.step=4;/);
  /* Recorrente: repetição volta ao Valor. */
  await js("W=W_VAZIO();W.modo='agendar';W.service='limpeza';W.addr='Rua A, 1';W.lat=-8.05;W.lng=-34.9;W.oferta=120;W.rec={vezes:4,dias:[1],hora:'08:00',inicio:'2030-01-07'};W.step=5;render();1");await espera(500);
  assert.match(await texto(),/Confira e envie/);
  assert.match(await mudar('Endereço'),/W\.step=2;/);assert.match(await mudar('Repetição'),/W\.step=4;/);
  /* Pedido reaproveitado abre direto no Conferir. */
  assert.equal(await js("PASSOS.agendar[PASSOS.agendar.length-1]"),'confirmar');
});

t('profissional (demonstração): oportunidades, agenda e desempenho',async()=>{
  await ir(SITE+'/?demo=1&role=profissional#/app/oportunidades',2500);
  assert.match(await texto(),/Oportunidades/);
  assert.match(await texto(),/Aceitar R\$/);
  await ir(SITE+'/?demo=1&role=profissional#/app/agenda');
  assert.match(await texto(),/Agenda/);assert.match(await texto(),/Bloquear horário/);
  await js("bloquearHorario();1");await espera(300);
  assert.equal(await js("!!document.getElementById('blData')"),true,'modal de bloqueio abre');
  await js("closeModal();1");
  await ir(SITE+'/?demo=1&role=profissional#/app/desempenho');
  assert.match(await texto(),/Desempenho/);
});

t('conteúdo de usuário é escapado na tela (sem HTML injetado)',async()=>{
  await ir(SITE+'/?demo=1&role=cliente#/app/inicio',2500);
  const r=await js("(()=>{const d=document.createElement('div');d.innerHTML=esc('<img src=x onerror=window.__xss=1>');document.body.appendChild(d);return !d.querySelector('img')&&!window.__xss;})()");
  assert.equal(r,true);
});
