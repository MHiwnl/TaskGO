/* =========================================================
   INÍCIO DA ÁREA DE USO (#/app/inicio)
   A faixa azul diz o que está acontecendo agora; abaixo, o pedido em andamento
   em linhas, o que espera por você, o preparo da conta e os atalhos.
   Tudo vem da conta: sem pedido, sem número inventado.
   ========================================================= */
const STATUS_ATIVOS=['aguardando_pagamento','aguardando','a_caminho','chegou','em_andamento','agendado'];
/* Ordem de urgência: o que está acontecendo agora vem antes do que está marcado. */
const PRIORIDADE_STATUS={aguardando_pagamento:-1,a_caminho:0,chegou:1,em_andamento:2,aguardando:3,agendado:4};

function resumoConta(u){
  const isPro=u.role==='profissional',os=myOrders();
  const ativos=os.filter(o=>STATUS_ATIVOS.includes(o.status))
    .sort((a,b)=>(PRIORIDADE_STATUS[a.status]-PRIORIDADE_STATUS[b.status])||((a.sched||a.created)-(b.sched||b.created)));
  const concluidos=os.filter(o=>o.status==='concluido');
  const avaliar=concluidos.filter(o=>!avaliacaoFeita(o,ladoAtende(o)).nota);
  /* Só conversas de pedidos em andamento: depois de concluído ou cancelado o chat fica bloqueado. */
  const respostas=myChats().filter(c=>{const m=c.msgs||[];const o=os.find(x=>String(x.id)===String(c.orderId));return o&&STATUS_ATIVOS.includes(o.status)&&m.length&&!m[m.length-1].me;});
  const naoLidas=myNotifs().filter(n=>!n.read).length;
  const notas=concluidos.filter(o=>ladoAtende(o)===isPro).map(o=>avaliacaoRecebida(o,isPro).nota).filter(Boolean);
  return {isPro,os,ativos,ativo:ativos[0]||null,concluidos,avaliar,respostas,naoLidas,
    media:notas.length?notas.reduce((a,b)=>a+b,0)/notas.length:null};
}

/* Frase da faixa: a segunda voz do título diz o momento do pedido mais urgente. */
function fraseDoMomento(r){
  const o=r.ativo;
  if(!o)return r.isPro?'nenhum atendimento em andamento.':'o que você precisa resolver?';
  const s=svc(o.service).n.toLowerCase();
  if(ladoAtende(o))return ({a_caminho:'você está a caminho do cliente.',chegou:'você chegou ao local.',em_andamento:'atendimento em andamento.',aguardando:'um pedido espera sua resposta.',agendado:'você tem um atendimento marcado.'})[o.status];
  const quem=o.service==='ajuda'?'quem vai ajudar':'o profissional';
  return ({a_caminho:quem+' está a caminho.',chegou:quem+' chegou.',em_andamento:(o.service==='ajuda'?'sua ajuda':'seu serviço de '+s)+' está em andamento.',aguardando_pagamento:'seu pedido espera o pagamento.',aguardando:'seu pedido aguarda aceite.',agendado:'você tem um serviço marcado.'})[o.status];
}
function detalheDoMomento(r){
  const o=r.ativo;
  if(!o)return r.isPro?'Os pedidos da sua categoria aparecem em Oportunidades. O endereço do cliente só aparece depois do aceite.':
    'Escolha o serviço, diga quanto quer pagar e receba quem aceita na sua região.';
  const quando=o.status==='agendado'&&o.sched?' · marcado para '+dt(o.sched):'';
  return esc(svc(o.service).n)+' · '+counterLabel(o)+': '+esc(counterpart(o).name)+quando;
}
function acoesDoMomento(r){
  const o=r.ativo;
  if(!o)return r.isPro?'<a class="btn btn-primary" href="#/app/oportunidades">'+ic('target')+'Ver oportunidades</a>':
    '<a class="btn btn-primary" href="#/app/contratar">'+ic('plus')+'Escolher um serviço</a>';
  const acompanhar=o.status==='aguardando_pagamento'?'Pagar pedido':o.status==='aguardando'?(ladoAtende(o)?'Ver pedido e responder':'Ver pedido'):o.status==='agendado'?'Ver pedido':'Acompanhar pedido';
  return '<a class="btn btn-primary" href="#/app/pedido/'+o.id+'">'+ic(['a_caminho','chegou'].includes(o.status)?'nav':'list')+acompanhar+'</a>'+
    (['aguardando','aguardando_pagamento'].includes(o.status)?'':'<a class="btn btn-ghost" href="#/app/mensagens">'+ic('chat')+'Conversar</a>');
}

/* Faixa azul do topo: só na aba inicial. */
function faixaArea(u){
  /* Administrador: o nome já está no botão da conta. A faixa mostra o que pede atenção no site agora;
     o primeiro item com pendência leva direto à ação. */
  if(ehSuporte(u)){
    const c=chamadosSuporte||[],ab=c.filter(x=>x.status==='aberto').length,em=c.filter(x=>x.status==='em_atendimento').length;
    return '<section class="azul area-faixa area-faixa-curta faixa-adm" aria-label="Agora no suporte"><div class="wrap area-faixa-in"><ul class="faixa-adm-lista"><li><span class="faixa-adm-item"><b>'+ab+'</b> '+(ab===1?'emergência esperando':'emergências esperando')+'</span></li><li><span class="faixa-adm-item"><b>'+em+'</b> em atendimento</span></li></ul></div></section>';
  }
  if(ehAdministrador(u)){
    /* Equipe: a faixa mostra as pendências do Hoje; cada uma leva à fila. */
    const h=PAINEL.hoje;
    if(!h)return '<section class="azul area-faixa area-faixa-curta faixa-adm" aria-label="Agora no site"><div class="wrap area-faixa-in"><p class="faixa-adm-item">'+esc(NOME_PAPEL[u.role]||'Equipe')+' · conferindo o que precisa de ação...</p></div></section>';
    const p=h.pendencias,n=(v,um,varios)=>'<b>'+numeroBR(v)+'</b> '+(v===1?um:varios);
    const itens=[['denuncias','denúncia esperando','denúncias esperando','moderacao'],['recursos','recurso esperando','recursos esperando','moderacao'],
      ['verificacoes','verificação esperando','verificações esperando','verificacoes'],['pagamentos','problema de pagamento','problemas de pagamento','pagamentos'],
      ['emergencias','emergência aberta','emergências abertas',null]].filter(x=>p[x[0]]!=null);
    return '<section class="azul area-faixa area-faixa-curta faixa-adm" aria-label="Agora no site"><div class="wrap area-faixa-in"><ul class="faixa-adm-lista">'+
      itens.map(x=>'<li>'+(p[x[0]]&&x[3]?'<a class="faixa-adm-item pede" href="#/app/admin/filas" onclick="PAINEL.fila=\''+x[3]+'\'">'+n(p[x[0]],x[1],x[2])+ic('arrow')+'</a>':'<span class="faixa-adm-item">'+n(p[x[0]]||0,x[1],x[2])+'</span>')+'</li>').join('')+'</ul></div></section>';
  }
  const r=resumoConta(u),nome=esc(String(u.name||'').split(' ')[0]||'você');
  const registro=[
    ['Pedidos ativos',String(r.ativos.length)],
    [r.isPro?'Atendimentos concluídos':'Serviços concluídos',String(r.concluidos.length)],
    ['Notificações não lidas',String(r.naoLidas)]
  ].concat(r.media!=null?[['Avaliação média',r.media.toFixed(1).replace('.',',')+' de 5']]:[]);
  return '<section class="azul area-faixa" aria-label="Agora"><div class="wrap area-faixa-in">'+
    '<div class="area-agora">'+
      '<h1 class="duo area-titulo"><span>olá, '+nome.toLowerCase()+'.</span> <strong>'+fraseDoMomento(r)+'</strong></h1>'+
      '<p class="area-detalhe">'+detalheDoMomento(r)+'</p>'+
      '<div class="area-acoes-agora">'+acoesDoMomento(r)+'</div>'+
    '</div>'+
    '<dl class="area-registro">'+registro.map(x=>'<div><dt>'+x[0]+'</dt><dd>'+x[1]+'</dd></div>').join('')+'</dl>'+
  '</div></section>';
}

/* O pedido mais urgente escrito como ordem de serviço: chave à esquerda, valor à direita. */
function pedidoEmLinhas(o,isPro){
  const etapas=['aguardando','a_caminho','chegou','em_andamento','concluido'];
  const passo=o.status==='agendado'?0:etapas.indexOf(o.status);
  const endereco=o.status==='aguardando'?(isPro?'Aparece depois que você aceitar':'Enviado ao profissional depois do aceite'):(o.address||clientOf(o).address||'—');
  const linhas=[
    ['Serviço',esc(svc(o.service).n),true],
    [counterLabel(o),esc(counterpart(o).name),true],
    ['Etapa',stTag(o.status),true],
    ['Quando',o.status==='agendado'&&o.sched?dt(o.sched):'Pedido em '+dt(o.created),true],
    ['Endereço',esc(endereco),passo>=1||(!isPro&&o.status!=='aguardando')],
    ['Valor',o.price?money(o.price)+' <small>a confirmar no pagamento</small>':'A combinar',passo>=1]
  ];
  return '<ul class="ticket-linhas inicio-pedido">'+linhas.map(l=>'<li class="ticket-linha'+(l[2]?' feita':'')+'"><span class="ticket-chave">'+l[0]+'</span><span>'+l[1]+'</span></li>').join('')+'</ul>';
}

function dashInicio(){
  const u=me(),r=resumoConta(u);
  const outros=r.ativos.slice(1);
  /* Em andamento */
  const andamento='<section class="inicio-bloco" aria-labelledby="ini-andamento"><div class="inicio-cab"><h2 class="sub" id="ini-andamento">'+(r.isPro?'Atendimento em andamento':'Pedido em andamento')+'</h2>'+
      (r.os.length?'<a class="link-seta" href="#/app/pedidos">'+(r.isPro?'Todos os atendimentos':'Todos os pedidos')+ic('arrow')+'</a>':'')+'</div>'+
    (r.ativo?pedidoEmLinhas(r.ativo,ladoAtende(r.ativo))+
      (outros.length?'<p class="inicio-nota">'+(outros.length===1?'Outro pedido ativo':'Outros '+outros.length+' pedidos ativos')+': '+outros.map(o=>'<a href="#/app/pedido/'+o.id+'">'+esc(svc(o.service).n)+' · '+ST[o.status][0].toLowerCase()+'</a>').join(', ')+'.</p>':''):
     '<div class="inicio-vazio">'+ic(r.isPro?'target':'list')+'<p><b>'+(r.isPro?'Nenhum atendimento agora.':'Nenhum pedido em andamento.')+'</b> '+
       (r.isPro?'Quando você aceitar uma oportunidade, ela aparece aqui, linha por linha, até o fim do atendimento.':'Quando você contratar um serviço, ele aparece aqui, linha por linha, do aceite até a conclusão.')+'</p></div>')+
    '</section>';
  /* O que espera por você */
  const pendencias=[];
  r.respostas.forEach(c=>{const o=S.orders.find(x=>String(x.id)===String(c.orderId));if(!o)return;const ult=c.msgs[c.msgs.length-1];
    pendencias.push(['chat',esc(counterpart(o).name)+' mandou mensagem','“'+esc(String(ult.t).slice(0,70))+(String(ult.t).length>70?'…':'')+'”','#/app/mensagens']);});
  if(r.naoLidas)pendencias.push(['bell',r.naoLidas+(r.naoLidas===1?' notificação não lida':' notificações não lidas'),'Avisos sobre seus pedidos e sua conta','#/app/notificacoes']);
  r.avaliar.slice(0,2).forEach(o=>pendencias.push(['star',(ladoAtende(o)?'Avalie o cliente ':'Avalie ')+esc(counterpart(o).name),esc(svc(o.service).n)+' · concluído em '+dt(o.created),'#/app/pedido/'+o.id]));
  const espera='<section class="inicio-bloco" aria-labelledby="ini-espera"><div class="inicio-cab"><h2 class="sub" id="ini-espera">Esperando por você</h2></div>'+
    (pendencias.length?'<ul class="inicio-lista">'+pendencias.map(p=>'<li><a class="rowitem" href="'+p[3]+'"><div class="ico-box">'+ic(p[0])+'</div><div class="grow"><b>'+p[1]+'</b><small>'+p[2]+'</small></div>'+ic('arrow')+'</a></li>').join('')+'</ul>':
     '<div class="inicio-vazio">'+ic('checkCircle')+'<p><b>Nada pendente.</b> Mensagens, avisos e avaliações que precisarem de você aparecem aqui.</p></div>')+
    '</section>';
  /* Preparo da conta: só itens que a pessoa pode resolver no perfil. */
  const temEndereco=(u.addresses||[]).length>0||!!u.address;
  const passos=[
    (DEMO_MODE?null:['Identidade verificada',!!u.verified,r.isPro?'Exigida para receber oportunidades.':'Exigida para fazer pedidos.']),
    ['Foto de perfil',!!u.photo,'Ajuda '+(r.isPro?'o cliente':'o profissional')+' a reconhecer você.'],
    ['Telefone',!!u.phone,'Para combinar o atendimento.'],
    r.isPro?['Serviço que você faz',!!u.service,'Define quais oportunidades chegam até você.']:['Endereço salvo',temEndereco,'Escolha o endereço com um toque ao contratar.'],
    r.isPro?['Sobre você',!!u.bio,'Uma frase sobre sua experiência.']:null
  ].filter(Boolean);
  const feitos=passos.filter(p=>p[1]).length;
  /* Com tudo feito, a seção some: não há mais nada a pedir. O perfil continua no menu da conta. */
  const conta=feitos===passos.length?'':'<section class="inicio-bloco" aria-labelledby="ini-conta"><div class="inicio-cab"><h2 class="sub" id="ini-conta">Sua conta</h2><span class="inicio-conta-total">'+feitos+' de '+passos.length+'</span></div>'+
    '<ul class="ticket-linhas inicio-conta">'+passos.map(p=>'<li class="ticket-linha'+(p[1]?' feita':'')+'"><span class="ticket-chave">'+(p[1]?'Feito':'Falta')+'</span><span><b>'+p[0]+'</b><small>'+p[2]+'</small></span></li>').join('')+'</ul>'+
    '<a class="btn btn-ghost btn-sm" href="#/painel">'+ic('user')+'Completar perfil</a>'+
    '</section>';
  /* Atalhos */
  const atalhos=(r.isPro?[['target','Oportunidades','#/app/oportunidades'],['cal','Agenda','#/app/agenda'],['trendUp','Desempenho','#/app/desempenho'],['wallet','Carteira','#/app/carteira'],['award','Plano Pro','#/app/plano']]:
    [['plus','Contratar','#/app/contratar'],['mapPin','Endereços','#/painel'],['wallet','Carteira','#/app/carteira'],['help','Ajuda','#/ajuda']]);
  const atalhosHtml='<nav class="inicio-atalhos" aria-label="Atalhos">'+atalhos.map(a=>'<a href="'+a[2]+'">'+ic(a[0])+'<span>'+a[1]+'</span>'+ic('arrow')+'</a>').join('')+'</nav>';
  return '<div class="inicio">'+
    '<div class="inicio-col">'+andamento+(r.isPro?blocoMelhoresOportunidades():'')+atalhosHtml+'</div>'+
    '<div class="inicio-col">'+espera+blocoAjudaRapida()+conta+'</div>'+
  '</div>';
}
