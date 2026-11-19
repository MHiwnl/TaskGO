/* ---------- QUEM SOMOS ---------- */
function pageSobre(){
  return folha('quem somos',duo('nascida em Recife,','feita para quem trabalha',1)+
    '<p class="corpo">A TaskGO foi criada em 2026 por estudantes da UNIBRA, em Recife, para tirar do improviso a contratação de serviços do dia a dia.</p>','cabeca')+
  folha('a TaskGO','<div class="duas-colunas"><div>'+
    '<p class="corpo">Contratar alguém para consertar, limpar, instalar ou ensinar ainda depende de indicação e de uma tarde de mensagens. Do outro lado, o profissional autônomo perde tempo e dinheiro com pedidos que não fecham.</p>'+
    '<p class="corpo">A TaskGO junta as duas pontas num pedido só: quem precisa diz o que quer e quanto paga, quem atende vê tudo antes de aceitar, e o acompanhamento, a conversa e o pagamento ficam no mesmo lugar.</p></div>'+
    '<div><h2 class="bloco-titulo">a TaskGO em números</h2><div class="tabela-hoje">'+
    [['Fundação','2026, em Recife/PE'],['Serviços no catálogo',String(SERVICES.length)],['Categorias por profissional','até 10 (20 no Plano Pro)'],['Mensalidade','opcional (Plano Pro)'],['Taxa para quem contrata','zero']]
    .map(n=>'<div><span class="t2">'+n[0]+'</span><b>'+n[1]+'</b></div>').join('')+'</div></div></div>','folha-densa')+
  folha('propósito','<div class="recursos">'+[
    ['O que resolvemos','Indicação, preço, agenda, acompanhamento e pagamento, que antes ficavam espalhados, agora num fluxo só.'],
    ['Para quem','Profissionais autônomos, que ganham uma vitrine e pedidos na sua região, e quem precisa de um serviço com rapidez e segurança.'],
    ['No que acreditamos','Transparência no preço, respeito ao trabalho de quem atende e segurança para os dois lados.'],
    ['Como ganhamos','Uma taxa pequena sobre o que o profissional recebe e o Plano Pro, opcional para quem atende. Sem custo para quem contrata.']
  ].map(x=>'<div class="recurso"><h2 class="sub" style="margin-bottom:10px">'+x[0]+'</h2><p>'+x[1]+'</p></div>').join('')+'</div>','folha-densa')+
  folha('time',duo('time','fundador')+secaoTime());
}
