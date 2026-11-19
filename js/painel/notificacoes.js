/* ---------- NOTIFICACOES ----------
   Três categorias:
   Pedidos: trabalhos novos, propostas, aceites, chegada, conclusão e mensagens dos pedidos.
   Conta: assuntos do perfil (avisos e restrições da moderação, lembrete de foto, denúncias).
   Sistema: avisos gerais escritos pelo administrador para todas as contas. */
const CATEGORIAS_NOTIF=[['todas','Todas','bell'],['pedidos','Pedidos','list'],['conta','Conta','user'],['sistema','Sistema','megafone']];
let categoriaNotif='todas';
/* Para onde leva cada notificação. Sem destino claro: null (abre o aviso num cartão). */
function rotaDaNotif(n){
  const u=me()||{},t=(String(n.t||'')+' '+String(n.d||'')).toLowerCase(),k=String(n.kind||'');
  const equipe=typeof ehSuporte==='function'&&(ehSuporte(u)||(typeof ehAdministrador==='function'&&ehAdministrador(u)));
  if(equipe)return /emergência|emergencia/.test(t)?'/app/suporte':'/app/admin/filas';
  if(n.pedidoId){
    if(k==='mensagem')return '/app/mensagens';
    const o=(S.orders||[]).find(x=>x.id===n.pedidoId);
    if(o&&o.obra)return '/app/obra/'+encodeURIComponent(n.pedidoId);
    if(!o&&u.role==='profissional')return /obra|orçamento/.test(t)?'/app/obra/'+encodeURIComponent(n.pedidoId):'/app/oportunidades';
    return '/app/pedido/'+encodeURIComponent(n.pedidoId);
  }
  if(['moderacao','denuncia'].includes(k)||/advertência|suspens|banid|recurso|denúncia/.test(t))return '/app/conta-situacao';
  if(/foto de perfil|verifica|identidade|cpf|configura/.test(t))return '/painel';
  if(/carteira|taxa|balanço|saque|resgate|saldo|pix/.test(t))return '/app/carteira';
  return null;
}
function categoriaDaNotif(n){
  const k=String(n.kind||'');
  if(k==='sistema')return 'sistema';
  if(['conta','account','moderacao','denuncia','seguranca'].includes(k))return 'conta';
  return 'pedidos';
}
function iconeDaNotif(n){
  const k=String(n.kind||'');
  if(k==='sistema')return 'megafone';
  if(k==='mensagem'||k==='message')return 'chat';
  if(k==='proposta')return 'money';
  if(k==='moderacao'||k==='denuncia')return 'shield';
  if(k==='conta'||k==='account')return /foto/i.test(n.t||'')?'camera':'user';
  if(k==='review')return 'star';
  if(/chamado agora/i.test(n.t||''))return 'bolt';
  return 'list';
}
function dashNotificacoes(){
  if(!DEMO_MODE&&window.__taskgoNotificationsError)return dashHead('Notificações','')+'<div class="card center"><p class="muted">Não foi possível carregar o histórico do servidor.</p><button class="btn btn-ghost" onclick="window.__taskgoNotificationsLoaded=false;window.__taskgoNotificationsError=\'\';render()">Tentar novamente</button></div>';
  const todas=myNotifs(),unread=todas.filter(n=>!n.read).length;
  const conta=c=>todas.filter(n=>!n.read&&(c==='todas'||categoriaDaNotif(n)===c)).length;
  const list=todas.filter(n=>categoriaNotif==='todas'||categoriaDaNotif(n)===categoriaNotif);
  const actions=unread?'<button class="btn btn-ghost" onclick="markAllNotifsRead()">'+ic('check')+' Marcar todas como lidas ('+unread+')</button>':'';
  const abas='<div class="notif-abas" role="tablist" aria-label="Categorias de notificação">'+CATEGORIAS_NOTIF.map(c=>{const n=conta(c[0]);
    return '<button type="button" role="tab" aria-selected="'+(categoriaNotif===c[0])+'" class="notif-aba'+(categoriaNotif===c[0]?' ativa':'')+'" onclick="categoriaNotif=\''+c[0]+'\';render()">'+ic(c[2])+'<span>'+c[1]+'</span>'+(n?'<span class="aba-n">'+fmtContador(n)+'</span>':'')+'</button>';}).join('')+'</div>';
  const rows=list.map((n,i)=>{
    const key=String(n.id).replace(/[^a-zA-Z0-9_-]/g,''),cat=categoriaDaNotif(n);
    return '<div class="rowitem notif-linha notif-'+cat+(!n.read?' rowitem-nova':'')+'" style="--i:'+Math.min(i,8)+'" role="button" tabindex="0" onclick="openNotif(\''+key+'\')" onkeydown="if(event.key===\'Enter\'||event.key===\' \'){event.preventDefault();openNotif(\''+key+'\')}">'+
      '<div class="ico-box">'+ic(iconeDaNotif(n))+'</div>'+
      '<div class="grow"><span class="notif-cat">'+({pedidos:'Pedidos',conta:'Conta',sistema:'Sistema'})[cat]+'</span><b>'+esc(n.t||'Notificação')+'</b><small>'+esc(n.d||'')+'</small>'+'<small class="notif-abrir">'+(rotaDaNotif(n)?'Abrir':'Ver aviso')+' '+ic('arrow')+'</small>'+'</div>'+
      '<span class="muted notif-quando">'+dt(n.ts)+'</span></div>';
  }).join('');
  const vazio={todas:'Nenhuma notificação por enquanto',pedidos:'Nada sobre pedidos por enquanto',conta:'Nada sobre a sua conta',sistema:'Nenhum aviso do sistema'}[categoriaNotif];
  const subtitle=unread?unread+(unread===1?' não lida.':' não lidas.'):'Tudo lido.';
  return dashHead('Notificações',subtitle+' Pedidos, conta e avisos do sistema.',actions)+abas+
    (list.length?'<div class="notif-lista">'+rows+'</div>':'<div class="card emptystate">'+ic('bell')+'<b>'+vazio+'</b><p class="muted" style="margin-top:8px">'+(categoriaNotif==='sistema'?'Quando a TaskGO mandar um aviso para todos, ele aparece aqui.':'Novos trabalhos, propostas e avisos sobre seus pedidos aparecem aqui.')+'</p></div>');
}
