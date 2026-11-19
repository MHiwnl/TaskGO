/* Purge unverified demo wallet values from browser state; these were never real funds. */
let paymentsDemoStateCleared=false;
for(const user of S.users){
  if(Number(user.balance)!==0||Number(user.pending)!==0||user.plan==='plus')paymentsDemoStateCleared=true;
  user.balance=0;user.pending=0;if(user.plan==='plus')user.plan='free';
}
if(S.tx.length){S.tx=[];paymentsDemoStateCleared=true;}
if(paymentsDemoStateCleared)Store.save();

let financePageData=null,financePageLoading=false,financePageError='',financePageUserId=null;
async function carregarCarteiraFinanceira(){
  if(financePageLoading||DEMO_MODE)return;
  const usuario=me();if(!usuario||!String(usuario.id).startsWith('db_'))return;
  const requestedUserId=usuario.id;financePageUserId=requestedUserId;
  financePageLoading=true;financePageError='';
  try{
    const options={credentials:'include',headers:{Accept:'application/json'}};
    const [walletResponse,txResponse,withdrawalResponse]=await Promise.all([
      fetch(API_BASE_URL+'/api/financeiro/carteira',options),
      fetch(API_BASE_URL+'/api/financeiro/transacoes?page=0&size=20',options),
      fetch(API_BASE_URL+'/api/financeiro/saques',options)
    ]);
    if(!walletResponse.ok||!txResponse.ok||!withdrawalResponse.ok)throw new Error('Não foi possível consultar a carteira no servidor.');
    const data={wallet:await walletResponse.json(),transactions:await txResponse.json(),withdrawals:await withdrawalResponse.json()};
    if(me()?.id===requestedUserId)financePageData=data;
  }catch(error){financePageError=error.message||'Falha ao consultar os dados financeiros.';}
  finally{financePageLoading=false;if(route.page==='app'&&route.sub==='carteira')render();}
}
function dashCarteira(){
  if(DEMO_MODE)return dashHead('Carteira demonstrativa','Os valores abaixo são fictícios e não representam dinheiro real.')+
    '<div class="card"><h2 class="sub">Dados de demonstração</h2><p class="muted" style="margin-top:10px">Não é possível adicionar saldo, receber pagamentos ou sacar neste ambiente.</p></div>';
  if(financePageUserId!==me()?.id){financePageData=null;financePageError='';financePageUserId=me()?.id||null;}
  if(!financePageData&&!financePageLoading)carregarCarteiraFinanceira();
  const body=financePageLoading?'<div class="card" role="status">Consultando carteira no servidor…</div>':
    financePageError?'<div class="card"><h2 class="sub">Carteira indisponível</h2><p class="muted" style="margin-top:10px">'+esc(financePageError)+'</p><button class="btn btn-ghost" onclick="financePageData=null;financePageError=\'\';carregarCarteiraFinanceira()">Tentar novamente</button></div>':
    !financePageData?'<div class="card">Entre novamente com uma conta persistida para consultar a carteira.</div>':
    '<div class="grid g3" style="margin-bottom:18px">'+[['Disponível',financePageData.wallet.disponivel],['Pendente',financePageData.wallet.pendente],['Bloqueado',financePageData.wallet.bloqueado]].map(item=>'<div class="card"><span class="muted">'+item[0]+'</span><h2 class="sub" style="margin-top:8px">'+money(item[1])+'</h2></div>').join('')+'</div>'+ 
        '<div class="card"><h2 class="sub">Extrato</h2>'+(financePageData.transactions.content?.length?financePageData.transactions.content.map(tx=>'<div style="display:flex;justify-content:space-between;gap:12px;padding:12px 0;border-bottom:1px solid var(--line)"><span>'+esc(TX_ROTULO[tx.tipo]||tx.tipo)+' · '+esc(statusTx(tx.status))+'</span><b>'+money(tx.valor)+'</b></div>').join(''):'<p class="muted" style="margin-top:10px">Nenhuma movimentação registrada no servidor.</p>')+'</div>'+ 
    (financePageData.withdrawals.length?'<div class="card" style="margin-top:18px"><h2 class="sub">Solicitações de saque</h2>'+financePageData.withdrawals.map(item=>'<p class="muted" style="margin-top:8px">'+money(item.valor)+' · '+esc(statusTx(item.status))+' · '+new Date(item.criadaEm).toLocaleString('pt-BR')+'</p>').join('')+'</div>':'');
  return dashHead('Carteira','Saldo e extrato.')+body;
}

function dashIndicacoes(){
  return dashHead('Indique a TaskGO','Conhece alguém que trabalha bem ou precisa de um serviço? Mande o link.')+
    '<div class="card"><p class="t2">taskgohq.com.br</p><button class="btn btn-ghost btn-sm" style="margin-top:14px" onclick="copiarLinkTaskgo()">'+ic('copy')+'Copiar link</button></div>';
}

async function concluirServico(id){
  const pedido=S.orders.find(o=>o.id===id),usuario=me();
  if(!pedido||!usuario)return toast('Pedido não encontrado.','err');
  const profissional=souAtendente(pedido);
  const cliente=usuario.role==='cliente'&&(pedido.clientId===usuario.id||String(pedido.clientEmail||'').toLowerCase()===String(usuario.email||'').toLowerCase());
  if(!profissional&&!cliente)return toast('Este pedido não pertence à sua conta.','err');
  if(pedido.status!=='em_andamento')return toast('O serviço não está em andamento.','err');
  try{await guardarPedidoAlterado(pedido,{status:'concluido'});toast(profissional&&pedido.paid==='retido'?'Serviço concluído. O pagamento é liberado quando o cliente confirmar, ou em 12 horas.':'Serviço concluído.','ok');abrirAvaliacao(id);}
  catch(error){toast(error.message||'O servidor não confirmou a conclusão do serviço.','err');}
}
function copiarLinkTaskgo(){navigator.clipboard.writeText('https://taskgohq.com.br').then(()=>toast('Link copiado','ok')).catch(()=>toast('Copie o endereço: taskgohq.com.br','ok'));}
