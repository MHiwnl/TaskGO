/* =========================================================
   SERVIÇO RECORRENTE
   O mesmo serviço várias vezes (2 a 30), em dias da semana fixos, num horário.
   Só para serviços cobrados por dia ou visita. O cliente paga por semana;
   cada vez vai para o profissional quando o cliente confirma (ou em 24 h).
   ========================================================= */
const REC_MAX=30;
const REC={id:null,dados:null,erro:'',carregando:false};
const ST_VEZ={pendente:['A pagar','tag-muted'],paga:['Paga, com a TaskGO','tag-info'],feita:['Feita, esperando confirmação','tag-warn'],liberada:['Paga ao profissional','tag-ok'],a_pagar:['Confirmada, a pagar','tag-warn'],
  contestada:['Com o suporte','tag-danger'],faltou:['Não veio · devolvida','tag-muted'],cancelada:['Cancelada','tag-muted'],devolvida:['Devolvida','tag-muted']};
const diaRec=d=>new Date(d+'T12:00').toLocaleDateString('pt-BR',{weekday:'short',day:'2-digit',month:'2-digit'});
const nomesDias=ds=>(ds||[]).map(n=>(DIAS_SEMANA.find(d=>d[0]===n)||[0,''])[1]).join(', ');

/* Mesmas datas que o servidor gera: a partir do início, só nos dias escolhidos, até completar as vezes. */
function datasRecorrentes(inicio,dias,vezes){
  const r=[];if(!inicio||!dias||!dias.length)return r;
  const d=new Date(inicio+'T12:00');
  for(let guarda=0;r.length<vezes&&guarda<2000;guarda++,d.setDate(d.getDate()+1)){const w=d.getDay()||7;if(dias.includes(w))r.push(d.toISOString().slice(0,10));}
  return r;
}
function semanaRec(primeira,d){
  const a=new Date(primeira+'T12:00'),seg=new Date(a);seg.setDate(a.getDate()-((a.getDay()||7)-1));
  return Math.floor((new Date(d+'T12:00')-seg)/(7*864e5))+1;
}

/* ---------- Contratar: bloco opcional no valor ---------- */
function recPadrao(){const amanha=new Date(Date.now()+864e5).toISOString().slice(0,10);return {vezes:4,dias:[1,3,5],inicio:amanha,hora:'08:00'};}
function blocoRecorrenteContratar(){
  if(W.modo!=='agendar'||!servicoRecorrente(W.service))return '';
  const r=W.rec,ligado=!!r,unidade=(svc(W.service).unit||'vez');
  let corpo='';
  if(ligado){
    const datas=datasRecorrentes(r.inicio,r.dias,r.vezes),v=Number(W.oferta)||0,semanas=datas.length?semanaRec(datas[0],datas[datas.length-1]):0;
    const min=new Date(Date.now()+864e5).toISOString().slice(0,10),max=new Date(Date.now()+60*864e5).toISOString().slice(0,10);
    corpo='<div class="rec-campos">'+
      '<div class="field rec-vezes"><label for="wRecVezes">Quantas vezes</label><div class="rec-passo">'+
        '<button type="button" class="icon-btn" aria-label="Menos uma vez" onclick="recSet(\'vezes\','+(r.vezes-1)+')"'+(r.vezes<=2?' disabled':'')+'>−</button>'+
        '<input class="input" id="wRecVezes" type="number" inputmode="numeric" min="2" max="'+REC_MAX+'" value="'+r.vezes+'" onchange="recSet(\'vezes\',this.value)">'+
        '<button type="button" class="icon-btn" aria-label="Mais uma vez" onclick="recSet(\'vezes\','+(r.vezes+1)+')"'+(r.vezes>=REC_MAX?' disabled':'')+'>+</button></div>'+
        '<div class="hint">De 2 a '+REC_MAX+'.</div></div>'+
      '<fieldset class="field obra-dias rec-dias"><legend class="rotulo-campo">Dias da semana</legend>'+
        DIAS_SEMANA.map(d=>'<label class="chip-check"><input type="checkbox" value="'+d[0]+'" '+(r.dias.includes(d[0])?'checked':'')+' onchange="recDia('+d[0]+',this.checked)"><span>'+d[1]+'</span></label>').join('')+
        '<button type="button" class="btn btn-ghost btn-sm" onclick="recSet(\'dias\',[1,2,3,4,5])">Seg a sex</button></fieldset>'+
      '<div class="grid g2" style="gap:0 16px">'+
        '<div class="field"><label for="wRecInicio">Começa em</label><input class="input" id="wRecInicio" type="date" min="'+min+'" max="'+max+'" value="'+esc(r.inicio)+'" onchange="recSet(\'inicio\',this.value)"></div>'+
        '<div class="field"><label for="wRecHora">Horário</label><input class="input" id="wRecHora" type="time" min="06:00" max="21:00" step="900" value="'+esc(r.hora)+'" onchange="recSet(\'hora\',this.value)"></div></div>'+
      (datas.length?'<ol class="rec-datas" aria-label="Datas">'+datas.map(d=>'<li>'+esc(diaRec(d))+'</li>').join('')+'</ol>':'<p class="pag-recusa">Escolha pelo menos um dia da semana.</p>')+
      '<div class="orc-soma rec-soma"><span>'+(v?money(v)+' por '+esc(unidade)+' × '+r.vezes:'Informe o valor de cada vez')+'</span>'+(v?'<b>'+money(v*r.vezes)+'</b>':'')+
        (v&&semanas?'<small>'+(modeloDireto()?'Cada vez é paga direto ao profissional, por Pix, depois de acontecer. Nada é cobrado agora.':'Pago por semana, em '+semanas+(semanas===1?' semana':' semanas')+'. Nada é cobrado agora.')+'</small>':'')+'</div>'+
    '</div>';
  }
  return '<section class="rec-bloco'+(ligado?' ligado':'')+'" aria-labelledby="recTitulo">'+
    '<label class="rec-liga"><input type="checkbox" '+(ligado?'checked':'')+' onchange="recLigar(this.checked)">'+
    '<span><b id="recTitulo">Serviço recorrente</b><small>O mesmo serviço em vários dias, no mesmo horário</small></span></label>'+corpo+'</section>';
}
function recLigar(on){if(on)W.rec=W.recAntes||recPadrao();else{W.recAntes=W.rec;W.rec=null;}render();}
function recSet(k,v){
  if(!W.rec)return;
  if(k==='vezes'){v=Math.round(Number(v));if(!(v>=2))v=2;if(v>REC_MAX)v=REC_MAX;}
  W.rec[k]=v;W.recAntes=W.rec;render();
  const foco=document.getElementById(k==='vezes'?'wRecVezes':k==='inicio'?'wRecInicio':k==='hora'?'wRecHora':'');if(foco&&k!=='vezes')foco.focus();
}
function recDia(n,on){if(!W.rec)return;W.rec.dias=on?[...new Set([...W.rec.dias,n])].sort():W.rec.dias.filter(x=>x!==n);W.recAntes=W.rec;render();}
function recValido(){
  if(!W.rec)return true;
  const r=W.rec;
  if(!(r.vezes>=2&&r.vezes<=REC_MAX)){toast('Informe de 2 a '+REC_MAX+' vezes.','err');return false;}
  if(!r.dias.length){toast('Escolha pelo menos um dia da semana.','err');return false;}
  if(!r.inicio||r.inicio<=new Date().toISOString().slice(0,10)){toast('O serviço recorrente começa a partir de amanhã.','err');return false;}
  if(!/^\d{2}:\d{2}$/.test(r.hora||'')||r.hora<'06:00'||r.hora>'21:00'){toast('Escolha um horário entre 06:00 e 21:00.','err');return false;}
  return true;
}
function resumoRec(r){return r.vezes+' vezes · '+nomesDias(r.dias)+' às '+r.hora+' · a partir de '+new Date(r.inicio+'T12:00').toLocaleDateString('pt-BR');}

/* ---------- Pedido: semanas e vezes ---------- */
async function carregarRecorrente(id,forcar){
  if(REC.carregando||(!forcar&&REC.id===id&&REC.dados))return;
  REC.carregando=true;
  try{REC.dados=await apiJSON('/api/recorrentes/'+encodeURIComponent(id));REC.id=id;REC.erro='';}
  catch(e){REC.id=id;REC.dados=null;REC.erro=erroLegivel(e,'Não foi possível abrir as vezes deste pedido.');}
  finally{REC.carregando=false;}
  if(route.page==='app'&&route.sub==='pedido'&&route.id===id)render();
}
function recarregarRecorrente(){esquecerChaves();if(REC.id){const id=REC.id;atualizarPedidoLocal(id).finally(()=>carregarRecorrente(id,true));}}

function blocoRecorrente(o,isPro){
  if(DEMO_MODE)return '';
  /* Muda o status, quem atende ou a confirmação: busca as vezes de novo. */
  const chave=[o.status,o.proEmail||'',o.confirmacaoCliente||''].join('|');
  if(REC.id!==o.id||REC.chave!==chave){REC.id=o.id;REC.chave=chave;REC.dados=null;REC.erro='';}
  if(!REC.dados&&!REC.erro){carregarRecorrente(o.id);return '<div class="card rec-painel" aria-busy="true"><p class="muted">Carregando as vezes…</p></div>';}
  if(REC.erro)return '<div class="card rec-painel"><p class="pag-recusa" role="alert">'+esc(REC.erro)+'</p><button class="btn btn-ghost btn-sm" onclick="carregarRecorrente(\''+esc(o.id)+'\',true)">Tentar de novo</button></div>';
  const d=REC.dados,pl=d.plano||{},total=(d.semanas||[]).reduce((n,s)=>n+s.vezes.length,0),agora=Date.now();
  const acoes=v=>{
    const id=esc(v.id),b=[];
    if(isPro&&v.status===(d.direto?'pendente':'paga')&&o.proEmail&&agora>=v.quando-36e5)b.push('<button class="btn btn-primary btn-sm" onclick="acaoVez(\''+id+'\',\'feita\')">'+ic('check')+'Marcar como feita</button>');
    if(!isPro&&v.status==='feita')b.push('<button class="btn btn-primary btn-sm" onclick="acaoVez(\''+id+'\',\'confirmar\')">'+ic('check')+'Confirmar</button>');
    if(isPro&&v.status==='a_pagar')b.push('<button class="btn btn-primary btn-sm" onclick="acaoVez(\''+id+'\',\'recebida\',\'Recebimento registrado.\')">'+ic('check')+'Recebi '+money(v.valor)+'</button>');
    if(!isPro&&(v.status==='feita'||v.status===(d.direto?'pendente':'paga')&&o.proEmail&&agora>=v.quando+36e5))b.push('<button class="btn btn-ghost btn-sm" onclick="naoVeio(\''+id+'\',\''+esc(diaRec(v.data))+'\','+(v.status==='feita')+')">Não veio</button>');
    return b.length?'<div class="rec-vez-acoes">'+b.join('')+'</div>':'';
  };
  const semanas=(d.semanas||[]).map(s=>{
    const st=s.vezes.every(v=>v.status!=='pendente')?'':s.podePagar?'agora':'depois';
    return '<li class="rec-semana"><div class="rec-semana-cab"><b>Semana '+s.numero+'</b>'+
      (!d.direto&&Number(s.aPagar)>0?(s.podePagar?'<button class="btn btn-primary btn-sm" onclick="pagarSemana('+s.numero+','+Number(s.aPagar)+')">'+ic('lock')+'Pagar '+money(s.aPagar)+'</button>':'<span class="muted">'+money(s.aPagar)+' a pagar</span>'):'')+'</div>'+
      '<ul class="rec-vezes">'+s.vezes.map(v=>{const x=ST_VEZ[v.status]||[v.status,'tag-muted'];
        return '<li><div class="rec-vez-linha"><span class="rec-vez-data">'+esc(diaRec(v.data))+' · '+new Date(v.quando).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})+'</span>'+
          '<span class="tag '+x[1]+'">'+x[0]+'</span><span class="rec-vez-valor">'+money(v.valor)+'</span></div>'+
          (v.status==='feita'&&v.liberarApos?'<small class="muted">'+(isPro?'Vai para a sua carteira':'Vai para o profissional')+' em '+new Date(v.liberarApos).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'})+' se não houver resposta.</small>':'')+
          acoes(v)+'</li>';}).join('')+'</ul></li>';
  }).join('');
  const aviso=d.direto?(isPro?(d.recebePorVez!=null?'<p class="hint">Em cada vez: marque como feita, o cliente confirma e paga '+money((d.semanas[0]||{vezes:[{}]}).vezes[0].valor)+' direto a você. A taxa da TaskGO fica na Carteira.</p>':''):
      '<p class="hint">Você paga cada vez direto ao profissional, por Pix, depois de confirmar. Não pague antes.</p>'+(Number(d.aPagarAgora)>0?blocoChavePix(o.id,'Pague'):'')):
    !isPro&&!d.confirmado&&o.status==='aguardando'?'<p class="hint">O pagamento da 1ª semana abre quando você confirmar quem aceitou.</p>':
    !isPro&&d.proximaSemana&&d.confirmado&&['agendado','em_andamento'].includes(o.status)?'<p class="hint">Pague cada semana antes da 1ª vez dela. Semana sem pagamento no dia é cancelada.</p>':
    isPro&&d.recebePorVez!=null?'<p class="hint">Você recebe '+money(d.recebePorVez)+' por vez, já sem a taxa, quando o cliente confirma.</p>':'';
  return '<section class="card rec-painel" aria-labelledby="recPainelTitulo">'+
    '<div class="inicio-cab"><h2 class="sub" id="recPainelTitulo">Serviço recorrente</h2><span class="tag tag-info">'+ic('refresh')+' '+total+' vezes</span></div>'+
    '<p class="rec-plano">'+esc(nomesDias(pl.dias))+' às '+esc(pl.hora||'')+' · '+esc(diaRec(pl.inicio||''))+' a '+esc(diaRec(pl.fim||pl.inicio||''))+'</p>'+
    '<div class="obra-resumo rec-resumo"><div><span>Total</span><b>'+money(d.total)+'</b></div><div><span>'+(d.direto?'Pago ao profissional':'Pago')+'</span><b>'+money(d.pago)+'</b></div><div><span>Feitas</span><b>'+d.feitas+' de '+total+'</b></div></div>'+
    aviso+'<ol class="rec-semanas">'+semanas+'</ol></section>';
}
async function acaoVez(id,acao,msg){
  try{REC.dados=await apiJSON('/api/recorrentes/vezes/'+encodeURIComponent(id)+'/'+acao,'POST',{},'Não foi possível salvar.');closeModal();toast(msg||(acao==='feita'?'Marcada como feita. O cliente confirma.':acao==='confirmar'?'Confirmado. O valor foi para o profissional.':'Registrado.'),'ok');recarregarRecorrente();}
  catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');}
}
function naoVeio(id,dia,feita){
  openModal('<div class="modal-head"><div><h3>O profissional não veio em '+dia+'?</h3><p>'+(feita?'Ele marcou esta vez como feita. O suporte da TaskGO vai analisar antes de liberar o valor.':'O valor desta vez volta para você e a falta fica registrada.')+'</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-danger" onclick="acaoVez(\''+esc(id)+'\',\'nao_veio\',\''+(feita?'Enviado ao suporte.':'O valor desta vez volta para você.')+'\')">Não veio</button></div>');
}
async function pagarSemana(n,valor){
  if(!PG.config||PG.cartoes===null||PG.saldo==null)await carregarPagamentos(true);
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido),saldo=Number(PG.saldo)||0;
  OB.pag={tipo:'semana',id:n,valor,metodo:saldo>=valor?'carteira':cs.length?'cartao:'+cs[0].id:'pix',parcelas:1,
    url:'/api/recorrentes/'+encodeURIComponent(REC.id)+'/semanas/'+n+'/pagar',depois:recarregarRecorrente};
  openModal(htmlPagarObra());
}
/* Ao abrir (ou voltar para) um pedido, as vezes são buscadas de novo. */
window.addEventListener('hashchange',()=>{if(location.hash.includes('/app/pedido/'))REC.chave=null;});

/* ---------- Suporte: vezes contestadas (em Problemas de pagamento) ---------- */
let recFila=null;
function filaRecorrentes(){
  if(!recFila){setTimeout(async()=>{try{recFila=await apiJSON('/api/admin/recorrentes');}catch(e){recFila={erro:erroLegivel(e,'Não foi possível carregar.')};}render();},0);return '';}
  if(recFila.erro)return '<p class="pag-recusa">'+esc(recFila.erro)+'</p>';
  if(!recFila.length)return '';
  return '<section class="card"><h2 class="sub admin-ttl">Serviço recorrente ('+recFila.length+')</h2><ul class="lista-painel fila-obras">'+recFila.map(v=>
    '<li><div class="grow"><b>'+esc(v.servico)+' · vez de '+esc(diaRec(v.data))+' · '+money(v.valor)+'</b>'+
    '<p>O profissional marcou como feita'+(v.feitaEm?' em '+new Date(v.feitaEm).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'}):'')+'; o cliente diz que ele não veio.</p>'+
    '<small class="muted">'+esc(v.cliente)+' · '+esc(v.profissional||'')+' · '+esc(v.pedidoId)+'</small>'+
    '<div class="obra-acoes"><button class="btn btn-ghost btn-sm" onclick="verPedidoAdmin(\''+esc(v.pedidoId)+'\')">Ver pedido</button>'+
    '<button class="btn btn-danger btn-sm" onclick="decidirVez(\''+esc(v.id)+'\',false)">Devolver ao cliente</button>'+
    '<button class="btn btn-primary btn-sm" onclick="decidirVez(\''+esc(v.id)+'\',true)">'+ic('check')+'Pagar o profissional</button></div></div></li>').join('')+'</ul></section>';
}
async function decidirVez(id,aoProfissional){
  try{await apiJSON('/api/admin/recorrentes/vezes/'+encodeURIComponent(id)+'/decidir','POST',{aoProfissional},'Não foi possível decidir.');toast('Decidido.','ok');recFila=null;render();}
  catch(e){toast(erroLegivel(e,'Não foi possível decidir.'),'err');}
}
