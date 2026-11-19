/* ---------- SUPORTE E EMERGÊNCIA ----------
   Quem está num pedido aperta Emergência: abre um chamado que chega na hora à equipe de suporte.
   A conta de suporte (TASKGO_SUPORTE_EMAILS) só vê a fila de chamados, com alerta sonoro e visual. */
const ehSuporte=u=>!!u&&u.role==='suporte';
const ETAPA_CHAMADO={aberto:'Aberto, esperando o suporte',em_atendimento:'Em atendimento',resolvido:'Resolvido'};

/* ================= Quem pede ajuda ================= */
let meuChamado=null;
function emergencia(){
  const id=route.sub==='pedido'?route.id:null;
  openModal('<div class="modal-head"><div><h3 style="color:var(--danger-text)">Emergência</h3><p>Se alguém corre perigo agora, ligue primeiro para a polícia ou o SAMU.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
  '<div style="display:flex;flex-direction:column;gap:10px">'+
  '<a class="btn btn-danger btn-block" href="tel:190">'+ic('phone')+' Ligar para a Polícia Militar (190)</a>'+
  '<a class="btn btn-danger btn-block" href="tel:192">'+ic('phone')+' Ligar para o SAMU (192)</a></div>'+
  (id?'<div class="emerg-suporte"><b>Avisar a equipe da TaskGO</b><p class="muted">O suporte recebe na hora, com o pedido, as duas pessoas e a sua localização (se você permitir).</p>'+
    '<label class="leitor" for="emerg-msg">O que está acontecendo (opcional)</label><textarea class="textarea" id="emerg-msg" maxlength="1000" placeholder="O que está acontecendo (opcional)"></textarea>'+
    '<button class="btn btn-primary btn-block" id="btnChamarSuporte" onclick="chamarSuporte(\''+esc(id)+'\')">'+ic('bell')+' Chamar o suporte da TaskGO</button>'+
    '<div id="emerg-status" role="status" aria-live="assertive"></div></div>':''));
  if(id)mostrarMeuChamado(id);
}
function posicaoRapida(){
  return new Promise(ok=>{if(!navigator.geolocation)return ok(null);
    navigator.geolocation.getCurrentPosition(p=>ok({latitude:p.coords.latitude,longitude:p.coords.longitude}),()=>ok(null),{enableHighAccuracy:true,timeout:6000,maximumAge:30000});});
}
async function chamarSuporte(pedidoId){
  const b=$('#btnChamarSuporte');if(b){b.disabled=true;b.textContent='Chamando o suporte...';}
  if(DEMO_MODE){meuChamado={status:'aberto',criadoEm:Date.now(),pedidoId};pintarMeuChamado();return;}
  try{
    const pos=await posicaoRapida();
    meuChamado=await apiJSON('/api/suporte/emergencia','POST',Object.assign({pedidoId,mensagem:(($('#emerg-msg')||{}).value||'').trim()},pos||{}),'Não foi possível chamar o suporte.');
    pintarMeuChamado();
  }catch(error){if(b){b.disabled=false;b.innerHTML=ic('bell')+' Chamar o suporte da TaskGO';}
    const st=$('#emerg-status');if(st){st.className='recuperacao-erro';st.textContent=erroLegivel(error,'Não foi possível chamar o suporte.')+' Ligue 190 se for urgente.';}}
}
async function mostrarMeuChamado(pedidoId){
  if(DEMO_MODE)return;
  try{const lista=await apiJSON('/api/suporte/meus-chamados');meuChamado=lista.find(c=>c.pedidoId===pedidoId&&c.status!=='resolvido')||null;if(meuChamado)pintarMeuChamado();}catch(e){}
}
function pintarMeuChamado(){
  const st=$('#emerg-status'),b=$('#btnChamarSuporte');if(!st||!meuChamado)return;
  if(b)b.hidden=meuChamado.status!=='resolvido';
  st.className='emerg-chamado '+meuChamado.status;
  st.innerHTML='<b>'+(meuChamado.status==='aberto'?'Chamado aberto. O suporte foi avisado agora.':meuChamado.status==='em_atendimento'?(esc(meuChamado.atendente||'O suporte')+' está cuidando do seu chamado.'):'Chamado resolvido.')+'</b>'+
    '<span>'+esc(ETAPA_CHAMADO[meuChamado.status]||'')+' · aberto às '+new Date(meuChamado.criadoEm).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})+(meuChamado.nota?' · '+esc(meuChamado.nota):'')+'</span>';
}

/* ================= Equipe de suporte ================= */
let chamadosSuporte=null,alertaTitulo=null;
function dashSuporte(){
  const c=chamadosSuporte;
  if(c==null){carregarChamados();return dashHead('Emergências','Chamados abertos pelo botão de emergência dos pedidos.')+'<p class="muted">Carregando...</p>';}
  const abertos=c.filter(x=>x.status==='aberto'),atend=c.filter(x=>x.status==='em_atendimento'),feitos=c.filter(x=>x.status==='resolvido');
  return dashHead('Emergências','Chamados abertos pelo botão de emergência dos pedidos. Chegam aqui na hora, com som.')+
    avisoPermissaoNotificacao()+
    '<section aria-labelledby="t-abertos"><h2 class="sub suporte-ttl" id="t-abertos">Esperando alguém assumir ('+abertos.length+')</h2>'+(abertos.length?abertos.map(cartaoChamado).join(''):'<p class="muted suporte-vazio">Nenhum chamado esperando. Esta tela avisa com som quando chegar um.</p>')+'</section>'+
    '<section aria-labelledby="t-atend"><h2 class="sub suporte-ttl" id="t-atend">Em atendimento ('+atend.length+')</h2>'+(atend.length?atend.map(cartaoChamado).join(''):'<p class="muted suporte-vazio">Nenhum chamado em atendimento.</p>')+'</section>'+
    (feitos.length?'<section aria-labelledby="t-feitos"><h2 class="sub suporte-ttl" id="t-feitos">Resolvidos</h2><ul class="lista-recursos">'+feitos.slice(0,20).map(x=>'<li><p><b>'+esc(x.autor?x.autor.nome:'')+'</b> · '+esc(nomeServicoSeguro(x.pedido&&x.pedido.servico))+(x.nota?' · '+esc(x.nota):'')+'</p><small>'+dataHora(x.criadoEm)+' → '+dataHora(x.resolvidoEm)+' · '+esc(x.atendente||'')+'</small></li>').join('')+'</ul></section>':'');
}
const nomeServicoSeguro=id=>{const s=SERVICES.find(x=>x.id===id);return s?s.n:(id||'Pedido');};
function tempoDesde(ts){const m=Math.max(0,Math.floor((Date.now()-ts)/6e4));return m<1?'agora':m<60?'há '+m+' min':'há '+Math.floor(m/60)+' h '+(m%60)+' min';}
function linkMapa(lat,lng,rotulo){return lat==null?'':'<a class="btn btn-ghost btn-sm" target="_blank" rel="noopener" href="https://www.openstreetmap.org/?mlat='+lat+'&mlon='+lng+'#map=17/'+lat+'/'+lng+'">'+ic('mapPin')+' '+rotulo+'</a>';}
function pessoaChamado(rot,p){
  if(!p)return '';
  return '<div><dt>'+rot+'</dt><dd>'+esc(p.nome||'')+(p.telefone?' · <a href="tel:'+esc(String(p.telefone).replace(/[^0-9+]/g,''))+'">'+esc(p.telefone)+'</a>':'')+'</dd></div>';
}
function cartaoChamado(x){
  const p=x.pedido||{},aberto=x.status==='aberto';
  return '<article class="chamado'+(aberto?' urgente':'')+'" id="ch-'+esc(x.id)+'">'+
    '<div class="chamado-topo"><span class="tag-urgente">'+ic('bell')+' '+(aberto?'Emergência':'Em atendimento')+'</span><time class="chamado-tempo" data-desde="'+x.criadoEm+'">'+tempoDesde(x.criadoEm)+'</time></div>'+
    '<h3 class="chamado-ttl">'+esc(x.autor?x.autor.nome:'Alguém')+' pediu ajuda <small>como '+esc(x.autor?x.autor.tipo:'')+'</small></h3>'+
    (x.mensagem?'<p class="chamado-msg">“'+esc(x.mensagem)+'”</p>':'<p class="muted">Sem mensagem.</p>')+
    '<dl class="admin-linhas">'+
      '<div><dt>Pedido</dt><dd>'+esc(nomeServicoSeguro(p.servico))+' · '+esc((ST[p.status]||[p.status||'—'])[0])+'</dd></div>'+
      '<div><dt>Endereço</dt><dd>'+esc(p.endereco||p.bairro||'—')+'</dd></div>'+
      pessoaChamado('Cliente',p.cliente)+pessoaChamado('Profissional',p.profissional)+
      (x.atendente?'<div><dt>Atendente</dt><dd>'+esc(x.atendente)+'</dd></div>':'')+
    '</dl>'+
    '<div class="chamado-acoes">'+linkMapa(x.latitude,x.longitude,'Onde quem chamou está')+(p.posicaoProfissional?linkMapa(p.posicaoProfissional.latitude,p.posicaoProfissional.longitude,'Última posição do profissional'):'')+
      '<a class="btn btn-ghost btn-sm" href="tel:190">'+ic('phone')+' 190</a><a class="btn btn-ghost btn-sm" href="tel:192">'+ic('phone')+' 192</a></div>'+
    (aberto?'<button class="btn btn-danger btn-block" onclick="assumirChamado(\''+esc(x.id)+'\')">Assumir este chamado</button>':
      '<div class="field" style="margin:12px 0 10px"><label for="nota-'+esc(x.id)+'">O que foi feito</label><input class="input" id="nota-'+esc(x.id)+'" maxlength="500" placeholder="Ex.: falei com o cliente, tudo resolvido."></div>'+
      '<button class="btn btn-primary btn-block" onclick="resolverChamado(\''+esc(x.id)+'\')">'+ic('check')+' Marcar como resolvido</button>')+
  '</article>';
}
async function carregarChamados(){
  try{chamadosSuporte=await apiJSON('/api/suporte/chamados','GET',undefined,'Não foi possível carregar os chamados.');}
  catch(error){chamadosSuporte=[];toast(erroLegivel(error,'Não foi possível carregar os chamados.'),'err');}
  if(route.sub==='suporte')render();
}
async function assumirChamado(id){
  try{await apiJSON('/api/suporte/chamados/'+encodeURIComponent(id)+'/assumir','POST',undefined,'Não foi possível assumir.');pararAlerta();toast('Chamado assumido. Ligue para as pessoas agora.','ok');}
  catch(error){toast(erroLegivel(error,'Não foi possível assumir.'),'err');}
  carregarChamados();
}
async function resolverChamado(id){
  const nota=(($('#nota-'+id)||{}).value||'').trim();
  try{await apiJSON('/api/suporte/chamados/'+encodeURIComponent(id)+'/resolver','POST',{nota},'Não foi possível resolver.');toast('Chamado resolvido.','ok');}
  catch(error){toast(erroLegivel(error,'Não foi possível resolver.'),'err');}
  carregarChamados();
}
/* Alerta de chamado novo: som repetido, título piscando e notificação do sistema até alguém assumir. */
let somAlerta=null;
function tocarAlerta(){
  try{const ctx=new (window.AudioContext||window.webkitAudioContext)();[0,0.35,0.7].forEach(t=>{const o=ctx.createOscillator(),g=ctx.createGain();o.type='square';o.frequency.value=880;g.gain.value=0.15;o.connect(g);g.connect(ctx.destination);o.start(ctx.currentTime+t);o.stop(ctx.currentTime+t+0.2);});setTimeout(()=>ctx.close(),1500);}catch(e){}
}
function alertarChamadoNovo(){
  tocarAlerta();clearInterval(somAlerta);somAlerta=setInterval(()=>{if(chamadosSuporte&&chamadosSuporte.some(x=>x.status==='aberto'))tocarAlerta();else pararAlerta();},6000);
  const original=document.title;clearInterval(alertaTitulo);let on=false;alertaTitulo=setInterval(()=>{document.title=(on=!on)?'(!) EMERGÊNCIA':original;},900);
  try{if('Notification' in window&&Notification.permission==='granted')new Notification('Emergência na TaskGO',{body:'Um chamado acabou de chegar. Abra a TaskGO para assumir.',requireInteraction:true});}catch(e){}
}
function pararAlerta(){clearInterval(somAlerta);clearInterval(alertaTitulo);somAlerta=null;alertaTitulo=null;atualizarTituloAba&&atualizarTituloAba('app');}
function avisoPermissaoNotificacao(){
  if(!('Notification' in window)||Notification.permission==='granted')return '';
  return '<p class="painel-aviso">'+ic('bell')+' Para ouvir e ver os chamados mesmo com a aba em segundo plano, <button type="button" class="log-quem" onclick="Notification.requestPermission().then(()=>render())">permita as notificações</button> e deixe esta aba aberta.</p>';
}
/* Relógios "há X min" andam sozinhos; a lista se atualiza a cada 20 s caso algum evento se perca. */
setInterval(()=>{document.querySelectorAll('.chamado-tempo').forEach(t=>t.textContent=tempoDesde(Number(t.dataset.desde)));},30000);
setInterval(()=>{if(route.sub==='suporte'&&ehSuporte(me())&&!DEMO_MODE){const antes=(chamadosSuporte||[]).filter(x=>x.status==='aberto').length;
  apiJSON('/api/suporte/chamados').then(l=>{const novos=l.filter(x=>x.status==='aberto').length;const mudou=JSON.stringify(l)!==JSON.stringify(chamadosSuporte);chamadosSuporte=l;if(novos>antes)alertarChamadoNovo();if(mudou&&!document.querySelector('#conteudo input:focus'))render();}).catch(()=>{});}},20000);
