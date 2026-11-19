/* ---------- SERVIÇOS ---------- */
/* Grupos do catálogo, por sentido. */
const GRUPOS_CATALOGO=GRUPOS_SERVICOS.map(g=>[g[0].toLowerCase(),g[1]]);
function pageServicos(){
  if(route.sub)return pageServicoDetalhe(route.sub);
  const grupos=GRUPOS_CATALOGO;
  return folha('serviços',duo(SERVICES.length+' serviços,','um pedido só',1)+
  '<p class="corpo">Escolha o serviço, diga quanto quer pagar e receba quem aceita na sua região.</p>','cabeca')+
  '<section class="folha-densa" style="padding-top:0"><div class="wrap folha-in"><div class="folha-corpo">'+
  grupos.map(g=>'<h2 class="catalogo-grupo">'+g[0]+'</h2><div class="catalogo">'+g[1].map(svc).map(s=>'<div><a href="#/servicos/'+s.id+'">'+ic(s.ico)+'<h3>'+s.n+'</h3><p>'+s.d+'</p></a></div>').join('')+'</div>').join('')+
  '</div></div></section>';
}
function pageServicoDetalhe(id){
  const s=svc(id),pros=S.pros.filter(p=>p.service===id).slice(0,4);
  const contratar=me()?'#/app/contratar?servico='+s.id:'#/entrar?tipo=cliente&next=app/contratar';
  return folha('serviço','<a href="#/servicos" class="seta" style="margin-bottom:24px"><i>'+ic('arrowLeft')+'</i><span>Todos os serviços</span></a>'+
  '<div class="duas-colunas"><div><h1 class="duo"><strong>'+s.n+'</strong></h1><p class="corpo">'+s.d+'</p>'+
  '<p class="corpo">Valor de referência: <b>'+money(s.min)+' a '+money(s.max)+'</b> por '+esc(s.unit)+'. Você diz quanto quer pagar e o profissional aceita ou propõe outro valor.</p>'+
  '<div class="setas">'+linkSeta(contratar,'Contratar '+s.n.toLowerCase(),1)+'</div></div>'+
  '<div><h2 class="bloco-titulo">como funciona</h2>'+
  (DEMO_MODE&&pros.length?pros.map(p=>proRow(p)).join(''):
   '<ol class="etapas">'+[['Peça','Diga onde, quando e quanto quer pagar.'],['Escolha','Veja quem aceitou, com nota e avaliações.'],['Acompanhe','Siga a chegada no mapa e confirme com o código.'],['Pague depois','Direto ao profissional, por Pix ou dinheiro.']]
     .map(x=>'<li><div><h3>'+x[0]+'</h3><p>'+x[1]+'</p></div></li>').join('')+'</ol>'+
   '<p class="t2" style="font-size:.92rem;margin-top:18px">Trabalha com '+esc(s.n.toLowerCase())+'? Receba pedidos da sua região.</p>'+
   '<div class="setas">'+linkSeta('#/entrar?tipo=profissional','Criar conta de profissional')+'</div>')+
  '</div></div>','cabeca');
}
function proRow(p,sel,quotedPrice){
  const rating=Number(p.rating),reviews=Number(p.reviews)||0;
  return '<div class="pro '+(sel?'sel':'')+'">'+avatar({name:p.name},46)+
  '<div style="flex:1;min-width:0"><b style="display:block;font-size:.95rem">'+esc(p.name)+(typeof seloPro==='function'?seloPro(p.pro,true):'')+'</b>'+
  '<span class="muted" style="font-size:.8rem">'+(reviews?stars(rating)+' '+rating.toFixed(1)+' · '+reviews+' avaliações':'Sem avaliações registradas')+'</span>'+
  '<div style="display:flex;gap:10px;margin-top:6px;flex-wrap:wrap"><span class="tag tag-muted">'+esc(p.city||'Cidade não informada')+'</span></div></div>'+
  '<div style="text-align:right"><div class="muted" style="font-size:.68rem">Preço</div><b style="font-size:.9rem">A combinar</b></div></div>';
}
