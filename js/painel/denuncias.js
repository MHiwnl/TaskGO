/* ---------- DENÚNCIAS ----------
   Quem esteve no mesmo pedido pode denunciar a outra pessoa. O administrador decide na Moderação. */
const MOTIVOS_DENUNCIA=[['contato_fora','Pediu contato ou pagamento por fora'],['comportamento','Comportamento ofensivo ou agressivo'],['nao_compareceu','Não apareceu ou sumiu'],
  ['servico','Serviço malfeito ou diferente do combinado'],['golpe','Tentativa de golpe'],['outro','Outro motivo']];
/* Pedidos que esta conta já denunciou: o botão vira "Denúncia enviada" (o servidor também recusa a segunda). */
let JA_DENUNCIADOS=null;
function carregarJaDenunciados(){
  if(JA_DENUNCIADOS||DEMO_MODE||!me())return;
  JA_DENUNCIADOS=new Set();
  apiJSON('/api/denuncias/minhas').then(l=>{(l||[]).forEach(d=>d.pedidoId&&JA_DENUNCIADOS.add(d.pedidoId));if(route.page==='app')render();}).catch(()=>{JA_DENUNCIADOS=null;});
}
function botaoDenunciar(pedidoId,nome){
  carregarJaDenunciados();
  if(JA_DENUNCIADOS&&JA_DENUNCIADOS.has(pedidoId))return '<span class="denuncia-enviada" role="status">'+ic('checkCircle')+' Denúncia enviada · em análise</span>';
  return '<button type="button" class="btn btn-ghost btn-sm btn-denunciar" onclick="abrirDenuncia(\''+esc(pedidoId)+'\',\''+esc(String(nome||'').replace(/'/g,''))+'\')">'+ic('flag')+' Denunciar</button>';
}
function abrirDenuncia(pedidoId,nome){
  openModal('<div class="modal-head"><div><h3>Denunciar '+esc(nome||'esta pessoa')+'</h3><p>A denúncia vai para a equipe da TaskGO. A outra pessoa não fica sabendo quem denunciou.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<form onsubmit="enviarDenuncia(event,\''+esc(pedidoId)+'\')">'+
    '<fieldset class="field motivos-denuncia"><legend>O que aconteceu?</legend>'+MOTIVOS_DENUNCIA.map((m,i)=>'<label class="motivo"><input type="radio" name="motivo" value="'+m[0]+'"'+(i===0?' required':'')+'><span>'+m[1]+'</span></label>').join('')+'</fieldset>'+
    '<div class="field"><label for="texto-denuncia">Conte com suas palavras (opcional, obrigatório em "Outro motivo")</label><textarea class="textarea" id="texto-denuncia" name="texto" maxlength="2000" placeholder="O que foi dito ou feito, e quando."></textarea></div>'+
    '<p class="hint" style="margin-bottom:16px">Em perigo? Use o botão de emergência do pedido ou ligue 190.</p>'+
    '<div style="display:flex;gap:10px"><button type="button" class="btn btn-ghost btn-block" onclick="closeModal()">Voltar</button><button class="btn btn-danger btn-block" id="btnDenunciar">Enviar denúncia</button></div></form>');
}
async function enviarDenuncia(e,pedidoId){
  e.preventDefault();const f=new FormData(e.target),botao=$('#btnDenunciar');
  const motivo=f.get('motivo'),texto=String(f.get('texto')||'').trim();
  if(!motivo)return toast('Escolha o que aconteceu.','err');
  if(motivo==='outro'&&texto.length<10)return toast('Conte o que aconteceu (pelo menos 10 caracteres).','err');
  if(botao.disabled)return;
  botao.disabled=true;botao.textContent='Enviando...';
  try{
    if(!DEMO_MODE)await apiJSON('/api/denuncias','POST',{pedidoId,motivo,texto},'A denúncia não foi enviada.');
    (JA_DENUNCIADOS=JA_DENUNCIADOS||new Set()).add(pedidoId);
    closeModal();toast('Denúncia enviada. A equipe analisa e a resposta chega nas notificações.','ok');render();
  }catch(error){
    /* Já denunciado (outro clique ou outra aba): trava o botão do mesmo jeito. */
    if(error&&error.status===409){(JA_DENUNCIADOS=JA_DENUNCIADOS||new Set()).add(pedidoId);closeModal();toast(erroLegivel(error,'Você já denunciou este pedido.'),'ok');render();return;}
    toast(erroLegivel(error,'A denúncia não foi enviada.'),'err');botao.disabled=false;botao.textContent='Enviar denúncia';
  }
}

/* ---------- Administrador: denúncias na Moderação ---------- */
let adminDenuncias=null;
async function carregarDenunciasAdmin(){
  try{adminDenuncias=await apiJSON('/api/admin/denuncias','GET',undefined,'Não foi possível carregar as denúncias.');}
  catch(error){adminDenuncias=[];toast(erroLegivel(error,'Não foi possível carregar as denúncias.'),'err');}
  if(route.sub==='admin'&&route.id==='moderacao')render();
}
const situacaoCurta=p=>!p||p.avisos==null?'':' · '+p.avisos+' aviso(s)'+(p.banido?' · banida':p.suspenso?' · suspensa':'');
function secaoDenunciasAdmin(){
  if(adminDenuncias==null){carregarDenunciasAdmin();return '<section class="card" style="margin-top:16px"><h2 class="sub" style="font-size:1.05rem">Denúncias</h2><p class="muted">Carregando...</p></section>';}
  const abertas=adminDenuncias.filter(d=>d.status==='em_analise'),feitas=adminDenuncias.filter(d=>d.status!=='em_analise');
  return '<section class="card" style="margin-top:16px"><h2 class="sub" style="font-size:1.05rem;margin-bottom:12px">Denúncias em análise ('+abertas.length+')</h2>'+
    (abertas.length?abertas.map(d=>'<article class="adm-recurso"><div class="adm-quem"><b>'+esc(d.denunciado.nome||'Conta')+'</b><small>'+esc(d.denunciado.email||'')+situacaoCurta(d.denunciado)+' · '+d.denunciasContraEle+' denúncia(s) no total</small></div>'+
      '<p><span class="tag tag-warn">'+esc(d.motivoTexto)+'</span>'+(d.texto?' '+esc(d.texto):'')+'</p>'+
      '<small class="muted">Feita por '+esc(d.denunciante.nome||'')+' · '+dataHora(d.criadaEm)+(d.pedidoId?' · <button type="button" class="log-quem" onclick="verPedidoAdmin(\''+esc(d.pedidoId)+'\')">ver pedido</button> · <button type="button" class="log-quem" onclick="verConversaPedido(\''+esc(d.pedidoId)+'\')">ver conversa</button>':'')+'</small>'+
      '<div class="field" style="margin:12px 0 10px"><label for="dresp-'+esc(d.id)+'">Resposta para quem denunciou (opcional)</label><input class="input" id="dresp-'+esc(d.id)+'" maxlength="500"></div>'+
      '<div style="display:flex;gap:10px;flex-wrap:wrap"><button class="btn btn-ghost btn-sm" onclick="decidirDenuncia(\''+esc(d.id)+'\',\'improcedente\',false)">Improcedente</button>'+
      '<button class="btn btn-ghost btn-sm" onclick="decidirDenuncia(\''+esc(d.id)+'\',\'procedente\',false)">Procedente, sem aviso</button>'+
      '<button class="btn btn-danger btn-sm" onclick="decidirDenuncia(\''+esc(d.id)+'\',\'procedente\',true)">Procedente e dar aviso</button></div></article>').join(''):'<p class="muted">Nenhuma denúncia esperando decisão.</p>')+
    (feitas.length?'<h3 class="recursos-ttl">Decididas</h3><ul class="lista-recursos">'+feitas.slice(0,20).map(d=>'<li><span class="tag '+(d.status==='procedente'?'tag-danger':'tag-muted')+'">'+(d.status==='procedente'?'Procedente':'Improcedente')+'</span><p><b>'+esc(d.denunciado.nome||'')+'</b>: '+esc(d.motivoTexto)+'</p><small>'+dataHora(d.decididaEm||d.criadaEm)+'</small></li>').join('')+'</ul>':'')+
  '</section>';
}
async function decidirDenuncia(id,decisao,aviso){
  const resposta=(($('#dresp-'+id)||{}).value||'').trim();
  try{await apiJSON('/api/admin/denuncias/'+encodeURIComponent(id)+'/'+decisao,'POST',{resposta,aviso},'A decisão não foi salva.');
    toast(decisao==='procedente'?(aviso?'Denúncia procedente; a conta recebeu um aviso.':'Denúncia procedente.'):'Denúncia improcedente.','ok');
    adminDenuncias=null;PAINEL.hoje=null;render();}
  catch(error){toast(erroLegivel(error,'A decisão não foi salva.'),'err');}
}
