/* ---------- OPORTUNIDADES (PRO) ----------
   Pedidos abertos da categoria. Só quem está online recebe e vê; urgentes vêm primeiro.
   Antes do aceite o profissional vê o bairro, a descrição, o valor oferecido e a nota do cliente. */
const API_PEDIDOS_OPORTUNIDADES=API_BASE_URL+'/api/pedidos/contratados';
let paginaOportunidades=0;
/* null enquanto o servidor não responde; no modo demonstração começa online. */
let proOnline=null;
function dashOportunidades(){
  /* Para receber oportunidades, a identidade precisa estar verificada. */
  if(precisaVerificacao())return dashHead('Oportunidades','Para receber pedidos, a TaskGO confere a sua identidade uma vez.')+painelVerificacaoServico('atender');
  if(!DEMO_MODE&&!CATS.lista)carregarCategoriasPro();
  const minhas=categoriasDoPro(),cat=minhas.length?(minhas.length===1?svc(minhas[0]).n:null):null;
  return dashHead('Oportunidades',(minhas.length>1?'Pedidos das suas <b>'+minhas.length+' categorias</b> esperando um profissional.':cat?'Pedidos de <b>'+esc(cat)+'</b>, a sua categoria, esperando um profissional.':'Você ainda não escolheu sua categoria: escolha no Perfil para receber pedidos.')+' O endereço só aparece depois do aceite. <a class="lp-auth-link" href="#/painel">Mudar categorias</a>')+
    '<div class="disponibilidade" id="disponibilidade">'+linhaDisponibilidade()+'</div>'+
    '<div class="area-linha" id="linhaArea">'+linhaArea()+'</div>'+
    '<div id="mural-pedidos" aria-live="polite"><div class="card center"><b>Carregando pedidos...</b></div></div>';
}
function linhaDisponibilidade(){
  if(proOnline===null)return '<span class="muted">Conferindo sua disponibilidade...</span>';
  return '<span class="disp-estado'+(proOnline?' on':'')+'"><i aria-hidden="true"></i>'+(proOnline?'Você está online':'Você está offline')+'</span>'+
    '<span class="muted disp-texto">'+(proOnline?'Pedidos novos da sua categoria chegam aqui na hora.':'Nenhum pedido chega enquanto você estiver offline.')+'</span>'+
    '<button class="switch'+(proOnline?' on':'')+'" role="switch" aria-checked="'+proOnline+'" aria-label="Receber pedidos" onclick="alternarDisponibilidade()"><span></span></button>';
}
async function carregarDisponibilidade(){
  if(DEMO_MODE){proOnline=S.demoOnline!==false;return proOnline;}
  try{const r=await apiJSON('/api/profissionais/eu/disponibilidade');proOnline=!!r.online;}
  catch(error){proOnline=true;console.warn('Disponibilidade:',error);}
  return proOnline;
}
async function alternarDisponibilidade(){
  const novo=!proOnline;
  try{
    if(DEMO_MODE){S.demoOnline=novo;Store.save();}
    else{const r=await apiJSON('/api/profissionais/eu/disponibilidade','PUT',{online:novo},'Não foi possível mudar sua disponibilidade.');proOnline=!!r.online;}
    proOnline=novo;toast(novo?'Você está online. Pedidos novos chegam aqui.':'Você está offline. Nenhum pedido novo chega.',novo?'ok':undefined);
  }catch(error){toast(erroLegivel(error,'Não foi possível mudar sua disponibilidade.'),'err');}
  const linha=$('#disponibilidade');if(linha)linha.innerHTML=linhaDisponibilidade();
  carregarPedidos();
}

/* Cartão de uma oportunidade: urgência, nota do cliente, valor e as duas saídas (aceitar ou propor outro valor). */
function cartaoOportunidade(pedido,modo){
  if(pedido.obra)return cartaoObraOportunidade(pedido);
  const servico=SERVICES.find(item=>item.id===pedido.service),id=esc(String(pedido.id));
  const nota=Number(pedido.clienteNota)||0,n=Number(pedido.clienteAvaliacoes)||0;
  const aceitar=modo==='demo'?'aceitarPedidoDemo':'aceitarPedidoPersistido';
  const nova=oportunidadeNova(String(pedido.id));
  return '<article class="card op-in oportunidade'+(pedido.urgente?' urgente':'')+(nova?' op-nova':'')+'" id="op-'+id+'" onclick="marcarOportunidadeVista(&quot;'+id+'&quot;)">'+
    (pedido.urgente?'<span class="tag-urgente">'+ic('bolt')+' Urgente · para agora</span>':'')+(nova?'<span class="tag-nova">Nova</span>':'')+
    (pedido.recorrente?'<span class="tag-obra tag-rec">'+ic('refresh')+' Recorrente · '+Number(pedido.recorrente.vezes)+' vezes · total '+money(pedido.recorrente.total)+'</span>':'')+
    '<div class="op-corpo"><div class="ico-box">'+ic(servico?servico.ico:'wrench',servico?'ic-svc-'+servico.id:'')+'</div>'+
    '<div class="op-info"><b>'+esc(servico?servico.n:pedido.service)+'</b><p class="muted">'+esc(pedido.notes||'Pedido sem descrição.')+'</p>'+(textoRespostas(pedido.service,pedido.respostas)?'<p class="muted">'+textoRespostas(pedido.service,pedido.respostas)+'</p>':'')+
    '<small class="muted">'+(pedido.km!=null?'<b class="op-km">'+distanciaOportunidade(pedido)+'</b> · ':'')+(pedido.bairro?esc(pedido.bairro)+' · ':'')+(pedido.created?'há '+haQuanto(pedido.created)+' · ':'')+'endereço depois do aceite</small>'+
    '<span class="op-cliente">'+fotoPerfil(pedido.clienteUid,pedido.clienteNome||'Cliente',28,!!pedido.clienteTemFoto)+esc(pedido.clienteNome||'Cliente')+' · '+(n?stars(nota)+' '+nota.toFixed(1)+' ('+n+')':'cliente novo, sem avaliações')+'</span></div>'+
    '<div class="op-valor"><span class="muted">'+(pedido.recorrente?'Por vez':'Oferece')+'</span><b>'+money(pedido.price)+'</b></div></div>'+
    (pedido.recorrente?'<p class="op-agendado">'+ic('cal')+esc(nomesDias(pedido.recorrente.dias))+' às '+esc(pedido.recorrente.hora)+' · '+esc(diaRec(pedido.recorrente.inicio))+' a '+esc(diaRec(pedido.recorrente.fim))+'</p>':pedido.sched?'<p class="op-agendado">'+ic('cal')+'Para '+new Date(pedido.sched).toLocaleString('pt-BR',{weekday:'short',day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'})+'</p>':'')+
    (pedido.conflito?'<p class="op-conflito" role="note">'+ic('lock')+esc(pedido.conflito)+'</p>':'')+
    fotosOportunidade(pedido)+
    '<div class="op-proposta-slot" id="opp-'+id+'"></div>'+
    '<div class="op-acoes">'+(pedido.recorrente?'':'<button class="btn btn-ghost btn-sm" onclick="abrirContraproposta(&quot;'+id+'&quot;,null,'+Number(pedido.price||0)+')">Propor outro valor</button>')+
    '<button id="aceitar-'+id+'" class="btn btn-primary btn-sm"'+(pedido.conflito?' disabled title="Bate com a sua agenda"':'')+' onclick="'+aceitar+'(&quot;'+id+'&quot;)">'+ic('check')+' Aceitar '+(pedido.recorrente?Number(pedido.recorrente.vezes)+' vezes':money(pedido.price))+'</button></div></article>';
}
/* Obra: não se aceita direto; o profissional abre a obra e manda orçamento (ou propõe visita técnica). */
function cartaoObraOportunidade(p){
  const s=svc(p.service),id=esc(String(p.id));
  return '<article class="card op-in oportunidade op-obra" id="op-'+id+'">'+
    '<span class="tag-obra">'+ic('hammer')+' Obra · pede orçamento</span>'+
    '<div class="op-corpo"><div class="ico-box">'+ic(s.ico,'ic-svc-'+s.id)+'</div>'+
    '<div class="op-info"><b>'+esc(s.n)+'</b><p class="muted">'+esc(p.notes||'')+'</p>'+
    '<small class="muted">'+(p.km!=null?'<b class="op-km">'+distanciaOportunidade(p)+'</b> · ':'')+(p.bairro?esc(p.bairro)+' · ':'')+(p.created?'há '+haQuanto(p.created):'')+'</small>'+
    '<span class="op-cliente">'+fotoPerfil(p.clienteUid,p.clienteNome||'Cliente',28,!!p.clienteTemFoto)+esc(p.clienteNome||'Cliente')+'</span></div>'+
    '<div class="op-valor"><span class="muted">Até</span><b>'+money(p.price)+'</b></div></div>'+
    fotosOportunidade(p)+
    '<div class="op-acoes"><button class="btn btn-primary btn-sm" onclick="go(\'/app/obra/'+id+'\')">'+ic('file')+' Ver obra e orçar</button></div></article>';
}
function haQuanto(ts){const m=Math.max(1,Math.round((Date.now()-ts)/6e4));return m<60?m+' min':Math.round(m/60)+' h';}
/* Erro de carregamento em português: a causa e o que fazer. O detalhe técnico fica só no console. */
function mensagemErroPedidos(error){
  const status=Number(error&&error.status)||0;
  if(status===401)return {t:'Sua sessão expirou.',d:'Entre de novo para ver os pedidos da sua categoria.',acao:'<a class="btn btn-primary btn-sm" href="#/entrar?next=app/oportunidades">Entrar de novo</a>'};
  if(status===403)return {t:'Sua conta não pode receber pedidos agora.',d:error.message||'Veja a situação da conta em Minha área.',acao:'<a class="btn btn-ghost btn-sm" href="#/app/conta-situacao">Ver situação da conta</a>'};
  if(!status)return {t:'Sem conexão com o servidor.',d:'Confira sua internet. Se ela estiver funcionando, o servidor da TaskGO pode estar fora do ar.',acao:'<button class="btn btn-ghost btn-sm" onclick="carregarPedidos()">Tentar de novo</button>'};
  return {t:'O servidor não conseguiu responder agora.',d:'Tente de novo em alguns minutos.',acao:'<button class="btn btn-ghost btn-sm" onclick="carregarPedidos()">Tentar de novo</button>'};
}
const vazioOportunidades=()=>{const cat=me().service?svc(me().service).n:'da sua categoria';return '<div class="card emptystate">'+ic('target')+'<b>Nenhum pedido de '+esc(cat)+' agora</b><p style="margin-top:6px">Você só recebe pedidos da sua categoria. Quando um cliente pedir '+esc(cat)+', o pedido aparece aqui na hora.</p></div>';};
const offlineOportunidades=()=>'<div class="card emptystate">'+ic('moon')+'<b>Você está offline</b><p style="margin-top:6px">Fique online para ver e receber os pedidos da sua categoria.</p><button class="btn btn-primary" style="margin-top:14px" onclick="alternarDisponibilidade()">Ficar online</button></div>';
const ordenarUrgentes=lista=>lista.slice().sort((a,b)=>(b.urgente?1:0)-(a.urgente?1:0)||(b.created||0)-(a.created||0));

/* ---------- Modo demonstração: pedidos fictícios, sem chamar o servidor ---------- */
function oportunidadesDemo(){
  if(!S.demoOportunidades){
    const agora=Date.now();
    S.demoOportunidades=[
      {id:'op_demo_1',service:'eletrica',price:150,bairro:'Boa Viagem',clientId:'c2',clienteNome:'Camila',clienteNota:5.0,clienteAvaliacoes:7,urgente:true,created:agora-2*6e4,notes:'Chuveiro desarmando o disjuntor quando liga no quente. Preciso para agora.'},
      {id:'op_demo_2',service:'eletrica',price:110,bairro:'Pina',clientId:'c3',clienteNome:'Condomínio Aurora',clienteNota:4.9,clienteAvaliacoes:23,created:agora-12*6e4,notes:'Tomada da cozinha escureceu e parou de funcionar.'},
      {id:'op_demo_3',service:'eletrica',price:190,bairro:'Madalena',clientId:'c4',clienteNome:'Juliana',clienteNota:4.7,clienteAvaliacoes:3,created:agora-26*6e4,notes:'Instalar ventilador de teto no quarto. O ventilador já está comprado.'}
    ];
    Store.save();
  }
  const categoria=me().service;
  return S.demoOportunidades.filter(p=>!categoria||p.service===categoria);
}
function aceitarPedidoDemo(id,silencioso){
  const u=me(),i=(S.demoOportunidades||[]).findIndex(p=>p.id===id);if(i<0)return null;
  const p=S.demoOportunidades.splice(i,1)[0],cliente=(S.clients||[]).find(c=>c.id===p.clientId)||{};
  const pedido={id:'o_'+uid(),code:String(rand(1000,9999)),created:p.created,status:'a_caminho',price:p.price,service:p.service,proId:u.proId,proEmail:u.email,clientId:p.clientId,address:cliente.address||'',notes:p.notes,rating:null,progress:.05,acceptedAt:Date.now()};
  S.orders.unshift(pedido);Store.save();
  if(!silencioso){toast('Pedido aceito. O endereço do cliente já aparece no pedido.','ok');go('/app/pedido/'+pedido.id);}
  else if(route.sub==='oportunidades')go('/app/pedido/'+pedido.id);
  return pedido;
}

/* Preenche, em cada cartão, a situação da minha proposta (aguardando, contraproposta do cliente, recusada). */
async function preencherMinhasPropostas(lista){
  await Promise.all(lista.map(async pedido=>{
    try{const p=await minhaPropostaAtual(pedido.id),alvo=document.getElementById('opp-'+pedido.id);if(alvo)alvo.innerHTML=faixaPropostaPro(p,pedido.id);}
    catch(error){console.warn('Proposta de '+pedido.id+':',error);}
  }));
}
async function carregarPedidos(){
  const container=document.getElementById('mural-pedidos');
  if(!container)return;
  if(proOnline===null){await carregarDisponibilidade();const linha=$('#disponibilidade');if(linha)linha.innerHTML=linhaDisponibilidade();}
  if(!areaAtend)carregarAreaAtendimento();
  if(DEMO_MODE){
    if(!proOnline){container.innerHTML=offlineOportunidades();return;}
    const lista=ordenarUrgentes(oportunidadesDemo());
    container.innerHTML=lista.length?lista.map(p=>cartaoOportunidade(p,'demo')).join(''):vazioOportunidades();
    return preencherMinhasPropostas(lista);
  }
  /* Sem filtro: o servidor junta as oportunidades de todas as categorias do profissional. */
  try{
    const response=await fetch(API_PEDIDOS_OPORTUNIDADES+'/disponiveis?page='+paginaOportunidades+'&size=20&sort=created,desc');
    if(response.status===409){proOnline=false;const linha=$('#disponibilidade');if(linha)linha.innerHTML=linhaDisponibilidade();container.innerHTML=offlineOportunidades();return;}
    if(!response.ok)throw await erroDaResposta(response,'HTTP '+response.status);
    const resultado=await response.json();
    const pedidos=ordenarUrgentes(resultado&&Array.isArray(resultado.content)?resultado.content.filter(p=>p.id):[]);
    if(!$('#mural-pedidos'))return;
    if(!pedidos.length){container.innerHTML=vazioOportunidades();return;}
    container.innerHTML=pedidos.map(p=>cartaoOportunidade(p,'real')).join('')+
      (resultado.totalPages>1?'<div class="card" style="display:flex;justify-content:space-between;align-items:center;margin-top:12px"><button class="btn btn-ghost btn-sm" '+(resultado.first?'disabled':'')+' onclick="paginaOportunidades=Math.max(0,paginaOportunidades-1);carregarPedidos()">Anterior</button><small class="muted">Página '+(resultado.page+1)+' de '+Math.max(1,resultado.totalPages)+'</small><button class="btn btn-ghost btn-sm" '+(resultado.last?'disabled':'')+' onclick="paginaOportunidades++;carregarPedidos()">Próxima</button></div>':'');
    preencherMinhasPropostas(pedidos);
  }catch(error){
    console.error('Erro ao carregar pedidos:',error);
    const m=mensagemErroPedidos(error);
    container.innerHTML='<div class="card emptystate" role="alert">'+ic('target')+'<b>'+esc(m.t)+'</b><p style="margin-top:6px">'+esc(m.d)+'</p><div style="margin-top:14px">'+m.acao+'</div></div>';
  }
}
async function aceitarPedidoPersistido(id){
  const button=document.getElementById('aceitar-'+id),u=me();
  if(!u||!u.proId)return toast('Complete seu cadastro profissional para aceitar pedidos.','err');
  if(!button||button.disabled)return;
  button.disabled=true;button.textContent='Enviando...';
  try{
    const body=await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)+'/aceitar','POST',undefined,'O servidor não confirmou o aceite.');
    if(!body||String(body.id)!==String(id))throw new Error('O servidor retornou um pedido inválido.');
    body._persisted=true;
    const i=S.orders.findIndex(o=>String(o.id)===String(id));if(i<0)S.orders.unshift(body);else S.orders[i]=Object.assign(S.orders[i],body);
    Store.save();toast('Pedido aceito. Espere o cliente confirmar você para ver o endereço.','ok');go('/app/pedido/'+encodeURIComponent(id));
  }catch(error){
    console.error('Erro ao aceitar pedido:',error);toast(erroLegivel(error,'Não foi possível aceitar o pedido.'),'err');
    await carregarPedidos();
  }
}
