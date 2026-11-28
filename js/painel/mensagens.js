/* ---------- MENSAGENS ---------- */
let chatAtivo='';
function chatPeer(ch){
  const o=S.orders.find(x=>x.id===ch.orderId);
  return o?counterpart(o):{name:'TaskGO',rating:5};
}
const CONVERSA_ABERTA=['a_caminho','chegou','em_andamento','agendado'];
/* Uma conversa por pessoa: os pedidos com o mesmo profissional (ou cliente) viram uma conversa só.
   As mensagens continuam ligadas ao pedido em que foram escritas; a nova vai para o pedido mais recente em andamento. */
function chaveConversa(o){
  const outro=ladoAtende(o)?(o.clientEmail||o.clientId):(o.proEmail||o.proId);
  return 'p:'+String(outro||o.id).toLowerCase();
}
function myChats(){
  const porPedido=new Map(S.chats.map(c=>[String(c.orderId),c]));
  const grupos=new Map();
  myOrders().filter(o=>!['aguardando','aguardando_pagamento'].includes(o.status)&&(o.status!=='cancelado'||(porPedido.get(String(o.id))||{msgs:[]}).msgs.length))
    .forEach(o=>{const k=chaveConversa(o);if(!grupos.has(k))grupos.set(k,[]);grupos.get(k).push(o);});
  return [...grupos.entries()].map(([k,os])=>{
    os.sort((a,b)=>(b.created||0)-(a.created||0));
    const ativo=os.find(o=>CONVERSA_ABERTA.includes(o.status))||os[0];
    const msgs=os.flatMap(o=>((porPedido.get(String(o.id))||{}).msgs||[]).map(m=>Object.assign({orderId:String(o.id)},m))).sort((a,b)=>(a.ts||0)-(b.ts||0));
    return {id:k,orderId:String(ativo.id),pedidos:os.map(o=>String(o.id)),msgs};
  }).sort((a,b)=>((b.msgs[b.msgs.length-1]||{}).ts||0)-((a.msgs[a.msgs.length-1]||{}).ts||0));
}
/* Guarda a mensagem enviada no pedido em que ela foi escrita. */
function guardarMsgChat(orderId,msg){
  let c=S.chats.find(x=>String(x.orderId)===String(orderId));
  if(!c){c={id:String(orderId),orderId:String(orderId),msgs:[]};S.chats.push(c);}
  c.msgs.push(msg);Store.save();
}
function dashMensagens(){
  const isPro=me().role==='profissional',list=myChats();
  const c=list.find(x=>x.id===chatAtivo||x.pedidos.includes(String(chatAtivo)))||list[0];
  if(!c)return dashHead('Mensagens','')+'<div class="card emptystate">'+ic('chat')+'Nenhuma conversa ainda</div>';
  chatAtivo=c.id;
  const p=chatPeer(c);
  return dashHead('Mensagens',isPro?'Converse com os clientes dos seus atendimentos.':'Converse com os profissionais dos seus pedidos.')+
  '<div class="grid g2 mensagens-grade">'+
  '<div class="card mensagens-lista" style="padding-top:0">'+list.map(ch=>{
    const pp=chatPeer(ch),last=ch.msgs[ch.msgs.length-1]||{t:'Conversa iniciada após a confirmação do pedido.'},oo=S.orders.find(x=>x.id===ch.orderId);
    const servicos=[...new Set(ch.pedidos.map(id=>{const x=S.orders.find(y=>y.id===id);return x?svc(x.service).n.split(' ')[0]:'';}).filter(Boolean))];
    return '<div class="rowitem" role="button" tabindex="0" data-fk="chat-'+ch.id+'" aria-current="'+(ch.id===chatAtivo)+'" onkeydown="if(event.key===\'Enter\'||event.key===\' \'){event.preventDefault();this.click()}" style="cursor:pointer;'+(ch.id===chatAtivo?'background:var(--surface-2);padding-inline:10px':'')+'" onclick="chatAtivo=\''+ch.id+'\';render()">'+avatar(pp,40)+
    '<div class="grow"><b>'+esc(pp.name)+'</b><small style="display:block">'+esc(last.t||(last.foto?'Foto':''))+'</small></div>'+
    (oo?'<span class="tag tag-muted">'+esc(servicos.join(', '))+(ch.pedidos.length>1?' · '+ch.pedidos.length+' pedidos':'')+'</span>':'')+'</div>';
  }).join('')+'</div>'+
  '<div class="card"><div class="chat-cab" style="display:flex;align-items:center;gap:12px;padding-bottom:16px;border-bottom:1px solid var(--line);margin-bottom:16px">'+
  avatar(p,44)+'<div style="flex:1"><b style="display:block">'+esc(p.name)+'</b><span class="muted" style="font-size:.8rem">'+(c.pedidos.length>1?c.pedidos.length+' pedidos com esta pessoa':'Conversa do pedido')+'</span></div>'+ 
  '<a class="btn btn-ghost btn-sm" href="#/app/pedido/'+c.orderId+'">Ver pedido</a>'+botaoDenunciar(c.orderId,p.name)+'</div>'+
  '<div class="chat" id="chatBox" tabindex="0" role="log" aria-label="Mensagens da conversa">'+(c.msgs.length?c.msgs.map((m,i)=>(c.pedidos.length>1&&(i===0||c.msgs[i-1].orderId!==m.orderId)?divisorPedido(m.orderId):'')+'<div class="msg '+(m.me?'me':'them')+'">'+(m.automatic?'<small>Resposta automática</small>':'')+(m.foto?miniatura(urlFotoPedido(m.foto),'Foto enviada na conversa'):'')+esc(m.t)+'<small>'+new Date(m.ts).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})+(m.me&&m.readAt?' · Lida':'')+'</small></div>').join(''):'<p class="hint center">Ainda não há mensagens nesta conversa.</p>')+'</div>'+ 
  rodapeConversa(S.orders.find(x=>x.id===c.orderId))+'</div></div>';
}
function divisorPedido(id){const o=S.orders.find(x=>x.id===id);return o?'<p class="chat-divisor"><span>'+esc(svc(o.service).n)+' · '+dt(o.created)+'</span></p>':'';}
let avisoConversa='';
function rodapeConversa(o){
  if(!o||!CONVERSA_ABERTA.includes(o.status))return '<p class="conversa-fechada">'+ic('lock')+' Conversa encerrada. Ela fica aqui só para consulta.</p>';
  const s=situacaoConta();
  if(s.banido||s.suspenso)return '<p class="conversa-fechada">'+ic('lock')+' '+(s.banido?'Conta banida':'Conta suspensa até '+dataHora(s.suspensoAte))+': você não pode enviar mensagens. <a href="#/app/conta-situacao">Ver situação e recorrer</a></p>';
  return (avisoConversa?'<div class="aviso-conversa" role="alert">'+ic('flag')+'<span>'+esc(avisoConversa)+' <a href="#/app/conta-situacao">Recorrer</a></span></div>':'')+
  '<form style="display:flex;gap:10px;margin-top:16px" onsubmit="enviarMsg(event)">'+
    '<label class="btn btn-ghost chat-anexo" aria-label="Enviar foto">'+ic('camera')+'<input type="file" accept="image/*" class="leitor" onchange="enviarFotoConversa(this)"></label>'+
    '<input class="input" id="msgIn" aria-label="Mensagem" aria-describedby="regraConversa" placeholder="Escreva uma mensagem..." autocomplete="off" maxlength="2000">'+
  '<button class="btn btn-primary" aria-label="Enviar mensagem">'+ic('arrow')+'</button></form>'+
  '<p class="hint" id="regraConversa" style="margin-top:8px">Não envie números, valores nem contatos. Mensagens assim são barradas e geram aviso na conta.</p>';
}
async function enviarMsg(e){
  e.preventDefault();
  const input=$('#msgIn'),text=(input||{}).value.trim();if(!text)return;
  const chat=myChats().find(item=>String(item.id)===String(chatAtivo));if(!chat)return;
  const requestId=input.dataset.requestId||(input.dataset.requestId=crypto.randomUUID());
  if(input)input.disabled=true;
  try{
    let sent;
    if(DEMO_MODE){const motivo=motivoBloqueioLocal(text);if(motivo)throw infracaoDemo(motivo);sent={id:'m_'+uid(),me:true,t:text,ts:Date.now()};}
    else sent=await enviarMensagemPedido(chat.orderId,text,requestId);
    avisoConversa='';
    guardarMsgChat(chat.orderId,sent);
    if(input)delete input.dataset.requestId;render();
  }catch(error){
    if(input)input.disabled=false;
    /* Barrada pela moderação: o aviso fica na conversa, não some num toast. */
    if(error.status===422||error.status===403){avisoConversa=error.message;if(input)delete input.dataset.requestId;if(!DEMO_MODE)await carregarSituacaoConta();render();return;}
    toast(erroLegivel(error,'Não foi possível enviar a mensagem.'),'err');
  }
}

