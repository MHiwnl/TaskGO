/* =========================================================
   CARTEIRA NO PAGAMENTO DIRETO
   O dinheiro do serviço vai do cliente para quem atende, sem passar pela TaskGO.
   Quem atende: a chave Pix onde recebe, a cobrança automática da taxa num cartão autorizado
   e o histórico. Quem contrata: os pagamentos feitos e os recibos.
   ========================================================= */
const ROTULO_MOVIMENTO={RECEBIDO_PIX_DIRETO:'Recebido do cliente por Pix',RECEBIDO_MAQUININHA:'Recebido do cliente no cartão',RECEBIDO_DINHEIRO:'Recebido do cliente em dinheiro',
  PENDENCIA_PAGA:'Taxa da TaskGO paga',PENDENCIA_PAGA_DIRETO:'Valor em aberto recebido',TAXA_DINHEIRO:'Taxa da TaskGO descontada do saldo',REPASSE:'Recebido pela TaskGO',ESTORNO:'Devolução'};
function carteiraDireta(){
  const u=me(),isPro=u.role==='profissional',ajudou=!isPro&&!!(typeof AJ!=='undefined'&&AJ.estado&&AJ.estado.conhece);
  if(!PGD.pend)carregarPendencias();
  if(financePageUserId!==u.id){financePageData=null;financePageError='';financePageUserId=u.id;}
  if(!financePageData&&!financePageLoading&&!financePageError)carregarCarteiraFinanceira();
  if(isPro||ajudou)return dashHead('Carteira','A chave onde você recebe dos clientes e a taxa da TaskGO.')+
    '<div class="carteira-direta">'+secaoChaveRecebimento()+(isPro?secaoPlanoCarteira():'')+secaoCobrancaAutomatica()+secaoPendencias()+historicoMovimentos()+'</div>';
  return dashHead('Pagamentos','O que você pagou e o recibo de cada serviço.')+'<div class="carteira-direta">'+secaoPendencias()+pagamentosCliente()+'</div>';
}

/* Chave Pix de quem atende: é para ela que vai o QR que o cliente vê no fim do serviço. */
function secaoChaveRecebimento(){
  if(RESGATE.chave===undefined){RESGATE.chave=null;setTimeout(carregarChavePix,0);}
  const k=RESGATE.chave;
  return '<section class="carteira-bloco"><h2 class="sub">Receber dos clientes</h2>'+
    '<p class="t2" style="margin:6px 0 14px">No fim de cada serviço, o cliente vê um QR do Pix com esta chave e o valor.</p>'+
    (k&&!RESGATE.editar?'<p class="resgate-chave">'+ic('pix')+'<span>'+esc(TIPOS_CHAVE[k.tipo]||k.tipo)+': <b>'+esc(k.chave)+'</b></span><button type="button" class="link-btn" onclick="RESGATE.editar=true;render()">trocar</button></p>':
    '<form class="grid g2 form-chave" style="gap:0 16px" onsubmit="salvarChavePix(event)" novalidate><div class="field"><label for="chTipo">Tipo de chave</label><select class="select" id="chTipo" onchange="trocarTipoChave()">'+
      Object.entries(TIPOS_CHAVE).map(e=>'<option value="'+e[0]+'">'+e[1]+'</option>').join('')+'</select></div>'+
      '<div class="field"><label for="chValor">Chave Pix</label>'+inputChave('CPF')+'<p class="hint" id="chErro" role="alert" hidden></p></div>'+
      '<div><button class="btn btn-primary">'+ic('check')+'Salvar chave</button></div></form>')+
    '</section>';
}

/* Cobrança automática: a taxa de cada serviço recebido é cobrada sozinha no cartão que a pessoa autorizou. */
let autorizaTaxa={cartao:null,aceito:false,salvando:false};
function secaoCobrancaAutomatica(){
  if(PG.cartoes===null||PG.cartoes===undefined){carregarPagamentos(true);return '';}
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido),ativo=PGD.pend&&PGD.pend.cartaoTaxa;
  const nome=c=>esc(c.bandeira)+' •••• '+esc(c.final4);
  let corpo;
  if(ativo){
    const c=(PG.cartoes||[]).find(x=>x.id===ativo);
    corpo='<p class="taxa-auto-ligada">'+ic('checkCircle')+'<span><b>Ligada</b> no '+(c?nome(c):'cartão autorizado')+'. A taxa de cada serviço é cobrada quando você confirma que recebeu.</span></p>'+
      '<button type="button" class="btn btn-ghost btn-sm" onclick="definirCobrancaAutomatica(null)"'+(autorizaTaxa.salvando?' disabled':'')+'>Desligar</button>';
  }else if(!cs.length){
    corpo='<p class="t2">Cadastre um cartão de crédito e autorize a cobrança da taxa de cada serviço.</p>'+
      (cartaoNaPagina()?'<p class="hint">Pague uma taxa em aberto com "Cartão de crédito", na página segura do Asaas: o cartão fica salvo aqui para as próximas.</p>':
        '<button type="button" class="btn btn-primary btn-sm" onclick="novoCartao(c=>{autorizaTaxa.cartao=c.id;render();})">'+ic('plus')+'Cadastrar cartão</button>');
  }else{
    if(!cs.some(c=>c.id===autorizaTaxa.cartao))autorizaTaxa.cartao=(cs.find(c=>c.padrao)||cs[0]).id;
    corpo='<div class="formas" role="radiogroup" aria-label="Cartão para a taxa">'+cs.map(c=>opcaoPag('taxaCartao',c.id,autorizaTaxa.cartao,nome(c),textoCartao(c),'card','autorizaTaxa.cartao=this.value;marcarForma(this)')).join('')+'</div>'+
      '<label class="check-linha"><input type="checkbox"'+(autorizaTaxa.aceito?' checked':'')+' onchange="autorizaTaxa.aceito=this.checked"> Autorizo a TaskGO a cobrar neste cartão a taxa de cada serviço que eu receber.</label>'+
      '<button type="button" class="btn btn-primary btn-sm" onclick="definirCobrancaAutomatica(autorizaTaxa.cartao)"'+(autorizaTaxa.salvando?' disabled':'')+'>'+ic('lock')+'Ligar cobrança automática</button>';
  }
  return '<section class="carteira-bloco"><h2 class="sub">Taxa da TaskGO</h2><p class="t2" style="margin:6px 0 14px">'+(souPro()?'10% nos serviços comuns e recorrentes (Plano Pro), de 12% a 6% nas obras e 10% na ajuda rápida.':'15% nos serviços comuns, 12% a partir de R$ 1.000, de 12% a 6% nas obras e 10% na ajuda rápida.')+'</p>'+corpo+'</section>';
}
async function definirCobrancaAutomatica(cartaoId){
  if(cartaoId&&!autorizaTaxa.aceito)return toast('Marque a autorização para ligar a cobrança automática.','err');
  autorizaTaxa.salvando=true;render();
  try{
    await apiJSON('/api/pendencias/cobranca-automatica','PUT',{cartaoId,autorizado:!!cartaoId},'Não foi possível salvar.');
    toast(cartaoId?'Cobrança automática ligada. As taxas em aberto foram cobradas no cartão.':'Cobrança automática desligada.','ok');
    autorizaTaxa.aceito=false;financePageData=null;
  }catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');}
  finally{autorizaTaxa.salvando=false;await carregarPendencias(true);render();}
}

function historicoMovimentos(){
  if(financePageError)return '<section class="carteira-bloco"><h2 class="sub">Histórico</h2><p class="pag-recusa">'+esc(financePageError)+'</p></section>';
  if(!financePageData)return '<section class="carteira-bloco"><h2 class="sub">Histórico</h2><p class="muted">Carregando…</p></section>';
  const lista=(financePageData.transactions&&financePageData.transactions.content)||[];
  return '<section class="carteira-bloco"><h2 class="sub">Histórico</h2>'+
    (lista.length?'<ul class="mov-lista">'+lista.map(t=>{const saida=/^(TAXA_|PENDENCIA_PAGA$)/.test(t.tipo);
      return '<li><div><b>'+esc(ROTULO_MOVIMENTO[t.tipo]||TX_ROTULO[t.tipo]||t.tipo)+'</b><small>'+dataHora(t.criadaEm)+'</small></div><span class="mov-valor'+(saida?' saida':'')+'">'+(saida?'− ':'')+money(t.valor)+'</span></li>';}).join('')+'</ul>':
      '<p class="muted" style="margin-top:8px">Os serviços que você receber e as taxas pagas aparecem aqui.</p>')+'</section>';
}

/* Cliente: pagamentos feitos direto aos profissionais, com o recibo de cada serviço. */
function pagamentosCliente(){
  const pagos=myOrders().filter(o=>o.status==='concluido').sort((a,b)=>(b.completedAt||b.created)-(a.completedAt||a.created));
  return '<section class="carteira-bloco"><h2 class="sub">Serviços pagos</h2>'+
    (pagos.length?'<ul class="mov-lista">'+pagos.map(o=>'<li><div><b>'+esc(svc(o.service).n)+'</b><small>'+dataHora(o.completedAt||o.created)+' · '+esc(o.professionalName||'Profissional')+' · '+esc(rotuloPagamentoPedido(o))+'</small></div>'+
      '<span class="mov-valor">'+money(o.price)+'</span><button type="button" class="btn btn-ghost btn-sm" onclick="baixarRecibo(\''+esc(o.id)+'\')">'+ic('file')+'Recibo</button></li>').join('')+'</ul>':
      '<p class="muted" style="margin-top:8px">Quando um serviço seu for concluído, ele aparece aqui com o recibo.</p>')+'</section>';
}
