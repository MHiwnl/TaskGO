/* =========================================================
   PAGAMENTO (modo de teste por enquanto)
   Cliente: cartões salvos, escolha no Contratar, tela Pagar (Pix e cartão).
   Pedido: resumo do pagamento para o cliente e para o profissional.
   ========================================================= */
const PG={config:null,cartoes:null,carregando:false,depoisDoCartao:null};
const CARTOES_TESTE=[['4111 1111 1111 1111','Visa, aprovado'],['5555 5555 5555 4444','Mastercard, aprovado'],['4000 0000 0000 0002','Recusado pelo banco'],['4000 0000 0000 9995','Sem limite']];
const TX_ROTULO={PAGAMENTO:'Pagamento de pedido',REPASSE:'Recebido por serviço',ESTORNO:'Devolução',SAQUE_RESERVADO:'Resgate por Pix',RESGATE:'Resgate por Pix',TROCO_ENVIADO:'Troco online enviado',TROCO_RECEBIDO:'Troco recebido',RECEBIDO_DINHEIRO:'Recebido em dinheiro (fora da carteira)',TAXA_DINHEIRO:'Taxa de serviço em dinheiro',PENDENCIA_PAGA:'Pendência paga'};
const TX_STATUS={PENDENTE:'em andamento',CONCLUIDA:'concluído',FALHOU:'não enviado, valor devolvido',SOLICITADO:'solicitado',PROCESSANDO:'enviando',CONCLUIDO:'enviado'};
function statusTx(s){return TX_STATUS[String(s).toUpperCase()]||String(s).toLowerCase();}

function pagamentoLigado(){return !DEMO_MODE&&PG.config&&PG.config.modo&&PG.config.modo!=='desligado';}
function pagamentoTeste(){return !!PG.config&&(PG.config.teste===true||PG.config.modo==='simulado');}
/* Asaas: o cartão é digitado na página segura dele; depois do primeiro pagamento fica salvo aqui. */
function cartaoNaPagina(){return !!PG.config&&PG.config.cartaoNaPagina===true;}
async function carregarPagamentos(forcar){
  if(DEMO_MODE||!me()||PG.carregando)return;
  if(!forcar&&PG.config&&(PG.cartoes||!['cliente','profissional'].includes(me().role)))return;
  PG.carregando=true;
  try{
    PG.config=PG.config||await apiJSON('/api/pagamentos/config');
    /* Cliente paga com cartão; quem atende autoriza um cartão para a taxa da TaskGO. */
    if(['cliente','profissional'].includes(me()?.role))PG.cartoes=await apiJSON('/api/pagamentos/cartoes');
    try{PG.saldo=Number((await apiJSON('/api/financeiro/carteira')).disponivel)||0;}catch(e){PG.saldo=0;}
  }catch(e){PG.config=PG.config||{modo:'desligado'};PG.cartoes=PG.cartoes||[];}
  finally{PG.carregando=false;}
  if(route.page==='app'||route.page==='contratar')render();
}
function faixaTeste(){return pagamentoTeste()?'<p class="pag-teste">'+ic('flag')+'<span><b>Modo de teste.</b> Nenhum dinheiro de verdade é cobrado.</span></p>':'';}
/* Até 6x sem juros; a partir de R$ 1.000, até 12x (de 7x a 12x com juros pagos pelo cliente). Parcela mínima de R$ 50. */
function maxParcelas(v){return Math.max(1,Math.min(Number(v)>=1000?12:6,Math.floor(Number(v)/50)));}
function totalComJuros(v,n){if(n<=6)return Number(v);const i=Number(PG.config&&PG.config.jurosMes)||0.0249,f=Math.pow(1+i,n);return Math.round(Number(v)*i*f/(f-1)*100)/100*n;}
function taxaPct(o){const p=Number(o&&o.price)||0;if(o&&o.service==='ajuda')return 10;
  /* Plano Pro: 10% nos pedidos comuns (obra começa em R$ 3.000). */
  const pro=o&&o.modo!=='obra'&&(o.proPlanoPro||(me()&&me().role==='profissional'&&typeof souPro==='function'&&souPro()));
  if(pro&&p<3000)return 10;
  return p>=10000?6:p>=5000?8:p>=3000?10:p>=1000?12:15;}
function campoParcelas(valor,atual,onchange,id){
  const n=maxParcelas(valor);if(n<2)return '';
  const juros=Number(PG.config&&PG.config.jurosMes)||0.0249;
  return '<div class="field pag-parcelas"><label for="'+id+'">Parcelas</label><select class="select" id="'+id+'" onchange="'+onchange+'">'+
    Array.from({length:n},(_,i)=>{const k=i+1,t=totalComJuros(valor,k);return '<option value="'+k+'"'+(Number(atual)===k?' selected':'')+'>'+k+'x de '+money(t/k)+(k<=6?(i?' sem juros':' à vista'):' · total '+money(t)+' (juros de '+String(Math.round(juros*1000)/10).replace('.',',')+'% ao mês)')+'</option>';}).join('')+'</select></div>';
}
function opcaoPag(nome,valor,atual,rotulo,descricao,icone,onchange,extra){
  return '<label class="forma'+(valor===atual?' sel':'')+(extra||'')+'"><input type="radio" name="'+nome+'" value="'+esc(valor)+'"'+(valor===atual?' checked':'')+' onchange="'+onchange+'">'+
    ic(icone)+'<span><b>'+rotulo+'</b><small>'+descricao+'</small></span></label>';
}
function validadeCartao(c){return String(c.mes).padStart(2,'0')+'/'+String(c.ano).slice(-2);}
/* Cartão salvo pelo Asaas não informa validade (guardada como 12/2099). */
function textoCartao(c){return c.ano>=2099?'Crédito · salvo no Asaas':c.vencido?'Vencido em '+validadeCartao(c):'Crédito · vence '+validadeCartao(c);}
function opcoesCartoes(nome,atual,onchange){
  return (PG.cartoes||[]).map(c=>opcaoPag(nome,'cartao:'+c.id,atual,esc(c.bandeira)+' •••• '+esc(c.final4),
    textoCartao(c)+(c.padrao&&!c.vencido?' · principal':''),'card',onchange,c.vencido?' forma-off':'')).join('');
}

/* ---------- Escolha no Contratar ---------- */
function escolhaPagamento(valor){
  if(DEMO_MODE||!me())return '';
  if(!PG.config||PG.cartoes===null){carregarPagamentos();return '<div class="pag-escolha" aria-busy="true"><p class="muted">Carregando formas de pagamento…</p></div>';}
  if(!pagamentoLigado())return '';
  if(modeloDireto()){
    if(!W.pag||!['pix_direto','cartao_maquininha','dinheiro'].includes(W.pag.metodo)||W.pag.metodo==='dinheiro'&&Number(valor)>=1000)W.pag={metodo:'pix_direto'};
    return '<section class="pag-escolha"><h3>Como você vai pagar</h3><div class="formas" role="radiogroup" aria-label="Forma de pagamento">'+
      opcaoPag('wPag','pix_direto',W.pag.metodo,'Pix para o profissional','QR com o valor, depois do serviço','pix','escolherPagContratar(this.value)')+
      opcaoPag('wPag','cartao_maquininha',W.pag.metodo,'Cartão na maquininha','Crédito ou débito, na maquininha de quem atender','card','escolherPagContratar(this.value)')+
      (Number(valor)<1000?opcaoPag('wPag','dinheiro',W.pag.metodo,'Dinheiro no local','Em espécie, a quem atender, no fim do serviço','money','escolherPagContratar(this.value)'):'')+
      '</div><p class="hint">Nada é cobrado agora. Você paga direto a quem atender, só depois que o serviço terminar.</p></section>';
  }
  if(PG.saldo==null&&!PG.carregando)carregarPagamentos(true);
  const cs=PG.cartoes||[];
  const saldo=Number(PG.saldo)||0,cabeNaCarteira=saldo>0&&saldo>=Number(valor||0)&&Number(valor)>0;
  if(W.pag&&W.pag.metodo==='carteira'&&!cabeNaCarteira)W.pag=null;
  if(W.pag&&W.pag.metodo==='dinheiro'&&Number(valor)>=1000)W.pag=null;
  if(!W.pag||(W.pag.metodo==='cartao'&&!cs.some(c=>c.id===W.pag.cartaoId&&!c.vencido))||(W.pag.metodo==='cartao_novo'&&!cartaoNaPagina())){const p=cs.find(c=>c.padrao&&!c.vencido)||cs.find(c=>!c.vencido);W.pag=p?{metodo:'cartao',cartaoId:p.id,parcelas:1}:{metodo:'pix'};}
  const atual=['pix','cartao_novo','carteira','dinheiro'].includes(W.pag.metodo)?W.pag.metodo:'cartao:'+W.pag.cartaoId;
  return '<section class="pag-escolha"><h3>Como você vai pagar</h3>'+faixaTeste()+
    '<div class="formas" role="radiogroup" aria-label="Forma de pagamento">'+
      opcoesCartoes('wPag',atual,'escolherPagContratar(this.value)')+
      (cartaoNaPagina()?opcaoPag('wPag','cartao_novo',atual,(cs.length?'Outro cartão de crédito':'Cartão de crédito'),'Você digita o cartão na página segura do Asaas','card','escolherPagContratar(this.value)'):'')+
      opcaoPag('wPag','pix',atual,'Pix','Você paga pelo app do banco e o pedido sai na hora','pix','escolherPagContratar(this.value)')+
      (cabeNaCarteira?opcaoPag('wPag','carteira',atual,'Carteira TaskGO','Saldo de '+money(saldo),'wallet','escolherPagContratar(this.value)'):'')+
      (Number(valor)<1000?opcaoPag('wPag','dinheiro',atual,'Dinheiro no local','Você paga a quem atender, em espécie, no fim do serviço','money','escolherPagContratar(this.value)'):'')+
    '</div>'+
    (cartaoNaPagina()?'':'<button type="button" class="btn btn-ghost btn-sm pag-novo" onclick="novoCartao(c=>{W.pag={metodo:\'cartao\',cartaoId:c.id,parcelas:1};render();})">'+ic('plus')+'Adicionar cartão</button>')+
    (W.pag.metodo==='cartao'?campoParcelas(valor,W.pag.parcelas,'W.pag.parcelas=Number(this.value)','wParcelas'):'')+
    '<p class="hint">'+(W.pag.metodo==='dinheiro'?'O pedido sai sem pagar agora. Tenha o valor em dinheiro no fim do serviço; você pode trocar a forma de pagamento uma vez durante o atendimento.':(W.pag.metodo==='cartao'?'Cobramos no cartão ao enviar.':W.pag.metodo==='carteira'?'Sai do saldo da sua carteira ao enviar.':W.pag.metodo==='cartao_novo'?'Ao enviar, abrimos a página segura do Asaas para você digitar o cartão.':'Ao enviar, aparecem o QR e o código Pix.')+' O valor fica com a TaskGO até o serviço terminar; se o pedido for cancelado, ele volta para você.')+'</p></section>';
}
function escolherPagContratar(v){W.pag=['pix','cartao_novo','carteira','dinheiro','pix_direto','cartao_maquininha'].includes(v)?{metodo:v}:{metodo:'cartao',cartaoId:v.slice(7),parcelas:1};render();}
/* Texto do botão de enviar, conforme o modo e a forma de pagamento. */
function textoEnviarPedido(modo){
  const m=pagamentoLigado()&&W.pag?W.pag.metodo:'nenhum',ajuda=modo==='ajuda',agora=modo==='agora';
  if(m==='nenhum'||m==='dinheiro'||m==='pix_direto'||m==='cartao_maquininha')return ajuda?' Pedir ajuda agora':agora?' Chamar profissionais agora':'Enviar pedido ';
  if(m==='pix')return ajuda?' Pagar com Pix e pedir ajuda':agora?' Pagar com Pix e chamar':'Enviar e pagar com Pix ';
  return ajuda?' Pagar e pedir ajuda':agora?' Pagar e chamar agora':'Pagar e enviar ';
}
function pagamentoEscolhidoValido(){
  if(!pagamentoLigado())return true;
  if(!W.pag){toast('Escolha como você vai pagar','err');return false;}
  if(W.pag.metodo==='cartao'&&!(PG.cartoes||[]).some(c=>c.id===W.pag.cartaoId&&!c.vencido)){toast('Escolha um cartão válido ou pague com Pix','err');return false;}
  if(W.pag.metodo==='carteira'&&!(Number(PG.saldo)>=Number(W.oferta))){toast('O saldo da carteira não cobre o valor. Escolha outra forma.','err');return false;}
  return true;
}
/* Depois de criar o pedido: cobra no cartão escolhido ou abre a tela do Pix. */
async function pagarPedidoNovo(pedido,escolha){
  const destino=pedido.modo==='agora'||pedido.urgente&&pedido.quando==='agora'?'/app/chamando/'+encodeURIComponent(pedido.id):'/app/pedido/'+encodeURIComponent(pedido.id);
  if(escolha&&escolha.metodo==='cartao_novo'){
    try{const c=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(pedido.id),'POST',{metodo:'cartao_novo'});if(c.link)window.open(c.link,'_blank','noopener');}
    catch(e){toast(erroLegivel(e,'Não foi possível abrir o pagamento.'),'err');}
    PAGAR.aba='cartao';go('/app/pagar/'+encodeURIComponent(pedido.id));return;
  }
  if(escolha&&escolha.metodo==='carteira'){
    try{const c=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(pedido.id),'POST',{metodo:'carteira'});
      if(c.status==='paga'){PG.saldo=null;await atualizarPedidoLocal(pedido.id);toast('Pago com o saldo da carteira.','ok');go(destino);return;}}
    catch(e){toast(erroLegivel(e,'O pagamento não foi concluído.'),'err');}
  }
  if(escolha&&escolha.metodo==='cartao'){
    try{
      const c=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(pedido.id),'POST',{metodo:'cartao',cartaoId:escolha.cartaoId,parcelas:escolha.parcelas||1});
      if(c.status==='paga'){await atualizarPedidoLocal(pedido.id);toast('Pago com '+c.cartao+'. O pedido já está procurando profissional.','ok');go(destino);return;}
    }catch(e){toast(erroLegivel(e,'O pagamento não foi concluído.'),'err');}
  }
  go('/app/pagar/'+encodeURIComponent(pedido.id));
}
async function atualizarPedidoLocal(id){const email=me()?.email;if(email)try{await syncPedidos(email);}catch(e){}}

/* ---------- Tela Pagar ---------- */
let PAGAR={id:null,resumo:null,erro:'',aba:null,escolha:null,parcelas:1,enviando:false,timer:null,fim:null,pago:false};
function telaPagar(id){
  if(DEMO_MODE)return dashHead('Pagar','Na demonstração não há pagamento.');
  if(me()?.role!=='cliente')return dashHead('Pagar','Só contas de cliente pagam pedidos.');
  if(PAGAR.id!==id){pararPagar();PAGAR={id,resumo:null,erro:'',aba:null,escolha:null,parcelas:1,enviando:false,timer:null,fim:null,pago:false};carregarPagar();}
  if(!PG.config||PG.cartoes===null)carregarPagamentos();
  const o=S.orders.find(x=>x.id===id),s=svc(o?.service||'');
  const voltar='<button type="button" class="btn-voltar" onclick="go(\'/app/pedido/'+esc(id)+'\')">'+ic('arrowLeft')+'<span>Voltar</span></button>';
  const r=PAGAR.resumo;
  let corpo;
  if(PAGAR.erro&&!r)corpo='<p class="muted">'+esc(PAGAR.erro)+'</p><button class="btn btn-ghost" onclick="carregarPagar()">'+ic('refresh')+'Tentar de novo</button>';
  else if(!r)corpo='<p class="muted" role="status">Carregando o pagamento…</p>';
  else if(PAGAR.pago||Number(r.falta)<=0)corpo=pagoOk(o);
  else{
    if(!PAGAR.aba)PAGAR.aba=r.aberta?(r.aberta.metodo==='pix'?'pix':'cartao'):(PG.cartoes||[]).some(c=>!c.vencido)?'cartao':'pix';
    corpo='<div class="pag-valor"><span>'+(r.situacao==='retido'?'Diferença a pagar':'Total')+'</span><b>'+money(r.falta)+'</b></div>'+faixaTeste()+
      (r.recusa?'<p class="pag-recusa" role="alert">'+ic('x')+esc(r.recusa)+'</p>':'')+
      '<div class="pag-abas" role="tablist">'+[['pix','Pix','pix'],['cartao','Cartão','card']].map(a=>'<button type="button" role="tab" aria-selected="'+(PAGAR.aba===a[0])+'" class="'+(PAGAR.aba===a[0]?'sel':'')+'" onclick="PAGAR.aba=\''+a[0]+'\';render()">'+ic(a[2])+a[1]+'</button>').join('')+'</div>'+
      (PAGAR.aba==='pix'?painelPix(r):painelCartao(r));
  }
  return '<div class="card pag-tela"><div class="passo-cab">'+voltar+'<div class="passo-tit"><h2 class="sub">Pagar pedido</h2><p class="muted">'+(o?esc(s.n)+' · '+money(o.price):'')+'</p></div></div>'+corpo+'</div>';
}
function painelPix(r){
  const a=r.aberta&&r.aberta.metodo==='pix'?r.aberta:null;
  if(!a)return '<div class="pag-painel"><p class="t2">O código vale por 1 hora. O pedido sai assim que o pagamento é confirmado.</p>'+
    '<button class="btn btn-primary btn-lg" onclick="gerarPix()"'+(PAGAR.enviando?' disabled aria-busy="true"':'')+'>'+ic('pix')+(PAGAR.enviando?'Gerando…':'Gerar Pix de '+money(r.falta))+'</button></div>';
  ligarEsperaPix(a);
  return '<div class="pag-painel pag-pix">'+
    (a.qrCode?'<img class="pag-qr" src="'+esc(a.qrCode)+'" alt="QR code do Pix de '+esc(money(a.valor))+'" width="220" height="220">':'')+
    '<div class="pag-pix-dados"><ol class="pag-passos"><li>Abra o app do seu banco e escolha Pix.</li><li>Leia o QR ou cole o código.</li><li>Confirme o pagamento de <b>'+money(a.valor)+'</b>.</li></ol>'+
    '<label class="leitor" for="pixCodigo">Código Pix copia e cola</label><textarea id="pixCodigo" class="textarea pag-codigo" readonly rows="3" onclick="this.select()">'+esc(a.pixCopiaECola)+'</textarea>'+
    '<div class="pag-acoes"><button class="btn btn-primary" onclick="copiarPix()">'+ic('copy')+'Copiar código</button>'+
    (pagamentoTeste()?'<button class="btn btn-ghost" onclick="simularPagamentoPix(\''+esc(a.id)+'\')"'+(PAGAR.enviando?' disabled':'')+'>'+ic('checkCircle')+'Simular pagamento</button>':'')+'</div>'+
    '<p class="pag-espera" role="status"><span class="pag-ponto" aria-hidden="true"></span>Esperando o pagamento · expira em <b id="pixExpira">'+tempoRestante(a.expiraEm)+'</b></p></div></div>';
}
function painelCartao(r){
  /* Cartão salvo em análise ou sem resposta do Asaas: não pede o cartão de novo, só espera a conferência. */
  if(r.aberta&&r.aberta.metodo==='cartao'&&(r.aberta.cartao||!r.aberta.link)){ligarEsperaPix(r.aberta);return '<div class="pag-painel"><p class="pag-espera" role="status"><span class="pag-ponto" aria-hidden="true"></span>Estamos conferindo o pagamento'+(r.aberta.cartao?' no '+esc(r.aberta.cartao):' no cartão')+' com o Asaas. Esta tela atualiza sozinha.</p></div>';}
  const ab=r.aberta&&r.aberta.metodo==='cartao'&&r.aberta.link?r.aberta:null;
  /* Asaas: cobrança aberta na página segura dele; esta tela espera a confirmação. */
  if(ab){ligarEsperaPix(ab);return '<div class="pag-painel"><p class="t2">Digite o cartão na página segura do Asaas. Esta tela atualiza sozinha quando o pagamento for aprovado.</p>'+
    '<div class="pag-acoes"><a class="btn btn-primary btn-lg" href="'+esc(ab.link)+'" target="_blank" rel="noopener">'+ic('lock')+'Abrir página de pagamento</a>'+
    (pagamentoTeste()?'<button class="btn btn-ghost" onclick="simularPagamentoPix(\''+esc(ab.id)+'\')"'+(PAGAR.enviando?' disabled':'')+'>'+ic('checkCircle')+'Simular pagamento</button>':'')+'</div>'+
    '<p class="pag-espera" role="status"><span class="pag-ponto" aria-hidden="true"></span>Esperando o pagamento no Asaas</p></div>';}
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido);
  if(!PAGAR.escolha||!cs.some(c=>c.id===PAGAR.escolha))PAGAR.escolha=(cs.find(c=>c.padrao)||cs[0]||{}).id||null;
  return '<div class="pag-painel">'+
    (PG.cartoes&&PG.cartoes.length?'<div class="formas" role="radiogroup" aria-label="Cartão">'+opcoesCartoes('pgCartao','cartao:'+PAGAR.escolha,'PAGAR.escolha=this.value.slice(7);PAGAR.parcelas=1;render()')+'</div>':'<p class="t2">Nenhum cartão salvo.</p>')+
    (cartaoNaPagina()?'<button type="button" class="btn btn-ghost btn-sm pag-novo" onclick="pagarCartaoNovo()"'+(PAGAR.enviando?' disabled':'')+'>'+ic('plus')+(cs.length?'Pagar com outro cartão':'Pagar com cartão de crédito')+'</button>':
     '<button type="button" class="btn btn-ghost btn-sm pag-novo" onclick="novoCartao(c=>{PAGAR.escolha=c.id;render();})">'+ic('plus')+'Adicionar cartão</button>')+
    (PAGAR.escolha?campoParcelas(r.falta,PAGAR.parcelas,'PAGAR.parcelas=Number(this.value)','pgParcelas'):'')+
    (cartaoNaPagina()&&!cs.length?'':'<button class="btn btn-primary btn-lg pag-pagar" onclick="pagarComCartao()"'+(!PAGAR.escolha||PAGAR.enviando?' disabled':'')+(PAGAR.enviando?' aria-busy="true"':'')+'>'+ic('lock')+(PAGAR.enviando?'Pagando…':'Pagar '+money(r.falta))+'</button>')+'</div>';
}
async function pagarCartaoNovo(){
  if(PAGAR.enviando)return;PAGAR.enviando=true;render();
  try{const c=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(PAGAR.id),'POST',{metodo:'cartao_novo'},'Não foi possível abrir o pagamento.');if(c.link)window.open(c.link,'_blank','noopener');}
  catch(e){toast(erroLegivel(e,'Não foi possível abrir o pagamento.'),'err');}
  PAGAR.enviando=false;await carregarPagar();
}
function pagoOk(o){
  const destino=o&&o.modo==='agora'?'/app/chamando/'+o.id:'/app/pedido/'+PAGAR.id;
  clearTimeout(PAGAR.fim);PAGAR.fim=setTimeout(()=>{if(route.sub==='pagar'&&route.id===PAGAR.id)go(destino);},1600);
  return '<div class="pag-ok" role="status"><span class="pag-ok-ic">'+ic('check')+'</span><h3>Pagamento confirmado</h3><p class="t2">'+(o&&o.modo==='agora'?'Chamando os profissionais mais perto…':'O pedido já está procurando profissional.')+'</p>'+
    '<a class="btn btn-primary" href="#'+destino+'">Continuar '+ic('arrow')+'</a></div>';
}
function tempoRestante(fim){const s=Math.max(0,Math.round((Number(fim)-Date.now())/1000));return String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');}
async function carregarPagar(){
  const id=PAGAR.id;if(!id)return;
  try{const r=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id));if(PAGAR.id!==id)return;PAGAR.resumo=r;PAGAR.erro='';if(Number(r.falta)<=0){PAGAR.pago=true;await atualizarPedidoLocal(id);}}
  catch(e){if(PAGAR.id===id)PAGAR.erro=erroLegivel(e,'Não foi possível abrir o pagamento.');}
  if(route.sub==='pagar')render();
}
async function gerarPix(){
  if(PAGAR.enviando)return;PAGAR.enviando=true;render();
  try{await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(PAGAR.id),'POST',{metodo:'pix'});}
  catch(e){toast(erroLegivel(e,'Não foi possível gerar o Pix.'),'err');}
  PAGAR.enviando=false;await carregarPagar();
}
async function pagarComCartao(){
  if(PAGAR.enviando||!PAGAR.escolha)return;PAGAR.enviando=true;render();
  try{
    const c=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(PAGAR.id),'POST',{metodo:'cartao',cartaoId:PAGAR.escolha,parcelas:PAGAR.parcelas||1});
    if(c.status==='paga'){PAGAR.pago=true;await atualizarPedidoLocal(PAGAR.id);}
  }catch(e){toast(erroLegivel(e,'O pagamento não foi concluído.'),'err');}
  PAGAR.enviando=false;await carregarPagar();
}
async function simularPagamentoPix(cobrancaId){
  if(PAGAR.enviando)return;PAGAR.enviando=true;render();
  try{const c=await apiJSON('/api/pagamentos/cobrancas/'+encodeURIComponent(cobrancaId)+'/simular-pix','POST');if(c.status==='paga')PAGAR.pago=true;}
  catch(e){toast(erroLegivel(e,'Não foi possível simular o pagamento.'),'err');}
  PAGAR.enviando=false;if(PAGAR.pago)await atualizarPedidoLocal(PAGAR.id);await carregarPagar();
}
function copiarPix(){
  const t=$('#pixCodigo');if(!t)return;
  (navigator.clipboard?navigator.clipboard.writeText(t.value):Promise.reject()).then(()=>toast('Código Pix copiado','ok')).catch(()=>{t.select();document.execCommand('copy');toast('Código Pix copiado','ok');});
}
/* Enquanto o Pix está aberto: confere a cada 3 s e atualiza o relógio. */
function ligarEsperaPix(a){
  if(PAGAR.timer)return;
  let n=0;
  PAGAR.timer=setInterval(async()=>{
    if(route.sub!=='pagar'||route.id!==PAGAR.id){pararPagar();return;}
    const e=$('#pixExpira');if(e)e.textContent=tempoRestante(a.expiraEm);
    if(++n%3)return;
    try{const c=await apiJSON('/api/pagamentos/cobrancas/'+encodeURIComponent(a.id));if(c.status!=='pendente'){pararPagar();if(c.status==='paga'){PAGAR.pago=true;await atualizarPedidoLocal(PAGAR.id);}await carregarPagar();}}catch(err){}
  },1000);
}
function pararPagar(){if(PAGAR.timer){clearInterval(PAGAR.timer);PAGAR.timer=null;}}

/* ---------- Novo cartão ---------- */
function novoCartao(depois){
  PG.depoisDoCartao=depois||null;
  openModal('<div class="modal-head"><div><h3>Adicionar cartão de crédito</h3><p>Guardamos só a bandeira, os 4 últimos números e a validade.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<form id="formCartao" class="pag-form" onsubmit="salvarNovoCartao(event)" novalidate>'+faixaTeste()+
    '<div class="field"><label for="ccNumero">Número do cartão</label><div class="pag-numero"><input class="input" id="ccNumero" inputmode="numeric" autocomplete="cc-number" maxlength="23" placeholder="0000 0000 0000 0000" oninput="formatarNumeroCartao(this)" required><span id="ccBandeira" class="pag-bandeira" aria-live="polite"></span></div></div>'+
    '<div class="field"><label for="ccNome">Nome no cartão</label><input class="input" id="ccNome" autocomplete="cc-name" maxlength="80" placeholder="Como está impresso" required></div>'+
    '<div class="grid g2" style="gap:0 16px"><div class="field"><label for="ccValidade">Validade</label><input class="input" id="ccValidade" inputmode="numeric" autocomplete="cc-exp" maxlength="5" placeholder="MM/AA" oninput="formatarValidade(this)" required></div>'+
    '<div class="field"><label for="ccCvv">Código de segurança</label><input class="input" id="ccCvv" inputmode="numeric" autocomplete="cc-csc" maxlength="4" placeholder="CVV" oninput="this.value=this.value.replace(/\\D/g,\'\')" required></div></div>'+
    ((PG.cartoes||[]).length?'<label class="pag-check"><input type="checkbox" id="ccPadrao"> Usar como cartão principal</label>':'')+
    (pagamentoTeste()?'<details class="pag-testes"><summary>Cartões de teste</summary><ul>'+CARTOES_TESTE.map(t=>'<li><button type="button" onclick="preencherCartaoTeste(\''+t[0]+'\')"><span>'+t[0]+'</span><small>'+t[1]+'</small></button></li>').join('')+'</ul><p class="hint">Use qualquer nome, uma validade futura e um código de 3 números.</p></details>':'')+
    '<p id="ccErro" class="pag-recusa" role="alert" hidden></p>'+
    '<div class="pag-form-acoes"><button type="button" class="btn btn-ghost" onclick="closeModal()">Cancelar</button><button type="submit" class="btn btn-primary" id="ccSalvar">'+ic('lock')+'Salvar cartão</button></div></form>',
    {onOpen:()=>{const n=$('#ccNumero');if(n)n.focus();}});
}
function bandeiraDoNumero(d){
  if(/^(4011|4312|4389|4514|4576|5041|5066|5067|509\d|6277|6362|6363|650\d|6516|6550)/.test(d))return 'Elo';
  if(/^(606282|3841)/.test(d))return 'Hipercard';
  if(/^3[47]/.test(d))return 'Amex';
  if(/^4/.test(d))return 'Visa';
  if(/^(5[1-5]|222[1-9]|22[3-9]\d|2[3-6]\d{2}|27[01]\d|2720)/.test(d))return 'Mastercard';
  return '';
}
function formatarNumeroCartao(el){
  const d=el.value.replace(/\D/g,'').slice(0,19);
  el.value=d.replace(/(\d{4})(?=\d)/g,'$1 ');
  const b=$('#ccBandeira');if(b)b.textContent=bandeiraDoNumero(d);
  const cvv=$('#ccCvv');if(cvv)cvv.maxLength=bandeiraDoNumero(d)==='Amex'?4:3;
}
function formatarValidade(el){const d=el.value.replace(/\D/g,'').slice(0,4);el.value=d.length>2?d.slice(0,2)+'/'+d.slice(2):d;}
function preencherCartaoTeste(n){
  const set=(id,v)=>{const e=$('#'+id);if(e)e.value=v;};
  set('ccNumero',n);formatarNumeroCartao($('#ccNumero'));
  if(!$('#ccNome').value)set('ccNome','Cliente Teste');
  if(!$('#ccValidade').value)set('ccValidade','12/'+String(new Date().getFullYear()+3).slice(-2));
  if(!$('#ccCvv').value)set('ccCvv','123');
}
async function salvarNovoCartao(e){
  e.preventDefault();
  const erro=$('#ccErro'),botao=$('#ccSalvar');
  const val=($('#ccValidade').value||'').split('/');
  const corpo={numero:$('#ccNumero').value,titular:$('#ccNome').value,mes:Number(val[0])||null,ano:Number(val[1])||null,cvv:$('#ccCvv').value,padrao:!!($('#ccPadrao')&&$('#ccPadrao').checked)};
  const falta=!corpo.numero.trim()?'Informe o número do cartão.':!corpo.titular.trim()?'Informe o nome como está no cartão.':!corpo.mes||!corpo.ano?'Informe a validade (MM/AA).':!corpo.cvv?'Informe o código de segurança.':'';
  if(falta){erro.textContent=falta;erro.hidden=false;return;}
  botao.disabled=true;botao.setAttribute('aria-busy','true');
  try{
    const c=await apiJSON('/api/pagamentos/cartoes','POST',corpo);
    PG.cartoes=await apiJSON('/api/pagamentos/cartoes');
    closeModal();toast(c.bandeira+' •••• '+c.final4+' salvo','ok');
    const depois=PG.depoisDoCartao;PG.depoisDoCartao=null;if(depois)depois(c);else render();
  }catch(err){erro.textContent=erroLegivel(err,'Não foi possível salvar o cartão.');erro.hidden=false;botao.disabled=false;botao.removeAttribute('aria-busy');}
}

/* ---------- Cartões na Carteira ---------- */
function secaoCartoes(){
  if(DEMO_MODE)return '';
  if(!PG.config||PG.cartoes===null){carregarPagamentos();return '<section class="carteira-bloco"><h2 class="sub">Cartões</h2><p class="muted" style="margin-top:8px">Carregando…</p></section>';}
  if(!pagamentoLigado())return '';
  const cs=PG.cartoes||[];
  return '<section class="carteira-bloco"><h2 class="sub">Cartões</h2>'+faixaTeste()+
    (cs.length?'<ul class="cartao-lista">'+cs.map(c=>'<li class="cartao-item'+(c.vencido?' vencido':'')+'">'+ic('card')+'<div><b>'+esc(c.bandeira)+' •••• '+esc(c.final4)+'</b><small>'+esc(c.titular)+' · '+(c.vencido?'vencido em ':'vence ')+validadeCartao(c)+'</small></div>'+
      (c.padrao?'<span class="tag tag-info">Principal</span>':'<button type="button" class="btn btn-ghost btn-sm" onclick="cartaoPrincipal(\''+esc(c.id)+'\')">Tornar principal</button>')+
      '<button type="button" class="icon-btn" aria-label="Remover '+esc(c.bandeira)+' final '+esc(c.final4)+'" onclick="removerCartao(\''+esc(c.id)+'\')">'+ic('trash')+'</button></li>').join('')+'</ul>':
      '<p class="t2" style="margin:8px 0 14px">Nenhum cartão salvo.'+(cartaoNaPagina()?' O cartão fica salvo aqui depois do primeiro pagamento na página segura do Asaas.':' Com um cartão, o pedido é pago e enviado num toque.')+'</p>')+
    (cartaoNaPagina()?'':'<button type="button" class="btn btn-primary" onclick="novoCartao()">'+ic('plus')+'Adicionar cartão</button>')+'</section>';
}
async function cartaoPrincipal(id){try{await apiJSON('/api/pagamentos/cartoes/'+encodeURIComponent(id)+'/padrao','PUT');PG.cartoes=await apiJSON('/api/pagamentos/cartoes');render();}catch(e){toast(erroLegivel(e,'Não foi possível mudar o cartão principal.'),'err');}}
async function removerCartao(id){
  const c=(PG.cartoes||[]).find(x=>x.id===id);if(!c)return;
  if(!await confirmar({titulo:'Remover o '+c.bandeira+' •••• '+c.final4+'?',botao:'Remover',perigo:true,icone:'card'}))return;
  try{await apiJSON('/api/pagamentos/cartoes/'+encodeURIComponent(id),'DELETE');PG.cartoes=await apiJSON('/api/pagamentos/cartoes');toast('Cartão removido','ok');render();}
  catch(e){toast(erroLegivel(e,'Não foi possível remover o cartão.'),'err');}
}

/* ---------- Resumo no pedido ---------- */
const PGP={id:null,dados:null,em:0,carregando:false};
function blocoPagamentoPedido(o,isPro){
  if(DEMO_MODE||!o||(!o.paid&&o.status!=='aguardando_pagamento'))return '';
  setTimeout(()=>carregarPagamentoPedido(o.id,isPro),0);
  return '<div class="card pag-pedido" id="pagPedido" aria-live="polite">'+(PGP.id===o.id&&PGP.dados?htmlPagamentoPedido(o,isPro,PGP.dados):'<p class="muted">Carregando o pagamento…</p>')+'</div>';
}
async function carregarPagamentoPedido(id,isPro,forcar){
  if(PGP.carregando)return;
  if(!forcar&&PGP.id===id&&Date.now()-PGP.em<4000)return;
  PGP.carregando=true;
  try{const d=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id));PGP.id=id;PGP.dados=d;PGP.em=Date.now();}catch(e){}
  finally{PGP.carregando=false;}
  const caixa=$('#pagPedido'),o=S.orders.find(x=>x.id===id);
  if(caixa&&o&&PGP.id===id&&PGP.dados)caixa.innerHTML=htmlPagamentoPedido(o,isPro,PGP.dados);
}
function htmlPagamentoPedido(o,isPro,d){
  const pagas=(d.cobrancas||[]).filter(c=>c.status==='paga'||c.status==='estornada');
  const como=pagas.map(c=>c.metodo==='pix'?'Pix':c.metodo==='carteira'?'Carteira TaskGO':c.cartao+(c.parcelas>1?' em '+c.parcelas+'x':'')).filter((x,i,a)=>a.indexOf(x)===i).join(' e ');
  const cab=(t,cls)=>'<div class="pag-pedido-cab">'+ic('wallet')+'<h2 class="sub">Pagamento</h2><span class="tag '+cls+'">'+t+'</span></div>';
  const linha=(rot,val)=>'<div><dt>'+rot+'</dt><dd>'+val+'</dd></div>';
  if(isPro){
    if(d.situacao==='liberado')return cab('Recebido','tag-ok')+'<p class="t2">'+money(o.repasse!=null?o.repasse:d.repasse)+' caíram na sua carteira.</p><a class="btn btn-ghost btn-sm" href="#/app/carteira">'+ic('wallet')+'Ver carteira</a>';
    if(d.situacao==='estornando')return cab('Devolvendo','tag-muted')+'<p class="t2">O pedido foi cancelado e o valor está voltando para o cliente.</p>';
    if(d.situacao==='estornado')return cab('Devolvido','tag-muted')+'<p class="t2">'+(d.liberacao==='devolvido'?'A equipe analisou o problema e devolveu o valor ao cliente.':'O pedido foi cancelado e o valor voltou para o cliente.')+'</p>';
    if(o.status==='concluido'&&d.liberacao==='aguardando')return cab('Esperando o cliente','tag-warn')+'<p class="t2">'+money(d.repasse)+' vão para a sua carteira quando o cliente confirmar, ou sozinhos às '+horaLiberacao(d.liberarApos)+'.</p>';
    if(o.status==='concluido'&&d.liberacao==='problema')return cab('Em análise','tag-warn')+'<p class="t2">O cliente relatou um problema. O pagamento fica retido até a equipe da TaskGO decidir.</p>';
    if(Number(d.falta)>0)return cab('Falta a diferença','tag-warn')+'<p class="t2">Esperando o cliente pagar '+money(d.falta)+' da diferença do valor combinado. Depois disso você pode marcar a chegada.</p>';
    return cab('Garantido','tag-ok')+'<dl class="resumo-pedido">'+linha('Cliente pagou',money(d.pago))+linha('Taxa TaskGO ('+taxaPct(o)+'%)','− '+money(d.taxa))+linha('Você recebe ao concluir','<b class="pag-verde">'+money(d.repasse)+'</b>')+'</dl>';
  }
  if(o.status==='aguardando_pagamento'||Number(d.falta)>0){
    const dif=d.situacao==='retido';
    return cab(dif?'Falta a diferença':'Esperando pagamento','tag-warn')+'<p class="t2">'+(dif?'O valor combinado ficou maior. Pague '+money(d.falta)+' para o profissional seguir.':'O pedido só procura profissional depois de pago.')+'</p>'+
      (d.recusa?'<p class="pag-recusa">'+ic('x')+esc(d.recusa)+'</p>':'')+
      '<a class="btn btn-primary" href="#/app/pagar/'+esc(o.id)+'">'+ic('lock')+'Pagar '+money(d.falta)+'</a>';
  }
  if(o.status==='concluido'&&d.liberacao==='aguardando')return cab('Confirme o serviço','tag-warn')+'<p class="t2">O profissional marcou como concluído. Confirme se está tudo certo. Sem resposta, o pagamento vai para ele às '+horaLiberacao(d.liberarApos)+'.</p>'+
    '<div class="pag-acoes"><button class="btn btn-primary" onclick="confirmarConclusao(\''+esc(o.id)+'\')">'+ic('check')+'Confirmar que terminou</button><button class="btn btn-ghost" onclick="abrirProblemaPedido(\''+esc(o.id)+'\')">Tive um problema</button></div>';
  if(o.status==='concluido'&&d.liberacao==='problema')return cab('Em análise','tag-warn')+'<p class="t2">Recebemos o seu relato. O pagamento fica retido até a equipe da TaskGO decidir.</p>';
  if(d.situacao==='estornando')return cab('Devolvendo','tag-warn')+'<p class="t2">'+money(d.pago)+' voltam para '+esc(como||'você')+' assim que o banco liberar.</p>';
  if(d.situacao==='estornado')return cab('Devolvido','tag-muted')+'<p class="t2">'+money(d.devolvido)+' voltaram para '+esc(como||'você')+'.</p>';
  if(d.situacao==='liberado')return cab('Concluído','tag-ok')+'<dl class="resumo-pedido">'+linha('Pago com',esc(como))+linha('Valor do serviço',money(o.price))+(Number(d.devolvido)>0?linha('Devolvido a você',money(d.devolvido)):'')+'</dl>';
  return cab('Pago','tag-ok')+'<dl class="resumo-pedido">'+linha('Pago com',esc(como))+linha('Valor',money(d.pago))+'</dl><p class="hint" style="margin-top:10px">O valor fica com a TaskGO e só vai para o profissional quando o serviço terminar.</p>'+
    (!o.trocouForma&&Number(o.price)<1000&&!['concluido','cancelado','aguardando_pagamento'].includes(o.status)?'<button class="btn btn-ghost btn-sm" style="margin-top:10px" onclick="abrirTrocaForma(\''+esc(o.id)+'\')">'+ic('money')+'Pagar em dinheiro</button>':'');
}
function horaLiberacao(ms){if(!ms)return '—';const d=new Date(Number(ms)),hoje=new Date();return (d.toDateString()===hoje.toDateString()?'':d.toLocaleDateString('pt-BR',{day:'2-digit',month:'2-digit'})+' ')+d.toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});}
async function confirmarConclusao(id){
  try{await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id)+'/confirmar-conclusao','POST');toast('Confirmado. O pagamento foi para o profissional.','ok');await atualizarPedidoLocal(id);carregarPagamentoPedido(id,false,true);render();}
  catch(e){toast(erroLegivel(e,'Não foi possível confirmar.'),'err');}
}
function abrirProblemaPedido(id){
  openModal('<div class="modal-head"><div><h3>Tive um problema</h3><p>O pagamento fica retido até a equipe da TaskGO analisar.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="probTexto">O que aconteceu?</label><textarea class="textarea" id="probTexto" maxlength="1000" placeholder="Ex.: o profissional saiu antes de terminar a instalação"></textarea></div>'+
    '<p id="probErro" class="pag-recusa" role="alert" hidden></p><div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-danger" id="probEnviar" onclick="enviarProblemaPedido(\''+esc(id)+'\')">Enviar à equipe</button></div>',{onOpen:()=>$('#probTexto').focus()});
}
async function enviarProblemaPedido(id){
  const t=($('#probTexto').value||'').trim(),erro=$('#probErro');
  if(t.length<10){erro.textContent='Conte o problema em pelo menos 10 caracteres.';erro.hidden=false;return;}
  $('#probEnviar').disabled=true;
  try{await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id)+'/problema','POST',{motivo:t});closeModal();toast('Enviado. A equipe vai analisar.','ok');await atualizarPedidoLocal(id);carregarPagamentoPedido(id,false,true);}
  catch(e){erro.textContent=erroLegivel(e,'Não foi possível enviar.');erro.hidden=false;$('#probEnviar').disabled=false;}
}
function rotuloPagamentoPedido(o){
  if(o.status==='aguardando_pagamento')return 'Esperando pagamento';
  if(o.formaPagamento==='dinheiro'&&!o.paid)return 'Dinheiro no local';
  if(o.formaPagamento==='pix_direto'&&!o.paid)return 'Pix ao profissional, depois do serviço';
  if(o.formaPagamento==='cartao_maquininha'&&!o.paid)return 'Cartão na maquininha do profissional';
  if(o.status==='concluido'&&o.paid==='retido')return o.liberacao==='problema'?'Retido, em análise':'Esperando confirmação';
  return ({retido:'Pago, com a TaskGO até concluir',liberado:'Pago e repassado',estornando:'Devolvendo ao cliente',estornado:'Devolvido',dinheiro:'Pago em dinheiro',pix_direto:'Pago por Pix ao profissional',cartao_maquininha:'Pago no cartão (maquininha)',nao_pago:'Não pago (pendência)',pago_depois:'Pago depois (pendência)'})[o.paid]||'—';
}

/* ---------- Resgate do profissional (chave Pix) ---------- */
let RESGATE={chave:undefined,enviando:false};
async function carregarChavePix(){try{const k=await apiJSON('/api/financeiro/chave-pix');RESGATE.chave=k&&k.chave?k:null;}catch(e){RESGATE.chave=null;}if(route.sub==='carteira')render();}
const TIPOS_CHAVE={CPF:'CPF',CNPJ:'CNPJ',EMAIL:'E-mail',PHONE:'Telefone',EVP:'Chave aleatória'};
function secaoResgate(saldo){
  if(DEMO_MODE||!pagamentoLigado())return '';
  if(RESGATE.chave===undefined){RESGATE.chave=null;setTimeout(carregarChavePix,0);}
  const k=RESGATE.chave,valor=Number(saldo)||0;
  return '<section class="carteira-bloco"><h2 class="sub">Resgatar</h2>'+faixaTeste()+
    '<p class="t2" style="margin:6px 0 14px">Disponível: <b>'+money(valor)+'</b>. O resgate vai por Pix para a sua chave.</p>'+
    (k?'<p class="resgate-chave">'+ic('pix')+'<span>'+esc(TIPOS_CHAVE[k.tipo]||k.tipo)+': <b>'+esc(k.chave)+'</b></span><button type="button" class="link-btn" onclick="RESGATE.chave=null;RESGATE.editar=true;render()">trocar</button></p>':'')+
    (!k||RESGATE.editar?'<form class="grid g2 form-chave" style="gap:0 16px" onsubmit="salvarChavePix(event)" novalidate><div class="field"><label for="chTipo">Tipo de chave</label><select class="select" id="chTipo" onchange="trocarTipoChave()">'+
       Object.entries(TIPOS_CHAVE).map(e=>'<option value="'+e[0]+'">'+e[1]+'</option>').join('')+'</select></div>'+
       '<div class="field"><label for="chValor">Chave Pix</label>'+inputChave('CPF')+'<p class="hint" id="chErro" role="alert" hidden></p></div>'+
       '<div><button class="btn btn-ghost">'+ic('check')+'Salvar chave</button></div></form>':'')+
    '<button type="button" class="btn btn-primary" onclick="resgatarTudo('+valor+')"'+(!k||valor<=0||RESGATE.enviando?' disabled':'')+'>'+ic('down')+(RESGATE.enviando?'Enviando...':'Resgatar '+money(valor))+'</button></section>';
}
/* Cada tipo de chave aceita só o formato dele: a máscara corta o que passar do tamanho. */
const so=(v,n)=>String(v).replace(/\D/g,'').slice(0,n);
function digitosValidos(d,pesos){let soma=0;for(let i=0;i<pesos.length;i++)soma+=d[i]*pesos[i];const r=soma%11;return (r<2?0:11-r)===Number(d[pesos.length]);}
function cpfOk(d){if(d.length!==11||/^(\d)\1+$/.test(d))return false;for(const n of[9,10]){let s=0;for(let i=0;i<n;i++)s+=d[i]*(n+1-i);if(s*10%11%10!==Number(d[n]))return false;}return true;}
function cnpjOk(d){return d.length===14&&!/^(\d)\1+$/.test(d)&&digitosValidos(d,[5,4,3,2,9,8,7,6,5,4,3,2])&&digitosValidos(d,[6,5,4,3,2,9,8,7,6,5,4,3,2]);}
const FORMATO_CHAVE={
  CPF:{max:14,modo:'numeric',ph:'000.000.000-00',mascara:v=>so(v,11).replace(/^(\d{3})(\d)/,'$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/,'$1.$2.$3').replace(/\.(\d{3})(\d{1,2})$/,'.$1-$2'),
    ok:v=>cpfOk(so(v,11)),erro:'CPF inválido. Confira os 11 números.'},
  CNPJ:{max:18,modo:'numeric',ph:'00.000.000/0000-00',mascara:v=>so(v,14).replace(/^(\d{2})(\d)/,'$1.$2').replace(/^(\d{2})\.(\d{3})(\d)/,'$1.$2.$3').replace(/\.(\d{3})(\d)/,'.$1/$2').replace(/(\d{4})(\d{1,2})$/,'$1-$2'),
    ok:v=>cnpjOk(so(v,14)),erro:'CNPJ inválido. Confira os 14 números.'},
  EMAIL:{max:77,modo:'email',tipo:'email',ph:'voce@gmail.com',mascara:v=>String(v).replace(/\s/g,'').toLowerCase(),
    ok:v=>/^[a-z0-9._%+-]+@[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}$/.test(v),erro:'E-mail inválido. Use o formato nome@provedor.com.'},
  PHONE:{max:15,modo:'tel',tipo:'tel',ph:'(81) 90000-0000',mascara:v=>so(v,11).replace(/^(\d{2})(\d)/,'($1) $2').replace(/(\d{5})(\d{1,4})$/,'$1-$2'),
    ok:v=>/^\d{2}9\d{8}$/.test(so(v,11)),erro:'Celular inválido. Use DDD + 9 números.'},
  EVP:{max:36,modo:'text',ph:'00000000-0000-0000-0000-000000000000',mascara:v=>{const h=String(v).toLowerCase().replace(/[^0-9a-f]/g,'').slice(0,32);return [h.slice(0,8),h.slice(8,12),h.slice(12,16),h.slice(16,20),h.slice(20)].filter(Boolean).join('-');},
    ok:v=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(v),erro:'Chave aleatória inválida. Ela tem 32 letras e números.'}
};
function inputChave(tipo){const f=FORMATO_CHAVE[tipo];return '<input class="input" id="chValor" type="'+(f.tipo||'text')+'" inputmode="'+f.modo+'" maxlength="'+f.max+'" placeholder="'+f.ph+'" autocomplete="off" spellcheck="false" oninput="mascararChave(this)">';}
function trocarTipoChave(){const el=$('#chValor');if(!el)return;el.outerHTML=inputChave($('#chTipo').value);const e=$('#chErro');if(e)e.hidden=true;$('#chValor').focus();}
function mascararChave(el){const f=FORMATO_CHAVE[$('#chTipo').value];const v=f.mascara(el.value);if(v!==el.value)el.value=v;const e=$('#chErro');if(e)e.hidden=true;el.removeAttribute('aria-invalid');}
async function salvarChavePix(e){
  e.preventDefault();
  const tipo=$('#chTipo').value,f=FORMATO_CHAVE[tipo],el=$('#chValor'),erro=$('#chErro');
  if(!f.ok(el.value)){el.setAttribute('aria-invalid','true');if(erro){erro.textContent=el.value?f.erro:'Digite a chave Pix.';erro.hidden=false;}el.focus();return;}
  try{RESGATE.chave=await apiJSON('/api/financeiro/chave-pix','PUT',{tipo,chave:el.value},'Não foi possível salvar a chave.');RESGATE.editar=false;render();if(!voltarDepoisDoAviso('Chave Pix salva. Voltando para o pedido...'))toast('Chave Pix salva','ok');}
  catch(err){toast(erroLegivel(err,'Não foi possível salvar a chave.'),'err');}
}
async function resgatarTudo(valor){
  if(RESGATE.enviando||!(valor>0))return;
  if(!await confirmar({titulo:'Resgatar para a sua chave Pix?',valor,valorRotulo:'Valor',botao:'Resgatar',icone:'pix'}))return;
  RESGATE.enviando=true;render();
  try{
    const r=await fetch(API_BASE_URL+'/api/financeiro/saques',{method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({valor:Math.round(valor*100)/100})});
    if(!r.ok)throw await erroDaResposta(r,'Não foi possível resgatar.');
    const s=await r.json();
    toast(s.status==='CONCLUIDO'?'Resgate enviado para a sua chave Pix':s.status==='FALHOU'?'O resgate não foi enviado; o valor voltou para o saldo':'Resgate enviado; o banco está processando','ok');
  }catch(err){toast(erroLegivel(err,'Não foi possível resgatar.'),'err');}
  RESGATE.enviando=false;financePageData=null;render();
}
