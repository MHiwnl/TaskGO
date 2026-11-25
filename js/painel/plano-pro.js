/* =========================================================
   PLANO PRO (quem atende)
   R$ 29,90 por mês, no cartão (renova sozinho) ou no Pix (paga a cada mês).
   Enquanto vale: taxa de 10% nos serviços comuns e no recorrente, primeiro na lista com o selo Pro,
   o Chamar agora uma onda antes e até 20 categorias. Cancelar só para a renovação.
   ========================================================= */
const PLANO={dados:null,carregando:false,erro:'',metodo:null,trocando:false,enviando:false,espera:null};
const PLANO_PRECO=29.9;

async function carregarPlano(forcar){
  if(DEMO_MODE||!me()||PLANO.carregando||(PLANO.dados&&!forcar))return;
  PLANO.carregando=true;
  try{PLANO.dados=await apiJSON('/api/plano','GET',undefined,'Não foi possível consultar o plano.');PLANO.erro='';}
  catch(e){PLANO.erro=erroLegivel(e,'Não foi possível consultar o plano.');}
  finally{PLANO.carregando=false;if(route.page==='app'||route.page==='painel')render();}
}
/* A conta logada tem o Plano Pro valendo agora. */
function souPro(){
  if(DEMO_MODE||!me()||me().role!=='profissional')return false;
  if(!PLANO.dados){carregarPlano();return false;}
  return !!PLANO.dados.pro;
}
function seloPro(sim,pequeno){return sim?'<span class="selo-pro'+(pequeno?' pequeno':'')+'" title="Profissional com o Plano Pro">'+ic('award')+'<span>Pro</span></span>':'';}
/* A partir de quanto por mês em serviços comuns o plano se paga (5 pontos de taxa a menos). */
const PLANO_COMPENSA=Math.ceil(PLANO_PRECO/0.05);

function linhasBeneficios(){
  const linha=(chave,titulo,texto)=>'<li class="ticket-linha"><span class="ticket-chave">'+chave+'</span><span><b>'+titulo+'</b><small>'+texto+'</small></span></li>';
  return '<ul class="ticket-linhas plano-linhas">'+
    linha('Taxa','10% nos serviços comuns e recorrentes','Sem o plano: 15%, ou 12% a partir de R$ 1.000. Obras e ajuda rápida mantêm a taxa delas.')+
    linha('Lista','Primeiro na sua categoria, com o selo Pro','O cliente vê o selo na lista, no aceite e nos orçamentos de obra.')+
    linha('Chamar agora','O chamado chega uma onda antes','Quem seria avisado na segunda onda entra na primeira.')+
    linha('Categorias','Até 20','Sem o plano: até 10. Quando o plano acaba, ficam as 10 primeiras.')+
    linha('Compensa','A partir de '+money(PLANO_COMPENSA)+' por mês','Em serviços comuns, a taxa menor paga a mensalidade.')+
  '</ul>';
}

function estadoPlano(d){
  if(!d||!d.pagoAte||!d.pro)return d&&d.cobranca?'Esperando o pagamento do 1º mês.':'Sem plano. Você paga a taxa normal.';
  if(d.status==='cancelada')return 'Vale até '+dataCurta(d.pagoAte)+'. Não renova.';
  return 'Ativo até '+dataCurta(d.pagoAte)+'. Renova '+(d.forma==='pix'?'por Pix: você paga a cada mês':'sozinho no '+(d.cartao?esc(d.cartao):'cartão'))+'.';
}

function dashPlano(){
  const u=me();
  if(u.role!=='profissional')return dashHead('Sem mensalidade','Quem contrata não paga nada à TaskGO. Quem atende paga uma taxa só sobre o que recebe.')+
    '<div class="card"><p class="t2">15% nos serviços comuns, 12% a partir de R$ 1.000, de 12% a 6% nas obras e 10% na ajuda rápida.</p>'+
    '<a class="btn btn-ghost btn-sm" style="margin-top:14px" href="#/planos">Ver todos os preços</a></div>';
  if(DEMO_MODE)return cabecaPlano(null)+'<section class="carteira-bloco"><h2 class="sub">O que muda</h2>'+linhasBeneficios()+'</section>';
  if(!PLANO.dados){carregarPlano();return dashHead('Plano Pro',PLANO.erro||'Carregando…')+(PLANO.erro?'<button class="btn btn-ghost btn-sm" onclick="PLANO.erro=\'\';carregarPlano(true)">Tentar de novo</button>':'');}
  const d=PLANO.dados;
  return cabecaPlano(d)+
    '<div class="plano-corpo"><section class="carteira-bloco"><h2 class="sub">O que muda</h2>'+linhasBeneficios()+'</section>'+
    '<section class="carteira-bloco" aria-live="polite">'+(d.pro?blocoAssinatura(d):blocoAssinar(d))+'</section></div>';
}

/* Faixa azul: preço e situação. */
function cabecaPlano(d){
  return '<a href="#/app/carteira" class="pill" style="margin-bottom:18px">'+ic('arrowLeft')+' Carteira</a>'+
    '<section class="azul plano-faixa" aria-label="Plano Pro">'+
      '<h1 class="plano-nome">plano <b>pro</b></h1>'+
      '<p class="plano-preco"><b>'+money(PLANO_PRECO)+'</b><i>por mês</i></p>'+
      '<p class="plano-estado">'+(d?estadoPlano(d):'Cartão ou Pix. Cancele quando quiser: vale até o fim do mês pago.')+'</p>'+
    '</section>';
}

/* Sem plano: escolher como pagar e assinar. */
function blocoAssinar(d){
  if(d.cobranca&&d.cobranca.pixCopiaECola)return '<h2 class="sub">Pague o Pix</h2>'+blocoPixPlano(d.cobranca);
  if(PG.cartoes===null||PG.cartoes===undefined){carregarPagamentos(true);}
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido);
  const opcoes=cs.map(c=>['cartao:'+c.id,esc(c.bandeira)+' •••• '+esc(c.final4),'Renova sozinho todo mês neste cartão','card'])
    .concat(cartaoNaPagina()?[['cartao_novo',cs.length?'Outro cartão de crédito':'Cartão de crédito','Você digita na página segura do Asaas. Renova sozinho todo mês','card']]:[])
    .concat([['pix','Pix','Você paga a cada mês, pelo app do banco','pix']]);
  if(!opcoes.some(o=>o[0]===PLANO.metodo))PLANO.metodo=opcoes[0][0];
  return '<h2 class="sub">Como pagar</h2>'+faixaTeste()+
    '<div class="formas" role="radiogroup" aria-label="Como pagar o Plano Pro">'+opcoes.map(o=>opcaoPag('planoMetodo',o[0],PLANO.metodo,o[1],o[2],o[3],'PLANO.metodo=this.value;marcarForma(this)')).join('')+'</div>'+
    (!cs.length&&!cartaoNaPagina()?'<button type="button" class="link-btn plano-cartao-novo" onclick="novoCartao(c=>{PLANO.metodo=\'cartao:\'+c.id;render();})">'+ic('plus')+'Cadastrar um cartão</button>':'')+
    '<p id="planoErro" class="pag-recusa" role="alert" hidden></p>'+
    '<button class="btn btn-primary btn-lg plano-assinar" onclick="assinarPlano()"'+(PLANO.enviando?' disabled aria-busy="true"':'')+'>'+ic('lock')+(PLANO.enviando?'Assinando…':'Assinar por '+money(PLANO_PRECO))+'</button>';
}
function corpoMetodo(m){return m.startsWith('cartao:')?{forma:'cartao',metodo:'cartao',cartaoId:m.slice(7)}:m==='cartao_novo'?{forma:'cartao',metodo:'cartao_novo'}:{forma:'pix',metodo:'pix'};}
async function assinarPlano(){
  if(PLANO.enviando)return;
  PLANO.enviando=true;render();
  try{
    const r=await apiJSON('/api/plano/assinar','POST',corpoMetodo(PLANO.metodo),'Não foi possível assinar.');
    tratarCobrancaPlano(r);
  }catch(e){PLANO.enviando=false;render();const p=$('#planoErro');if(p){p.textContent=erroLegivel(e,'Não foi possível assinar.');p.hidden=false;}return;}
  PLANO.enviando=false;render();
}
/* Resposta de assinar ou pagar: pago na hora (cartão salvo), Pix para pagar ou página do Asaas. */
function tratarCobrancaPlano(r){
  PLANO.dados=r.situacao||PLANO.dados;
  const c=r.cobranca;
  if(!c||c.status==='paga'){if(c)toast(PLANO.dados&&PLANO.dados.pro?'Plano Pro ativo.':'Pago.','ok');else toast('A renovação voltou a valer.','ok');carregarCategoriasPro&&carregarCategoriasPro(true);return;}
  if(c.link&&!c.pixCopiaECola){window.open(c.link,'_blank','noopener');toast('Termine o pagamento na página do Asaas. O plano liga sozinho quando ele for confirmado.','ok');}
  esperarPlano(c.id);
}
function blocoPixPlano(c){
  esperarPlano(c.id);
  return '<div class="pag-pix">'+(c.qrCode?'<img class="pag-qr" src="'+esc(c.qrCode)+'" alt="QR code do Pix de '+esc(money(c.valor))+'" width="200" height="200">':'')+
    '<div class="pag-pix-dados"><p class="plano-pix-valor">'+money(c.valor)+'</p><label class="rotulo-campo" for="planoPixCod">Código Pix copia e cola</label><input class="input" id="planoPixCod" readonly value="'+esc(c.pixCopiaECola)+'">'+
    '<div class="obra-acoes"><button class="btn btn-ghost btn-sm" onclick="navigator.clipboard.writeText($(\'#planoPixCod\').value);toast(\'Copiado\',\'ok\')">'+ic('copy')+'Copiar código</button>'+
    (pagamentoTeste()?'<button class="btn btn-ghost btn-sm" onclick="apiJSON(\'/api/pagamentos/cobrancas/'+esc(c.id)+'/simular-pix\',\'POST\').then(()=>carregarPlano(true)).catch(e=>toast(erroLegivel(e,\'Falhou\'),\'err\'))">Simular pagamento</button>':'')+'</div>'+
    '<p class="pag-espera"><span class="pag-ponto" aria-hidden="true"></span><span><span role="status">Esperando o pagamento</span> · <span class="plano-prazo">expira em <b class="plano-expira" data-fim="'+Number(c.expiraEm)+'">'+tempoRestante(c.expiraEm)+'</b></span></span></p></div></div>';
}
/* Confere a cobrança a cada 5 s por até 1 hora (o prazo do Pix). O servidor também confere sozinho, com a tela fechada. */
function esperarPlano(id){
  if(PLANO.espera&&PLANO.espera.id===id)return;
  if(PLANO.espera)clearInterval(PLANO.espera.t);
  let n=0;
  const t=setInterval(async()=>{
    if(++n>720||!me()){clearInterval(t);PLANO.espera=null;return;}
    try{const c=await apiJSON('/api/pagamentos/cobrancas/'+encodeURIComponent(id));
      if(c.status!=='pendente'){clearInterval(t);PLANO.espera=null;await carregarPlano(true);
        if(c.status==='paga'){toast('Plano Pro ativo.','ok');if(typeof carregarCategoriasPro==='function')carregarCategoriasPro(true);}
        else toast(c.motivo||'O pagamento não foi concluído.','err');}}catch(e){}
  },5000);
  PLANO.espera={id,t};
}

/* Com plano: renovação, pagar o mês (Pix), trocar a forma e cancelar ou voltar a renovar. */
function blocoAssinatura(d){
  const cancelada=d.status==='cancelada';
  const renova=cancelada?'Não renova':d.forma==='pix'?'Por Pix, até '+dataCurta(d.pagoAte):'Sozinho no '+(d.cartao?esc(d.cartao):'cartão')+', em '+dataCurta(d.pagoAte);
  const linha=(chave,valor)=>'<li class="ticket-linha"><span class="ticket-chave">'+chave+'</span><span><b>'+valor+'</b></span></li>';
  const pixAberto=d.cobranca&&d.cobranca.pixCopiaECola;
  return '<h2 class="sub">Sua assinatura</h2>'+
    '<ul class="ticket-linhas plano-linhas">'+linha('Situação',cancelada?'Renovação cancelada':'Ativa')+linha('Vale até',dataCurta(d.pagoAte))+linha('Renovação',renova)+'</ul>'+
    (pixAberto?'<h3 class="plano-sub">Pague o próximo mês</h3>'+blocoPixPlano(d.cobranca):'')+
    (PLANO.trocando?blocoTrocarForma(d):'')+
    '<p id="planoErro" class="pag-recusa" role="alert" hidden></p>'+
    '<div class="obra-acoes plano-acoes">'+
      (cancelada?'<button class="btn btn-primary" onclick="voltarRenovar()"'+(PLANO.enviando?' disabled':'')+'>'+ic('refresh')+'Voltar a renovar</button>':
        (d.forma==='pix'&&d.podePagar&&!pixAberto?'<button class="btn btn-primary" onclick="pagarMesPlano()"'+(PLANO.enviando?' disabled':'')+'>'+ic('pix')+'Pagar o próximo mês</button>':'')+
        (PLANO.trocando?'':'<button class="btn btn-ghost" onclick="PLANO.trocando=true;PLANO.metodo=null;render()">'+ic('card')+'Trocar forma de pagamento</button>')+
        '<button class="btn btn-danger" onclick="cancelarPlano()"'+(PLANO.enviando?' disabled':'')+'>Cancelar renovação</button>')+
    (d.desistirAte?'<button class="btn btn-ghost" onclick="desistirPlano()"'+(PLANO.enviando?' disabled':'')+'>Desistir e receber o valor de volta (até '+dataCurta(d.desistirAte)+')</button>':'')+
    '</div>';
}
function blocoTrocarForma(d){
  if(PG.cartoes===null||PG.cartoes===undefined)carregarPagamentos(true);
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido);
  const opcoes=cs.map(c=>['cartao:'+c.id,esc(c.bandeira)+' •••• '+esc(c.final4),'Renova sozinho todo mês neste cartão','card']).concat([['pix','Pix','Você paga a cada mês, pelo app do banco','pix']]);
  const atual=d.forma==='pix'?'pix':'cartao:'+d.cartaoId;
  if(!opcoes.some(o=>o[0]===PLANO.metodo))PLANO.metodo=opcoes.some(o=>o[0]===atual)?atual:opcoes[0][0];
  return '<h3 class="plano-sub">Como renovar</h3><div class="formas" role="radiogroup" aria-label="Como renovar o Plano Pro">'+
    opcoes.map(o=>opcaoPag('planoForma',o[0],PLANO.metodo,o[1],o[2],o[3],'PLANO.metodo=this.value;marcarForma(this)')).join('')+'</div>'+
    (!cs.length&&!cartaoNaPagina()?'<button type="button" class="link-btn plano-cartao-novo" onclick="novoCartao(c=>{PLANO.metodo=\'cartao:\'+c.id;render();})">'+ic('plus')+'Cadastrar um cartão</button>':'')+
    '<div class="obra-acoes"><button class="btn btn-ghost btn-sm" onclick="PLANO.trocando=false;render()">Voltar</button><button class="btn btn-primary btn-sm" onclick="salvarFormaPlano()">'+ic('check')+'Salvar</button></div>';
}
async function salvarFormaPlano(){
  const m=corpoMetodo(PLANO.metodo);
  try{const r=await apiJSON('/api/plano/forma','PUT',{forma:m.forma,cartaoId:m.cartaoId||null},'Não foi possível trocar.');
    PLANO.dados=r.situacao;PLANO.trocando=false;toast(m.forma==='pix'?'O plano passa a renovar por Pix.':'O plano passa a renovar no cartão.','ok');render();}
  catch(e){toast(erroLegivel(e,'Não foi possível trocar.'),'err');}
}
async function pagarMesPlano(){
  if(PLANO.enviando)return;PLANO.enviando=true;render();
  try{tratarCobrancaPlano(await apiJSON('/api/plano/pagar','POST',{metodo:'pix'},'Não foi possível gerar o Pix.'));}
  catch(e){toast(erroLegivel(e,'Não foi possível gerar o Pix.'),'err');}
  PLANO.enviando=false;render();
}
async function voltarRenovar(){
  const d=PLANO.dados;if(!d||PLANO.enviando)return;
  const corpo=d.forma==='cartao'&&d.cartaoId?{forma:'cartao',metodo:'cartao',cartaoId:d.cartaoId}:{forma:'pix',metodo:'pix'};
  PLANO.enviando=true;render();
  try{tratarCobrancaPlano(await apiJSON('/api/plano/assinar','POST',corpo,'Não foi possível voltar a renovar.'));}
  catch(e){toast(erroLegivel(e,'Não foi possível voltar a renovar.'),'err');}
  PLANO.enviando=false;render();
}
async function cancelarPlano(){
  const d=PLANO.dados;if(!d)return;
  const ok=await confirmar({titulo:'Cancelar a renovação?',texto:'O plano continua valendo até '+dataCurta(d.pagoAte)+'. Depois, a taxa volta a 15% e ficam as 10 primeiras categorias.',botao:'Cancelar renovação',perigo:true});
  if(!ok)return;
  PLANO.enviando=true;render();
  try{const r=await apiJSON('/api/plano/cancelar','POST',undefined,'Não foi possível cancelar.');PLANO.dados=r.situacao;toast('Renovação cancelada. O plano vale até '+dataCurta(d.pagoAte)+'.','ok');}
  catch(e){toast(erroLegivel(e,'Não foi possível cancelar.'),'err');}
  PLANO.enviando=false;render();
}

async function desistirPlano(){
  const d=PLANO.dados;if(!d||!d.desistirAte)return;
  const ok=await confirmar({titulo:'Desistir do Plano Pro?',texto:'Os benefícios acabam na hora e a renovação é cancelada. Você recebe de volta todo o valor pago no plano.',botao:'Desistir',perigo:true});
  if(!ok)return;
  PLANO.enviando=true;render();
  try{const r=await apiJSON('/api/plano/desistir','POST',undefined,'Não foi possível desistir.');PLANO.dados=r.situacao;toast(r.aviso,'ok');}
  catch(e){toast(erroLegivel(e,'Não foi possível desistir.'),'err');}
  PLANO.enviando=false;render();
}

/* Bloco na Carteira de quem atende. */
function secaoPlanoCarteira(){
  if(DEMO_MODE)return '';
  if(!PLANO.dados){carregarPlano();return '';}
  const d=PLANO.dados;
  return '<section class="carteira-bloco plano-na-carteira"><h2 class="sub">Plano Pro '+seloPro(d.pro,true)+'</h2>'+
    '<p class="t2" style="margin:6px 0 14px">'+estadoPlano(d)+(d.pro?'':' Com o Pro, 10% de taxa nos serviços comuns, destaque na lista e até 20 categorias, por '+money(PLANO_PRECO)+' por mês.')+'</p>'+
    '<a class="btn '+(d.pro?'btn-ghost':'btn-primary')+' btn-sm" href="#/app/plano">'+ic('award')+(d.pro?'Ver minha assinatura':'Conhecer o Plano Pro')+'</a></section>';
}
/* Cronômetro do Pix do plano: um só relógio para a tela; no zero, recarrega para mostrar que expirou.
   Ao voltar para a aba (tela apagada, aba em segundo plano), consulta na hora: o pagamento pode já ter caído. */
setInterval(()=>{
  document.querySelectorAll('.plano-expira[data-fim]').forEach(e=>{
    e.textContent=tempoRestante(e.dataset.fim);
    if(Number(e.dataset.fim)<=Date.now()&&!e.dataset.zerou){e.dataset.zerou='1';carregarPlano(true);}
  });
},1000);
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&PLANO.dados&&PLANO.dados.cobranca)carregarPlano(true);});
