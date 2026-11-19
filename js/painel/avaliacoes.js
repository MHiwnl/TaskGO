/* ---------- AVALIAÇÕES ---------- */
/* Cada pedido concluído guarda duas avaliações:
   rating/comment = o cliente avaliou o profissional;
   clientRating/clientComment = o profissional avaliou o cliente.
   (reviewByRole vem dos dados de demonstração antigos.) */
function notaDoCliente(o){return o.reviewByRole==='profissional'?null:(Number(o.rating)||null);}
function notaDoProfissional(o){return Number(o.clientRating)||(o.reviewByRole==='profissional'?Number(o.rating)||null:null);}
function avaliacaoRecebida(o,isPro){
  return isPro?{nota:notaDoCliente(o),texto:o.comment||''}:{nota:notaDoProfissional(o),texto:o.clientComment||''};
}
function avaliacaoFeita(o,isPro){
  return isPro?{nota:notaDoProfissional(o),texto:o.clientComment||''}:{nota:notaDoCliente(o),texto:o.comment||''};
}
function quandoAvaliacao(o){
  const ref=Number(o.completedAt)||Number(o.created)||Date.now();
  const dias=Math.max(0,Math.round((Date.now()-ref)/864e5));
  return dias===0?'hoje':dias===1?'ontem':'há '+dias+' dias';
}

function dashAvaliacoes(){
  const u=me(),isPro=u.role==='profissional';
  const concluidos=myOrders().filter(o=>o.status==='concluido').sort((a,b)=>(b.completedAt||b.created)-(a.completedAt||a.created));
  const pendentes=concluidos.filter(o=>!avaliacaoFeita(o,isPro).nota);
  const recebidas=concluidos.map(o=>({o,...avaliacaoRecebida(o,isPro)})).filter(r=>r.nota);
  const feitas=concluidos.map(o=>({o,...avaliacaoFeita(o,isPro)})).filter(r=>r.nota);
  const media=recebidas.length?recebidas.reduce((a,r)=>a+r.nota,0)/recebidas.length:null;
  const dist=[5,4,3,2,1].map(n=>({n,q:recebidas.filter(r=>Math.round(r.nota)===n).length}));
  const total=recebidas.length||1;

  const cartao=r=>{
    const outro=counterpart(r.o);
    return '<li class="aval-item">'+avatar(outro,40)+
      '<div class="aval-corpo"><div class="aval-topo"><b>'+esc(outro.name)+'</b>'+stars(r.nota)+'</div>'+
      '<span class="muted aval-meta">'+esc(svc(r.o.service).n)+' · '+quandoAvaliacao(r.o)+'</span>'+
      (r.texto?'<p class="aval-texto">'+esc(r.texto)+'</p>':'<p class="aval-texto muted">Sem comentário.</p>')+'</div></li>';
  };

  const blocoPendentes=pendentes.length?
    '<section class="card aval-pendentes"><h2 class="sub">'+ic('star')+' Para avaliar <span class="aba-n">'+fmtContador(pendentes.length)+'</span></h2>'+
    '<p class="muted">'+(isPro?'Conte como foi atender estes clientes.':'Conte como foi o serviço destes profissionais.')+'</p><ul class="aval-lista">'+
    pendentes.map(o=>{const outro=counterpart(o);return '<li class="aval-item">'+avatar(outro,40)+
      '<div class="aval-corpo"><b>'+esc(outro.name)+'</b><span class="muted aval-meta">'+esc(svc(o.service).n)+' · concluído '+quandoAvaliacao(o)+'</span></div>'+
      '<button class="btn btn-primary btn-sm" onclick="abrirAvaliacao(\''+o.id+'\')">Avaliar</button></li>';}).join('')+'</ul></section>':'';

  return dashHead('Avaliações',isPro?'O que os clientes dizem sobre o seu trabalho e o que você disse deles.':'O que você disse dos profissionais e o que eles disseram de você.')+
  blocoPendentes+
  '<div class="aval-grade">'+
  '<div class="card aval-resumo"><span class="lbl">Sua nota</span><div class="aval-media">'+(media==null?'—':media.toFixed(1).replace('.',','))+'</div>'+
  (media==null?'<p class="muted">Você ainda não recebeu avaliações.</p>':stars(media)+'<p class="muted">'+recebidas.length+' '+(recebidas.length===1?'avaliação recebida':'avaliações recebidas')+'</p>')+
  '<div class="aval-dist">'+dist.map(d=>'<div><span>'+d.n+'</span><div class="bar"><i style="width:'+(d.q/total*100)+'%"></i></div><span class="muted">'+d.q+'</span></div>').join('')+'</div></div>'+
  '<div class="aval-colunas">'+
  '<section><h2 class="sub">Recebidas</h2>'+(recebidas.length?'<ul class="aval-lista">'+recebidas.map(cartao).join('')+'</ul>':
    '<div class="card emptystate">'+ic('star')+'<b>Nenhuma avaliação recebida</b><p>'+(isPro?'Quando um cliente avaliar seu serviço, ela aparece aqui.':'Quando um profissional avaliar você, ela aparece aqui.')+'</p></div>')+'</section>'+
  '<section><h2 class="sub">Que você fez</h2>'+(feitas.length?'<ul class="aval-lista">'+feitas.map(cartao).join('')+'</ul>':
    '<div class="card emptystate">'+ic('edit')+'<b>Você ainda não avaliou ninguém</b><p>Depois de um serviço concluído, a avaliação fica disponível aqui.</p></div>')+'</section>'+
  '</div></div>';
}
