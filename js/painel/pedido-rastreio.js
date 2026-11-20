/* ---------- DETALHE DO PEDIDO / RASTREIO ---------- */
let trackTimer=null;
function dashPedido(id){
  const o=S.orders.find(x=>x.id===id);
  if(o&&o.obra)return dashObra(id);
  if(!o)return '<div class="card emptystate">'+ic('x')+'Pedido não encontrado<div style="margin-top:14px"><a class="btn btn-ghost" href="#/app/pedidos">Voltar</a></div></div>';
  /* Pedido aberto ainda não tem profissional: os dados dele vêm do próprio pedido quando existir. */
  const p=proById(o.proId)||{name:o.professionalName||'Profissional',rating:0,reviews:0,bio:o.status==='aguardando'?'Ainda sem profissional: escolha uma proposta.':o.status==='aguardando_pagamento'?'Ainda sem profissional: o pedido sai depois do pagamento.':''},cli=clientOf(o),s=svc(o.service),isPro=ladoAtende(o),cp=isPro?cli:p;
  const cpRating=Number(cp.rating)||0;
  const cpRatingHtml=cpRating>0?stars(cpRating)+' '+cpRating.toFixed(1):'<span class="muted">Sem avaliações</span>';
  const reviewRating=isPro?(o.clientRating||(o.reviewByRole==='profissional'?o.rating:null)):(o.reviewByRole==='profissional'?null:o.rating);
  const rec=!!(o.recorrente&&o.recorrente.vezes);
  const pendente=o.confirmacaoCliente==='pendente'&&(o.status==='a_caminho'||rec&&o.status==='agendado');
  const live=o.status==='a_caminho'&&!pendente;
  return '<button type="button" class="btn-voltar" style="margin-bottom:18px" onclick="go(\'/app/pedidos\')">'+ic('arrowLeft')+'<span>'+(isPro?'Atendimentos':'Meus pedidos')+'</span></button>'+
  dashHead(s.n,'Criado em '+dt(o.created),stTag(o.status))+
  (pendente?(isPro?
    '<div class="card espera-confirmar">'+ic('clock')+'<div><b>Esperando '+esc(cli.name.split(' ')[0])+' confirmar você</b><p class="muted">O cliente está vendo o seu perfil. Quando ele confirmar, o endereço e o telefone aparecem aqui e você já pode sair. Se ele recusar, o pedido some daqui sem punição.</p></div></div>'
    :'<div class="card confirmar-pro" id="confirmar-pro" data-morph-manter="'+esc(o.id)+'" data-pedido="'+esc(o.id)+'"><p class="muted">Carregando o perfil de quem aceitou...</p></div>'):'')+
  colunasPedido(''+
    (rec?blocoRecorrente(o,isPro):'')+
    /* Concluído ou cancelado: sem mapa, mensagens nem emergência; o atendimento acabou. */
    (!rec&&!['cancelado','concluido','aguardando','aguardando_pagamento'].includes(o.status)&&!pendente?'<div class="card">'+
      (live?mapaRastreioCard(o,isPro):
      '<div class="center muted" style="padding:18px 0">'+(o.status==='chegou'?'Chegada informada pelo profissional. O compartilhamento de GPS foi encerrado.':o.status==='concluido'?'Serviço concluído. O compartilhamento de GPS foi encerrado.':o.status==='cancelado'?'Pedido cancelado. O compartilhamento de GPS foi encerrado.':'Rastreamento disponível quando o profissional estiver a caminho.')+'</div>')+ 
      '<div style="display:flex;justify-content:space-between;align-items:center;margin:14px 2px 0;font-size:.85rem"><span class="muted">Endereço do atendimento</span>'+ 
      '<span class="muted">'+esc(o.address||'')+'</span></div>'+ 
      '<div style="display:flex;gap:10px;margin-top:20px;flex-wrap:wrap">'+
      '<a class="btn btn-ghost btn-sm" href="#/app/mensagens">'+ic('chat')+' Mensagem</a>'+
      '<button class="btn btn-danger btn-sm" onclick="emergencia()">'+ic('phone')+' Emergência</button></div>'+
    '</div>':'')+
    /* CÓDIGO */
    (!rec&&['a_caminho','chegou'].includes(o.status)&&!pendente?(isPro
     /* PROFISSIONAL: digita o código que o cliente informa */
     ?(o.status==='chegou'?'<div class="card" style="margin-top:20px;text-align:center;border-top-color:var(--brand)">'+
      '<div class="ico-box" style="margin:0 auto 12px">'+ic('key')+'</div>'+
      '<h2 class="sub" style="font-size:1.05rem">Validação de chegada</h2>'+
      '<p class="muted" style="font-size:.88rem;margin-top:6px;max-width:440px;margin-inline:auto">Ao chegar, peça ao cliente o código de 4 dígitos que aparece no aplicativo dele e digite abaixo. O serviço só começa depois disso.</p>'+
      '<div class="code-boxes">'+[0,1,2,3].map(i=>'<input id="cb'+i+'" inputmode="numeric" maxlength="1" oninput="cbNext('+i+')">').join('')+'</div>'+
      '<button class="btn btn-grad" onclick="validarCodigo(\''+o.id+'\')">'+ic('checkCircle')+' Validar e iniciar serviço</button>'+
      '</div>':'<div class="card" style="margin-top:20px"><b>Aguardando chegada</b><p class="muted">O código só pode ser informado depois que o profissional registrar a chegada no pedido.</p></div>')
     /* CLIENTE: mostra o código para informar ao profissional */
     :'<div class="card" style="margin-top:20px;text-align:center;border-top-color:var(--brand)">'+
      '<div class="ico-box" style="margin:0 auto 12px">'+ic('key')+'</div>'+
      '<h2 class="sub" style="font-size:1.05rem">Seu código de segurança</h2>'+
      '<p class="muted" style="font-size:.88rem;margin-top:6px;max-width:440px;margin-inline:auto">Informe este código a '+esc(p.name.split(' ')[0])+' somente quando ele chegar ao local. Ele digita o código no app para iniciar o serviço.</p>'+
      '<div class="big-code" style="margin:20px 0">'+o.code+'</div>'+
      '<span class="pill '+(o.status==='chegou'?'pill-brand':'')+'">'+(o.status==='chegou'?ic('mapPin')+' O profissional chegou — informe o código':ic('clock')+' Aguardando a chegada do profissional')+'</span>'+
      '<details class="demo-note"><summary>Testar a visão do profissional</summary><span class="demo-note-content">Crie ou acesse uma conta profissional para acompanhar o pedido por esse perfil.</span></details></div>'):'')+
    (o.status==='aguardando'?(isPro?
     '<div class="card" style="margin-top:20px;border-top-color:var(--brand)">'+
     '<div style="display:flex;gap:14px;align-items:flex-start"><div class="ico-box">'+ic('bell')+'</div><div style="flex:1"><b>Novo pedido recebido</b><p class="muted" style="font-size:.88rem;margin-top:6px">'+esc(cli.name)+' solicitou '+esc(s.n)+' por '+money(o.price)+'. Revise o endereço e confirme se pode atender.</p></div></div>'+ 
     '<div style="display:flex;gap:10px;margin-top:18px;flex-wrap:wrap"><button class="btn btn-ghost" onclick="recusarPedidoCliente(\''+o.id+'\')">Recusar pedido</button><button class="btn btn-grad" onclick="confirmarPedidoCliente(\''+o.id+'\')">'+ic('check')+' Confirmar atendimento</button></div></div>'
     :o.proEmail?'<div class="card" style="margin-top:20px"><b>Enviado para '+esc(o.professionalName||'o profissional')+'</b><p class="muted" style="font-size:.88rem;margin-top:6px">Você recebe um aviso quando ele responder. Se ele recusar, o pedido é cancelado. Sem resposta, cancele e peça de novo para todos em Contratar.</p></div>':painelPropostasCliente(o)):'')+
    (o.status==='em_andamento'&&!rec?
     '<div class="card" style="margin-top:20px;display:flex;gap:16px;align-items:center;flex-wrap:wrap">'+
     '<div class="ico-box">'+ic('play')+'</div><div style="flex:1;min-width:200px"><b>Serviço em andamento</b>'+
     '<p class="muted" style="font-size:.88rem">Iniciado após validação do código. Ao terminar, confirme a conclusão para liberar o pagamento.</p></div>'+
     '<button class="btn btn-grad" onclick="concluirServico(\''+o.id+'\')">'+ic('check')+' Concluir serviço</button></div>':'')+
    (o.status==='concluido'?
     '<div class="card" style="margin-top:20px">'+
     '<div style="display:flex;gap:16px;align-items:center;flex-wrap:wrap"><div class="ico-box" style="background:color-mix(in srgb,var(--accent) 14%,transparent)">'+ic('checkCircle')+'</div>'+
     '<div style="flex:1;min-width:200px"><b>'+(isPro?'Serviço concluído':'Pedido finalizado')+'</b><p class="muted" style="font-size:.88rem">'+(reviewRating?'Você avaliou com '+reviewRating+' estrelas.':(isPro?'Conte como foi sua experiência com o cliente.':'Avalie o profissional que realizou o serviço.'))+'</p></div>'+ 
     (reviewRating?stars(reviewRating):'<button class="btn btn-primary" onclick="abrirAvaliacao(\''+o.id+'\')">'+ic('star')+' '+(isPro?'Avaliar cliente':'Avaliar profissional')+'</button>')+'</div>'+ 
     '<div style="display:flex;gap:10px;margin-top:16px;flex-wrap:wrap"><button class="btn btn-ghost btn-sm" onclick="baixarRecibo(\''+o.id+'\')">'+ic('file')+' Baixar recibo</button>'+
     (isPro?'':o.proEmail&&!o.obra&&o.service!=='ajuda'&&!o.recorrente?'<button class="btn btn-primary btn-sm" onclick="chamarDeNovo(\''+o.id+'\')">'+ic('refresh')+' Chamar '+esc(String(o.professionalName||'o profissional').split(' ')[0])+' de novo</button>':
       '<a class="btn btn-ghost btn-sm" href="#/app/contratar?servico='+o.service+'">'+ic('refresh')+' Contratar novamente</a>')+'</div></div>':'')+
    blocoFotosPedido(o)+
    (o.notes?'<div class="card" style="margin-top:20px"><b style="font-size:.9rem">Descrição do pedido</b><p class="muted" style="font-size:.9rem;margin-top:8px">'+esc(o.notes)+'</p></div>':'')+
  '',''+
    (['aguardando','aguardando_pagamento'].includes(o.status)&&!isPro?'':'<div class="card" style="margin-bottom:16px"><h2 class="sub" style="font-size:1rem;margin-bottom:16px">'+(isPro?'Cliente':'Profissional')+'</h2>'+
    '<div style="display:flex;gap:12px;align-items:center">'+avatar(counterpart(o),52)+
    '<div><b style="display:block">'+esc(cp.name)+seloVerificado(isPro?o.clientVerificado:o.proVerificado,true)+seloPro(!isPro&&o.proPlanoPro,true)+'</b><span class="muted" style="font-size:.8rem">'+cpRatingHtml+' · '+
      (isPro?(Number(cli.jobs)||0)+' pedidos na TaskGO':p.reviews+' avaliações')+'</span>'+ 
    '</div></div>'+ 
    (isPro?'<div style="margin-top:14px">'+
       '<div style="display:flex;justify-content:space-between;gap:12px;padding:9px 0;border-bottom:1px solid var(--line)"><span class="muted" style="font-size:.84rem">Telefone</span><b style="font-size:.84rem">'+esc(cli.phone||'—')+'</b></div>'+
       '<div style="display:flex;justify-content:space-between;gap:12px;padding:9px 0"><span class="muted" style="font-size:.84rem">Cliente desde</span><b style="font-size:.84rem">'+new Date(cli.since||Date.now()).toLocaleDateString('pt-BR',{month:'short',year:'numeric'})+'</b></div></div>'
          :(p.bio?'<p class="muted" style="font-size:.86rem;margin-top:14px">'+esc(p.bio)+'</p>':''))+
    (!['aguardando','aguardando_pagamento'].includes(o.status)?'<div style="margin-top:14px">'+botaoDenunciar(o.id,cp.name)+'</div>':'')+'</div>')+
    (rec?'':pedidoEmDinheiro(o)?blocoDinheiroPedido(o,isPro):blocoPagamentoPedido(o,isPro))+
    '<div class="card"><h2 class="sub" style="font-size:1rem;margin-bottom:14px">Resumo</h2>'+
    (rec?[['Endereço',o.address||'—'],['Categoria',s.n],['Pagamento',modeloDireto()?'Pix ao profissional, a cada vez':'Por semana, pela TaskGO'],['Cada vez',money(o.price)],['Total',money(o.recorrente.total)]]:
    [['Endereço',o.address||'—'],['Categoria',s.n],['Pagamento',DEMO_MODE?'Demonstração':rotuloPagamentoPedido(o)],[['aguardando','aguardando_pagamento'].includes(o.status)?'Você oferece':'Valor combinado',money(o.price)]])
    .map(r=>'<div style="display:flex;justify-content:space-between;gap:14px;padding:10px 0;border-bottom:1px solid var(--line)"><span class="muted" style="font-size:.85rem">'+r[0]+'</span><b style="font-size:.85rem;text-align:right">'+esc(r[1])+'</b></div>').join('')+
    (['a_caminho','agendado','aguardando','aguardando_pagamento'].includes(o.status)||rec&&o.status==='em_andamento'?'<button class="btn btn-danger btn-block" style="margin-top:16px" onclick="cancelarPedido(\''+o.id+'\')">'+(rec&&['agendado','em_andamento'].includes(o.status)?'Cancelar próximas vezes':'Cancelar pedido')+'</button>':'')+
    '</div>'+
    (['concluido','cancelado'].includes(o.status)?'':'<div class="card" style="margin-top:16px">'+
    '<div style="display:flex;gap:10px"><div style="flex:none">'+ic(isPro?'key':'shield')+'</div><div><b style="font-size:.88rem">'+(isPro?(rec?'Em cada vez':'Antes de iniciar'):'Dica de segurança')+'</b>'+
    '<p class="muted" style="font-size:.84rem;margin-top:4px">'+(isPro?(rec?'Ao terminar, marque a vez como feita. O valor vai para a sua carteira quando o cliente confirmar, ou em 24 horas.':'Peça o código de 4 dígitos ao cliente e valide no app. Só depois inicie o serviço.'):'Pague só com o serviço pronto e combine tudo pelo chat do pedido. Algo errado? Use o botão de emergência ou denuncie.')+'</p></div></div></div>')+
  '');
}
/* Duas colunas só quando a da esquerda tem conteúdo; senão os cartões ocupam a largura toda, lado a lado. */
function colunasPedido(esq,dir){
  if(String(esq).trim())return '<div class="grid g2 pedido-colunas"><div>'+esq+'</div><div>'+dir+'</div></div>';
  return '<div class="pedido-solo">'+dir+'</div>';
}
function startTracking(){
  const o=S.orders.find(x=>x.id===route.id);
  if(!o)return;
  if(!DEMO_MODE){
    if(o.status!=='a_caminho'||o.confirmacaoCliente==='pendente'){pararRastreio();return;}
    ligarRastreio(o);
    return;
  }
  initTrackingMap(o.id,o.progress||0);
  placeMarker('live',o.progress||0);
  if(o.status!=='a_caminho')return;
  const duration=45000;
  if(!o.trackingStartedAt)o.trackingStartedAt=Date.now()-(Math.max(0,Math.min(1,o.progress||0))*duration);
  trackTimer=setInterval(()=>{
    o.progress=Math.max(0,Math.min(1,(Date.now()-o.trackingStartedAt)/duration));
    const totalDist=4.6;
    o.dist=+((1-o.progress)*totalDist).toFixed(1);
    o.eta=Math.max(1,Math.round((1-o.progress)*18));
    placeMarker('live',o.progress);
    const d=$('#liveDist'),e=$('#liveEta');
    if(d)d.textContent=(o.dist==null?'—':Number(o.dist).toFixed(1));
    if(e)e.textContent=o.eta;
    if(o.progress>=1){
      clearInterval(trackTimer);trackTimer=null;
      guardarPedidoAlterado(o,{status:'chegou'}).then(()=>{toast('O profissional chegou ao local!','ok');render();}).catch(error=>{toast(error.message||'O servidor não confirmou a chegada.','err');render();});
    }else Store.save();
  },300);
}
function gpsStatus(message){const el=$('#gps-status');if(el)el.textContent=message;}
/* Chegada: uma vez por toque (o botão trava enquanto envia), com tempo limite em cada etapa.
   Posição: a do compartilhamento, se for recente; senão pede ao aparelho (precisa e, se demorar, aproximada).
   Se a resposta se perder, confere no servidor: a chegada pode já ter sido registrada. */
let chegando=null;
const ERRO_GPS={1:'Permita o acesso à localização (cadeado na barra de endereço) para informar a chegada.',2:'O aparelho não conseguiu achar sua posição. Ligue o GPS e tente de novo.',3:'A localização demorou demais. Vá para um lugar aberto, perto de uma janela, e toque em Cheguei de novo.'};
function comTempo(promessa,ms,texto){let t;return Promise.race([promessa,new Promise((_,erro)=>{t=setTimeout(()=>erro(new Error(texto)),ms);})]).finally(()=>clearTimeout(t));}
function posicaoParaChegada(){
  const ult=rastreio&&rastreio.ultimaPos;
  if(ult&&Date.now()-ult.timestamp<60000)return Promise.resolve(ult);
  const pedir=opc=>new Promise((ok,erro)=>navigator.geolocation.getCurrentPosition(ok,erro,opc));
  return pedir({enableHighAccuracy:true,timeout:9000,maximumAge:30000})
    .catch(e=>{if(e&&e.code===1)throw e;return pedir({enableHighAccuracy:false,timeout:9000,maximumAge:120000});});
}
function botoesChegada(ocupado){
  document.querySelectorAll('[data-chegada]').forEach(b=>{b.disabled=ocupado;b.setAttribute('aria-busy',String(ocupado));b.innerHTML=ocupado?'<span class="pag-ponto"></span> Confirmando...':ic('mapPin')+' Cheguei';});
}
async function informarChegada(id){
  if(chegando)return;
  const o=S.orders.find(item=>item.id===id);if(!o||!souAtendente(o)||o.status!=='a_caminho')return toast('Não foi possível informar a chegada neste pedido.','err');
  chegando=id;botoesChegada(true);
  try{
    /* Com ponto marcado pelo cliente, manda a posição antes: o servidor só aceita a chegada a até 150 m. */
    if(!DEMO_MODE&&o.lat!=null){
      if(!navigator.geolocation)throw new Error('Este aparelho não informa a localização. Sem ela, não dá para confirmar a chegada.');
      gpsStatus('Conferindo sua localização...');
      let pos;
      try{pos=await posicaoParaChegada();}catch(e){throw new Error(ERRO_GPS[e&&e.code]||'Não foi possível obter sua localização.');}
      const r=await comTempo(fetch(API_BASE_URL+'/api/pedidos/contratados/'+encodeURIComponent(id)+'/localizacao',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({latitude:pos.coords.latitude,longitude:pos.coords.longitude,accuracy:Math.min(pos.coords.accuracy||50,9999),measuredAt:Date.now()})}),15000,'A conexão está lenta. Toque em Cheguei de novo.');
      if(!r.ok&&r.status!==429)throw await erroDaResposta(r,'Não foi possível enviar sua localização.');
    }
    gpsStatus('Registrando a chegada...');
    try{await comTempo(guardarPedidoAlterado(o,{status:'chegou'}),20000,'O servidor demorou para responder.');}
    catch(erroEnvio){
      /* A resposta pode ter se perdido com a chegada já registrada: confere antes de acusar erro. */
      await atualizarPedidoLocal(id);
      const atual=S.orders.find(item=>item.id===id);
      if(!atual||atual.status!=='chegou')throw erroEnvio;
    }
    pararCompartilhamentoGPS();gpsStatus('');toast('Chegada registrada. O cliente foi avisado.','ok');
  }catch(error){gpsStatus('');toast(erroLegivel(error,'O servidor não confirmou a chegada.')+' Toque em Cheguei para tentar de novo.','err');}
  finally{chegando=null;render();}
}
window.addEventListener('beforeunload',()=>{if(typeof pararCompartilhamentoGPS==='function')pararCompartilhamentoGPS();});
function abrirValidacao(id){
  openModal('<div class="modal-head"><div><h3>Validar chegada</h3><p>Digite o código de 4 dígitos para iniciar o serviço.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
  '<div class="code-boxes">'+[0,1,2,3].map(i=>'<input id="cb'+i+'" inputmode="numeric" maxlength="1" oninput="cbNext('+i+')">').join('')+'</div>'+
  '<p class="hint center" style="margin-bottom:16px">Nunca compartilhe o código antes do profissional chegar.</p>'+
  '<button class="btn btn-grad btn-block" onclick="validarCodigo(\''+id+'\')">Confirmar</button>',
  {onOpen:()=>{const f=$('#cb0');if(f)f.focus();}});
}
function cbNext(i){const el=$('#cb'+i);el.value=el.value.replace(/\D/g,'');if(el.value&&i<3)$('#cb'+(i+1)).focus();}
async function validarCodigo(id){
  const o=S.orders.find(x=>x.id===id);
  const v=[0,1,2,3].map(i=>($('#cb'+i)||{}).value||'').join('');
  if(!/^[0-9]{4}$/.test(v))return toast('Digite os 4 números do código.','err');
  /* Fora da demonstração quem confere é o servidor: o profissional nunca recebe o código. */
  if(DEMO_MODE&&v!==o.code)return toast('Código incorreto. Peça ao cliente para conferir o código no aplicativo dele.','err');
  try{await guardarPedidoAlterado(o,{status:'em_andamento',progress:1,code:v});closeModal();toast('Chegada validada! Serviço iniciado.','ok');render();}
  catch(error){toast(erroLegivel(error,'O servidor não confirmou o início do serviço.'),'err');}
}
async function confirmarPedidoCliente(id){
  const order=S.orders.find(item=>item.id===id),user=me();
  if(!order||!user||!souAtendente(order))return toast('Este pedido não pertence ao seu perfil.','err');
  if(order.status!=='aguardando')return toast('Este pedido já foi respondido.','err');
  try{
    const response=await fetch(API_BASE_URL+'/api/pedidos/contratados/'+encodeURIComponent(id)+'/aceitar',{method:'POST'});
    if(!response.ok)throw new Error('O servidor não confirmou o aceite.');
    Object.assign(order,await response.json());toast('Atendimento confirmado.','ok');render();
  }catch(error){toast(error.message||'Não foi possível aceitar o pedido.','err');}
}
async function recusarPedidoCliente(id){
  const o=S.orders.find(x=>x.id===id),u=me();
  if(!o||!u||!souAtendente(o))return toast('Este pedido não pertence ao seu perfil.','err');
  if(o.status!=='aguardando')return toast('Este pedido já foi respondido.','err');
  try{await guardarPedidoAlterado(o,{status:'cancelado',cancelReason:'professional_unavailable'});toast('Pedido recusado.','ok');go('/app/pedidos');}
  catch(error){toast(error.message||'O servidor não confirmou a recusa.','err');}
}
let ratingVal=5;
function abrirAvaliacao(id){
  ratingVal=1;
  const isPro=ladoAtende(S.orders.find(x=>x.id===id));
  const reviewTags=isPro
    ? ['Comunicativo','Respeitoso','Acesso facilitado','Informa\u00e7\u00f5es claras','Local organizado','Colaborativo']
    : ['Pontual','Educado','Caprichoso','Pre\u00e7o justo','Limpo','R\u00e1pido'];
  const reviewTitle=isPro?'Avalie o cliente':'Avalie o profissional';
  const reviewSubtitle=isPro
    ? 'Sua avalia\u00e7\u00e3o ajuda outros profissionais da TaskGO.'
    : 'Sua opini\u00e3o ajuda toda a comunidade TaskGO.';
  const reviewPlaceholder=isPro
    ? 'Como foi a comunica\u00e7\u00e3o e o atendimento do cliente?'
    : 'Conte como foi o servi\u00e7o...';
  openModal('<div class="modal-head"><div><h3>'+reviewTitle+'</h3><p>'+reviewSubtitle+'</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
  '<div id="rstars" role="group" aria-label="Escolha uma nota de 1 a 5 estrelas" style="display:flex;gap:8px;justify-content:center;margin:10px 0 20px">'+
  [1,2,3,4,5].map(i=>'<button type="button" aria-label="Avaliar com '+i+' '+(i===1?'estrela':'estrelas')+'" aria-pressed="'+(i===1?'true':'false')+'" onclick="setRating('+i+')" style="background:none;border:0;cursor:pointer;padding:0"><svg viewBox="0 0 24 24" style="width:38px;height:38px;fill:var(--warn);opacity:'+(i<=ratingVal?1:.25)+';transition:opacity .15s,transform .15s" data-s="'+i+'">'+I.star+'</svg></button>').join('')+'</div>'+ 
  '<div class="field"><label>Coment\u00e1rio (opcional)</label><textarea class="textarea" id="rcom" placeholder="'+reviewPlaceholder+'"></textarea></div>'+
  '<div id="reviewTags" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:18px">'+reviewTags.map(t=>'<button type="button" class="pill" aria-pressed="false" onclick="toggleReviewTag(this)">'+t+'</button>').join('')+'</div>'+
  '<button class="btn btn-grad btn-block" onclick="salvarAvaliacao(\''+id+'\')">Enviar avalia\u00e7\u00e3o</button>');
}
function toggleReviewTag(button){
  const selected=button.classList.toggle('pill-brand');
  button.setAttribute('aria-pressed',selected?'true':'false');
}
function setRating(n){
  ratingVal=Math.max(1,Math.min(5,Number(n)||1));
  $$('#rstars button').forEach((b,i)=>{
    const star=i+1;
    b.setAttribute('aria-pressed',star===ratingVal?'true':'false');
    const svg=b.querySelector('svg');
    if(svg){svg.style.opacity=star<=ratingVal?1:.25;svg.style.transform=star<=ratingVal?'scale(1.06)':'scale(1)';}
  });
}
async function salvarAvaliacao(id){
  const order=S.orders.find(x=>x.id===id);if(!order)return toast('Pedido não encontrado.','err');
  try{
    const response=await fetch(API_BASE_URL+'/api/pedidos/contratados/'+encodeURIComponent(id)+'/avaliacoes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({rating:ratingVal,comment:($('#rcom')||{}).value||''})});
    if(!response.ok)throw new Error('A avaliação não foi confirmada pelo servidor.');
    const saved=await response.json();Object.assign(order,saved);closeModal();toast('Avaliação salva. Obrigado!','ok');render();
  }catch(error){toast(error.message||'Não foi possível salvar a avaliação.','err');}
}
/* Cliente: confere quem aceitou (foto, nota, serviços feitos, minutos até chegar) e confirma ou recusa. */
async function carregarConfirmacao(id){
  const no=document.getElementById('confirmar-pro');if(!no||no.dataset.carregado===id)return;no.dataset.carregado=id;
  let d;try{d=await apiJSON('/api/chamar-agora/'+encodeURIComponent(id));}catch(e){no.innerHTML='<p class="muted">'+esc(erroLegivel(e,'Não foi possível carregar o perfil.'))+'</p>';return;}
  const pr=d.profissional;if(!pr){no.dataset.carregado='';return;}
  const nota=pr.nota?'<span class="folha-nota">'+stars(pr.nota)+' '+String(pr.nota.toFixed(1)).replace('.',',')+'</span> <span class="muted">('+pr.avaliacoes+')</span>':'<span class="muted">Ainda sem avaliações</span>';
  no.innerHTML='<div class="folha-pro sobe"><h2 class="sub">'+esc(pr.nome.split(' ')[0])+' aceitou o seu pedido</h2>'+
    '<div class="folha-quem">'+fotoPerfil(pr.uid,pr.nome,72,pr.temFoto)+'<div><b class="folha-nome">'+esc(pr.nome)+'</b>'+nota+
    '<span class="folha-feitos">'+ic('checkCircle')+' '+pr.servicos+' '+(pr.servicos===1?'serviço feito':'serviços feitos')+' na TaskGO</span></div>'+
    '<div class="folha-eta">'+(pr.minutos?'<b>'+pr.minutos+'</b><span>min até você</span>':'<span>sem posição agora</span>')+(pr.km!=null?'<small>'+String(pr.km).replace('.',',')+' km</small>':'')+'</div></div>'+
    '<p class="muted folha-dica">O endereço e o seu telefone só vão para ele depois que você confirmar. Se não gostar do perfil, recuse: o pedido volta a procurar.</p>'+
    '<div class="folha-acoes"><button type="button" class="btn btn-ghost" onclick="recusarProfissional(\''+esc(id)+'\')">Recusar</button>'+
    '<button type="button" class="btn btn-grad btn-lg" onclick="confirmarProfissional(\''+esc(id)+'\')">'+ic('check')+' Confirmar '+esc(pr.nome.split(' ')[0])+'</button></div></div>';
}
function cancelarPedido(id){
  const o=S.orders.find(x=>x.id===id);
  openModal('<div class="modal-head"><div><h3>Cancelar pedido</h3><p>Essa ação não pode ser desfeita.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
  '<div class="field"><label>Motivo do cancelamento</label><select class="select" id="cmot"><option>Resolvi o problema sozinho</option><option>Encontrei outro profissional</option><option>O valor ficou acima do esperado</option><option>Demora no atendimento</option><option>Outro motivo</option></select></div>'+
  '<p class="aviso-linha" style="margin-bottom:18px">Ao cancelar, o pedido deixa de valer para as duas pessoas'+(o&&o.paid==='retido'?' e o que você pagou volta inteiro':'')+'.</p>'+
  '<div style="display:flex;gap:10px"><button class="btn btn-ghost btn-block" onclick="closeModal()">Voltar</button>'+
  '<button class="btn btn-danger btn-block" onclick="confirmarCancel(\''+id+'\')">Cancelar pedido</button></div>');
}
async function confirmarCancel(id){
  const o=S.orders.find(x=>x.id===id);if(!o)return toast('Pedido não encontrado.','err');
  try{
    await guardarPedidoAlterado(o,{status:'cancelado',cancelReason:(($('#cmot')||{}).value||o.cancelReason||'')});closeModal();
    /* Cliente volta ao Contratar com tudo preenchido, para pedir de novo em um toque. */
    if(!ladoAtende(o)){reaproveitarPedido(o);toast('Pedido cancelado'+(o.paid==='retido'?' e valor devolvido':'')+'. Seus dados continuam no Contratar.','ok');go('/app/contratar');return;}
    toast('Pedido cancelado.','ok');render();
  }
  catch(error){toast(error.message||'O servidor não confirmou o cancelamento.','err');}
}
/* Recibo do serviço: abre numa janela própria, pronto para imprimir ou salvar em PDF. */
function baixarRecibo(id){
  const o=S.orders.find(x=>x.id===id);if(!o)return toast('Pedido não encontrado.','err');
  const isPro=ladoAtende(o),cli=clientOf(o),s=svc(o.service),d=o.dinheiro||{};
  const quando=new Date(Number(o.completedAt||o.created)).toLocaleString('pt-BR',{dateStyle:'long',timeStyle:'short'});
  const linha=(r,v)=>'<tr><th>'+r+'</th><td>'+v+'</td></tr>';
  const html='<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>Recibo TaskGO '+esc(o.id)+'</title>'+
    '<style>body{font:15px/1.5 system-ui,Segoe UI,Roboto,sans-serif;color:#111;max-width:640px;margin:40px auto;padding:0 24px}h1{font-size:22px;margin:0}'+
    '.topo{display:flex;justify-content:space-between;align-items:baseline;border-bottom:2px solid #1f4fff;padding-bottom:12px;margin-bottom:24px}.marca{font-weight:800;font-size:20px}.marca b{color:#1f4fff}'+
    'table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:9px 0;border-bottom:1px solid #e5e5e5;vertical-align:top}th{color:#555;font-weight:500;width:42%}'+
    '.total td{font-size:20px;font-weight:700}.nota{color:#555;font-size:13px;margin-top:24px}button{margin-top:24px;padding:10px 18px;font:inherit;cursor:pointer}@media print{button{display:none}}</style></head><body>'+
    '<div class="topo"><span class="marca">Task<b>GO</b></span><span>Recibo nº '+esc(String(o.id).slice(-10).toUpperCase())+'</span></div>'+
    '<h1>Recibo de prestação de serviço</h1><p>Serviço concluído em '+esc(quando)+'.</p><table>'+
    linha('Serviço',esc(s.n))+linha('Cliente',esc(cli.name||o.clientName||'Cliente'))+linha('Profissional',esc(o.professionalName||'Profissional'))+
    linha('Local',esc(o.bairro||o.address||'—'))+linha('Forma de pagamento',esc(rotuloPagamentoPedido(o)))+
    (isPro&&d.taxa!=null?linha('Taxa TaskGO',money(d.taxa))+linha('Líquido para o profissional',money(Number(d.recebido||o.price)-Number(d.taxa))):'')+
    '<tr class="total"><th>Valor do serviço</th><td>'+money(o.price)+'</td></tr></table>'+
    '<p class="nota">O pagamento do serviço é feito diretamente ao profissional. A TaskGO conecta cliente e profissional e não executa o serviço. taskgohq.com.br</p>'+
    '<button onclick="print()">Imprimir ou salvar em PDF</button></body></html>';
  const janela=window.open('','_blank');
  if(!janela)return toast('Libere as janelas do navegador para abrir o recibo.','err');
  janela.document.open();janela.document.write(html);janela.document.close();
}

