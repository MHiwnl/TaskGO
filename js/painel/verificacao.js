/* =========================================================
   VERIFICAÇÃO DE IDENTIDADE
   Conta: envia documento (frente e, se tiver, verso) e uma selfie. Administrador compara e decide.
   Aprovada: selo "Verificado". As imagens são apagadas assim que a decisão é tomada.
   ========================================================= */
let minhaVerificacao=null,verifEnvio={frente:null,verso:null,selfie:null},verifAdmin=null;

function seloVerificado(sim,pequeno){return sim?'<span class="selo-verificado'+(pequeno?' pequeno':'')+'" title="Identidade verificada pela TaskGO">'+ic('checkCircle')+'<span>Verificado</span></span>':'';}

async function carregarMinhaVerificacao(){
  if(DEMO_MODE)return;
  const antes=me()&&me().verified;
  try{minhaVerificacao=await apiJSON('/api/verificacao');if(me()){me().verified=!!minhaVerificacao.verificado;Store.save();}}catch(e){minhaVerificacao={status:'erro'};}
  const c=$('#seloConta');if(c)c.outerHTML=blocoVerificacaoConta();
  if(document.getElementById('verifServico')||(me()&&me().verified!==antes))render();
}

/* ---------- Exigida para usar os serviços ----------
   Cliente: para fazer pedidos. Profissional: para receber oportunidades. O resto da conta funciona sem ela. */
function precisaVerificacao(u){u=u||me();return !DEMO_MODE&&!!u&&(u.role==='cliente'||u.role==='profissional')&&!u.verified;}
let verifRecarga=null;
function painelVerificacaoServico(contexto){
  const v=minhaVerificacao,pedir=contexto==='pedir';
  if(!v||v.status==='erro'){if(!v)setTimeout(carregarMinhaVerificacao,0);
    return '<div class="card verif-servico" id="verifServico">'+(v?'<p class="pag-recusa" role="alert">'+ic('x')+'Não foi possível carregar a sua verificação.</p><button class="btn btn-ghost" onclick="minhaVerificacao=null;render()">'+ic('refresh')+'Tentar de novo</button>':'<p class="muted" role="status">Carregando...</p>')+'</div>';}
  const para=pedir?'fazer pedidos':'receber oportunidades';
  let h;
  if(v.status==='pendente'){
    clearTimeout(verifRecarga);verifRecarga=setTimeout(()=>{if(document.getElementById('verifServico'))carregarMinhaVerificacao();},30000);
    h='<div class="verif-servico-cab">'+ic('clock')+'<h2 class="sub">Verificação em análise</h2></div>'+
      '<p class="t2">A equipe está conferindo seu documento e sua selfie. Quando aprovar, você já pode '+para+'. Você recebe um aviso.</p>'+
      '<button class="btn btn-ghost" onclick="carregarMinhaVerificacao()">'+ic('refresh')+'Atualizar</button>';
  }else if(v.status==='recusada'){
    h='<div class="verif-servico-cab recusada">'+ic('x')+'<h2 class="sub">Verificação recusada</h2></div>'+
      '<dl class="verif-recusa"><div><dt>Motivo</dt><dd>'+esc(v.motivo||'Não informado.')+'</dd></div><div><dt>Como corrigir</dt><dd>'+esc(v.correcao||'Envie fotos nítidas do documento e uma selfie de frente.')+'</dd></div></dl>'+
      '<button class="btn btn-primary" onclick="abrirVerificacao()">'+ic('shield')+'Enviar de novo</button>';
  }else{
    h='<div class="verif-servico-cab">'+ic('shield')+'<h2 class="sub">Verifique sua identidade para '+para+'</h2></div>'+
      '<ol class="verif-passos">'+[['camera','Foto do documento','RG ou CNH, com a foto e o nome legíveis.'],['user','Selfie','Seu rosto de frente, com boa luz.'],['checkCircle','Análise da equipe','Comparamos os dois e apagamos as imagens depois.']]
        .map(p=>'<li>'+ic(p[0])+'<div><b>'+p[1]+'</b><small>'+p[2]+'</small></div></li>').join('')+'</ol>'+
      '<button class="btn btn-primary" onclick="abrirVerificacao()">'+ic('shield')+'Verificar identidade</button>';
  }
  return '<div class="card verif-servico" id="verifServico">'+h+'</div>';
}
/* Etiqueta e botão no cartão da foto, em Configurações. */
function blocoVerificacaoConta(){
  const u=me();
  if(DEMO_MODE||ehAdministrador(u)||ehSuporte(u))return '<div id="seloConta" style="margin-top:10px"><span class="tag tag-info">'+ic('shield')+' Conta ativa</span></div>';
  if(!minhaVerificacao){setTimeout(carregarMinhaVerificacao,0);return '<div id="seloConta" style="margin-top:10px"><span class="tag tag-muted">Verificação...</span></div>';}
  const v=minhaVerificacao;
  const corpo=v.verificado?seloVerificado(true):
    v.status==='pendente'?'<span class="tag tag-warn">'+ic('clock')+' Verificação em análise</span>':
    '<span class="tag tag-muted">Identidade não verificada</span>'+(v.status==='recusada'&&v.motivo?'<p class="hint" style="margin-top:8px">Recusada: '+esc(v.motivo)+(v.correcao?'. Como corrigir: '+esc(v.correcao):'')+'</p>':'')+
    '<button class="btn btn-primary btn-sm btn-block" style="margin-top:10px" onclick="abrirVerificacao()">'+ic('shield')+(v.status==='recusada'?'Enviar de novo':'Verificar identidade')+'</button>';
  return '<div id="seloConta" style="margin-top:10px">'+corpo+'</div>';
}
function abrirVerificacao(){
  verifEnvio={frente:null,verso:null,selfie:null};
  openModal(htmlVerificacao(),{wide:true});
}
function htmlVerificacao(){
  const campo=(k,rot,dica,capture)=>'<div class="verif-campo"><b>'+rot+'</b><small class="muted">'+dica+'</small>'+
    (verifEnvio[k]?'<div class="foto-item">'+miniatura(verifEnvio[k],rot)+'<button type="button" class="foto-apagar" aria-label="Trocar" onclick="verifEnvio.'+k+'=null;$(\'.modal\').innerHTML=htmlVerificacao()">'+ic('x')+'</button></div>':
     '<label class="foto-add">'+ic('camera')+'<span>Escolher</span><input type="file" accept="image/*"'+(capture?' capture="'+capture+'"':'')+' class="leitor" onchange="lerFotoVerificacao(\''+k+'\',this)"></label>')+'</div>';
  return '<div class="modal-head"><div><h3>Verificar identidade</h3><p>A equipe compara o documento com a selfie. As imagens são apagadas depois da análise.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="verif-grade">'+
      campo('frente','Documento: frente','RG ou CNH, com a foto e o nome legíveis.','environment')+
      campo('verso','Documento: verso','Opcional. Mande se o CPF estiver no verso.','environment')+
      campo('selfie','Selfie','Seu rosto de frente, sem óculos escuros nem boné.','user')+
    '</div>'+
    '<p id="verifErro" class="pag-recusa" role="alert" hidden></p>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Cancelar</button><button class="btn btn-primary" id="verifEnviar" onclick="enviarVerificacao()"'+(verifEnvio.frente&&verifEnvio.selfie?'':' disabled')+'>'+ic('shield')+'Enviar para análise</button></div>';
}
async function lerFotoVerificacao(k,input){
  const f=input.files&&input.files[0];input.value='';if(!f)return;
  try{verifEnvio[k]=await comprimirFoto(f);$('.modal').innerHTML=htmlVerificacao();}catch(e){toast(e.message,'err');}
}
async function enviarVerificacao(){
  const b=$('#verifEnviar'),erro=$('#verifErro');if(b)b.disabled=true;
  try{
    minhaVerificacao=await apiJSON('/api/verificacao','POST',verifEnvio,'Não foi possível enviar.');
    closeModal();toast('Enviado. A equipe vai analisar.','ok');
    const c=$('#seloConta');if(c)c.outerHTML=blocoVerificacaoConta();
    if(document.getElementById('verifServico'))render();
  }catch(e){erro.textContent=erroLegivel(e,'Não foi possível enviar.');erro.hidden=false;if(b)b.disabled=false;}
}

/* ---------- Administração > Verificações ---------- */
async function carregarVerificacoesAdmin(){
  try{verifAdmin=await apiJSON('/api/admin/verificacoes');}catch(e){verifAdmin={erro:erroLegivel(e,'Não foi possível carregar.')};}
  if(route.sub==='admin')render();
}
function adminVerificacoes(){
  if(!verifAdmin){setTimeout(carregarVerificacoesAdmin,0);return '<div class="card center" role="status">Carregando...</div>';}
  if(verifAdmin.erro)return '<div class="card emptystate" role="alert">'+esc(verifAdmin.erro)+'</div>';
  if(!verifAdmin.length)return '<div class="card emptystate">'+ic('shield')+'<b>Nenhuma verificação esperando análise</b></div>';
  const img=(id,parte)=>API_BASE_URL+'/api/admin/verificacoes/'+encodeURIComponent(id)+'/'+parte;
  return verifAdmin.map(v=>'<section class="card verif-admin" aria-label="Verificação de '+esc(v.nome)+'">'+
    '<div class="verif-admin-cab"><div><b>'+esc(v.nome)+'</b><small class="muted">'+esc(v.email||'')+' · '+(v.tipo==='profissional'?'Profissional':'Cliente')+(v.cpf?' · CPF '+esc(v.cpf):'')+(v.nascimento?' · nasc. '+esc(v.nascimento):'')+'</small></div>'+
    '<small class="muted">Enviada '+dataHora(v.enviadaEm)+'</small></div>'+
    '<div class="verif-fotos">'+miniatura(img(v.id,'frente'),'Documento, frente')+(v.temVerso?miniatura(img(v.id,'verso'),'Documento, verso'):'')+miniatura(img(v.id,'selfie'),'Selfie')+'</div>'+
    '<div class="pag-acoes"><button class="btn btn-ghost" onclick="recusarVerificacao(\''+esc(v.id)+'\')">Recusar</button><button class="btn btn-primary" onclick="aprovarVerificacao(\''+esc(v.id)+'\')">'+ic('check')+'Aprovar</button></div></section>').join('');
}
/* A data vem do documento, nunca do perfil (que a própria pessoa preenche): o campo abre vazio. */
function aprovarVerificacao(id){
  openModal('<div class="modal-head"><div><h3>Aprovar verificação</h3><p>Digite a data de nascimento que está no documento.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="vNasc">Data de nascimento</label><input class="input" id="vNasc" type="date" required max="'+new Date().toISOString().slice(0,10)+'"></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" onclick="confirmarAprovacaoVerificacao(\''+esc(id)+'\')">'+ic('check')+'Aprovar</button></div>');
}
function confirmarAprovacaoVerificacao(id){
  const n=$('#vNasc').value;
  if(!n)return toast('Digite a data de nascimento do documento.','err');
  decidirVerificacao(id,'aprovar',null,null,n);
}
/* Motivos prontos, cada um com a correção que a pessoa vê. "Outro" deixa escrever os dois. */
const MOTIVOS_RECUSA=[
  ['Documento ilegível','Fotografe o documento em um lugar claro, sem reflexo, com as letras e a foto nítidas.'],
  ['Documento cortado ou incompleto','Mostre o documento inteiro, com as quatro pontas aparecendo. Se o CPF estiver no verso, mande o verso também.'],
  ['A selfie não confere com a foto do documento','Tire a selfie de frente, sem óculos escuros, boné ou máscara, com o rosto inteiro aparecendo.'],
  ['Documento vencido','Envie um RG ou uma CNH dentro da validade.'],
  ['Nome ou CPF diferentes do cadastro','Confira o nome completo e o CPF em Configurações: eles precisam ser iguais aos do documento. Depois, envie de novo.'],
  ['Outro','']];
function recusarVerificacao(id){
  openModal('<div class="modal-head"><div><h3>Recusar verificação</h3><p>A pessoa vê o motivo e como corrigir, e pode mandar de novo.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="vMotivoSel">Motivo</label><select class="select" id="vMotivoSel" onchange="trocarMotivoRecusa(this.value)">'+MOTIVOS_RECUSA.map((m,i)=>'<option value="'+i+'">'+esc(m[0])+'</option>').join('')+'</select></div>'+
    '<div class="field" id="vMotivoOutro" hidden><label for="vMotivo">Escreva o motivo</label><input class="input" id="vMotivo" maxlength="300"></div>'+
    '<div class="field"><label for="vCorrecao">Como corrigir</label><textarea class="textarea" id="vCorrecao" maxlength="500">'+esc(MOTIVOS_RECUSA[0][1])+'</textarea></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-danger" onclick="confirmarRecusaVerificacao(\''+esc(id)+'\')">Recusar</button></div>');
}
function trocarMotivoRecusa(i){
  const m=MOTIVOS_RECUSA[Number(i)];const outro=m[0]==='Outro';
  $('#vMotivoOutro').hidden=!outro;$('#vCorrecao').value=m[1];if(outro)$('#vMotivo').focus();
}
function confirmarRecusaVerificacao(id){
  const m=MOTIVOS_RECUSA[Number($('#vMotivoSel').value)];
  const motivo=m[0]==='Outro'?$('#vMotivo').value.trim():m[0],correcao=$('#vCorrecao').value.trim();
  if(motivo.length<5)return toast('Escreva o motivo da recusa.','err');
  if(correcao.length<5)return toast('Diga como a pessoa corrige.','err');
  decidirVerificacao(id,'recusar',motivo,correcao);
}
async function decidirVerificacao(id,decisao,motivo,correcao,nascimento){
  try{const r=await apiJSON('/api/admin/verificacoes/'+encodeURIComponent(id)+'/'+decisao,'POST',{motivo:motivo||null,correcao:correcao||null,nascimento:nascimento||null},'Não foi possível decidir.');closeModal();
    toast(r&&r.menorDeIdade?'Menor de 18 anos: verificação recusada e conta banida':decisao==='aprovar'?'Verificação aprovada':'Verificação recusada',r&&r.menorDeIdade?'err':'ok');verifAdmin=null;render();}
  catch(e){toast(erroLegivel(e,'Não foi possível decidir.'),'err');}
}
