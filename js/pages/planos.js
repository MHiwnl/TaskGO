/* ---------- PREÇOS ----------
   Sem mensalidade obrigatória. Quem contrata não paga nada à TaskGO; quem atende paga uma taxa sobre o que recebe e pode assinar o Plano Pro. */
function pagePlanos(){
  const faixas=[
    ['Serviço comum','até R$ 999,99','15%'],
    ['Serviço comum','de R$ 1.000 a R$ 2.999,99','12%'],
    ['Obra','R$ 1.000 a R$ 2.999','12%'],
    ['Obra','R$ 3.000 a R$ 4.999','10%'],
    ['Obra','R$ 5.000 a R$ 9.999','8%'],
    ['Obra','a partir de R$ 10.000','6%'],
    ['Ajuda rápida','qualquer valor','10%'],
    ['Visita técnica','R$ 50 a R$ 100','15%']
  ];
  const planos=[
    {n:'Para quem contrata',p:'R$ 0',s:'',d:'Sem mensalidade e sem taxa',f:['Mais de 100 serviços','Profissionais com identidade verificada','Acompanhamento da chegada no mapa','Código de chegada e emergência','Pagamento direto, só depois do serviço','Obras em etapas e serviço recorrente'],cta:['#/entrar?tipo=cliente','Criar conta grátis']},
    {n:'Para quem atende',p:'R$ 0',s:'/mês',d:'Você só paga quando recebe',pop:true,f:['Pedidos da sua região na hora','Até 10 categorias','Você define ou negocia o preço','Recebe direto do cliente','Agenda, carteira e extrato','Taxa a partir de 6%'],cta:['#/entrar?tipo=profissional','Começar a atender']}
  ];
  return folha('preços',duo('simples assim:','sem mensalidade obrigatória',1)+'<p class="corpo">Quem contrata não paga nada à TaskGO. Quem atende paga uma taxa só sobre o que recebe e, se quiser, assina o Plano Pro.</p>','cabeca')+
  folha('planos','<div class="grid g2">'+planos.map(p=>'<div class="plan '+(p.pop?'pop':'')+'">'+
    '<h2 class="sub">'+p.n+'</h2><div class="price">'+p.p+'<small>'+p.s+'</small></div>'+
    '<span class="t2" style="font-size:.86rem">'+p.d+'</span>'+
    '<ul>'+p.f.map(f=>'<li>'+ic('check')+'<span>'+f+'</span></li>').join('')+'</ul>'+
    '<a class="btn '+(p.pop?'btn-primary':'btn-ghost')+' btn-block" href="'+p.cta[0]+'">'+p.cta[1]+'</a></div>').join('')+'</div>'+
  faixaPro()+
  '<h2 class="bloco-titulo mt-grande">taxa da TaskGO, paga por quem atende</h2>'+
  '<div class="tabela-hoje">'+faixas.map(x=>'<div><span class="t2">'+x[0]+' · '+x[1]+'</span><b>'+x[2]+'</b></div>').join('')+'</div>'+
  '<p class="hint" style="margin-top:12px">A taxa é calculada sobre o valor recebido e paga pela Carteira, por Pix ou cartão.</p>'+
  '<div class="faq-lista mt-grande">'+faqList([FAQ[1],FAQ[8],FAQ[2],FAQ[5]])+'</div>','folha-densa');
}
/* Plano Pro: opcional, só para quem atende. Preço único em PLANO_PRECO (js/painel/plano-pro.js). */
function faixaPro(){
  const pro=typeof me==='function'&&me()&&me().role==='profissional';
  const beneficios=[['Taxa nos serviços comuns e no recorrente','10%'],['Lugar na busca da categoria','primeiro'],['Chamar agora','recebe antes'],['Categorias','até 20'],['Selo no perfil','Pro']];
  return '<section class="azul pro-faixa mt-grande" aria-label="Plano Pro">'+
    '<div class="pro-faixa-cabeca"><h2 class="plano-nome">plano <b>pro</b></h2>'+
      '<p class="plano-preco"><b>'+money(PLANO_PRECO)+'</b><i>por mês</i></p>'+
      '<p class="plano-estado">Opcional, para quem atende. Cartão ou Pix. Cancele quando quiser: vale até o fim do mês pago.</p>'+
      linkSeta(pro?'#/app/plano':'#/entrar?tipo=profissional',pro?'Assinar o Plano Pro':'Começar a atender',1)+'</div>'+
    '<div class="tabela-hoje">'+beneficios.map(x=>'<div><span>'+x[0]+'</span><b>'+x[1]+'</b></div>').join('')+'</div>'+
  '</section>';
}
