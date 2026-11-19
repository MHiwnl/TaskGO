/* =========================================================
   ÁREA DE USO (#/app)
   Onde a pessoa usa a TaskGO depois do login. O site só apresenta;
   o painel (#/painel) cuida só do perfil.
   ========================================================= */
const ABAS_CLIENTE=[['inicio','Início','home'],['contratar','Contratar','plus'],['pedidos','Meus pedidos','list'],['mensagens','Mensagens','chat'],['avaliacoes','Avaliações','star'],['carteira','Carteira','wallet']];
const ABAS_PROFISSIONAL=[['inicio','Início','home'],['oportunidades','Oportunidades','target'],['pedidos','Atendimentos','list'],['agenda','Agenda','cal'],['desempenho','Desempenho','trendUp'],['mensagens','Mensagens','chat'],['avaliacoes','Avaliações','star'],['carteira','Carteira','wallet']];
/* O administrador não é cliente nem profissional: a área dele é só a administração. */
/* Equipe que usa o painel: administrador, moderador e financeiro (cada um vê só as suas abas). */
const ehAdministrador=u=>!!u&&['administrador','moderador','financeiro'].includes(u.role);
const ABAS_SUPORTE=[['suporte','Emergências','bell']];
function abasDaConta(u){return ehSuporte(u)?ABAS_SUPORTE.concat(secoesDaConta(u).map(s=>['admin/'+s[0],s[1],s[2]])):ehAdministrador(u)?secoesDaConta(u).map(s=>['admin/'+s[0],s[1],s[2]]):u.role==='profissional'?ABAS_PROFISSIONAL:ABAS_CLIENTE;}
function abaInicial(u){return abasDaConta(u)[0][0];}
/* Notificações ficam no topo, ao lado do perfil, com o número de não lidas. */
function botaoNotificacoes(n,ativo){
  const rotulo='Notificações'+(n?', '+n+(n===1?' não lida':' não lidas'):'');
  return '<a class="icon-btn area-sino'+(ativo?' ativo':'')+'" href="#/app/notificacoes" title="'+rotulo+'" aria-label="'+rotulo+'"'+(ativo?' aria-current="page"':'')+'>'+ic('bell')+(n?'<span class="area-sino-n" aria-hidden="true">'+fmtContador(n)+'</span>':'')+'</a>';
}
/* Cabeçalho próprio: logo leva ao site, as abas à área e o menu da conta ao perfil.
   A faixa azul aparece só na aba inicial de cada conta (Início, Visão geral, Emergências). */
function cabecalhoArea(u,abas){
  const unread=myNotifs().filter(n=>!n.read).length;
  const atual=['pedido','chamando','pagar'].includes(route.sub)?'pedidos':route.sub==='admin'?'admin/'+(route.id||'visao-geral'):route.sub;
  return '<header class="area-topo"><div class="wrap area-topo-in">'+
    marca()+
    '<span class="area-rotulo">'+(ehAdministrador(u)?(NOME_PAPEL[u.role]||'Administração'):ehSuporte(u)?'Suporte':'Minha área')+'</span>'+
    '<div class="area-acoes">'+themeBtn()+(ehAdministrador(u)?'':botaoNotificacoes(unread,atual==='notificacoes'))+menuConta(u)+'</div></div></header>'+
    (abas&&atual===abaInicial(u)?faixaArea(u):'')+
    (abas?'<div class="area-abas-faixa"><nav class="wrap area-abas" aria-label="Seções da área">'+abas.map(a=>'<a href="#/app/'+a[0]+'" class="'+(atual===a[0]?'active':'')+'"'+(atual===a[0]?' aria-current="page"':'')+'>'+ic(a[2])+'<span>'+(a[0]==='carteira'&&u&&u.role==='cliente'&&typeof modeloDireto==='function'&&modeloDireto()?'Pagamentos':a[1])+'</span>'+(['oportunidades','mensagens','avaliacoes'].includes(a[0])?bolinhaAba(a[0]):'')+'</a>').join('')+'</nav></div>':'');
}
/* Abas que passam da largura da tela: marca o lado que ainda tem abas e mostra a aba ativa. */
function ligarAbasArea(){
  const nav=document.querySelector('.area-abas');if(!nav)return;
  const marcar=()=>{nav.classList.toggle('mais-esq',nav.scrollLeft>4);nav.classList.toggle('mais-dir',nav.scrollLeft+nav.clientWidth<nav.scrollWidth-4);};
  const ativa=nav.querySelector('.active');
  if(ativa&&nav.scrollWidth>nav.clientWidth)nav.scrollLeft=Math.max(0,ativa.offsetLeft-nav.clientWidth/2+ativa.offsetWidth/2);
  marcar();if(!nav.__marcarAbas){nav.__marcarAbas=true;nav.addEventListener('scroll',marcar,{passive:true});}
}
function pageApp(){
  const u=me();
  if(!u){go('/entrar?next=app');return '';}
  const abas=abasDaConta(u);
  let sub=route.sub;
  if(ehSuporte(u)){
    /* Suporte: emergências e as filas de decisão (denúncias, recursos, verificações, pagamentos e obras), busca e atenção. */
    if(!['suporte','notificacoes','admin'].includes(sub)){history.replaceState(null,'','#/app/suporte');route.sub='suporte';}
    return '<div class="area">'+cabecalhoArea(u,abas)+'<main class="wrap area-main" id="conteudo" tabindex="-1">'+(route.sub==='notificacoes'?dashNotificacoes():route.sub==='admin'?dashAdministracao():dashSuporte())+'</main></div>';
  }
  if(ehAdministrador(u)){
    if(sub!=='admin'){const primeira=secoesDaConta(u)[0][0];history.replaceState(null,'','#/app/admin/'+primeira);route.sub='admin';route.id=primeira;}
    return '<div class="area">'+cabecalhoArea(u,abas)+'<main class="wrap area-main" id="conteudo" tabindex="-1">'+dashAdministracao()+'</main></div>';
  }
  if(!sub||(sub==='contratar'&&u.role==='profissional')||(sub==='oportunidades'&&u.role!=='profissional')){
    sub=abaInicial(u);history.replaceState(null,'','#/app/'+sub);route.sub=sub;
  }
  let content='';
  if(sub==='inicio')content=dashInicio();
  else if(sub==='pedido')content=dashPedido(route.id);
  else if(sub==='obra')content=dashObra(route.id);
  else if(sub==='chamando')content=telaChamando(route.id);
  else if(sub==='pagar')content=telaPagar(route.id);
  else if(sub==='contratar')content=pageContratar();
  else if(sub==='pedidos')content=dashPedidos();
  else if(sub==='carteira')content=carteiraArea();
  else if(sub==='mensagens')content=dashMensagens();
  else if(sub==='avaliacoes')content=dashAvaliacoes();
  else if(sub==='notificacoes')content=dashNotificacoes();
  else if(sub==='agenda')content=dashAgenda();
  else if(sub==='desempenho')content=dashDesempenho();
  else if(sub==='oportunidades')content=dashOportunidades();
  else if(sub==='plano')content=dashPlano();
  else if(sub==='indicacoes')content=dashIndicacoes();
  else if(sub==='conta-situacao')content=dashSituacaoConta();
  else{history.replaceState(null,'','#/app/'+abaInicial(u));route.sub=abaInicial(u);return pageApp();}
  return '<div class="area">'+cabecalhoArea(u,abas)+'<main class="wrap area-main" id="conteudo" tabindex="-1">'+(sub==='conta-situacao'?'':faixaSituacao())+content+'</main></div>';
}
/* Painel: só o perfil. O botão de voltar leva para a área de uso. */
function pageDash(){
  const u=me();
  if(!u){go('/entrar');return '';}
  return '<div class="area">'+cabecalhoArea(u,null)+
    '<main class="wrap area-main" id="conteudo" tabindex="-1"><div class="dash-tools"><a class="btn btn-ghost btn-sm voltar-site" href="#/app">'+ic('arrowLeft')+(ehAdministrador(u)?'Voltar para a administração':'Voltar para minha área')+'</a></div>'+(route.sub==='config'?dashConfig():dashPerfil())+'</main></div>';
}

/* ---------- Carteira: saldo, resgate e formas de pagamento e de resgate ---------- */
/* Carteira sem pagamento ligado: os campos de resgate aparecem desativados. */
let formaResgate='pix';
function opcaoForma(grupo,valor,atual,rotulo,descricao,icone){
  return '<label class="forma'+(valor===atual?' sel':'')+'"><input type="radio" name="'+grupo+'" value="'+valor+'"'+(valor===atual?' checked':'')+' onchange="formaResgate=this.value;render()">'+
    ic(icone)+'<span><b>'+rotulo+'</b><small>'+descricao+'</small></span></label>';
}
function campoDesativado(id,rotulo,html){return '<div class="field"><label for="'+id+'">'+rotulo+'</label>'+html.replace('>',' id="'+id+'" disabled>')+'</div>';}
function carteiraArea(){
  const u=me(),isPro=u.role==='profissional';
  const aviso=isPro?'<p class="aviso-linha" role="note">O que você recebe pelos serviços concluídos aparece no saldo acima.</p>':'';
  const saldo=financePageData?.wallet?.disponivel;
  const resgatar=isPro?
    '<section class="carteira-bloco"><h2 class="sub">Resgatar dinheiro</h2>'+
    '<p class="t2" style="margin:6px 0 14px">Saldo disponível para resgate: <b>'+(saldo==null?'—':money(saldo))+'</b>.</p>'+
    '<button type="button" class="btn btn-primary" disabled>'+ic('down')+'Resgatar</button>'+
    '<p class="hint" style="margin-top:8px">'+(Number(saldo)>0?'O resgate será enviado para a forma escolhida abaixo.':'O saldo aparece aqui quando um serviço seu é concluído.')+'</p></section>':'';
  const pagamento=!isPro?secaoCartoes():'';
  const bancos=['Banco do Brasil','Caixa Econômica','Bradesco','Itaú','Santander','Nubank','Inter','C6 Bank','Mercado Pago','PagBank'];
  const resgate=isPro?
    '<section class="carteira-bloco"><h2 class="sub">Forma de resgate</h2><p class="t2" style="margin:6px 0 14px">Para onde vai o dinheiro dos seus atendimentos.</p>'+
    '<div class="formas" role="radiogroup" aria-label="Forma de resgate">'+
      opcaoForma('res','pix',formaResgate,'PIX','Cai na hora, na chave que você escolher','pix')+
      opcaoForma('res','banco',formaResgate,'Conta bancária','Transferência para banco, agência e conta','building')+
    '</div><div class="forma-campos">'+
    (formaResgate==='pix'?
      '<div class="grid g2" style="gap:0 16px">'+campoDesativado('pixTipo','Tipo de chave','<select class="select"><option>CPF</option><option>E-mail</option><option>Telefone</option><option>Chave aleatória</option></select>')+campoDesativado('pixChave','Chave PIX','<input class="input" placeholder="Sua chave">')+'</div>':
      campoDesativado('bancoNome','Banco','<select class="select">'+bancos.map(b=>'<option>'+b+'</option>').join('')+'</select>')+
      '<div class="grid g2" style="gap:0 16px">'+campoDesativado('bancoAgencia','Agência','<input class="input" placeholder="0000">')+campoDesativado('bancoConta','Conta com dígito','<input class="input" placeholder="00000-0">')+'</div>'+
      campoDesativado('bancoTipo','Tipo de conta','<select class="select"><option>Corrente</option><option>Poupança</option></select>'))+
    '</div><button type="button" class="btn btn-primary" disabled>Salvar forma de resgate</button></section>':'';
  /* Com pagamento ligado, o resgate do profissional é real (chave Pix); senão, fica a prévia antiga. */
  /* Quem ajudou na ajuda rápida também tem saldo para resgatar. */
  const ajudou=!isPro&&!!(AJ.estado&&AJ.estado.conhece);
  if(!DEMO_MODE&&!PG.config){carregarPagamentos();return dashHead('Carteira','Carregando…');}
  /* Pagamento direto: sem saldo nem resgate; chave Pix, taxa e histórico (ou, para o cliente, pagamentos e recibos). */
  if(!DEMO_MODE&&modeloDireto())return carteiraDireta();
  if(isPro&&pagamentoLigado())return dashCarteira()+'<div class="carteira-extra">'+secaoPendencias()+secaoResgate(saldo)+'</div>';
  if(ajudou&&pagamentoLigado())return dashCarteira()+'<div class="carteira-extra">'+secaoPendencias()+secaoResgate(saldo)+pagamento+'</div>';
  if(!isPro&&pagamentoLigado())return dashCarteira()+'<div class="carteira-extra">'+secaoPendencias()+pagamento+'</div>';
  return dashCarteira()+'<div class="carteira-extra">'+aviso+resgatar+resgate+pagamento+'</div>';
}
