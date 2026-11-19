/* ---------- EQUIPE DE SUPORTE (administrador) ----------
   O administrador adiciona uma pessoa pelo nome e e-mail; ela recebe um convite para criar a senha
   e passa a receber os chamados de emergência. */
let equipeSuporte=null;
async function carregarEquipe(){
  try{equipeSuporte=await apiJSON('/api/admin/equipe','GET',undefined,'Não foi possível carregar a equipe.');}
  catch(error){equipeSuporte=[];toast(erroLegivel(error,'Não foi possível carregar a equipe.'),'err');}
  if(route.sub==='admin'&&route.id==='equipe')render();
}
function adminEquipe(){
  const e=equipeSuporte;
  if(e==null){carregarEquipe();return '<p class="muted">Carregando...</p>';}
  return '<div class="admin-duas equipe">'+
    '<section class="card" aria-labelledby="t-nova"><h2 class="sub admin-ttl" id="t-nova">Adicionar à equipe</h2>'+
      '<p class="muted" style="font-size:.88rem;margin:6px 0 16px">A pessoa recebe um e-mail com um link para criar a senha (vale 48 horas).</p>'+
      '<form onsubmit="adicionarSuporte(event)"><div class="field"><label for="sup-nome">Nome</label><input class="input" id="sup-nome" name="nome" required minlength="2" maxlength="120" autocomplete="off"></div>'+
      '<div class="field"><label for="sup-papel">Papel</label><select class="select" id="sup-papel" name="papel">'+
        '<option value="suporte">Suporte: atende as emergências</option><option value="moderador">Moderador: denúncias, recursos, verificações e auditoria</option><option value="financeiro">Financeiro: pagamentos, pedidos parados, números e planilhas</option></select></div>'+
      '<div class="field"><label for="sup-email">E-mail</label><input class="input" id="sup-email" name="email" type="email" required maxlength="254" autocomplete="off" placeholder="pessoa@email.com"></div>'+
      '<button class="btn btn-primary" id="btnAddSuporte">'+ic('plus')+' Adicionar e mandar convite</button></form></section>'+
    '<section class="card" aria-labelledby="t-equipe"><h2 class="sub admin-ttl" id="t-equipe">Equipe ('+e.length+')</h2>'+
      (e.length?'<ul class="lista-equipe">'+e.map(s=>'<li><div><b>'+esc(s.nome||'')+' <span class="tag tag-info">'+esc(NOME_PAPEL[s.papel]||s.papel||'Suporte')+'</span></b><small>'+esc(s.email)+'</small>'+
        '<span class="tag '+(s.convitePendente?'tag-warn':'tag-ok')+'">'+(s.convitePendente?'Convite enviado, senha ainda não criada':'Ativa')+'</span>'+
        (s.papel==='suporte'||!s.papel?'<small>'+s.chamadosAtendidos+(s.chamadosAtendidos===1?' chamado atendido':' chamados atendidos')+(s.emAtendimento?' · '+s.emAtendimento+' em atendimento agora':'')+'</small>':'')+'</div>'+
        '<div class="lista-equipe-acoes">'+(s.convitePendente?'<button class="btn btn-ghost btn-sm" onclick="reenviarConvite('+Number(s.id)+',this)">Reenviar convite</button>':'')+
        '<button class="btn btn-danger btn-sm" onclick="removerSuporte('+Number(s.id)+',\''+esc(String(s.nome||'').replace(/'/g,''))+'\')">Remover</button></div></li>').join('')+'</ul>':
      '<p class="muted">Ninguém na equipe ainda.</p>')+
    '</section></div>';
}
async function adicionarSuporte(ev){
  ev.preventDefault();const f=new FormData(ev.target),b=$('#btnAddSuporte');b.disabled=true;b.textContent='Enviando convite...';
  try{await apiJSON('/api/admin/equipe','POST',{nome:f.get('nome'),email:f.get('email'),papel:f.get('papel')},'Não foi possível adicionar.');
    toast('Convite enviado para '+f.get('email')+'.','ok');equipeSuporte=null;render();}
  catch(error){toast(erroLegivel(error,'Não foi possível adicionar.'),'err');b.disabled=false;b.innerHTML=ic('plus')+' Adicionar e mandar convite';}
}
async function reenviarConvite(id,b){
  b.disabled=true;
  try{await apiJSON('/api/admin/equipe/'+id+'/reenviar','POST',undefined,'Não foi possível reenviar.');toast('Convite reenviado.','ok');}
  catch(error){toast(erroLegivel(error,'Não foi possível reenviar.'),'err');}
  b.disabled=false;
}
function removerSuporte(id,nome){
  openModal('<div class="modal-head"><div><h3>Remover '+esc(nome)+' da equipe?</h3><p>A conta da equipe é apagada e a pessoa não entra mais. O que ela fez continua na auditoria.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div style="display:flex;gap:10px"><button class="btn btn-ghost btn-block" onclick="closeModal()">Voltar</button><button class="btn btn-danger btn-block" onclick="confirmarRemoverSuporte('+Number(id)+')">Remover da equipe</button></div>');
}
async function confirmarRemoverSuporte(id){
  try{const r=await fetch(API_BASE_URL+'/api/admin/equipe/'+id,{method:'DELETE'});if(!r.ok)throw await erroDaResposta(r,'Não foi possível remover.');
    closeModal();toast('Removido da equipe.','ok');equipeSuporte=null;render();}
  catch(error){toast(erroLegivel(error,'Não foi possível remover.'),'err');}
}
