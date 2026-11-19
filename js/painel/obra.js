/* =========================================================
   OBRA (#/app/obra/<id>)
   Serviço de vários dias ou acima de R$ 1.000. Antes: orçamentos com etapas, comparados lado a lado,
   e visita técnica paga (abatida se contratar). Depois: etapas pagas uma a uma e liberadas na aprovação,
   diário da obra, aditivos, garantia de 8% (a partir de R$ 3.000) e cancelamento no meio.
   ========================================================= */
const OB={id:null,dados:null,pedido:null,erro:'',carregando:false,form:null,pag:null,pixTimer:null};
const DIAS_SEMANA=[[1,'Seg'],[2,'Ter'],[3,'Qua'],[4,'Qui'],[5,'Sex'],[6,'Sáb'],[7,'Dom']];
const ST_ETAPA={pendente:['A pagar','tag-muted'],paga:['Paga, com a TaskGO','tag-info'],andamento:['Em andamento','tag-info'],entregue:['Entregue, esperando aprovação','tag-warn'],
  aprovada:['Aprovada e repassada','tag-ok'],problema:['Problema em análise','tag-danger'],mediacao:['Com a equipe','tag-warn'],devolvida:['Devolvida','tag-muted'],cancelada:['Cancelada','tag-muted']};
const pct=v=>String(Math.round(Number(v)*1000)/10).replace('.',',')+'%';
const dataCurta=ms=>ms?new Date(Number(ms)).toLocaleDateString('pt-BR',{day:'2-digit',month:'2-digit',year:'numeric'}):'—';

async function carregarObra(id,forcar){
  if(OB.carregando||(!forcar&&OB.id===id&&OB.dados))return;
  OB.carregando=true;
  try{
    OB.dados=await apiJSON('/api/obras/'+encodeURIComponent(id));OB.id=id;OB.erro='';
    OB.pedido=S.orders.find(o=>o.id===id)||await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)).catch(()=>null);
  }catch(e){OB.id=id;OB.dados=null;OB.erro=erroLegivel(e,'Não foi possível abrir a obra.');}
  finally{OB.carregando=false;}
  if(route.page==='app'&&['obra','pedido'].includes(route.sub)&&route.id===id)render();
}
function recarregarObra(){esquecerChaves();if(OB.id){const id=OB.id;atualizarPedidoLocal(id).finally(()=>carregarObra(id,true));}}

function dashObra(id){
  if(DEMO_MODE)return dashHead('Obra','Obras não existem na demonstração.');
  if(OB.id!==id){OB.id=id;OB.dados=null;OB.form=null;OB.erro='';}
  if(!OB.dados&&!OB.erro){carregarObra(id);return dashHead('Obra','Carregando...');}
  const voltar='<button type="button" class="btn-voltar" style="margin-bottom:18px" onclick="go(\''+(me().role==='profissional'&&OB.dados&&OB.dados.aberta?'/app/oportunidades':'/app/pedidos')+'\')">'+ic('arrowLeft')+'<span>Voltar</span></button>';
  if(OB.erro)return voltar+'<div class="card emptystate" role="alert">'+esc(OB.erro)+'<button class="btn btn-ghost" style="margin-top:12px" onclick="carregarObra(\''+esc(id)+'\',true)">'+ic('refresh')+'Tentar de novo</button></div>';
  const d=OB.dados,o=OB.pedido||{},s=svc(o.service||o.servico),cliente=me().role==='cliente'&&!souAtendente(o);
  const cab=dashHead('Obra · '+esc(s.n),'Pedido em '+dt(o.created),d.aberta?'<span class="tag tag-warn">Recebendo orçamentos</span>':o.status?stTag(o.status):'');
  const descr='<section class="card obra-descr"><h2 class="sub">O que precisa ser feito</h2><p class="t2">'+esc(o.notes||'')+'</p>'+
    '<dl class="resumo-pedido">'+(d.teto?'<div><dt>Até quanto o cliente quer gastar</dt><dd>'+money(d.teto)+'</dd></div>':'')+
    (d.inicioDesejado?'<div><dt>Quer começar em</dt><dd>'+esc(new Date(d.inicioDesejado+'T12:00').toLocaleDateString('pt-BR'))+'</dd></div>':'')+
    (o.bairro?'<div><dt>Bairro</dt><dd>'+esc(o.bairro)+'</dd></div>':'')+'</dl>'+
    (typeof fotosOportunidade==='function'&&o.fotos?fotosOportunidade(o):'')+'</section>';
  if(d.aberta)return voltar+cab+descr+(cliente?obraClienteAberta(d):obraProAberta(d));
  return voltar+cab+obraContratada(d,o,cliente)+descr;
}

/* ---------- Antes do contrato: cliente compara orçamentos ---------- */
function obraClienteAberta(d){
  const os=(d.orcamentos||[]).filter(x=>x.status==='enviado');
  const vis=(d.visitas||[]).filter(v=>!['recusada','cancelada'].includes(v.status));
  return blocoVisitas(vis,true)+
    '<section class="obra-bloco" aria-labelledby="obraOrc"><div class="inicio-cab"><h2 class="sub" id="obraOrc">Orçamentos ('+os.length+')</h2></div>'+
    (!os.length?'<div class="inicio-vazio">'+ic('file')+'<p><b>Nenhum orçamento ainda.</b> Os profissionais da categoria já foram avisados. Cada orçamento chega aqui com as etapas, o prazo e o valor.</p></div>':
    '<div class="orc-comparar" role="list">'+os.map(x=>cartaoOrcamento(x,true)).join('')+'</div>')+
    '<p class="hint">'+(d.direto?'Você paga cada etapa direto ao profissional, por Pix, depois de aprová-la.':'Você paga cada etapa antes de ela começar. A TaskGO guarda o valor e só repassa quando você aprova a etapa.')+'</p></section>'+
    '<div class="obra-rodape"><button class="btn btn-danger btn-sm" onclick="cancelarObra()">'+ic('x')+'Cancelar pedido</button></div>';
}
function cartaoOrcamento(x,cliente){
  const etapas=x.etapas||[];
  return '<article class="orc-card" role="listitem">'+
    '<header class="orc-quem">'+fotoPerfil(x.profissionalId,x.profissionalNome,40,!!x.profissionalTemFoto)+'<div><b>'+esc(x.profissionalNome||'Profissional')+(x.verificado?seloVerificado(true,true):'')+seloPro(x.pro,true)+'</b>'+
      '<small class="muted">'+(x.nota?stars(x.nota)+' '+String(x.nota).replace('.',',')+' · ':'')+(x.servicosFeitos||0)+' serviços feitos</small></div></header>'+
    '<div class="orc-total"><span>Total</span><b>'+money(x.total)+'</b></div>'+
    '<ol class="orc-etapas">'+etapas.map((e,i)=>'<li><span class="orc-n">'+(i+1)+'</span><div><b>'+esc(e.nome)+'</b><small>'+e.dias+(e.dias==1?' dia':' dias')+(e.entrega?' · '+esc(e.entrega):'')+'</small></div><span class="orc-v">'+money(e.valor)+'</span></li>').join('')+'</ol>'+
    '<dl class="orc-dados"><div><dt>Início</dt><dd>'+dataCurta(x.inicio)+'</dd></div><div><dt>Término previsto</dt><dd>'+dataCurta(x.previsaoFim)+'</dd></div>'+
      '<div><dt>Dias e horário</dt><dd>'+(x.diasTrabalho||[]).map(n=>(DIAS_SEMANA.find(d=>d[0]===n)||[0,''])[1]).join(', ')+' · '+esc(x.horario)+'</dd></div>'+
      '<div><dt>Material</dt><dd>'+(x.material==='incluso'?'Incluso no preço':'Você compra')+'</dd></div></dl>'+
    (x.material==='cliente'&&x.listaMaterial?'<details class="orc-lista"><summary>Lista de material</summary><p>'+esc(x.listaMaterial).replace(/\n/g,'<br>')+'</p></details>':'')+
    (x.observacao?'<p class="hint">'+esc(x.observacao)+'</p>':'')+
    (cliente?'<div class="orc-acoes">'+(x.contrato?'<button type="button" class="link-btn" onclick="verContrato(\''+esc(x.id)+'\')">Ler o contrato</button>':'')+
      '<button class="btn btn-ghost btn-sm" onclick="recusarOrcamento(\''+esc(x.id)+'\')">Recusar</button>'+
      '<button class="btn btn-primary btn-sm" onclick="aceitarOrcamento(\''+esc(x.id)+'\','+!!x.contrato+')">'+ic('check')+'Contratar</button></div>':
      '<p class="orc-estado">'+({enviado:'Enviado. Esperando o cliente.',aceito:'Aceito.',recusado:'O cliente escolheu outro orçamento.',retirado:'Retirado.'})[x.status]+'</p>')+
  '</article>';
}
async function verContrato(id){
  try{const r=await fetch(API_BASE_URL+'/api/obras/orcamentos/'+encodeURIComponent(id)+'/contrato');if(!r.ok)throw await erroDaResposta(r,'Não foi possível abrir o contrato.');
    openModal('<div class="modal-head"><div><h3>Contrato</h3><p>Aceito pelas duas partes ao contratar.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div><pre class="contrato-texto">'+esc(await r.text())+'</pre>',{wide:true});}
  catch(e){toast(erroLegivel(e,'Não foi possível abrir o contrato.'),'err');}
}
function aceitarOrcamento(id,contrato){
  openModal('<div class="modal-head"><div><h3>Contratar este orçamento</h3><p>A agenda do profissional é bloqueada nos dias combinados. Depois você paga a 1ª etapa para ele começar.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    (contrato?'<label class="check-linha"><input type="checkbox" id="obContrato"> Li e aceito o <button type="button" class="link-btn" onclick="verContrato(\''+esc(id)+'\')">contrato</button></label>':'')+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" id="obAceitar" onclick="confirmarAceiteOrcamento(\''+esc(id)+'\','+contrato+')">'+ic('check')+'Contratar</button></div>');
}
async function confirmarAceiteOrcamento(id,contrato){
  if(contrato&&!$('#obContrato').checked)return toast('Marque que leu e aceita o contrato.','err');
  $('#obAceitar').disabled=true;
  try{OB.dados=await apiJSON('/api/obras/orcamentos/'+encodeURIComponent(id)+'/aceitar','POST',{contratoAceito:contrato},'Não foi possível contratar.');closeModal();toast('Contratado. Pague a 1ª etapa para começar.','ok');recarregarObra();}
  catch(e){toast(erroLegivel(e,'Não foi possível contratar.'),'err');const b=$('#obAceitar');if(b)b.disabled=false;}
}
async function recusarOrcamento(id){
  if(!await confirmar({titulo:'Recusar este orçamento?',botao:'Recusar',perigo:true}))return;
  try{await apiJSON('/api/obras/orcamentos/'+encodeURIComponent(id)+'/recusar','POST');toast('Recusado.','ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível recusar.'),'err');}
}

/* ---------- Visita técnica ---------- */
const ST_VISITA={proposta:'Esperando pagamento',paga:'Paga · marcada',realizada:'Feita · confirme',liberada:'Feita e paga',recusada:'Recusada',cancelada:'Cancelada'};
function blocoVisitas(vis,cliente){
  if(!vis.length)return '';
  return '<section class="obra-bloco" aria-labelledby="obraVis"><div class="inicio-cab"><h2 class="sub" id="obraVis">Visita técnica</h2></div><ul class="obra-linhas">'+vis.map(v=>
    '<li><div><b>'+esc(v.profissionalNome||'Profissional')+' · '+money(v.valor)+'</b><small>'+new Date(v.data).toLocaleString('pt-BR',{weekday:'short',day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'})+' · '+ST_VISITA[v.status]+
      (cliente&&v.status==='proposta'?' · abatida se você contratar':'')+'</small></div><div class="obra-acoes">'+
      (cliente&&v.status==='proposta'&&OB.dados&&OB.dados.direto?'<button class="btn btn-ghost btn-sm" onclick="acaoVisita(\''+esc(v.id)+'\',\'recusar\')">Recusar</button><button class="btn btn-primary btn-sm" onclick="acaoVisita(\''+esc(v.id)+'\',\'aceitar\')">'+ic('check')+'Aceitar visita</button>':'')+
      (!cliente&&v.status==='realizada'&&OB.dados&&OB.dados.direto?'<button class="btn btn-primary btn-sm" onclick="acaoVisita(\''+esc(v.id)+'\',\'recebida\')">'+ic('check')+'Recebi '+money(v.valor)+'</button>':'')+
      (cliente&&v.status==='proposta'&&!(OB.dados&&OB.dados.direto)?'<button class="btn btn-ghost btn-sm" onclick="acaoVisita(\''+esc(v.id)+'\',\'recusar\')">Recusar</button><button class="btn btn-primary btn-sm" onclick="pagarObra(\'visita\',\''+esc(v.id)+'\','+Number(v.valor)+')">'+ic('lock')+'Pagar '+money(v.valor)+'</button>':'')+
      (cliente&&v.status==='realizada'&&!(OB.dados&&OB.dados.direto)?'<button class="btn btn-primary btn-sm" onclick="acaoVisita(\''+esc(v.id)+'\',\'confirmar\')">'+ic('check')+'Confirmar visita</button>':'')+
      (!cliente&&v.status==='paga'?'<button class="btn btn-primary btn-sm" onclick="acaoVisita(\''+esc(v.id)+'\',\'realizada\')">'+ic('check')+'Visita feita</button>':'')+
      (['proposta','paga'].includes(v.status)?'<button class="btn btn-ghost btn-sm" onclick="acaoVisita(\''+esc(v.id)+'\',\'cancelar\')">Cancelar</button>':'')+
    '</div></li>').join('')+'</ul>'+
    (cliente&&OB.dados&&OB.dados.direto&&vis.some(v=>v.status==='realizada')?blocoChavePix(OB.id,'Pague a visita:'):'')+'</section>';
}
async function acaoVisita(id,acao){
  if(acao==='cancelar'&&!await confirmar({titulo:'Cancelar a visita técnica?',botao:'Cancelar visita',voltar:'Manter',perigo:true}))return;
  try{await apiJSON('/api/obras/visitas/'+encodeURIComponent(id)+'/'+acao,'POST',null,'Não foi possível.');toast('Pronto.','ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível.'),'err');}
}

/* ---------- Antes do contrato: profissional orça ---------- */
function obraProAberta(d){
  const meu=(d.orcamentos||[]).filter(x=>x.status==='enviado')[0];
  const minhasVis=(d.visitas||[]).filter(v=>!['recusada','cancelada'].includes(v.status));
  if(OB.form)return formOrcamento(d);
  return blocoVisitas(minhasVis,false)+
    (meu?'<section class="obra-bloco"><div class="inicio-cab"><h2 class="sub">Seu orçamento</h2></div><div class="orc-comparar">'+cartaoOrcamento(meu,false)+'</div>'+
      '<div class="obra-acoes"><button class="btn btn-ghost btn-sm" onclick="retirarOrcamento(\''+esc(meu.id)+'\')">Retirar</button><button class="btn btn-primary btn-sm" onclick="novoOrcamento(true)">'+ic('edit')+'Mandar outro no lugar</button></div></section>':
    '<section class="obra-bloco"><div class="inicio-cab"><h2 class="sub">Orce esta obra</h2></div><p class="t2">Divida em etapas com valor, dias e o que entrega. O cliente paga cada etapa antes de ela começar e a TaskGO segura até ele aprovar.</p>'+
      '<div class="obra-acoes"><button class="btn btn-primary" onclick="novoOrcamento()">'+ic('file')+'Mandar orçamento</button>'+
      (minhasVis.length?'':'<button class="btn btn-ghost" onclick="proporVisita()">'+ic('mapPin')+'Propor visita técnica</button>')+'</div></section>')+
    regrasFaixa();
}
function regrasFaixa(){
  return '<section class="obra-bloco obra-regras"><h2 class="sub">Regras por valor</h2><table class="tabela-faixas"><thead><tr><th>Total</th><th>Taxa</th><th>O que pede</th></tr></thead><tbody>'+
    [['Serviço de um dia até R$ 999','15%','Pedido comum'],['Serviço de um dia de R$ 1.000 a 2.999','12%','Pedido comum, sem dinheiro, até 12x'],['Obra de R$ 1.000 a 2.999','12%','1 a 3 etapas'],['R$ 3.000 a 4.999','10%','2+ etapas · 3 serviços com nota 4+ · garantia de 8%'],
     ['R$ 5.000 a 9.999','8%','3+ etapas · 10 serviços com nota 4,5+ ou CNPJ/MEI · contrato · garantia'],['R$ 10.000 ou mais','6%','etapas de até 30% · contrato · conferência da equipe · garantia']]
    .map(r=>'<tr><td>'+r[0]+'</td><td>'+r[1]+'</td><td>'+r[2]+'</td></tr>').join('')+'</tbody></table></section>';
}
function novoOrcamento(){
  const amanha=new Date(Date.now()+864e5).toISOString().slice(0,10);
  OB.form={etapas:[{nome:'',valor:'',dias:1,entrega:''}],material:'incluso',lista:'',inicio:(OB.dados.inicioDesejado&&OB.dados.inicioDesejado>amanha)?OB.dados.inicioDesejado:amanha,dias:[1,2,3,4,5],hIni:'08:00',hFim:'17:00',obs:'',contrato:false};
  render();
}
function totalForm(){return (OB.form.etapas||[]).reduce((a,e)=>a+(Number(e.valor)||0),0);}
function faixaForm(t){return t>=10000?{taxa:.06,min:4,max:12,contrato:1}:t>=5000?{taxa:.08,min:3,max:12,contrato:1}:t>=3000?{taxa:.10,min:2,max:12}:t>=1000?{taxa:.12,min:1,max:3}:{taxa:.15,min:1,max:12};}
function formOrcamento(){
  const f=OB.form,t=totalForm(),fx=faixaForm(t);
  return '<section class="obra-bloco"><div class="passo-cab"><button type="button" class="btn-voltar" onclick="OB.form=null;render()">'+ic('arrowLeft')+'<span>Voltar</span></button><div class="passo-tit"><h2 class="sub">Orçamento</h2><p class="muted">Etapas, prazo e material. Quem decide é o cliente.</p></div></div>'+
    '<div class="orc-form-etapas">'+f.etapas.map((e,i)=>'<fieldset class="orc-etapa"><legend>Etapa '+(i+1)+'</legend>'+
      '<div class="field"><label for="obN'+i+'">Nome</label><input class="input" id="obN'+i+'" maxlength="120" value="'+esc(e.nome)+'" placeholder="Ex.: Demolição e contrapiso" oninput="OB.form.etapas['+i+'].nome=this.value"></div>'+
      '<div class="grid g2" style="gap:0 16px"><div class="field"><label for="obV'+i+'">Valor (R$)</label><input class="input" id="obV'+i+'" type="number" inputmode="decimal" min="1" step="0.01" value="'+esc(e.valor)+'" oninput="OB.form.etapas['+i+'].valor=this.value;pintarTotalOrc()"></div>'+
      '<div class="field"><label for="obD'+i+'">Dias de trabalho</label><input class="input" id="obD'+i+'" type="number" min="1" max="60" value="'+esc(e.dias)+'" oninput="OB.form.etapas['+i+'].dias=Number(this.value)"></div></div>'+
      '<div class="field"><label for="obE'+i+'">O que fica pronto</label><input class="input" id="obE'+i+'" maxlength="500" value="'+esc(e.entrega)+'" placeholder="Ex.: piso assentado e rejuntado" oninput="OB.form.etapas['+i+'].entrega=this.value"></div>'+
      (f.etapas.length>1?'<button type="button" class="link-btn" onclick="OB.form.etapas.splice('+i+',1);render()">Tirar esta etapa</button>':'')+'</fieldset>').join('')+'</div>'+
    (f.etapas.length<12?'<button type="button" class="btn btn-ghost btn-sm" onclick="OB.form.etapas.push({nome:\'\',valor:\'\',dias:1,entrega:\'\'});render()">'+ic('plus')+'Adicionar etapa</button>':'')+
    '<div class="orc-soma" id="orcSoma">'+somaOrc(t,fx)+'</div>'+
    '<div class="grid g2" style="gap:0 16px;margin-top:14px"><div class="field"><label for="obIni">Início</label><input class="input" id="obIni" type="date" value="'+esc(f.inicio)+'" onchange="OB.form.inicio=this.value"></div>'+
    '<div class="field"><span class="rotulo-campo">Horário</span><div class="obra-horario"><input class="input" type="time" aria-label="Começa" value="'+esc(f.hIni)+'" onchange="OB.form.hIni=this.value"><span>às</span><input class="input" type="time" aria-label="Termina" value="'+esc(f.hFim)+'" onchange="OB.form.hFim=this.value"></div></div></div>'+
    '<fieldset class="field obra-dias"><legend class="rotulo-campo">Dias da semana</legend>'+DIAS_SEMANA.map(d=>'<label class="chip-check"><input type="checkbox" '+(f.dias.includes(d[0])?'checked':'')+' onchange="OB.form.dias=this.checked?[...OB.form.dias,'+d[0]+']:OB.form.dias.filter(x=>x!=='+d[0]+')"><span>'+d[1]+'</span></label>').join('')+'</fieldset>'+
    '<div class="formas" role="radiogroup" aria-label="Material">'+opcaoPag('obMat','incluso',f.material,'Material incluso','Você compra; até 30% de cada etapa sai no início, com a foto da nota','box','OB.form.material=this.value;render()')+
      opcaoPag('obMat','cliente',f.material,'O cliente compra','Mande a lista do que ele precisa comprar','list','OB.form.material=this.value;render()')+'</div>'+
    (f.material==='cliente'?'<div class="field"><label for="obLista">Lista de material</label><textarea class="textarea" id="obLista" maxlength="3000" placeholder="Ex.: 20 sacos de argamassa AC-II; 12 m² de piso 60x60" oninput="OB.form.lista=this.value">'+esc(f.lista)+'</textarea></div>':'')+
    '<div class="field"><label for="obObs">Observação (opcional)</label><textarea class="textarea" id="obObs" maxlength="2000" oninput="OB.form.obs=this.value">'+esc(f.obs)+'</textarea></div>'+
    '<label class="check-linha" id="obContratoLinha"'+(fx.contrato?'':' hidden')+'><input type="checkbox" '+(f.contrato?'checked':'')+' onchange="OB.form.contrato=this.checked"> Aceito o contrato da TaskGO (obrigatório a partir de R$ 5.000)</label>'+
    '<div class="passo-rodape"><button type="button" class="btn-voltar" onclick="OB.form=null;render()">'+ic('arrowLeft')+'<span>Voltar</span></button><button class="btn btn-primary" id="obEnviar" onclick="enviarOrcamento()">'+ic('check')+'Mandar orçamento</button></div></section>';
}
function somaOrc(t,fx){
  const n=OB.form.etapas.length,aviso=n<fx.min?'Para este total, use pelo menos '+fx.min+' etapas.':n>fx.max?'Para este total, use no máximo '+fx.max+' etapas.':
    t>=10000&&OB.form.etapas.some(e=>Number(e.valor)>t*.3)?'A partir de R$ 10.000, nenhuma etapa passa de 30% do total.':'';
  return '<span>Total</span><b>'+money(t)+'</b><small>Você recebe '+money(t*(1-fx.taxa))+' (taxa da TaskGO de '+pct(fx.taxa)+')'+(t>=3000?' · 8% fica de garantia por 7 dias depois da última etapa':'')+'</small>'+(aviso?'<p class="pag-recusa">'+ic('x')+aviso+'</p>':'');
}
function pintarTotalOrc(){const t=totalForm(),fx=faixaForm(t);const n=$('#orcSoma');if(n)n.innerHTML=somaOrc(t,fx);const c=$('#obContratoLinha');if(c)c.hidden=!fx.contrato;}
async function enviarOrcamento(){
  const f=OB.form;
  const corpo={etapas:f.etapas.map(e=>({nome:e.nome,valor:Number(e.valor),dias:Number(e.dias),entrega:e.entrega})),material:f.material,listaMaterial:f.lista,inicio:f.inicio,
    diasTrabalho:[...new Set(f.dias)].sort(),horario:f.hIni+'-'+f.hFim,observacao:f.obs,contratoAceito:f.contrato};
  const b=$('#obEnviar');if(b)b.disabled=true;
  try{await apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/orcamentos','POST',corpo,'Não foi possível mandar.');OB.form=null;toast('Orçamento enviado ao cliente.','ok');recarregarObra();}
  catch(e){toast(erroLegivel(e,'Não foi possível mandar o orçamento.'),'err');if(b)b.disabled=false;}
}
async function retirarOrcamento(id){
  if(!await confirmar({titulo:'Retirar o seu orçamento?',botao:'Retirar',perigo:true}))return;
  try{await apiJSON('/api/obras/orcamentos/'+encodeURIComponent(id)+'/retirar','POST');toast('Retirado.','ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível.'),'err');}
}
function proporVisita(){
  const amanha=new Date(Date.now()+864e5);amanha.setHours(9,0,0,0);
  const v=new Date(amanha.getTime()-amanha.getTimezoneOffset()*6e4).toISOString().slice(0,16);
  openModal('<div class="modal-head"><div><h3>Propor visita técnica</h3><p>Você vai medir antes de orçar. O cliente paga antes; se ele contratar você, o valor é abatido da 1ª etapa.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="grid g2" style="gap:0 16px"><div class="field"><label for="vtValor">Valor (R$ 50 a R$ 100)</label><input class="input" id="vtValor" type="number" min="50" max="100" step="1" value="80"></div>'+
    '<div class="field"><label for="vtData">Dia e hora</label><input class="input" id="vtData" type="datetime-local" value="'+v+'"></div></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" onclick="enviarVisita()">'+ic('check')+'Propor</button></div>');
}
async function enviarVisita(){
  try{await apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/visitas','POST',{valor:Number($('#vtValor').value),data:new Date($('#vtData').value).getTime()},'Não foi possível propor.');closeModal();toast('Visita proposta ao cliente.','ok');recarregarObra();}
  catch(e){toast(erroLegivel(e,'Não foi possível propor.'),'err');}
}

/* ---------- Depois do contrato ---------- */
function obraContratada(d,o,cliente){
  const orc=(d.orcamentos||[]).find(x=>x.id===d.orcamentoAceito)||{},ets=d.etapas||[];
  const pago=ets.reduce((a,e)=>a+Number(e.pago),0),liberado=ets.reduce((a,e)=>a+Number(e.liberado),0);
  const feitas=ets.filter(e=>['aprovada','devolvida'].includes(e.status)).length;
  const prox=ets.find(e=>e.status==='pendente');
  const revisao=d.revisao==='pendente'?'<p class="obra-aviso" role="status">'+ic('shield')+'<span><b>A equipe da TaskGO está conferindo esta obra.</b> O pagamento da 1ª etapa abre assim que ela aprovar.</span></p>':
    d.revisao==='reprovada'?'<p class="obra-aviso erro" role="alert">'+ic('x')+'<span><b>A equipe não aprovou esta obra.</b> Nada foi cobrado.</span></p>':'';
  const g=d.garantia;
  const garantia=!g?'':'<section class="obra-bloco"><div class="inicio-cab"><h2 class="sub">Garantia</h2><span class="tag '+(g.status==='retida'?'tag-info':g.status==='defeito'?'tag-warn':'tag-ok')+'">'+({retida:'Retida',defeito:'Defeito em análise',liberada:'Liberada',decidida:'Decidida pela equipe'})[g.status]+'</span></div>'+
    '<p class="t2">'+money(g.valor)+' (8% da obra) '+(g.status==='retida'?'ficam com a TaskGO até '+dataHora(g.ate)+'. '+(cliente?'Se aparecer um defeito até lá, avise.':'Sem defeito relatado, caem na sua carteira.'):g.status==='defeito'?'retidos: o cliente relatou "'+esc(g.defeito||'')+'". A equipe decide.':'.')+'</p>'+
    (cliente&&g.status==='retida'&&g.ate>Date.now()?'<button class="btn btn-ghost btn-sm" onclick="relatarDefeito()">'+ic('flag')+'Relatar defeito</button>':'')+'</section>';
  return revisao+
    (d.direto&&cliente&&Number(d.aPagar)>0?'<section class="card obra-apagar">'+blocoChavePix(o.id,'Pague')+'</section>':'')+
    '<section class="obra-resumo"><div><span>Total</span><b>'+money(o.price||orc.total)+'</b></div><div><span>Etapas</span><b>'+feitas+' de '+ets.length+'</b></div>'+
    '<div><span>'+(cliente?'Você pagou':'Cliente pagou')+'</span><b>'+money(pago)+'</b></div><div><span>Término previsto</span><b>'+dataCurta(orc.previsaoFim)+'</b></div></section>'+
    (orc.contrato?'<p class="hint"><button type="button" class="link-btn" onclick="verContrato(\''+esc(orc.id)+'\')">Ver contrato</button> · aceito pelas duas partes</p>':'')+
    '<section class="obra-bloco" aria-labelledby="obraEtapas"><div class="inicio-cab"><h2 class="sub" id="obraEtapas">Etapas</h2></div><ol class="obra-etapas">'+ets.map(e=>linhaEtapa(e,cliente,prox,orc,d)).join('')+'</ol></section>'+
    garantia+blocoAditivos(d,cliente)+blocoDiario(d,cliente,o)+
    (o.status&&!['cancelado','concluido'].includes(o.status)?'<div class="obra-rodape"><a class="btn btn-ghost btn-sm" href="#/app/mensagens">'+ic('chat')+'Conversar</a><button class="btn btn-danger btn-sm" onclick="emergencia()">'+ic('phone')+'Emergência</button><button class="btn btn-danger btn-sm" onclick="cancelarObra()">'+ic('x')+'Cancelar obra</button></div>':'')+
    (liberado?'':'');
}
function linhaEtapa(e,cliente,prox,orc,d){
  const aPagarDireto=d.direto&&e.status==='aprovada'&&!e.pagaEm;
  const st=aPagarDireto?['Aprovada, a pagar','tag-warn']:ST_ETAPA[e.status]||[e.status,'tag-muted'],id=esc(e.id);
  let acoes='';
  if(d.direto){
    const anteriores=(d.etapas||[]).filter(x=>x.ordem<e.ordem),liberada=anteriores.every(x=>x.status==='aprovada'&&x.pagaEm||['cancelada','devolvida'].includes(x.status));
    if(cliente){
      if(e.status==='entregue')acoes='<button class="btn btn-ghost btn-sm" onclick="problemaEtapa(\''+id+'\')">Tive um problema</button><button class="btn btn-primary btn-sm" onclick="aprovarEtapa(\''+id+'\')">'+ic('check')+'Aprovar</button>';
      if(e.status==='andamento')acoes='<button class="btn btn-ghost btn-sm" onclick="problemaEtapa(\''+id+'\')">Tive um problema</button>';
    }else{
      if(e.status==='pendente'&&liberada&&d.revisao!=='pendente'&&d.revisao!=='reprovada')acoes='<button class="btn btn-primary btn-sm" onclick="iniciarEtapa(\''+id+'\')">'+ic('play')+'Começar etapa</button>';
      if(e.status==='andamento')acoes='<button class="btn btn-primary btn-sm" onclick="entregarEtapa(\''+id+'\')">'+ic('check')+'Etapa pronta</button>';
      if(aPagarDireto)acoes='<button class="btn btn-primary btn-sm" onclick="acaoEtapa(\''+id+'\',\'recebida\',null,\'Recebimento registrado. A taxa da TaskGO está na Carteira.\')">'+ic('check')+'Recebi '+money(e.aPagar)+'</button>';
    }
  }else if(cliente){
    if(prox&&prox.id===e.id&&d.revisao!=='pendente'&&d.revisao!=='reprovada')acoes='<button class="btn btn-primary btn-sm" onclick="pagarObra(\'etapa\',\''+id+'\','+Number(e.aPagar)+')">'+ic('lock')+'Pagar '+money(e.aPagar)+'</button>';
    if(e.status==='entregue')acoes='<button class="btn btn-ghost btn-sm" onclick="problemaEtapa(\''+id+'\')">Tive um problema</button><button class="btn btn-primary btn-sm" onclick="aprovarEtapa(\''+id+'\')">'+ic('check')+'Aprovar</button>';
    if(e.status==='andamento')acoes='<button class="btn btn-ghost btn-sm" onclick="problemaEtapa(\''+id+'\')">Tive um problema</button>';
  }else{
    if(e.status==='paga')acoes='<button class="btn btn-primary btn-sm" onclick="iniciarEtapa(\''+id+'\')">'+ic('play')+'Começar etapa</button>';
    if(e.status==='andamento')acoes='<button class="btn btn-primary btn-sm" onclick="entregarEtapa(\''+id+'\')">'+ic('check')+'Etapa pronta</button>';
    if(['paga','andamento'].includes(e.status)&&orc.material==='incluso'&&!Number(e.materialLiberado))acoes+='<button class="btn btn-ghost btn-sm" onclick="adiantarMaterial(\''+id+'\','+Number(e.pago)+')">'+ic('box')+'Adiantar material</button>';
  }
  const info=[e.dias+(e.dias==1?' dia':' dias')];
  if(Number(e.desconto)>0)info.push('visita abatida: − '+money(e.desconto));
  if(Number(e.materialLiberado)>0)info.push('material adiantado: '+money(e.materialLiberado));
  if(e.status==='entregue'&&e.liberarApos)info.push('sem resposta, libera às '+horaLiberacao(e.liberarApos));
  if(Number(e.devolvido)>0)info.push('devolvido: '+money(e.devolvido));
  if(e.aditivo)info.push('aditivo');
  return '<li class="obra-etapa '+esc(e.status)+'"><span class="orc-n">'+e.ordem+'</span><div class="obra-etapa-corpo"><div class="obra-etapa-cab"><b>'+esc(e.nome)+'</b><span class="tag '+st[1]+'">'+st[0]+'</span></div>'+
    (e.entrega?'<p class="t2">'+esc(e.entrega)+'</p>':'')+'<small class="muted">'+info.join(' · ')+'</small>'+
    (e.problema?'<p class="pag-recusa">'+ic('flag')+esc(e.problema)+'</p>':'')+
    ((e.fotos||[]).length?'<div class="fotos-grade">'+e.fotos.map(f=>miniatura(urlFotoPedido(f),'Etapa '+e.ordem+' pronta')).join('')+'</div>':'')+
    (acoes?'<div class="obra-acoes">'+acoes+'</div>':'')+'</div><b class="orc-v">'+money(e.valor)+'</b></li>';
}
async function acaoEtapa(id,acao,corpo,ok){
  try{await apiJSON('/api/obras/etapas/'+encodeURIComponent(id)+'/'+acao,'POST',corpo,'Não foi possível.');closeModal();toast(ok,'ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível.'),'err');}
}
function iniciarEtapa(id){acaoEtapa(id,'iniciar',null,'Etapa começou. O cliente foi avisado.');}
async function aprovarEtapa(id){if(await confirmar({titulo:'Aprovar a etapa?',texto:OB.dados&&OB.dados.direto?'Depois você paga o valor ao profissional por Pix.':'O valor vai para o profissional.',botao:'Aprovar',icone:'checkCircle'}))acaoEtapa(id,'aprovar',null,'Etapa aprovada.');}
function problemaEtapa(id){
  openModal('<div class="modal-head"><div><h3>Problema na etapa</h3><p>Só o valor desta etapa fica retido até a equipe decidir. As aprovadas continuam com o profissional.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="obProb">O que aconteceu?</label><textarea class="textarea" id="obProb" maxlength="1000" placeholder="Ex.: a bancada ficou torta e a pia não encaixa"></textarea></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-danger" onclick="acaoEtapa(\''+esc(id)+'\',\'problema\',{texto:$(\'#obProb\').value},\'Enviado à equipe.\')">Enviar à equipe</button></div>',{onOpen:()=>$('#obProb').focus()});
}
const FOTOS_OBRA={lista:[]};
function campoFotosObra(max){
  return '<div class="fotos-grade" id="obFotos">'+FOTOS_OBRA.lista.map((f,i)=>'<div class="foto-item">'+miniatura(f,'Foto '+(i+1))+'<button type="button" class="foto-apagar" aria-label="Tirar foto" onclick="FOTOS_OBRA.lista.splice('+i+',1);$(\'#obFotos\').outerHTML=campoFotosObra('+max+')">'+ic('x')+'</button></div>').join('')+
    (FOTOS_OBRA.lista.length<max?'<label class="foto-add">'+ic('camera')+'<span>Foto</span><input type="file" accept="image/*" class="leitor" onchange="lerFotoObra(this,'+max+')"></label>':'')+'</div>';
}
async function lerFotoObra(input,max){const f=input.files&&input.files[0];input.value='';if(!f)return;try{FOTOS_OBRA.lista.push(await comprimirFoto(f));$('#obFotos').outerHTML=campoFotosObra(max);}catch(e){toast(e.message,'err');}}
function entregarEtapa(id){
  FOTOS_OBRA.lista=[];
  openModal('<div class="modal-head"><div><h3>Etapa pronta</h3><p>Mande de 1 a 6 fotos. O cliente tem 24 horas para aprovar ou relatar um problema; sem resposta, o valor vai para você.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    campoFotosObra(6)+'<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" onclick="FOTOS_OBRA.lista.length?acaoEtapa(\''+esc(id)+'\',\'entregar\',{fotos:FOTOS_OBRA.lista},\'Entregue. O cliente foi avisado.\'):toast(\'Mande pelo menos 1 foto.\',\'err\')">'+ic('check')+'Entregar etapa</button></div>');
}
function adiantarMaterial(id,pago){
  FOTOS_OBRA.lista=[];const max=Math.floor(pago*30)/100;
  openModal('<div class="modal-head"><div><h3>Adiantar material</h3><p>Até '+money(max)+' (30% da etapa) saem agora para a compra do material. Mande a foto da nota.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="obMatV">Valor (R$)</label><input class="input" id="obMatV" type="number" min="1" max="'+max+'" step="0.01" value="'+max+'"></div>'+campoFotosObra(1)+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" onclick="FOTOS_OBRA.lista.length?acaoEtapa(\''+esc(id)+'\',\'material\',{valor:Number($(\'#obMatV\').value),foto:FOTOS_OBRA.lista[0]},\'Adiantamento na sua carteira.\'):toast(\'Mande a foto da nota.\',\'err\')">'+ic('check')+'Adiantar</button></div>');
}

/* ---------- Pagamento da etapa ou da visita ---------- */
async function pagarObra(tipo,id,valor){
  if(!PG.config||PG.cartoes===null||PG.saldo==null)await carregarPagamentos(true);
  const cs=(PG.cartoes||[]).filter(c=>!c.vencido),saldo=Number(PG.saldo)||0;
  OB.pag={tipo,id,valor,metodo:saldo>=valor?'carteira':cs.length?'cartao:'+cs[0].id:'pix',parcelas:1};
  openModal(htmlPagarObra(),{onOpen:()=>{}});
}
function htmlPagarObra(){
  const p=OB.pag,cs=(PG.cartoes||[]).filter(c=>!c.vencido),saldo=Number(PG.saldo)||0,cartao=p.metodo.startsWith('cartao:');
  const op=(v,t,dsc,i)=>opcaoPag('obPag',v,p.metodo,t,dsc,i,'OB.pag.metodo=this.value;OB.pag.parcelas=1;$(\'#modal-root .modal\').innerHTML=htmlPagarObra()');
  const max=p.tipo==='etapa'?Math.max(1,Math.min(12,Math.floor(p.valor/50))):1;
  const juros=Number(OB.dados&&OB.dados.jurosMes)||0.0249;
  const totalCom=n=>{if(n<=6)return p.valor;const f=Math.pow(1+juros,n);return Math.round(p.valor*juros*f/(f-1)*100)/100*n;};
  return '<div class="modal-head"><div><h3>Pagar '+money(p.valor)+'</h3><p>'+(p.tipo==='semana'?'Semana '+p.id+' do serviço recorrente. Cada vez só vai para o profissional quando você confirmar.':p.tipo==='etapa'?'O valor fica com a TaskGO e só vai para o profissional quando você aprovar a etapa.':'Visita técnica: abatida da 1ª etapa se você contratar este profissional.')+'</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    faixaTeste()+'<div class="formas" role="radiogroup" aria-label="Forma de pagamento">'+
      (saldo>=p.valor?op('carteira','Carteira TaskGO','Saldo de '+money(saldo),'wallet'):'')+
      cs.map(c=>op('cartao:'+c.id,esc(c.bandeira)+' •••• '+esc(c.final4),textoCartao(c),'card')).join('')+
      op('pix','Pix','Pelo app do banco','pix')+
      (cartaoNaPagina()?op('cartao_novo',cs.length?'Outro cartão de crédito':'Cartão de crédito','Na página segura do Asaas','card')+(p.tipo==='etapa'?op('boleto','Boleto','Compensa em até 3 dias úteis; a etapa começa depois','file'):''):'')+'</div>'+
    (cartao&&max>1?'<div class="field pag-parcelas"><label for="obParc">Parcelas</label><select class="select" id="obParc" onchange="OB.pag.parcelas=Number(this.value)">'+
      Array.from({length:max},(_,i)=>{const n=i+1,t=totalCom(n);return '<option value="'+n+'"'+(p.parcelas===n?' selected':'')+'>'+n+'x de '+money(t/n)+(n<=6?(n>1?' sem juros':' à vista'):' · total '+money(t)+' (juros de '+pct(juros)+' ao mês)')+'</option>';}).join('')+'</select></div>':'')+
    '<div id="obPix"></div><p id="obPagErro" class="pag-recusa" role="alert" hidden></p>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" id="obPagar" onclick="confirmarPagarObra()">'+ic('lock')+'Pagar</button></div>';
}
async function confirmarPagarObra(){
  const p=OB.pag,b=$('#obPagar'),erro=$('#obPagErro');if(b)b.disabled=true;
  const corpo=p.metodo.startsWith('cartao:')?{metodo:'cartao',cartaoId:p.metodo.slice(7),parcelas:p.parcelas}:{metodo:p.metodo};
  try{
    const c=await apiJSON(p.url||'/api/obras/'+(p.tipo==='etapa'?'etapas':'visitas')+'/'+encodeURIComponent(p.id)+'/pagar','POST',corpo,'Não foi possível pagar.');
    if(c.status==='paga'){closeModal();toast('Pago.','ok');PG.saldo=null;(p.depois||recarregarObra)();return;}
    if(!c.pixCopiaECola&&c.link){window.open(c.link,'_blank','noopener');esperarCobrancaObra(c.id);closeModal();toast('Termine o pagamento na página do Asaas. '+(p.tipo==='semana'?'O pedido':'A obra')+' atualiza sozinha.','ok');return;}
    if(c.pixCopiaECola){
      $('#obPix').innerHTML='<div class="pag-pix">'+(c.qrCode?'<img class="pag-qr" src="'+esc(c.qrCode)+'" alt="QR code do Pix" width="200" height="200">':'')+
        '<div class="pag-pix-dados"><label class="rotulo-campo" for="obPixCod">Código Pix copia e cola</label><input class="input" id="obPixCod" readonly value="'+esc(c.pixCopiaECola)+'">'+
        '<div class="obra-acoes"><button class="btn btn-ghost btn-sm" onclick="navigator.clipboard.writeText($(\'#obPixCod\').value);toast(\'Copiado\',\'ok\')">'+ic('copy')+'Copiar código</button>'+
        (pagamentoTeste()?'<button class="btn btn-ghost btn-sm" onclick="apiJSON(\'/api/pagamentos/cobrancas/'+esc(c.id)+'/simular-pix\',\'POST\').catch(e=>toast(erroLegivel(e,\'Falhou\'),\'err\'))">Simular pagamento</button>':'')+'</div>'+
        '<p class="muted" role="status"><span class="pag-ponto"></span> Esperando o pagamento…</p></div></div>';
      if(b)b.hidden=true;esperarCobrancaObra(c.id);
    }
  }catch(e){if(erro){erro.textContent=erroLegivel(e,'Não foi possível pagar.');erro.hidden=false;}if(b)b.disabled=false;}
}
function esperarCobrancaObra(cid){
  clearInterval(OB.pixTimer);let n=0;const depois=(OB.pag&&OB.pag.depois)||recarregarObra;
  OB.pixTimer=setInterval(async()=>{
    if(++n>200)return clearInterval(OB.pixTimer);
    try{const c=await apiJSON('/api/pagamentos/cobrancas/'+encodeURIComponent(cid));
      if(c.status!=='pendente'){clearInterval(OB.pixTimer);if(c.status==='paga'){if(document.getElementById('obPix'))closeModal();toast('Pagamento confirmado.','ok');}PG.saldo=null;depois();}}catch(e){}
  },4000);
}

/* ---------- Diário ---------- */
function blocoDiario(d,cliente,o){
  const lista=d.diario||[],aberto=lista.find(x=>!x.saidaEm),ativo=['agendado','em_andamento'].includes(o.status);
  return '<section class="obra-bloco" aria-labelledby="obraDiario"><div class="inicio-cab"><h2 class="sub" id="obraDiario">Diário da obra</h2>'+
    (!cliente&&ativo?(aberto?'<button class="btn btn-primary btn-sm" onclick="saidaObra()">'+ic('logout')+'Marcar saída</button>':'<button class="btn btn-primary btn-sm" onclick="chegadaObra()">'+ic('mapPin')+'Marcar chegada</button>'):'')+'</div>'+
    (!lista.length?'<div class="inicio-vazio">'+ic('cal')+'<p><b>Nenhum dia registrado.</b> '+(cliente?'Cada dia de trabalho aparece aqui, com a hora de chegada e saída, o que foi feito e uma foto.':'Marque a chegada ao começar e a saída ao terminar, contando o que foi feito.')+'</p></div>':
    '<ul class="obra-diario">'+lista.map(x=>'<li><div class="obra-dia"><b>'+esc(new Date(x.dia+'T12:00').toLocaleDateString('pt-BR',{weekday:'short',day:'2-digit',month:'2-digit'}))+'</b><small>'+
      new Date(x.chegadaEm).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})+' – '+(x.saidaEm?new Date(x.saidaEm).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'}):'trabalhando')+
      (x.distanciaM!=null?' · '+(x.distanciaM<=300?'no local':(x.distanciaM>=1000?(x.distanciaM/1000).toFixed(1).replace('.',',')+' km':x.distanciaM+' m')+' do endereço'):'')+'</small></div>'+
      '<div class="obra-dia-texto">'+(x.texto?'<p>'+esc(x.texto)+'</p>':'')+(x.fotoId?miniatura(urlFotoPedido(x.fotoId),'Foto do dia'):'')+'</div></li>').join('')+'</ul>')+'</section>';
}
function chegadaObra(){
  const mandar=(lat,lng)=>apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/diario/chegada','POST',lat==null?{}:{latitude:lat,longitude:lng},'Não foi possível marcar.')
    .then(()=>{toast('Chegada marcada.','ok');recarregarObra();}).catch(e=>toast(erroLegivel(e,'Não foi possível marcar.'),'err'));
  if(!navigator.geolocation)return mandar();
  navigator.geolocation.getCurrentPosition(p=>mandar(p.coords.latitude,p.coords.longitude),()=>mandar(),{enableHighAccuracy:true,timeout:10000,maximumAge:60000});
}
function saidaObra(){
  FOTOS_OBRA.lista=[];
  openModal('<div class="modal-head"><div><h3>Saída de hoje</h3><p>O cliente acompanha a obra por aqui.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="obDia">O que foi feito hoje</label><textarea class="textarea" id="obDia" maxlength="1000" placeholder="Ex.: removi o piso antigo e nivelei o contrapiso"></textarea></div>'+campoFotosObra(1)+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" onclick="enviarSaidaObra()">'+ic('check')+'Marcar saída</button></div>',{onOpen:()=>$('#obDia').focus()});
}
async function enviarSaidaObra(){
  try{await apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/diario/saida','POST',{texto:$('#obDia').value,foto:FOTOS_OBRA.lista[0]||null},'Não foi possível marcar.');closeModal();toast('Dia registrado.','ok');recarregarObra();}
  catch(e){toast(erroLegivel(e,'Não foi possível marcar.'),'err');}
}

/* ---------- Aditivos ---------- */
function blocoAditivos(d,cliente){
  const lista=d.aditivos||[],ativo=OB.pedido&&['agendado','em_andamento'].includes(OB.pedido.status);
  if(!lista.length&&(cliente||!ativo))return '';
  return '<section class="obra-bloco" aria-labelledby="obraAdit"><div class="inicio-cab"><h2 class="sub" id="obraAdit">Aditivos</h2>'+
    (!cliente&&ativo&&!lista.some(a=>a.status==='proposto')?'<button class="btn btn-ghost btn-sm" onclick="proporAditivo()">'+ic('plus')+'Pedir mais dias ou etapa nova</button>':'')+'</div>'+
    (lista.length?'<ul class="obra-linhas">'+lista.map(a=>'<li><div><b>'+(a.tipo==='etapa'?'Etapa nova: '+esc(a.nome)+' · '+money(a.valor)+' · ':'')+a.dias+(a.dias==1?' dia a mais':' dias a mais')+'</b><small>'+esc(a.motivo)+' · '+({proposto:'esperando o cliente',aceito:'aceito',recusado:'recusado'})[a.status]+'</small></div>'+
      (cliente&&a.status==='proposto'?'<div class="obra-acoes"><button class="btn btn-ghost btn-sm" onclick="responderAditivo(\''+esc(a.id)+'\',\'recusar\')">Recusar</button><button class="btn btn-primary btn-sm" onclick="responderAditivo(\''+esc(a.id)+'\',\'aceitar\')">'+ic('check')+'Aceitar</button></div>':'')+'</li>').join('')+'</ul>':
     '<p class="muted">Atraso ou trabalho que não estava no orçamento vira um aditivo que o cliente aceita ou recusa.</p>')+'</section>';
}
function proporAditivo(){
  openModal('<div class="modal-head"><div><h3>Pedir aditivo</h3><p>O cliente aceita ou recusa. Aceito, a agenda e o prazo são atualizados.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="formas" role="radiogroup" aria-label="Tipo de aditivo">'+opcaoPag('adTipo','dias','dias','Mais dias','Mesmo valor, prazo maior','clock','$(\'#adEtapa\').hidden=true;marcarForma(this)')+opcaoPag('adTipo','etapa','dias','Etapa nova','Trabalho fora do orçamento, com valor','plus','$(\'#adEtapa\').hidden=false;marcarForma(this)')+'</div>'+
    '<div id="adEtapa" hidden><div class="grid g2" style="gap:0 16px"><div class="field"><label for="adNome">Nome da etapa</label><input class="input" id="adNome" maxlength="120"></div><div class="field"><label for="adValor">Valor (R$)</label><input class="input" id="adValor" type="number" min="1" step="0.01"></div></div></div>'+
    '<div class="field"><label for="adDias">Dias a mais</label><input class="input" id="adDias" type="number" min="1" max="60" value="1"></div>'+
    '<div class="field"><label for="adMotivo">Motivo</label><textarea class="textarea" id="adMotivo" maxlength="1000" placeholder="Ex.: o contrapiso precisou secar mais dois dias"></textarea></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" onclick="enviarAditivo()">'+ic('check')+'Enviar ao cliente</button></div>');
}
async function enviarAditivo(){
  const tipo=document.querySelector('input[name="adTipo"]:checked').value;
  const corpo={tipo,dias:Number($('#adDias').value),motivo:$('#adMotivo').value,nome:$('#adNome').value,valor:tipo==='etapa'?Number($('#adValor').value):null};
  try{await apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/aditivos','POST',corpo,'Não foi possível enviar.');closeModal();toast('Enviado ao cliente.','ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível enviar.'),'err');}
}
async function responderAditivo(id,r){
  try{await apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/aditivos/'+encodeURIComponent(id)+'/'+r,'POST',null,'Não foi possível.');toast(r==='aceitar'?'Aditivo aceito.':'Aditivo recusado.','ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível.'),'err');}
}

/* ---------- Garantia e cancelamento ---------- */
function relatarDefeito(){
  openModal('<div class="modal-head"><div><h3>Relatar defeito</h3><p>A garantia fica retida até a equipe decidir. Combine o conserto pela conversa.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="obDef">O que apareceu?</label><textarea class="textarea" id="obDef" maxlength="1000" placeholder="Ex.: duas peças do piso soltaram na entrada"></textarea></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-danger" onclick="enviarDefeito()">Enviar</button></div>');
}
async function enviarDefeito(){try{await apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/defeito','POST',{texto:$('#obDef').value},'Não foi possível enviar.');closeModal();toast('Enviado à equipe.','ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível enviar.'),'err');}}
async function cancelarObra(){
  const contratada=OB.dados&&!OB.dados.aberta;
  if(!await confirmar(contratada?{titulo:'Cancelar a obra?',perigo:true,botao:'Cancelar obra',voltar:'Manter',itens:OB.dados&&OB.dados.direto?['As próximas etapas não começam.','O que já foi aprovado e pago fica com o profissional.']:['Etapa aprovada fica com o profissional.','Etapa paga e não começada volta para o cliente.','Etapa em andamento vai para a equipe decidir.']}:
    {titulo:'Cancelar este pedido de obra?',texto:'Visita técnica paga e ainda não feita é devolvida.',perigo:true,botao:'Cancelar',voltar:'Manter'}))return;
  try{await apiJSON('/api/obras/'+encodeURIComponent(OB.id)+'/cancelar','POST',{texto:''},'Não foi possível cancelar.');toast('Obra cancelada.','ok');recarregarObra();}catch(e){toast(erroLegivel(e,'Não foi possível cancelar.'),'err');}
}

/* ---------- Equipe: obras para decidir (em Problemas de pagamento) ---------- */
let obrasFila=null;
function filaObras(){
  if(!obrasFila){setTimeout(async()=>{try{obrasFila=await apiJSON('/api/admin/obras');}catch(e){obrasFila={erro:erroLegivel(e,'Não foi possível carregar.')};}render();},0);return '';}
  if(obrasFila.erro)return '<p class="pag-recusa">'+esc(obrasFila.erro)+'</p>';
  if(!obrasFila.length)return '';
  return '<section class="card"><h2 class="sub admin-ttl">Obras ('+obrasFila.length+')</h2><ul class="lista-painel fila-obras">'+obrasFila.map(o=>'<li><div class="grow"><b>'+esc(o.servico)+' · '+money(o.total)+' · '+({etapa:'Etapa para decidir',revisao:'Conferir antes da 1ª etapa',garantia:'Defeito na garantia'})[o.motivo]+'</b>'+
    '<small class="muted">'+esc(o.cliente)+' · '+esc(o.profissional||'')+' · '+esc(o.id)+'</small>'+
    (o.motivo==='revisao'&&o.orcamento?'<ol class="orc-etapas">'+o.orcamento.etapas.map((e,i)=>'<li><span class="orc-n">'+(i+1)+'</span><div><b>'+esc(e.nome)+'</b><small>'+e.dias+' dia(s)</small></div><span class="orc-v">'+money(e.valor)+'</span></li>').join('')+'</ol>'+
      '<div class="obra-acoes"><button class="btn btn-danger btn-sm" onclick="revisarObra(\''+esc(o.id)+'\',false)">Reprovar</button><button class="btn btn-primary btn-sm" onclick="revisarObra(\''+esc(o.id)+'\',true)">'+ic('check')+'Aprovar</button></div>':'')+
    (o.motivo==='etapa'?o.etapas.filter(e=>['problema','mediacao'].includes(e.status)).map(e=>'<div class="fila-etapa"><p><b>Etapa '+e.ordem+': '+esc(e.nome)+'</b> · retido '+money(Number(e.pago)-Number(e.liberado)-Number(e.devolvido))+(e.problema?' · "'+esc(e.problema)+'"':' · obra cancelada no meio')+'</p>'+
      ((e.fotos||[]).length?'<div class="fotos-grade">'+e.fotos.map(f=>miniatura(urlFotoPedido(f),'Etapa')).join('')+'</div>':'')+
      '<div class="obra-acoes"><label class="rotulo-campo" for="dec'+esc(e.id)+'">Ao profissional (R$)</label><input class="input dec-valor" id="dec'+esc(e.id)+'" type="number" min="0" step="0.01" max="'+(Number(e.pago)-Number(e.liberado)-Number(e.devolvido))+'" value="0">'+
      '<button class="btn btn-primary btn-sm" onclick="decidirEtapaObra(\''+esc(e.id)+'\')">Decidir</button></div></div>').join('')+
      (o.diario&&o.diario.length?'<details><summary>Diário ('+o.diario.length+' dias)</summary><ul class="obra-diario">'+o.diario.map(x=>'<li><b>'+esc(x.dia)+'</b> '+esc(x.texto||'')+'</li>').join('')+'</ul></details>':''):'')+
    (o.motivo==='garantia'&&o.garantia?'<p>Garantia de '+money(o.garantia.valor)+': "'+esc(o.garantia.defeito||'')+'"</p><div class="obra-acoes"><label class="rotulo-campo" for="decG'+esc(o.id)+'">Ao profissional (R$)</label><input class="input dec-valor" id="decG'+esc(o.id)+'" type="number" min="0" step="0.01" max="'+Number(o.garantia.valor)+'" value="0"><button class="btn btn-primary btn-sm" onclick="decidirGarantiaObra(\''+esc(o.id)+'\')">Decidir</button></div>':'')+
    '</div></li>').join('')+'</ul></section>';
}
async function revisarObra(id,aprovar){
  const motivo=aprovar?null:prompt('Motivo (o cliente e o profissional vão ver):');if(!aprovar&&!motivo)return;
  try{await apiJSON('/api/admin/obras/'+encodeURIComponent(id)+'/revisao','POST',{aprovar,motivo},'Não foi possível.');toast(aprovar?'Obra aprovada.':'Obra reprovada.','ok');obrasFila=null;render();}catch(e){toast(erroLegivel(e,'Não foi possível.'),'err');}
}
async function decidirEtapaObra(id){
  try{await apiJSON('/api/admin/obras/etapas/'+encodeURIComponent(id)+'/decidir','POST',{aoProfissional:Number($('#dec'+id).value)||0},'Não foi possível decidir.');toast('Decidido.','ok');obrasFila=null;render();}catch(e){toast(erroLegivel(e,'Não foi possível decidir.'),'err');}
}
async function decidirGarantiaObra(id){
  try{await apiJSON('/api/admin/obras/'+encodeURIComponent(id)+'/garantia','POST',{aoProfissional:Number($('#decG'+id).value)||0},'Não foi possível decidir.');toast('Decidido.','ok');obrasFila=null;render();}catch(e){toast(erroLegivel(e,'Não foi possível decidir.'),'err');}
}

/* ---------- CNPJ/MEI em Configurações (profissional) ---------- */
let cnpjPro=null;
function blocoCnpj(){
  if(DEMO_MODE||me().role!=='profissional')return '';
  if(cnpjPro===null){cnpjPro='';apiJSON('/api/obras/eu/cnpj').then(r=>{cnpjPro=r.cnpj||'';const i=$('#cfgCnpj');if(i)i.value=FORMATO_CHAVE.CNPJ.mascara(cnpjPro);}).catch(()=>{});}
  return '<div class="card" style="margin-top:20px"><h2 class="sub" style="font-size:1.05rem;margin-bottom:6px">CNPJ ou MEI</h2>'+
    '<p class="muted" style="font-size:.88rem;margin-bottom:12px">Com CNPJ cadastrado, você pode orçar obras a partir de R$ 5.000 mesmo sem 10 serviços concluídos.</p>'+
    '<div class="obra-acoes"><input class="input" id="cfgCnpj" inputmode="numeric" maxlength="18" placeholder="00.000.000/0000-00" value="'+esc(FORMATO_CHAVE.CNPJ.mascara(cnpjPro))+'" oninput="this.value=FORMATO_CHAVE.CNPJ.mascara(this.value)">'+
    '<button class="btn btn-ghost btn-sm" onclick="salvarCnpj()">'+ic('check')+'Salvar</button></div></div>';
}
async function salvarCnpj(){
  try{const r=await apiJSON('/api/obras/eu/cnpj','PUT',{cnpj:$('#cfgCnpj').value},'Não foi possível salvar.');cnpjPro=r.cnpj;toast(r.cnpj?'CNPJ salvo.':'CNPJ removido.','ok');}catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');}
}
