/* =========================================================
   PAGAMENTO EM DINHEIRO
   O cliente paga a quem atendeu, em espécie, no fim do serviço. Sem troco: troco online
   (sai do saldo de quem atendeu e vai para a carteira do cliente). A taxa da TaskGO sai do
   saldo de quem atendeu ou é cobrada no balanço semanal. O cliente troca a forma de
   pagamento uma vez. Cliente não pagou: pendência, e ele não faz novos pedidos até pagar.
   ========================================================= */
const PGD={pend:null,carregando:false,troca:null};
function pedidoEmDinheiro(o){return !!o&&(['dinheiro','pix_direto','cartao_maquininha'].includes(o.formaPagamento)||['dinheiro','pix_direto','cartao_maquininha','nao_pago','pago_depois'].includes(o.paid));}
/* Pagamento direto: o cliente paga quem atende (Pix na chave dele ou dinheiro); pela TaskGO passa só a taxa. */
function modeloDireto(){
  if(!PG.config&&!PG.carregando&&me()&&!DEMO_MODE)carregarPagamentos().then(()=>{if(PG.config&&PG.config.modelo==='direto')render();});
  return !!PG.config&&PG.config.modelo==='direto';
}
/* Chave Pix de quem atende: o servidor só mostra depois do serviço (ou com valor em aberto). */
const CHAVES={};
function blocoChavePix(pedidoId,texto){
  const k=CHAVES[pedidoId];
  if(k===undefined){
    CHAVES[pedidoId]=null;
    apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(pedidoId)+'/chave-pix').then(r=>{CHAVES[pedidoId]=r;render();})
      .catch(e=>{CHAVES[pedidoId]={erro:erroLegivel(e,'Não foi possível carregar a chave Pix.')};render();});
  }
  if(!k)return '<div class="chave-pix" aria-busy="true"><p class="muted">Carregando a chave Pix…</p></div>';
  if(k.erro)return '<div class="chave-pix"><p class="pag-recusa">'+esc(k.erro)+'</p></div>';
  /* QR e copia e cola com a chave, o valor e o nome de quem recebe: o cliente só confere e paga no app do banco. */
  const codigo=codigoPix({chave:k.chave,nome:k.nome,valor:k.valor,txid:'TASKGO'+String(pedidoId).replace(/[^A-Za-z0-9]/g,'').slice(-14)});
  const svg=qrPixSvg(codigo);
  return '<div class="chave-pix"><p class="t2">'+(texto||'Pague')+' <b>'+money(k.valor)+'</b> a '+esc(k.nome)+' por Pix.</p>'+
    '<div class="chave-pix-qr"><button type="button" class="pix-qr" aria-label="Ampliar o QR code do Pix de '+esc(money(k.valor))+' para '+esc(k.nome)+'" onclick="ampliarQrPix(\''+esc(pedidoId)+'\')"'+(svg?'':' disabled')+'>'+(svg||'<span class="muted">Gerando o QR…</span>')+
      (svg?'<span class="pix-qr-zoom">'+ic('search')+'Ampliar</span>':'')+'</button>'+
    '<div class="chave-pix-dados"><p class="hint">Abra o app do banco, escolha Pix e aponte a câmera. O valor já vem preenchido.</p>'+
    '<button type="button" class="btn btn-primary btn-sm" data-chave="'+esc(codigo)+'" data-msg="Código Pix copiado" onclick="copiarChavePix(this)">'+ic('copy')+'Copiar código Pix</button>'+
    '<div class="chave-pix-linha"><span class="chave-pix-tipo">'+esc(TIPOS_CHAVE_PIX[k.tipo]||k.tipo||'Chave')+'</span><code>'+esc(k.chave)+'</code>'+
    '<button type="button" class="btn btn-ghost btn-sm" data-chave="'+esc(k.chave)+'" onclick="copiarChavePix(this)">'+ic('copy')+'Copiar chave</button></div></div></div>'+
    '<p class="hint">Quem recebe confirma no pedido.</p></div>';
}
/* QR grande, para ler com a câmera de longe ou com a tela do celular apontada para outro celular. */
function ampliarQrPix(pedidoId){
  const k=CHAVES[pedidoId];if(!k||k.erro)return;
  const codigo=codigoPix({chave:k.chave,nome:k.nome,valor:k.valor,txid:'TASKGO'+String(pedidoId).replace(/[^A-Za-z0-9]/g,'').slice(-14)});
  openModal('<div class="modal-head"><div><h3>Pague '+money(k.valor)+' por Pix</h3><p>Para '+esc(k.nome)+'. Aponte a câmera do app do banco.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="pix-qr-grande" role="img" aria-label="QR code do Pix de '+esc(money(k.valor))+' para '+esc(k.nome)+'">'+qrPixSvg(codigo)+'</div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Fechar</button>'+
    '<button type="button" class="btn btn-primary" data-chave="'+esc(codigo)+'" data-msg="Código Pix copiado" onclick="copiarChavePix(this)">'+ic('copy')+'Copiar código Pix</button></div>',{className:'modal-qr'});
}
const TIPOS_CHAVE_PIX={CPF:'CPF',CNPJ:'CNPJ',EMAIL:'E-mail',PHONE:'Telefone',EVP:'Chave aleatória'};
function copiarChavePix(b){navigator.clipboard.writeText(b.dataset.chave).then(()=>toast(b.dataset.msg||'Chave copiada','ok')).catch(()=>toast('Copie à mão.','err'));}
function esquecerChaves(){for(const k in CHAVES)delete CHAVES[k];}
window.addEventListener('hashchange',esquecerChaves);
/* Marca a opção escolhida sem redesenhar a tela. */
function marcarForma(input){const g=input.closest('.formas');if(g)g.querySelectorAll('.forma').forEach(l=>l.classList.toggle('sel',l.contains(input)));}
const ROTULO_PENDENCIA={servico_nao_pago:'Serviço não pago',taxa_cancelamento:'Taxa de cancelamento',taxa_dinheiro:'Taxa da TaskGO'};

/* ---------- No pedido ---------- */
function blocoDinheiroPedido(o,isPro){
  if(DEMO_MODE||!pedidoEmDinheiro(o))return '';
  const cab=(t,cls)=>'<div class="pag-pedido-cab">'+ic('money')+'<h2 class="sub">Pagamento</h2><span class="tag '+cls+'">'+t+'</span></div>';
  const linha=(rot,val)=>'<div><dt>'+rot+'</dt><dd>'+val+'</dd></div>';
  const v=money(o.price),id=esc(o.id),pix=o.formaPagamento==='pix_direto',direto=modeloDireto();
  const nomeForma=pix?'Pix':'dinheiro';
  let h;
  if(direto&&o.paid==='nao_pago'){
    const np=o.naoPago||{};
    h=isPro?cab('Não pago','tag-danger')+'<p class="t2">O cliente ficou devendo '+money(np.valor)+' e não faz novos pedidos até pagar a você.</p>'+
        '<button class="btn btn-primary" onclick="pendenciaRecebida(\''+id+'\')">'+ic('check')+'O cliente me pagou</button>':
      cab('Pendência em aberto','tag-danger')+'<p class="t2">Quem atendeu informou que o pagamento não foi feito. Enquanto não pagar, você não faz novos pedidos.</p>'+blocoChavePix(o.id,'Pague');
  }else if(o.paid==='cartao_maquininha'){
    const d=o.dinheiro||{};
    h=isPro?cab('Recebido no cartão','tag-ok')+'<dl class="resumo-pedido">'+linha('Cliente pagou',money(d.recebido))+linha('Taxa TaskGO','− '+money(d.taxa))+'</dl>':
      cab('Pago no cartão','tag-ok')+'<p class="t2">Você pagou '+money(d.recebido)+' na maquininha de quem atendeu.</p>';
  }else if(o.paid==='pix_direto'){
    const d=o.dinheiro||{};
    h=isPro?cab('Recebido por Pix','tag-ok')+'<dl class="resumo-pedido">'+linha('Cliente pagou',money(d.recebido))+linha('Taxa TaskGO','− '+money(d.taxa))+'</dl>':
      cab('Pago por Pix','tag-ok')+'<p class="t2">Você pagou '+money(d.recebido)+' por Pix a quem atendeu.</p>';
  }else if(o.paid==='nao_pago'||o.paid==='pago_depois'){
    const np=o.naoPago||{},depois=o.paid==='pago_depois';
    h=isPro?cab(depois?'Pago depois':'Não pago',depois?'tag-ok':'tag-danger')+'<p class="t2">'+(depois?'O cliente pagou a pendência e o valor foi para a sua carteira.':'O cliente ficou com uma pendência de '+money(np.valor)+'. Quando ele pagar, o valor cai na sua carteira.')+'</p>':
      cab(depois?'Pendência paga':'Pendência em aberto',depois?'tag-ok':'tag-danger')+'<p class="t2">'+(depois?'Você pagou o que devia deste pedido.':'Quem atendeu informou que o pagamento não foi feito. Ficou uma pendência de '+money(np.valor)+'; enquanto não pagar, você não faz novos pedidos.')+'</p>'+
      (depois?'':'<a class="btn btn-primary" href="#/app/carteira">'+ic('wallet')+'Pagar pendência</a>');
  }else if(o.paid==='dinheiro'){
    const d=o.dinheiro||{},troco=Number(d.troco)||0;
    h=isPro?cab('Recebido em dinheiro','tag-ok')+'<dl class="resumo-pedido">'+linha('Cliente entregou',money(d.recebido))+(troco?linha('Troco',money(troco)+(d.trocoOnline?' · online, do seu saldo':' · em espécie')):'')+
        linha('Taxa TaskGO','− '+money(d.taxa))+'</dl><p class="hint" style="margin-top:10px">A taxa sai do seu saldo da carteira; o que faltar é cobrado no balanço semanal.</p>':
      cab('Pago em dinheiro','tag-ok')+'<p class="t2">Você pagou '+money(d.recebido)+' em dinheiro.'+(troco&&d.trocoOnline?' '+money(troco)+' de troco estão na sua carteira TaskGO.':'')+'</p>';
  }else if(o.formaPagamento==='cartao_maquininha'){
    if(isPro){
      if(o.status==='concluido')h=cab('Receba no cartão','tag-warn')+'<p class="t2">Passe <b>'+v+'</b> na sua maquininha, no crédito ou no débito.</p>'+
        '<div class="pag-acoes"><button class="btn btn-primary" id="dinConfirmar" onclick="confirmarPixDireto(\''+id+'\')">'+ic('check')+'Recebi no cartão</button>'+
        '<button class="btn btn-danger" onclick="clienteNaoPagou(\''+id+'\',true)">Cliente não pagou</button></div>';
      else h=cab('Cartão no fim do serviço','tag-muted')+'<p class="t2">O cliente paga <b>'+v+'</b> no cartão, na sua maquininha, quando o serviço terminar.</p>'+
        (['chegou','em_andamento'].includes(o.status)?'<button class="btn btn-danger btn-sm" onclick="clienteNaoPagou(\''+id+'\','+(o.status==='em_andamento')+')">Cliente não vai pagar</button>':'');
    }else h=cab(o.status==='concluido'?'Pague no cartão':'Cartão no fim do serviço','tag-warn')+'<p class="t2">'+(o.status==='concluido'?'Pague <b>'+v+'</b> na maquininha de quem atendeu, no crédito ou no débito.':'Você paga <b>'+v+'</b> no cartão, na maquininha de quem atender, só depois que o serviço terminar.')+'</p>'+
      (o.status==='cancelado'||o.status==='concluido'?'':o.trocouForma?'<p class="hint">Você já trocou a forma de pagamento deste pedido.</p>':
        '<button class="btn btn-ghost btn-sm" onclick="abrirTrocaForma(\''+id+'\')">'+ic('refresh')+'Trocar forma de pagamento</button>');
  }else if(isPro&&pix){
    if(o.status==='concluido')h=cab('Receba por Pix','tag-warn')+'<p class="t2">O cliente paga <b>'+v+'</b> por Pix na sua chave.</p>'+
      '<div class="pag-acoes"><button class="btn btn-primary" id="dinConfirmar" onclick="confirmarPixDireto(\''+id+'\')">'+ic('check')+'Recebi o Pix</button>'+
      '<button class="btn btn-danger" onclick="clienteNaoPagou(\''+id+'\',true)">Cliente não pagou</button></div>';
    else if(['chegou','em_andamento'].includes(o.status))h=cab('Pix no fim do serviço','tag-warn')+'<p class="t2">O cliente paga <b>'+v+'</b> por Pix na sua chave quando o serviço terminar.</p>'+
      '<button class="btn btn-danger btn-sm" onclick="clienteNaoPagou(\''+id+'\','+(o.status==='em_andamento')+')">Cliente não vai pagar</button>';
    else h=cab('Pix no fim do serviço','tag-muted')+'<p class="t2">O cliente paga <b>'+v+'</b> por Pix na sua chave quando o serviço terminar.</p>';
  }else if(!isPro&&pix){
    h=o.status==='concluido'?cab('Pague por Pix','tag-warn')+blocoChavePix(o.id,'Pague'):
      cab('Pix no fim do serviço','tag-warn')+'<p class="t2">Você paga <b>'+v+'</b> por Pix a quem atender, só depois que o serviço terminar. Não pague antes.</p>'+
      (o.status==='cancelado'?'':o.trocouForma?'<p class="hint">Você já trocou a forma de pagamento deste pedido.</p>':
        '<button class="btn btn-ghost btn-sm" onclick="abrirTrocaForma(\''+id+'\')">'+ic('refresh')+'Trocar forma de pagamento</button>');
  }else if(isPro){
    if(o.status==='concluido'){
      h=cab('Receba em dinheiro','tag-warn')+'<p class="t2">O cliente paga <b>'+v+'</b> em dinheiro agora.</p>'+
        '<div class="field din-campo"><label for="dinRecebido">Quanto o cliente entregou</label><div class="din-valor"><span aria-hidden="true">R$</span><input class="input" id="dinRecebido" type="number" inputmode="decimal" min="'+Number(o.price)+'" step="0.01" value="'+Number(o.price)+'" oninput="pintarTrocoDinheiro('+Number(o.price)+')"></div></div>'+
        '<div id="dinTroco" aria-live="polite"></div>'+
        '<div class="pag-acoes"><button class="btn btn-primary" id="dinConfirmar" onclick="confirmarDinheiro(\''+id+'\','+Number(o.price)+')">'+ic('check')+'Recebi o dinheiro</button>'+
        '<button class="btn btn-danger" onclick="clienteNaoPagou(\''+id+'\',true)">Cliente não pagou</button></div>';
    }else if(['chegou','em_andamento'].includes(o.status)){
      h=cab('Dinheiro no local','tag-warn')+'<p class="t2">Receba <b>'+v+'</b> em dinheiro no fim do serviço.</p>'+
        '<button class="btn btn-danger btn-sm" onclick="clienteNaoPagou(\''+id+'\','+(o.status==='em_andamento')+')">Cliente não vai pagar</button>';
    }else h=cab('Dinheiro no local','tag-muted')+'<p class="t2">O cliente paga <b>'+v+'</b> em dinheiro no fim do serviço.</p>';
  }else{
    h=cab('Dinheiro no local','tag-warn')+'<p class="t2">Pague <b>'+v+'</b> em dinheiro a quem atender, no fim do serviço.</p>'+
      (o.status==='cancelado'?'':o.trocouForma?'<p class="hint">Você já trocou a forma de pagamento deste pedido.</p>':
        '<button class="btn btn-ghost btn-sm" onclick="abrirTrocaForma(\''+id+'\')">'+ic(direto?'refresh':'card')+'Trocar forma de pagamento</button>');
  }
  return '<div class="card pag-pedido" id="pagDinheiro" aria-live="polite">'+h+'</div>';
}
function pintarTrocoDinheiro(preco){
  const n=$('#dinTroco'),v=Number(($('#dinRecebido')||{}).value);if(!n)return;
  const troco=Math.round((v-preco)*100)/100;
  if(!(troco>0)){n.innerHTML=v&&v<preco?'<p class="pag-recusa">'+ic('x')+'Menos que o valor do serviço ('+money(preco)+').</p>':'';return;}
  const atual=(document.querySelector('input[name="dinTrocoModo"]:checked')||{}).value||'especie';
  n.innerHTML='<p class="t2">Troco: <b>'+money(troco)+'</b></p><div class="formas" role="radiogroup" aria-label="Como dar o troco">'+
    opcaoPag('dinTrocoModo','especie',atual,'Dei o troco em dinheiro','Entreguei '+money(troco)+' ao cliente','money','marcarForma(this)')+
    (modeloDireto()?'':opcaoPag('dinTrocoModo','online',atual,'Troco online','Sai do seu saldo e vai para a carteira TaskGO do cliente','wallet','marcarForma(this)'))+'</div>';
}
async function confirmarDinheiro(id,preco){
  const v=Number(($('#dinRecebido')||{}).value);
  if(!(v>=preco))return toast('O cliente precisa entregar pelo menos '+money(preco)+'.','err');
  const online=(document.querySelector('input[name="dinTrocoModo"]:checked')||{}).value==='online';
  const b=$('#dinConfirmar');if(b)b.disabled=true;
  try{
    const r=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id)+'/dinheiro','POST',{recebido:v,trocoOnline:online},'Não foi possível registrar.');
    toast(r.trocoOnline?'Recebido. O troco foi para a carteira do cliente.':'Pagamento em dinheiro registrado.','ok');
    await atualizarPedidoLocal(id);render();
  }catch(e){toast(erroLegivel(e,'Não foi possível registrar.'),'err');if(b)b.disabled=false;}
}
async function confirmarPixDireto(id){
  const b=$('#dinConfirmar');if(b)b.disabled=true;
  try{await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id)+'/dinheiro','POST',{},'Não foi possível registrar.');toast('Pix registrado. A taxa da TaskGO está na Carteira.','ok');await atualizarPedidoLocal(id);render();}
  catch(e){toast(erroLegivel(e,'Não foi possível registrar.'),'err');if(b)b.disabled=false;}
}
async function pendenciaRecebida(id){
  const o=S.orders.find(x=>x.id===id),np=(o&&o.naoPago)||{};
  if(!await confirmar({titulo:'O cliente pagou você?',texto:'Confirme só depois de o dinheiro cair na sua conta.',valor:np.valor,valorRotulo:'Valor em aberto',icone:'checkCircle',
    itens:['A pendência do cliente é encerrada e ele volta a poder fazer pedidos.','A taxa da TaskGO sobre o serviço vai para a sua Carteira.'],botao:'Sim, recebi'}))return;
  try{await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id)+'/pendencia-recebida','POST',null,'Não foi possível registrar.');toast('Registrado. O cliente já pode fazer novos pedidos.','ok');await atualizarPedidoLocal(id);render();}
  catch(e){toast(erroLegivel(e,'Não foi possível registrar.'),'err');}
}
async function clienteNaoPagou(id,feito){
  const o=S.orders.find(x=>x.id===id);if(!o)return;
  if(!await confirmar({titulo:'O cliente não pagou?',texto:feito?'Use só se o serviço foi feito e o cliente se recusou a pagar.':'Use só se você chegou e o cliente não quis seguir com o atendimento.',
    perigo:true,icone:'alert',valor:feito?o.price:null,valorRotulo:'Fica devendo',
    itens:[feito?'O pedido é cancelado e o cliente fica devendo o valor do serviço.':'O pedido é cancelado e o cliente fica com a taxa de cancelamento (20%, de R$ 10 a R$ 50).',
      'Até pagar, ele não consegue fazer novos pedidos.',
      modeloDireto()?'Ele paga direto a você; quando receber, confirme no pedido.':'Quando ele pagar, o valor cai na sua carteira.',
      'Fica registrado no histórico da conta dele.'],botao:'Confirmar: não pagou'}))return;
  try{await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(id)+'/nao-pago','POST',null,'Não foi possível registrar.');toast('Registrado. O cliente ficou com a pendência.','ok');await atualizarPedidoLocal(id);render();}
  catch(e){toast(erroLegivel(e,'Não foi possível registrar.'),'err');}
}

/* ---------- Trocar a forma de pagamento (cliente, uma vez) ---------- */
async function abrirTrocaForma(id){
  const o=S.orders.find(x=>x.id===id);if(!o)return;
  if(!PG.config||PG.cartoes===null)await carregarPagamentos(true);
  const paraDinheiro=o.formaPagamento!=='dinheiro';
  if(modeloDireto()){
    const formas=[['pix_direto','Pix para o profissional','QR com o valor, depois do serviço','pix'],['cartao_maquininha','Cartão na maquininha','Crédito ou débito, na maquininha de quem atender','card'],['dinheiro','Dinheiro no local','Em espécie, no fim do serviço','money']]
      .filter(f=>f[0]!==o.formaPagamento&&!(f[0]==='dinheiro'&&Number(o.price)>=1000));
    PGD.troca={id,metodo:formas[0][0]};
    openModal('<div class="modal-head"><div><h3>Trocar forma de pagamento</h3><p>Só dá para trocar uma vez neste pedido.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
      '<div class="formas" role="radiogroup" aria-label="Nova forma de pagamento">'+formas.map(f=>opcaoPag('trocaForma',f[0],PGD.troca.metodo,f[1],f[2],f[3],'PGD.troca.metodo=this.value;marcarForma(this)')).join('')+'</div>'+
      '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" id="trocaOk" onclick="confirmarTrocaForma()">'+ic('check')+'Trocar</button></div>');
    return;
  }
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido),saldo=Number(PG.saldo)||0,valor=Number(o.price);
  PGD.troca={id,metodo:paraDinheiro?'dinheiro':(saldo>=valor?'carteira':cs.length?'cartao:'+cs[0].id:cartaoNaPagina()?'cartao_novo':'pix')};
  const op=(v,t,d,i)=>opcaoPag('trocaForma',v,PGD.troca.metodo,t,d,i,'PGD.troca.metodo=this.value;marcarForma(this)');
  const opcoes=paraDinheiro?op('dinheiro','Dinheiro no local','O valor pago volta para você e você paga em dinheiro no fim','money'):
    (saldo>=valor?op('carteira','Carteira TaskGO','Saldo de '+money(saldo),'wallet'):'')+
    cs.map(c=>op('cartao:'+c.id,esc(c.bandeira)+' •••• '+esc(c.final4),textoCartao(c),'card')).join('')+
    (cartaoNaPagina()?op('cartao_novo',cs.length?'Outro cartão de crédito':'Cartão de crédito','Você digita o cartão na página segura do Asaas','card'):'')+
    op('pix','Pix','Pelo app do banco','pix');
  openModal('<div class="modal-head"><div><h3>Trocar forma de pagamento</h3><p>Só dá para trocar uma vez neste pedido.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    faixaTeste()+'<div class="formas" role="radiogroup" aria-label="Nova forma de pagamento">'+opcoes+'</div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" id="trocaOk" onclick="confirmarTrocaForma()">'+ic('check')+'Trocar</button></div>');
}
async function confirmarTrocaForma(){
  const t=PGD.troca;if(!t)return;
  const corpo=t.metodo.startsWith('cartao:')?{metodo:'cartao',cartaoId:t.metodo.slice(7)}:{metodo:t.metodo};
  const b=$('#trocaOk');if(b)b.disabled=true;
  try{
    const r=await apiJSON('/api/pagamentos/pedidos/'+encodeURIComponent(t.id)+'/trocar-forma','POST',corpo,'Não foi possível trocar.');
    closeModal();await atualizarPedidoLocal(t.id);
    if(r.forma==='pix_direto'||r.forma==='cartao_maquininha'){toast(r.forma==='pix_direto'?'Trocado para Pix.':'Trocado para cartão na maquininha.','ok');render();return;}
    if(r.forma==='dinheiro'){toast(modeloDireto()?'Trocado para dinheiro.':'Trocado para dinheiro. O valor pago volta para você.','ok');render();return;}
    if(r.status==='paga'){toast('Pago. Não precisa entregar dinheiro.','ok');render();return;}
    if(r.link)window.open(r.link,'_blank','noopener');
    PAGAR.aba=corpo.metodo==='pix'?'pix':'cartao';go('/app/pagar/'+encodeURIComponent(t.id));
  }catch(e){toast(erroLegivel(e,'Não foi possível trocar.'),'err');if(b)b.disabled=false;}
}

/* ---------- Pendências (Carteira e bloqueio no Contratar) ---------- */
async function carregarPendencias(forcar){
  if(DEMO_MODE||!me()||PGD.carregando||(PGD.pend&&!forcar))return;
  PGD.carregando=true;
  try{PGD.pend=await apiJSON('/api/pendencias');}catch(e){PGD.pend=PGD.pend||{pendencias:[],devendo:0,taxasAbertas:0};}
  finally{PGD.carregando=false;}
  if(route.page==='app'&&['carteira','contratar'].includes(route.sub)||route.page==='contratar')render();
}
function avisoPendenciaContratar(){
  if(DEMO_MODE||!me()||me().role!=='cliente')return '';
  if(!PGD.pend){carregarPendencias();return '';}
  const d=Number(PGD.pend.devendo)||0;if(!(d>0))return '';
  return '<div class="aviso-pendencia" role="alert">'+ic('lock')+'<p><b>Você tem '+money(d)+' em aberto.</b> '+(modeloDireto()?'Pague direto a quem atendeu para fazer novos pedidos.':'Pague com cartão ou com o saldo da carteira para fazer novos pedidos.')+'</p><a class="btn btn-primary btn-sm" href="#/app/carteira">'+(modeloDireto()?'Ver o que falta':'Pagar agora')+'</a></div>';
}
function secaoPendencias(){
  if(DEMO_MODE||!pagamentoLigado())return '';
  if(!PGD.pend){carregarPendencias();return '';}
  const abertas=(PGD.pend.pendencias||[]).filter(x=>x.status==='aberta');
  if(!abertas.length)return '';
  const cliente=me().role==='cliente';
  return '<section class="carteira-bloco pend-carteira"><h2 class="sub">'+(abertas.some(x=>x.tipo!=='taxa_dinheiro')?'Pendências':'Taxas a pagar')+'</h2>'+
    (abertas.some(x=>x.tipo==='taxa_dinheiro')?'<p class="t2" style="margin:6px 0 10px">'+(modeloDireto()?'Taxas da TaskGO sobre o que você recebeu direto dos clientes. Pague por Pix ou cartão; o que ficar em aberto é cobrado no balanço de segunda-feira.':'Taxas dos serviços recebidos em dinheiro. Saem do seu saldo sempre que ele tiver valor; o que faltar é cobrado no balanço de segunda-feira.')+'</p>':'')+
    '<ul class="pend-lista">'+abertas.map(x=>'<li><div><b>'+esc(ROTULO_PENDENCIA[x.tipo]||x.tipo)+'</b><small>'+esc(x.servico)+' · '+dt(x.criadaEm)+(Number(x.pago)>0?' · já pago '+money(x.pago):'')+'</small></div>'+
      '<b class="pend-valor">'+money(x.restante)+'</b>'+
      (modeloDireto()&&x.tipo!=='taxa_dinheiro'?'<a class="btn btn-primary btn-sm" href="#/app/pedido/'+encodeURIComponent(x.pedidoId)+'">Ver chave Pix</a>':
      '<button class="btn btn-primary btn-sm" onclick="abrirPagarPendencia(\''+esc(x.id)+'\')">Pagar</button>')+'</li>').join('')+'</ul></section>';
}
async function abrirPagarPendencia(pid){
  const x=((PGD.pend||{}).pendencias||[]).find(p=>p.id===pid);if(!x)return;
  if(!PG.config||PG.cartoes===null)await carregarPagamentos(true);
  const doCliente=x.tipo!=='taxa_dinheiro',valor=Number(x.restante),saldo=Number(PG.saldo)||0;
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido);
  const taxaDireta=!doCliente&&modeloDireto();
  PGD.pagando={id:pid,metodo:saldo>=valor?'carteira':taxaDireta?'pix':cs.length?'cartao:'+cs[0].id:'cartao_novo'};
  const op=(v,t,d,i,off)=>opcaoPag('pagPend',v,PGD.pagando.metodo,t,d,i,'PGD.pagando.metodo=this.value;marcarForma(this)',off?' forma-off':'');
  const opcoes=op('carteira','Saldo da carteira TaskGO',saldo>=valor?'Saldo de '+money(saldo):'Saldo de '+money(saldo)+': não cobre o valor','wallet',saldo<valor)+
    (taxaDireta?op('pix','Pix','Pelo app do banco','pix')+(cartaoNaPagina()?op('cartao_novo','Cartão de crédito','Na página segura do Asaas','card'):''):'')+
    (doCliente?cs.map(c=>op('cartao:'+c.id,esc(c.bandeira)+' •••• '+esc(c.final4),textoCartao(c),'card')).join('')+
      (cartaoNaPagina()?op('cartao_novo',cs.length?'Outro cartão de crédito':'Cartão de crédito','Você digita o cartão na página segura do Asaas','card'):''):'');
  openModal('<div class="modal-head"><div><h3>Pagar '+money(valor)+'</h3><p>'+esc(ROTULO_PENDENCIA[x.tipo]||'')+' · '+esc(x.servico)+'</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    faixaTeste()+'<div class="formas" role="radiogroup" aria-label="Como pagar">'+opcoes+'</div>'+
    '<div id="pendPix"></div><p id="pendErro" class="pag-recusa" role="alert" hidden></p>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" id="pendOk" onclick="confirmarPagarPendencia()">'+ic('lock')+'Pagar</button></div>');
}
async function confirmarPagarPendencia(){
  const t=PGD.pagando;if(!t)return;
  const corpo=t.metodo.startsWith('cartao:')?{metodo:'cartao',cartaoId:t.metodo.slice(7)}:{metodo:t.metodo};
  const b=$('#pendOk'),erro=$('#pendErro');if(b)b.disabled=true;
  try{
    const r=await apiJSON('/api/pendencias/'+encodeURIComponent(t.id)+'/pagar','POST',corpo,'Não foi possível pagar.');
    if(r.status==='paga'){closeModal();toast('Pago.','ok');financePageData=null;PG.saldo=null;await carregarPendencias(true);carregarPagamentos(true);render();return;}
    if(r.pixCopiaECola){
      $('#pendPix').innerHTML='<div class="pag-pix">'+(r.qrCode?'<img class="pag-qr" src="'+esc(r.qrCode)+'" alt="QR code do Pix" width="200" height="200">':'')+
        '<div class="pag-pix-dados"><label class="rotulo-campo" for="pendPixCod">Código Pix copia e cola</label><input class="input" id="pendPixCod" readonly value="'+esc(r.pixCopiaECola)+'">'+
        '<div class="obra-acoes"><button class="btn btn-ghost btn-sm" onclick="navigator.clipboard.writeText($(\'#pendPixCod\').value);toast(\'Copiado\',\'ok\')">'+ic('copy')+'Copiar código</button>'+
        (pagamentoTeste()?'<button class="btn btn-ghost btn-sm" onclick="apiJSON(\'/api/pagamentos/cobrancas/'+esc(r.id)+'/simular-pix\',\'POST\').catch(e=>toast(erroLegivel(e,\'Falhou\'),\'err\'))">Simular pagamento</button>':'')+'</div>'+
        '<p class="muted" role="status"><span class="pag-ponto"></span> Esperando o pagamento…</p></div></div>';
      if(b)b.hidden=true;esperarPendencia(r.id,true);return;
    }
    if(r.link){window.open(r.link,'_blank','noopener');esperarPendencia(r.id);}
    closeModal();toast('Termine o pagamento na página do Asaas. A pendência some quando ele for confirmado.','ok');
  }catch(e){if(erro){erro.textContent=erroLegivel(e,'Não foi possível pagar.');erro.hidden=false;}if(b)b.disabled=false;}
}
/* Cartão na página do Asaas: confere a cada 5 s por até 10 minutos. */
function esperarPendencia(cobrancaId,fecharModal){
  let n=0;const t=setInterval(async()=>{
    if(++n>120||!me())return clearInterval(t);
    try{const c=await apiJSON('/api/pagamentos/cobrancas/'+encodeURIComponent(cobrancaId));
      if(c.status!=='pendente'){clearInterval(t);if(fecharModal&&document.getElementById('pendPix'))closeModal();await carregarPendencias(true);financePageData=null;render();if(c.status==='paga')toast('Pago.','ok');}}catch(e){}
  },5000);
}
