/* =========================================================
   CHAMAR AGORA
   Cliente: mapa com ele no centro, os profissionais da onda atual e o radar enquanto chama.
   Quando alguém aceita, sobe o cartão de baixo: foto, estrelas, serviços feitos e minutos até chegar.
   O cliente confirma ou recusa (e aí a chamada continua).
   Profissional: manda a posição enquanto está online e recebe o chamado em tela cheia.
   ========================================================= */
let chamando={id:null,dados:null,timer:null,mapa:null,camadaPerto:null,pinoPro:null,relogio:null};

/* Foto de perfil vinda do servidor; sem foto, as iniciais. */
function fotoPerfil(uid,nome,tamanho,temFoto){
  if(uid&&temFoto!==false)return '<img class="avatar" style="width:'+tamanho+'px;height:'+tamanho+'px" src="'+API_BASE_URL+'/api/fotos/'+encodeURIComponent(uid)+'" alt="Foto de '+esc(nome||'')+'" '+
    'onerror="this.replaceWith(Object.assign(document.createElement(\'div\'),{className:\'avatar\',style:this.getAttribute(\'style\')+\';font-size:'+Math.max(10,tamanho*.36)+'px\',textContent:'+esc(JSON.stringify(initials(nome||'?')))+'}))">';
  return avatar({name:nome||'?'},tamanho);
}

function telaChamando(id){
  const o=S.orders.find(x=>String(x.id)===String(id));
  if(chamando.id!==id){pararChamando();chamando={id,dados:null,timer:null,mapa:null,camadaPerto:null,pinoPro:null,relogio:null};}
  const s=svc(o?o.service:(chamando.dados&&chamando.dados.servico));
  return '<section class="chamando" aria-label="Chamando profissionais agora">'+
    '<div class="chamando-topo"><button type="button" class="btn-voltar" onclick="go(\'/app/pedidos\')">'+ic('arrowLeft')+'<span>Meus pedidos</span></button>'+
    '<div><h1 class="sub">'+ic(s.ico,'ic-svc-'+s.id)+' '+esc(s.n)+'</h1><p class="muted" id="chamando-linha" aria-live="polite">Preparando a chamada...</p></div></div>'+
    '<div class="chamando-palco"><div id="mapaChamando" class="chamando-mapa" role="application" aria-label="Mapa com você e os profissionais por perto"></div>'+
    '</div>'+
    '<div class="chamando-folha" id="chamando-folha"></div>'+
  '</section>';
}

function ligarChamando(){
  const no=document.getElementById('mapaChamando');if(!no||!window.L)return;
  if(chamando.mapa&&chamando.mapa.getContainer()===no&&no.isConnected)return;
  if(chamando.mapa){chamando.mapa.remove();chamando.mapa=null;}
  const o=S.orders.find(x=>String(x.id)===String(chamando.id))||{};
  const centro=o.lat!=null?[Number(o.lat),Number(o.lng)]:[-8.0476,-34.8770];
  const mapa=L.map(no,{zoomControl:true,scrollWheelZoom:true}).setView(centro,14);
  L.tileLayer(MAPA_TILES,{maxZoom:19,attribution:MAPA_CREDITO}).addTo(mapa);
  /* O radar fica preso ao ponto do cliente no mapa: anda junto quando o mapa é arrastado ou enquadra o profissional. */
  L.marker(centro,{icon:L.divIcon({className:'radar-pino',html:'<div class="radar" id="radar" aria-hidden="true"><i></i><i></i><i></i></div>',iconSize:[0,0],iconAnchor:[0,0]}),interactive:false,keyboard:false,zIndexOffset:-1000}).addTo(mapa);
  L.marker(centro,{icon:L.divIcon({className:'pino-voce',html:'<span>'+ic('user')+'</span>',iconSize:[40,40],iconAnchor:[20,20]}),title:'Você',keyboard:false}).addTo(mapa);
  chamando.mapa=mapa;
  setTimeout(()=>mapa.invalidateSize(),80);
  atualizarChamando();
  clearInterval(chamando.timer);chamando.timer=setInterval(atualizarChamando,3000);
  clearInterval(chamando.relogio);chamando.relogio=setInterval(pintarLinhaChamando,1000);
}
function pararChamando(){clearInterval(chamando.timer);clearInterval(chamando.relogio);chamando.timer=null;chamando.relogio=null;if(chamando.mapa){chamando.mapa.remove();chamando.mapa=null;}}

async function atualizarChamando(){
  if(!chamando.id||route.sub!=='chamando'){pararChamando();return;}
  let d;
  try{d=await apiJSON('/api/chamar-agora/'+encodeURIComponent(chamando.id));}
  catch(e){const l=document.getElementById('chamando-linha');if(l)l.textContent=erroLegivel(e,'Não foi possível atualizar a chamada.');return;}
  d.recebidoEm=Date.now();chamando.dados=d;
  if(d.status==='cancelado'){pararChamando();toast('Chamada cancelada.','ok');go('/app/pedidos');return;}
  /* Confirmado: vai para o acompanhamento do pedido. */
  if(d.profissional&&d.confirmacao==='confirmada'){pararChamando();const email=me()?.email;if(email)await syncPedidos(email).catch(()=>{});go('/app/pedido/'+encodeURIComponent(chamando.id));return;}
  pintarMapaChamando(d);pintarLinhaChamando();pintarFolhaChamando(d);
}
function pintarLinhaChamando(){
  const d=chamando.dados,l=document.getElementById('chamando-linha');if(!d||!l)return;
  const radar=document.getElementById('radar');
  if(d.profissional){l.innerHTML='<b>'+esc(d.profissional.nome.split(' ')[0])+' aceitou.</b> Confira o perfil e confirme.';if(radar)radar.classList.add('parado');return;}
  if(radar)radar.classList.remove('parado');
  const falta=Math.max(0,Math.ceil(((d.proximaOndaEm||0)-(Date.now()-d.recebidoEm))/1000));
  const aj=d.servico==='ajuda';
  if(!d.chamados)l.innerHTML=d.semMais?(aj?'<b>Ninguém com a ajuda rápida ligada perto agora.</b> Continuamos tentando a cada 20 segundos.':'<b>Nenhum profissional online perto agora.</b> Continuamos tentando a cada 20 segundos.'):(aj?'Procurando quem possa ajudar perto de você...':'Procurando profissionais online perto de você...');
  else l.innerHTML='<b>Chamando '+d.chamados+' '+(aj?(d.chamados===1?'pessoa':'pessoas'):(d.chamados===1?'profissional':'profissionais'))+'</b> perto de você'+(d.semMais?' · não há mais ninguém online por perto agora':falta?' · se ninguém aceitar, chamamos os próximos em '+falta+' s':' · chamando os próximos');
}
function pintarMapaChamando(d){
  const mapa=chamando.mapa;if(!mapa)return;
  if(chamando.camadaPerto)chamando.camadaPerto.remove();
  chamando.camadaPerto=L.layerGroup((d.profissional?[]:d.perto||[]).map(p=>L.marker([p.lat,p.lng],{icon:L.divIcon({className:'pino-pro',html:'<span>'+ic(svc(d.servico).ico)+'</span>',iconSize:[34,34],iconAnchor:[17,17]}),keyboard:false,title:'Profissional a '+String(p.km).replace('.',',')+' km'}))).addTo(mapa);
  const pr=d.profissional;
  if(pr&&pr.lat!=null){
    const ll=[pr.lat,pr.lng];
    if(!chamando.pinoPro)chamando.pinoPro=L.marker(ll,{icon:L.divIcon({className:'pino-pro escolhido',html:'<span>'+ic(svc(d.servico).ico)+'</span>',iconSize:[42,42],iconAnchor:[21,21]}),title:pr.nome}).addTo(mapa);
    else chamando.pinoPro.setLatLng(ll);
    if(!chamando.enquadrado){chamando.enquadrado=true;mapa.fitBounds(L.latLngBounds([ll,[d.lat,d.lng]]).pad(.35));}
  }else if(chamando.pinoPro){chamando.pinoPro.remove();chamando.pinoPro=null;chamando.enquadrado=false;}
}
function pintarFolhaChamando(d){
  const f=document.getElementById('chamando-folha');if(!f)return;
  const pr=d.profissional;
  const chave=pr?'pro:'+pr.uid+':'+(pr.minutos||''):d.semMais&&!d.chamados?'vazio':'chamando';
  if(f.dataset.chave===chave)return;f.dataset.chave=chave;
  if(pr){
    const nota=pr.nota?'<span class="folha-nota">'+stars(pr.nota)+' '+String(pr.nota.toFixed(1)).replace('.',',')+'</span> <span class="muted">('+pr.avaliacoes+')</span>':'<span class="muted">Ainda sem avaliações</span>';
    f.innerHTML='<div class="folha-pro sobe">'+
      '<div class="folha-quem">'+fotoPerfil(pr.uid,pr.nome,72,pr.temFoto)+'<div><b class="folha-nome">'+esc(pr.nome)+seloVerificado(pr.verificado,true)+seloPro(pr.pro,true)+'</b>'+nota+
      '<span class="folha-feitos">'+ic('checkCircle')+' '+pr.servicos+' '+(pr.servicos===1?'serviço feito':'serviços feitos')+' na TaskGO</span></div>'+
      '<div class="folha-eta">'+(pr.minutos?'<b>'+pr.minutos+'</b><span>min até você</span>':'<span>sem posição agora</span>')+(pr.km!=null?'<small>'+String(pr.km).replace('.',',')+' km</small>':'')+'</div></div>'+
      '<p class="muted folha-dica">O endereço e o seu telefone só vão para ele depois que você confirmar. Se não gostar do perfil, recuse: a chamada continua com os outros.</p>'+
      '<div class="folha-acoes"><button type="button" class="btn btn-ghost" onclick="recusarProfissional(\''+esc(chamando.id)+'\')">Recusar</button>'+
      '<button type="button" class="btn btn-grad btn-lg" onclick="confirmarProfissional(\''+esc(chamando.id)+'\')">'+ic('check')+' Confirmar '+esc(pr.nome.split(' ')[0])+'</button></div></div>';
    return;
  }
  if(chave==='vazio'){
    f.innerHTML='<div class="folha-espera sobe"><p><b>Ninguém online por perto agora.</b> A chamada continua: quem ficar online perto de você recebe na hora.</p>'+
      '<div class="folha-acoes"><button type="button" class="btn btn-danger" onclick="cancelarChamada(\''+esc(chamando.id)+'\')">Cancelar chamada</button>'+
      '<button type="button" class="btn btn-ghost" onclick="trocarParaAgendar(\''+esc(chamando.id)+'\')">'+ic('cal')+' Agendar em vez disso</button></div></div>';
    return;
  }
  f.innerHTML='<div class="folha-espera sobe"><p>Assim que alguém aceitar, a foto, a nota e o tempo até chegar aparecem aqui.</p>'+
    '<div class="folha-acoes"><button type="button" class="btn btn-danger" onclick="cancelarChamada(\''+esc(chamando.id)+'\')">Cancelar chamada</button></div></div>';
}
async function confirmarProfissional(id){
  try{await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)+'/confirmar-profissional','POST',null,'Não foi possível confirmar.');
    toast('Confirmado. O profissional já está indo.','ok');pararChamando();await syncPedidos(me().email).catch(()=>{});go('/app/pedido/'+encodeURIComponent(id));}
  catch(e){toast(erroLegivel(e,'Não foi possível confirmar.'),'err');}
}
async function recusarProfissional(id){
  try{await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)+'/recusar-profissional','POST',null,'Não foi possível recusar.');
    toast('Recusado. Continuamos procurando.','ok');
    await syncPedidos(me().email).catch(()=>{});
    if(route.sub==='chamando'){const f=document.getElementById('chamando-folha');if(f)f.dataset.chave='';atualizarChamando();}else render();}
  catch(e){toast(erroLegivel(e,'Não foi possível recusar.'),'err');}
}
async function cancelarChamada(id){
  const o=S.orders.find(x=>String(x.id)===String(id));if(!o)return go('/app/pedidos');
  try{await guardarPedidoAlterado(o,{status:'cancelado',cancelReason:'Cancelou a chamada'});pararChamando();toast('Chamada cancelada.','ok');go('/app/pedidos');}
  catch(e){toast(erroLegivel(e,'Não foi possível cancelar.'),'err');}
}
/* Ninguém perto: cancela a chamada e abre o Agendar com tudo preenchido. */
async function trocarParaAgendar(id){
  const o=S.orders.find(x=>String(x.id)===String(id));if(!o)return;
  try{await guardarPedidoAlterado(o,{status:'cancelado',cancelReason:'Trocou para agendar'});}catch(e){return toast(erroLegivel(e,'Não foi possível mudar agora.'),'err');}
  pararChamando();reaproveitarPedido(o);W.modo='agendar';W.urgency='hoje';W.step=PASSOS.agendar.length;go('/app/contratar');
}

/* ---------- Lado do profissional ---------- */
let presencaTimer=null,convitesAgora=[],convitesDispensados=new Set();
function ligarPresenca(){
  const u=me();
  /* Profissional online, ou qualquer pessoa com a ajuda rápida ligada. */
  if(DEMO_MODE||!u||!(u.role==='profissional'||ajudanteAtivo())||presencaTimer||!navigator.geolocation)return;
  const mandar=()=>{
    if(!me()||!((me().role==='profissional'&&proOnline!==false)||ajudanteAtivo()))return;
    navigator.geolocation.getCurrentPosition(p=>{
      apiJSON('/api/profissionais/eu/presenca','PUT',{latitude:p.coords.latitude,longitude:p.coords.longitude,accuracy:p.coords.accuracy||0}).catch(()=>{});
    },()=>{},{enableHighAccuracy:false,timeout:15000,maximumAge:60000});
  };
  mandar();presencaTimer=setInterval(mandar,30000);
  carregarConvites();
}
function desligarPresenca(){clearInterval(presencaTimer);presencaTimer=null;}
async function carregarConvites(){
  if(DEMO_MODE||!me()||!(me().role==='profissional'||ajudanteAtivo()))return;
  try{convitesAgora=await apiJSON('/api/chamar-agora/convites');}catch(e){convitesAgora=[];}
  pintarConvites();
}
function pintarConvites(){
  let no=document.getElementById('convites-agora');
  const lista=convitesAgora.filter(c=>!convitesDispensados.has(c.id));
  if(!lista.length){if(no)no.remove();return;}
  if(!no){no=document.createElement('div');no.id='convites-agora';no.className='convites-agora';no.setAttribute('role','alertdialog');no.setAttribute('aria-label','Chamado agora');document.body.appendChild(no);}
  const c=lista[0],s=svc(c.servico);
  if(no.dataset.id===c.id)return;no.dataset.id=c.id;
  no.innerHTML='<div class="convite sobe">'+
    '<div class="convite-topo">'+ic(c.servico==='ajuda'?'boia':'bolt')+'<b>'+(c.servico==='ajuda'?'Pedido de ajuda':'Chamado agora')+'</b>'+(lista.length>1?'<span class="aba-n">+'+(lista.length-1)+'</span>':'')+'<div class="convite-tempo" aria-hidden="true"><i></i></div></div>'+
    '<div class="convite-quem">'+fotoPerfil(c.clienteUid,c.clienteNome,56,c.clienteTemFoto)+'<div><b>'+esc(c.clienteNome||'Cliente')+'</b><span class="muted">'+ic(s.ico,'ic-svc-'+s.id)+' '+esc(s.n)+(c.bairro?' · '+esc(c.bairro):'')+'</span></div>'+
    '<div class="convite-valor"><b>'+money(c.valor)+'</b>'+(c.dinheiro?'<small class="convite-dinheiro">em dinheiro</small>':'')+(c.minutos?'<small>'+String(c.km).replace('.',',')+' km · ~'+c.minutos+' min</small>':'')+'</div></div>'+
    (c.descricao?'<p class="convite-desc">“'+esc(c.descricao)+'”</p>':'')+
    '<div class="folha-acoes"><button type="button" class="btn btn-ghost" onclick="dispensarConvite(\''+esc(c.id)+'\')">Agora não</button>'+
    '<button type="button" class="btn btn-grad btn-lg" onclick="aceitarConvite(\''+esc(c.id)+'\')">'+ic('check')+' Aceitar</button></div></div>';
  try{if(navigator.vibrate)navigator.vibrate([180,80,180]);}catch(e){}
  const b=no.querySelector('.btn-grad');if(b)b.focus({preventScroll:true});
}
function dispensarConvite(id){convitesDispensados.add(id);const no=document.getElementById('convites-agora');if(no)no.dataset.id='';pintarConvites();}
async function aceitarConvite(id){
  try{
    await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)+'/aceitar','POST',null,'Não foi possível aceitar.');
    convitesAgora=convitesAgora.filter(c=>c.id!==id);pintarConvites();
    await syncPedidos(me().email).catch(()=>{});
    toast('Você aceitou. Espere o cliente confirmar para sair.','ok');go('/app/pedido/'+encodeURIComponent(id));
  }catch(e){toast(erroLegivel(e,'Outro profissional aceitou primeiro.'),'err');convitesAgora=convitesAgora.filter(c=>c.id!==id);pintarConvites();}
}
function chamadoEncerrado(id){
  if(!convitesAgora.some(c=>c.id===id))return;
  convitesAgora=convitesAgora.filter(c=>c.id!==id);
  const no=document.getElementById('convites-agora');if(no&&no.dataset.id===id){no.dataset.id='';toast('Outro profissional aceitou esse chamado.');}
  pintarConvites();
}
