/* ---------- CONTADORES DAS ABAS ----------
   Bolinha com número em Oportunidades (pedidos ainda não vistos), Mensagens (não lidas),
   Avaliações (pendentes) e no sino (notificações). Acima de 99 vira "99+".
   A oportunidade conta como vista quando a pessoa toca nela; a mensagem, quando a conversa é aberta. */
let contadores={mensagensNaoLidas:0,oportunidades:[]};
const fmtContador=n=>n>99?'99+':String(n);
const chaveVistas=()=>'taskgo-op-vistas-'+(me()?me().id:'');
function oportunidadesVistas(){try{return new Set(JSON.parse(localStorage.getItem(chaveVistas())||'[]'));}catch(e){return new Set();}}
function marcarOportunidadeVista(id){
  const v=oportunidadesVistas();if(v.has(id))return;v.add(id);
  /* Guarda só as que ainda estão abertas, para a lista não crescer sem fim. */
  const abertas=new Set(contadores.oportunidades);const lista=[...v].filter(x=>abertas.has(x)||x===id).slice(-500);
  try{localStorage.setItem(chaveVistas(),JSON.stringify(lista));}catch(e){}
  const card=document.getElementById('op-'+id);if(card){card.classList.remove('op-nova');card.querySelector('.tag-nova')?.remove();}
  pintarContadores();
}
const oportunidadeNova=id=>!oportunidadesVistas().has(id);
function valorContador(aba){
  const u=me();if(!u)return 0;
  if(aba==='oportunidades'){const v=oportunidadesVistas();return contadores.oportunidades.filter(id=>!v.has(id)).length;}
  if(aba==='mensagens')return DEMO_MODE?resumoConta(u).respostas.length:contadores.mensagensNaoLidas;
  if(aba==='avaliacoes')return resumoConta(u).avaliar.length;
  if(aba==='notificacoes')return myNotifs().filter(n=>!n.read).length;
  return 0;
}
function bolinhaAba(aba){
  const n=valorContador(aba);
  return n?'<span class="aba-n" data-aba-n="'+aba+'" aria-label="'+n+(aba==='oportunidades'?' novas':aba==='mensagens'?' não lidas':' pendentes')+'">'+fmtContador(n)+'</span>':'<span class="aba-n" data-aba-n="'+aba+'" hidden></span>';
}
/* Atualiza só as bolinhas, sem redesenhar a tela. */
function pintarContadores(){
  document.querySelectorAll('[data-aba-n]').forEach(el=>{const n=valorContador(el.dataset.abaN);el.hidden=!n;el.textContent=n?fmtContador(n):'';});
  const sino=document.querySelector('.area-sino-n'),ns=valorContador('notificacoes');if(sino)sino.textContent=fmtContador(ns);
}
let contadoresCarregando=false;
async function carregarContadores(){
  const u=me();if(!u||contadoresCarregando||u.role==='administrador'||u.role==='suporte')return;
  if(DEMO_MODE){contadores.oportunidades=u.role==='profissional'&&S.demoOnline!==false?oportunidadesDemo().map(p=>p.id):[];pintarContadores();return;}
  contadoresCarregando=true;
  try{contadores=await apiJSON('/api/conta/contadores');pintarContadores();}catch(e){}
  contadoresCarregando=false;
}
setInterval(()=>{if(route.page==='app'&&document.visibilityState==='visible')carregarContadores();},15000);

/* ---------- Início do profissional: melhores oportunidades ----------
   Ordem: urgentes primeiro, depois o maior valor oferecido, a melhor nota do cliente e o pedido mais novo. */
function blocoMelhoresOportunidades(){
  return '<section class="inicio-bloco" aria-labelledby="ini-op"><div class="inicio-cab"><h2 class="sub" id="ini-op">Melhores oportunidades agora</h2>'+
    '<a class="link-seta" href="#/app/oportunidades">Todas'+ic('arrow')+'</a></div><div id="melhores-op"><p class="muted">Procurando pedidos na sua categoria...</p></div></section>';
}
async function carregarMelhoresOportunidades(){
  const alvo=document.getElementById('melhores-op');if(!alvo)return;
  let lista=[];
  try{
    if(DEMO_MODE)lista=S.demoOnline===false?null:oportunidadesDemo();
    else{const r=await fetch(API_BASE_URL+'/api/pedidos/contratados/disponiveis?page=0&size=50&sort=created,desc');
      if(r.status===409)lista=null;else if(r.ok)lista=(await r.json()).content||[];}
  }catch(e){lista=[];}
  if(!document.getElementById('melhores-op'))return;
  if(lista===null){alvo.innerHTML='<div class="inicio-vazio">'+ic('moon')+'<p><b>Você está offline.</b> Fique online em Oportunidades para receber pedidos.</p></div>';return;}
  const top=lista.slice().sort((a,b)=>(b.urgente?1:0)-(a.urgente?1:0)||(Number(b.price)||0)-(Number(a.price)||0)||(Number(b.clienteNota)||0)-(Number(a.clienteNota)||0)||(b.created||0)-(a.created||0)).slice(0,3);
  if(!top.length){alvo.innerHTML='<div class="inicio-vazio">'+ic('target')+'<p><b>Nenhum pedido aberto na sua categoria.</b> Quando aparecer, os melhores ficam aqui.</p></div>';return;}
  alvo.innerHTML='<ul class="melhores-op">'+top.map(o=>{const s=SERVICES.find(x=>x.id===o.service);
    return '<li><a href="#/app/oportunidades" onclick="marcarOportunidadeVista(\''+esc(o.id)+'\')">'+
      '<span class="mo-info"><b>'+esc(s?s.n:o.service)+(o.urgente?' <span class="tag-urgente">Urgente</span>':'')+(oportunidadeNova(o.id)?' <span class="tag-nova">Nova</span>':'')+'</b>'+
      '<small>'+(o.bairro?esc(o.bairro)+' · ':'')+esc(o.clienteNome||'Cliente')+(o.clienteAvaliacoes?' · '+Number(o.clienteNota).toFixed(1).replace('.',',')+' estrelas':' · cliente novo')+'</small></span>'+
      '<span class="mo-valor">'+money(o.price)+'</span>'+ic('arrow')+'</a></li>';}).join('')+'</ul>';
}
