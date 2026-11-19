/* ---------- PROPOSTAS ----------
   Cliente: no pedido aguardando, vê as propostas dos profissionais, aceita, recusa ou contrapropõe.
   Profissional: em cada oportunidade, aceita o valor do cliente ou propõe outro e acompanha a resposta.
   No modo demonstração tudo roda no navegador, com profissionais e clientes fictícios respondendo sozinhos. */
const PROPOSTA_STATUS={pendente:'Aguardando resposta',aceita:'Aceita',recusada:'Recusada',substituida:'Trocada por outra',cancelada:'Retirada'};
const caminhoPropostas=id=>'/api/pedidos/contratados/'+encodeURIComponent(id)+'/propostas';

async function buscarPropostas(pedidoId){
  if(DEMO_MODE)return ((S.demoPropostas||{})[pedidoId]||[]).slice();
  return apiJSON(caminhoPropostas(pedidoId),'GET',undefined,'Não foi possível carregar as propostas.');
}

/* ================= Cliente: painel de propostas no pedido ================= */
function painelPropostasCliente(o){
  return '<section class="card propostas" style="margin-top:20px" aria-labelledby="ttl-propostas">'+
    '<div class="propostas-cab"><h2 class="sub" id="ttl-propostas">Propostas</h2><span class="ao-vivo" id="propostas-ao-vivo">'+ic('clock')+' Atualiza sozinho</span></div>'+
    '<p class="muted" style="font-size:.88rem;margin:6px 0 16px">Você ofereceu <b>'+money(o.price)+'</b>. Quem está online na categoria pode aceitar esse valor na hora ou mandar outro. Aceitando uma proposta, as outras são recusadas.</p>'+
    '<div id="lista-propostas" data-morph-manter="propostas" aria-live="polite"><p class="muted">Carregando propostas...</p></div></section>';
}
/* Agrupa por profissional: só a negociação mais recente de cada um aparece, com o histórico logo abaixo. */
function negociacoes(lista){
  const porPro={};lista.forEach(p=>{(porPro[p.profissionalId]=porPro[p.profissionalId]||[]).push(p);});
  return Object.values(porPro).map(h=>h.sort((a,b)=>a.criadaEm-b.criadaEm)).sort((a,b)=>b[b.length-1].criadaEm-a[a.length-1].criadaEm);
}
function linhaNegociacao(h,pedidoId){
  const ultima=h[h.length-1],nota=Number(ultima.profissionalNota)||0,n=Number(ultima.profissionalAvaliacoes)||0;
  const pendenteDele=ultima.status==='pendente'&&ultima.autor==='profissional';
  const pendenteMinha=ultima.status==='pendente'&&ultima.autor==='cliente';
  const historico=h.length>1?'<ol class="prop-historico">'+h.slice(0,-1).map(p=>'<li>'+(p.autor==='cliente'?'Você':'Profissional')+' · '+money(p.valor)+' · '+esc(PROPOSTA_STATUS[p.status]||p.status)+'</li>').join('')+'</ol>':'';
  return '<article class="prop'+(pendenteDele?' prop-nova':'')+'" id="prop-'+esc(ultima.profissionalId)+'">'+
    '<div class="prop-quem">'+(DEMO_MODE?avatar({name:ultima.profissionalNome||'Profissional'},44):fotoPerfil(ultima.profissionalId,ultima.profissionalNome||'Profissional',44))+
      '<div><b>'+esc(ultima.profissionalNome||'Profissional')+'</b><span class="prop-nota">'+(n?stars(nota)+' '+nota.toFixed(1)+' · '+n+(n===1?' avaliação':' avaliações'):'Ainda sem avaliações')+'</span></div></div>'+
    '<div class="prop-valor"><span class="muted">'+(ultima.autor==='cliente'?'Sua contraproposta':'Proposta')+'</span><b>'+money(ultima.valor)+'</b><small>'+esc(PROPOSTA_STATUS[ultima.status]||ultima.status)+'</small></div>'+
    (pendenteDele?'<div class="prop-acoes"><button class="btn btn-ghost btn-sm" onclick="recusarProposta(\''+esc(pedidoId)+'\',\''+esc(ultima.id)+'\')">Recusar</button>'+
      '<button class="btn btn-ghost btn-sm" onclick="abrirContraproposta(\''+esc(pedidoId)+'\',\''+esc(ultima.profissionalId)+'\','+ultima.valor+')">Contrapropor</button>'+
      '<button class="btn btn-grad btn-sm" onclick="aceitarProposta(\''+esc(pedidoId)+'\',\''+esc(ultima.id)+'\')">'+ic('check')+' Aceitar '+money(ultima.valor)+'</button></div>':'')+
    (pendenteMinha?'<div class="prop-acoes"><button class="btn btn-ghost btn-sm" onclick="retirarProposta(\''+esc(pedidoId)+'\',\''+esc(ultima.id)+'\')">Retirar contraproposta</button></div>':'')+
    historico+'</article>';
}
async function carregarPropostasPedido(pedidoId){
  const alvo=$('#lista-propostas');if(!alvo)return;
  try{
    const lista=await buscarPropostas(pedidoId);
    if(!$('#lista-propostas'))return;
    const grupos=negociacoes(lista);
    alvo.innerHTML=grupos.length?grupos.map(h=>linhaNegociacao(h,pedidoId)).join(''):
      '<div class="prop-vazio">'+ic('users')+'<div><b>Nenhuma proposta ainda</b><p class="muted">Seu pedido já está com os profissionais online da categoria. Quando alguém responder, aparece aqui na hora.</p></div></div>';
  }catch(error){
    alvo.innerHTML='<div class="prop-vazio" role="alert">'+ic('x')+'<div><b>'+esc(erroLegivel(error,'Não foi possível carregar as propostas.'))+'</b><p><button class="btn btn-ghost btn-sm" onclick="carregarPropostasPedido(\''+esc(pedidoId)+'\')">Tentar de novo</button></p></div></div>';
  }
}
async function aceitarProposta(pedidoId,propostaId){
  try{
    if(DEMO_MODE)demoAceitarProposta(pedidoId,propostaId);
    else{
      const pedido=await apiJSON(caminhoPropostas(pedidoId)+'/'+encodeURIComponent(propostaId)+'/aceitar','POST',undefined,'O servidor não confirmou o aceite.');
      const o=S.orders.find(x=>x.id===pedidoId);if(o)Object.assign(o,pedido,{_persisted:true});Store.save();
    }
    toast('Proposta aceita. O profissional já recebeu o endereço.','ok');render();
  }catch(error){toast(erroLegivel(error,'Não foi possível aceitar a proposta.'),'err');carregarPropostasPedido(pedidoId);}
}
async function recusarProposta(pedidoId,propostaId){
  try{
    if(DEMO_MODE)demoMudarProposta(pedidoId,propostaId,'recusada');
    else await apiJSON(caminhoPropostas(pedidoId)+'/'+encodeURIComponent(propostaId)+'/recusar','POST',undefined,'O servidor não confirmou a recusa.');
    toast('Proposta recusada.');carregarPropostasPedido(pedidoId);
  }catch(error){toast(erroLegivel(error,'Não foi possível recusar a proposta.'),'err');}
}
async function retirarProposta(pedidoId,propostaId){
  try{
    if(DEMO_MODE)demoMudarProposta(pedidoId,propostaId,'cancelada');
    else await apiJSON(caminhoPropostas(pedidoId)+'/'+encodeURIComponent(propostaId)+'/cancelar','POST',undefined,'O servidor não confirmou.');
    toast('Contraproposta retirada.');carregarPropostasPedido(pedidoId);if(route.sub==='oportunidades')carregarPedidos();
  }catch(error){toast(erroLegivel(error,'Não foi possível retirar a proposta.'),'err');}
}
/* Mesmo formulário para os dois lados: o cliente contrapropõe a um profissional; o profissional propõe ao cliente. */
function abrirContraproposta(pedidoId,profissionalId,valorAtual){
  const doPro=me().role==='profissional';
  openModal('<div class="modal-head"><div><h3>'+(doPro?'Propor outro valor':'Contrapropor')+'</h3><p>'+(doPro?'O cliente recebe na hora e pode aceitar, recusar ou responder com outro valor.':'O profissional recebe na hora e pode aceitar ou recusar.')+'</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<form onsubmit="event.preventDefault();enviarProposta(\''+esc(pedidoId)+'\','+(profissionalId?'\''+esc(profissionalId)+'\'':'null')+')">'+
    '<div class="oferta" style="margin-bottom:8px"><label for="valorProposta" class="leitor">Valor em reais</label><span class="oferta-moeda" aria-hidden="true">R$</span>'+
    '<input id="valorProposta" class="oferta-campo" type="number" inputmode="numeric" min="1" max="100000" step="5" required value="'+(Number(valorAtual)||'')+'"></div>'+
    '<p class="hint" style="margin-bottom:18px">'+(valorAtual?'Valor atual na mesa: '+money(valorAtual)+'.':'')+' Combinar valores fora da TaskGO não é permitido.</p>'+
    '<div style="display:flex;gap:10px"><button type="button" class="btn btn-ghost btn-block" onclick="closeModal()">Voltar</button><button class="btn btn-grad btn-block" id="btnEnviarProposta">Enviar proposta</button></div></form>');
  setTimeout(()=>{const c=$('#valorProposta');if(c){c.focus();c.select();}},40);
}
async function enviarProposta(pedidoId,profissionalId){
  const valor=Number(($('#valorProposta')||{}).value),botao=$('#btnEnviarProposta');
  if(!(valor>=1&&valor<=100000))return toast('Informe um valor de R$ 1 a R$ 100.000.','err');
  if(botao){botao.disabled=true;botao.textContent='Enviando...';}
  try{
    if(DEMO_MODE)demoNovaProposta(pedidoId,profissionalId,valor);
    else await apiJSON(caminhoPropostas(pedidoId),'POST',profissionalId?{valor,profissionalId:Number(profissionalId)}:{valor},'O servidor não confirmou a proposta.');
    closeModal();toast('Proposta de '+money(valor)+' enviada.','ok');
    if(route.sub==='oportunidades')carregarPedidos();else carregarPropostasPedido(pedidoId);
  }catch(error){
    toast(erroLegivel(error,'A proposta não foi enviada.'),'err');
    if(botao){botao.disabled=false;botao.textContent='Enviar proposta';}
  }
}

/* ================= Profissional: a própria proposta numa oportunidade ================= */
async function minhaPropostaAtual(pedidoId){
  const lista=await buscarPropostas(pedidoId);
  const minhas=lista.filter(p=>DEMO_MODE?p.profissionalId===me().proId:true).sort((a,b)=>a.criadaEm-b.criadaEm);
  return minhas[minhas.length-1]||null;
}
function faixaPropostaPro(p,pedidoId){
  if(!p)return '';
  if(p.status==='pendente'&&p.autor==='profissional')return '<div class="op-proposta">'+ic('clock')+'<span>Você propôs <b>'+money(p.valor)+'</b>. Aguardando o cliente.</span><button class="btn btn-ghost btn-sm" onclick="retirarProposta(\''+esc(pedidoId)+'\',\''+esc(p.id)+'\')">Retirar</button></div>';
  if(p.status==='pendente'&&p.autor==='cliente')return '<div class="op-proposta op-proposta-cliente">'+ic('bell')+'<span>O cliente contrapropôs <b>'+money(p.valor)+'</b>.</span>'+
    '<button class="btn btn-ghost btn-sm" onclick="recusarProposta(\''+esc(pedidoId)+'\',\''+esc(p.id)+'\');setTimeout(carregarPedidos,300)">Recusar</button>'+
    '<button class="btn btn-grad btn-sm" onclick="aceitarContrapropostaPro(\''+esc(pedidoId)+'\',\''+esc(p.id)+'\')">Aceitar '+money(p.valor)+'</button></div>';
  if(p.status==='recusada')return '<div class="op-proposta">'+ic('x')+'<span>Sua proposta de '+money(p.valor)+' foi recusada. Você pode aceitar o valor do cliente ou propor outro.</span></div>';
  return '';
}
async function aceitarContrapropostaPro(pedidoId,propostaId){
  try{
    let pedido;
    if(DEMO_MODE)pedido=demoAceitarComoPro(pedidoId,propostaId);
    else{pedido=await apiJSON(caminhoPropostas(pedidoId)+'/'+encodeURIComponent(propostaId)+'/aceitar','POST',undefined,'O servidor não confirmou o aceite.');pedido._persisted=true;
      const i=S.orders.findIndex(o=>o.id===pedido.id);if(i<0)S.orders.unshift(pedido);else Object.assign(S.orders[i],pedido);Store.save();}
    toast('Combinado! O endereço do cliente já aparece no pedido.','ok');go('/app/pedido/'+encodeURIComponent(pedido.id));
  }catch(error){toast(erroLegivel(error,'Não foi possível aceitar.'),'err');carregarPedidos();}
}

/* ================= Modo demonstração ================= */
function demoLista(pedidoId){S.demoPropostas=S.demoPropostas||{};return (S.demoPropostas[pedidoId]=S.demoPropostas[pedidoId]||[]);}
function demoProposta(pedidoId,pro,autor,valor){
  return {id:'pp_'+uid(),pedidoId,profissionalId:pro.proId||pro.id,profissionalNome:pro.name,profissionalNota:pro.rating||null,profissionalAvaliacoes:pro.reviews||0,autor,valor,status:'pendente',criadaEm:Date.now(),respondidaEm:null};
}
function demoAtualizarTela(pedidoId){
  Store.save();
  if(route.sub==='pedido'&&route.id===pedidoId)carregarPropostasPedido(pedidoId);
  if(route.sub==='oportunidades')carregarPedidos();
}
/* Depois de enviar um pedido, dois profissionais fictícios respondem em alguns segundos. */
function propostasDemoChegando(pedidoId){
  const o=S.orders.find(x=>x.id===pedidoId);if(!o)return;
  const pros=S.pros.filter(p=>p.service===o.service).slice(0,2);
  if(!pros.length)return;
  const valores=[o.price+20,o.price+45];
  pros.forEach((pro,i)=>setTimeout(()=>{
    const atual=S.orders.find(x=>x.id===pedidoId);if(!atual||atual.status!=='aguardando')return;
    demoLista(pedidoId).push(demoProposta(pedidoId,pro,'profissional',valores[i]));
    toast('Nova proposta de '+pro.name.split(' ')[0]+': '+money(valores[i])+'.','ok');demoAtualizarTela(pedidoId);
  },4000+i*5000));
}
function demoMudarProposta(pedidoId,propostaId,status){
  const p=demoLista(pedidoId).find(x=>x.id===propostaId);if(!p||p.status!=='pendente')throw new Error('Esta proposta já foi respondida.');
  p.status=status;p.respondidaEm=Date.now();Store.save();return p;
}
function demoFecharPedido(pedidoId,p){
  const o=S.orders.find(x=>x.id===pedidoId);
  demoLista(pedidoId).forEach(x=>{if(x.status==='pendente'&&x.id!==p.id){x.status='recusada';x.respondidaEm=Date.now();}});
  if(o)Object.assign(o,{status:'a_caminho',proId:p.profissionalId,professionalName:p.profissionalNome,price:p.valor,acceptedAt:Date.now(),progress:.05});
  Store.save();return o;
}
function demoAceitarProposta(pedidoId,propostaId){const p=demoMudarProposta(pedidoId,propostaId,'aceita');demoFecharPedido(pedidoId,p);}
function demoNovaProposta(pedidoId,profissionalId,valor){
  const lista=demoLista(pedidoId),u=me();
  if(u.role==='profissional'){
    lista.forEach(x=>{if(x.status==='pendente'&&x.profissionalId===u.proId){x.status='substituida';x.respondidaEm=Date.now();}});
    lista.push(demoProposta(pedidoId,u,'profissional',valor));Store.save();
    /* O cliente fictício aceita depois de alguns segundos. */
    setTimeout(()=>{
      const p=lista.find(x=>x.status==='pendente'&&x.profissionalId===u.proId&&x.valor===valor);if(!p)return;
      p.status='aceita';p.respondidaEm=Date.now();
      const op=(S.demoOportunidades||[]).find(x=>x.id===pedidoId);
      if(op){op.price=valor;aceitarPedidoDemo(pedidoId,true);toast('O cliente aceitou sua proposta de '+money(valor)+'.','ok');}
    },6000);
    return;
  }
  const pro=proById(profissionalId)||{proId:profissionalId,name:'Profissional'};
  lista.forEach(x=>{if(x.status==='pendente'&&String(x.profissionalId)===String(profissionalId)){x.status='substituida';x.respondidaEm=Date.now();}});
  const nova=demoProposta(pedidoId,pro,'cliente',valor);lista.push(nova);Store.save();
  /* O profissional fictício aceita a contraproposta. */
  setTimeout(()=>{
    if(nova.status!=='pendente')return;
    nova.status='aceita';nova.respondidaEm=Date.now();demoFecharPedido(pedidoId,nova);
    toast((pro.name||'O profissional').split(' ')[0]+' aceitou sua contraproposta de '+money(valor)+'.','ok');
    if(route.sub==='pedido'&&route.id===pedidoId)render();
  },5000);
}
function demoAceitarComoPro(pedidoId,propostaId){
  const p=demoMudarProposta(pedidoId,propostaId,'aceita');
  const op=(S.demoOportunidades||[]).find(x=>x.id===pedidoId);if(op)op.price=p.valor;
  return aceitarPedidoDemo(pedidoId,true);
}
