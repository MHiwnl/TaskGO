/* ---------- CONFIGURAÇÕES ---------- */
/* Navegação entre as duas telas da conta: Perfil e Configurações. */
function abasPainel(ativa){
  return '<nav class="painel-abas" aria-label="Seções da conta" style="display:flex;gap:8px;margin-bottom:18px;flex-wrap:wrap">'+
    '<a class="pill'+(ativa==='perfil'?' pill-brand':'')+'" href="#/painel"'+(ativa==='perfil'?' aria-current="page"':'')+'>'+ic('user')+'Perfil</a>'+
    '<a class="pill'+(ativa==='config'?' pill-brand':'')+'" href="#/painel/config"'+(ativa==='config'?' aria-current="page"':'')+'>'+ic('cog')+'Configurações</a>'+
  '</nav>';
}

/* ---------- PERFIL: foto, identidade e dados pessoais ---------- */
function dashPerfil(){
  const u=me(),isPro=u.role==='profissional';
  return abasPainel('perfil')+
  dashHead('Perfil','Seus dados e o que aparece para outras pessoas.')+
  '<div class="grid g2" style="grid-template-columns:.4fr 1.6fr;align-items:start">'+
  '<div class="card"><div style="text-align:center">'+
  '<div style="position:relative;width:110px;margin:0 auto">'+avatar(u,110).replace('class="avatar"','class="avatar" style="margin:0 auto;width:110px;height:110px;font-size:40px"')+
  '<button class="icon-btn foto-trocar" aria-label="Trocar foto" onclick="document.getElementById(\'fotoIn\').click()">'+ic('camera')+'</button></div>'+
  '<input type="file" id="fotoIn" accept="image/*" class="hide" onchange="trocarFoto(this)">'+
  '<b style="display:block;margin-top:14px">'+esc(u.name)+seloPro(isPro&&souPro(),true)+'</b>'+
  '<span class="muted" style="font-size:.84rem">'+(ehEquipe(u)?(NOME_TIPO[u.role]||'Equipe'):isPro?(u.service?svc(u.service).n:'Categoria não informada'):'Cliente')+'</span>'+
  (ehEquipe(u)?'':blocoVerificacaoConta())+
  '<button class="btn btn-ghost btn-sm btn-block" style="margin-top:14px" onclick="document.getElementById(\'fotoIn\').click()">Trocar foto</button>'+
  (u.photo?'<button class="btn btn-ghost btn-sm btn-block" style="margin-top:8px" onclick="abrirEditorFoto(me().photo)">Ajustar foto</button>':'')+
  (u.photo?'<button class="btn btn-danger btn-sm btn-block" style="margin-top:8px" onclick="saveUser({photo:null});toast(\'Foto removida\');render()">Remover foto</button>':'')+
  '</div></div>'+
  '<div>'+
  '<form class="card" onsubmit="salvarPerfil(event)"><h2 class="sub" style="font-size:1.05rem;margin-bottom:18px">Dados pessoais</h2>'+
  '<div class="grid g2" style="gap:0 16px">'+
  '<div class="field"><label>Nome completo</label><input class="input" name="name" value="'+esc(u.name)+'" required></div>'+
  '<div class="field"><label>CPF</label><input class="input" name="cpf" value="'+esc(maskCPF(u.cpf||''))+'" oninput="this.value=maskCPF(this.value)" required></div>'+
  '<div class="field"><label for="cfgEmail">E-mail</label><input class="input" type="email" id="cfgEmail" value="'+esc(u.email)+'" readonly aria-describedby="cfgEmailDica"><div class="hint" id="cfgEmailDica">É o seu login. Para trocar, fale com o suporte.</div></div>'+
  '<div class="field"><label>Telefone / WhatsApp</label><input class="input" name="phone" value="'+esc(u.phone)+'" oninput="this.value=maskPhone(this.value)" required></div>'+
  '<div class="field"><label>Cidade</label><input class="input" name="city" value="'+esc(u.city)+'"></div>'+
  (ehEquipe(u)||isPro?'':
   (u.verified&&u.birth?
    '<div class="field"><label for="cfgNasc">Data de nascimento</label><input class="input" type="date" id="cfgNasc" name="birth" value="'+esc(u.birth)+'" readonly aria-describedby="cfgNascDica"><div class="hint" id="cfgNascDica">Conferida no seu documento. Para corrigir, fale com o suporte.</div></div>':
    '<div class="field"><label>Data de nascimento</label><input class="input" type="date" name="birth" value="'+esc(u.birth||'')+'"></div>'))+
  '</div>'+
  (isPro?'<div class="field"><label>Sobre você (aparece no seu perfil público)</label><textarea class="textarea" name="bio">'+esc(u.bio)+'</textarea></div>':'')+
  '<button class="btn btn-grad">'+ic('check')+' Salvar alterações</button></form>'+
  (ehCliente(u)?secaoEnderecos():'')+
  blocoCategoriasPro()+
  '</div></div>';
}

/* ---------- CONFIGURAÇÕES: segurança, avisos, dados e conta ---------- */
function dashConfig(){
  const u=me();
  return abasPainel('config')+
  dashHead('Configurações','Segurança, avisos e o controle da sua conta.')+
  '<div class="card"><h2 class="sub" style="font-size:1.05rem;margin-bottom:6px">Segurança</h2>'+
  '<p class="muted" style="font-size:.88rem;margin-bottom:8px">Proteja o acesso à sua conta.</p>'+
  linhaDoisFatores()+
  '<div class="check-row"><div><b style="font-size:.92rem;display:block">Senha</b><small class="muted">Use uma senha que você não repete em outros sites.</small></div>'+
  '<button class="btn btn-ghost btn-sm" onclick="trocarSenha()">Alterar senha</button></div>'+
  blocoSessoes()+'</div>'+
  blocoCnpj()+
  secaoAvisos()+
  (ehEquipe(u)?'':'<a class="card" href="#/app/conta-situacao" style="margin-top:20px;display:flex;align-items:center;justify-content:space-between;gap:12px;text-decoration:none">'+
    '<span><b style="font-size:.95rem;display:block">Situação da conta</b><small class="muted">Avisos, suspensões e recursos — com o motivo de cada um.</small></span>'+ic('arrow')+'</a>')+
  '<div class="card" style="margin-top:20px"><h2 class="sub" style="font-size:1.05rem;margin-bottom:6px">Seus dados</h2>'+
  '<p class="t2" style="font-size:.88rem;margin-bottom:14px">Baixe um arquivo com tudo o que a TaskGO guarda sobre você: cadastro, pedidos, propostas, mensagens, notificações e histórico da conta. Veja os prazos de guarda na <a class="lp-auth-link" href="#/legal/privacidade">Política de Privacidade</a>.</p>'+
  '<button class="btn btn-ghost" onclick="baixarMeusDados(this)">'+ic('download')+' Baixar meus dados</button></div>'+
  (ehEquipe(u)?'':'<div class="card" style="margin-top:20px;border-top-color:var(--danger)">'+
  '<h2 class="sub" style="font-size:1.05rem;margin-bottom:6px;color:var(--danger-text)">Zona de risco</h2>'+
  '<p class="muted" style="font-size:.88rem;margin-bottom:14px">Ações irreversíveis. Tenha certeza antes de continuar.</p>'+
  '<div style="display:flex;gap:10px;flex-wrap:wrap">'+
  (DEMO_MODE?'<button class="btn btn-ghost" onclick="Store.reset();location.reload()">Restaurar dados demo</button>':'')+
  '<button class="btn btn-danger" onclick="excluirConta()">'+ic('trash')+' Excluir minha conta</button></div></div>');
}

async function salvarPerfil(e){
  e.preventDefault();
  const f=new FormData(e.target),patch={};
  ['name','cpf','phone','city','address','bio','service','birth'].forEach(k=>{if(f.get(k)!==null)patch[k]=f.get(k);});
  try{await saveUser(patch);toast('Perfil atualizado com sucesso!','ok');render();}catch(error){toast(error.message||'Não foi possível salvar o perfil.','err');}
}
function trocarFoto(input){
  const file=input.files&&input.files[0];input.value='';if(!file)return;
  if(!/^image\//.test(file.type))return toast('Escolha um arquivo de imagem.','err');
  const rd=new FileReader();
  rd.onload=()=>abrirEditorFoto(rd.result);
  rd.readAsDataURL(file);
}
async function toggleNotif(k){const u=me(),next={...(u.notif||{}),[k]:!u.notif?.[k]};try{await saveUser({notif:next});render();}catch(error){toast(error.message||'Não foi possível salvar a preferência.','err');}}
function trocarSenha(){
  openModal('<div class="modal-head"><div><h3>Alterar senha</h3><p>Use 12 caracteres ou mais, misturando letras, números ou símbolos.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
  '<div class="field"><label for="pwOld">Senha atual</label><input class="input" type="password" id="pwOld" autocomplete="current-password"></div>'+
  '<div class="field"><label for="pwNew">Nova senha</label><input class="input" type="password" id="pwNew" autocomplete="new-password" oninput="conferirSenhaCampo(this,me().email,me().name)">'+blocoRegrasSenha('pwNew')+'</div>'+
  '<div class="field"><label for="pwNew2">Confirmar nova senha</label><input class="input" type="password" id="pwNew2" autocomplete="new-password"></div>'+
  '<button class="btn btn-grad btn-block" onclick="salvarSenha()">Salvar nova senha</button>');
}
async function salvarSenha(){
  const u=me(),o=$('#pwOld').value,n=$('#pwNew').value,n2=$('#pwNew2').value;
  const falta=faltaNaSenha(n,u.email,u.name);if(falta)return toast(falta,'err');
  if(n!==n2)return toast('As senhas não coincidem','err');
  try{const resposta=await fetch(API_BASE_URL+'/api/auth/senha',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({senhaAtual:o,novaSenha:n})});if(!resposta.ok)throw (resposta.status===401?new Error('Senha atual incorreta'):await erroDaResposta(resposta,'Não foi possível alterar a senha.'));closeModal();toast('Senha alterada com sucesso','ok');}
  catch(erro){toast(erro.message||'Não foi possível alterar a senha.','err');}
}
function excluirConta(){
  openModal('<div class="modal-head"><div><h3 style="color:var(--danger-text)">Excluir conta</h3><p>Todos os seus dados, pedidos e saldo serão perdidos.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
  '<div class="field"><label>Digite <b>EXCLUIR</b> para confirmar</label><input class="input" id="delConf" placeholder="EXCLUIR"></div>'+
  '<div class="field"><label>Senha atual</label><input class="input" id="delPassword" type="password" autocomplete="current-password"></div>'+ 
  '<button class="btn btn-danger btn-block" id="btnConfirmarExclusao" onclick="confirmarExclusao()">Excluir permanentemente</button>');
}
async function confirmarExclusao(){
  if(($('#delConf')||{}).value!=='EXCLUIR')return toast('Digite EXCLUIR para confirmar','err');
  const usuario=me();
  if(!usuario)return toast('Não foi possível identificar a conta atual.','err');
  const botao=$('#btnConfirmarExclusao');
  if(botao)botao.disabled=true;
  try{
    const resposta=await fetch(API_BASE_URL+'/api/auth/conta',{
      method:'DELETE',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({senha:$('#delPassword').value})
    });
    if(!resposta.ok){
      let erro={};
      try{erro=await resposta.json();}catch(_){/* resposta sem JSON */}
      throw new Error(erro.detail||erro.message||'Não foi possível excluir a conta.');
    }

    const pedidosExcluidos=new Set((S.orders||[])
      .filter(p=>p.clientId===usuario.id||(usuario.proId&&p.proId===usuario.proId))
      .map(p=>p.id));
    S.users=S.users.filter(u=>u.id!==usuario.id);
    S.orders=(S.orders||[]).filter(p=>!pedidosExcluidos.has(p.id));
    S.chats=(S.chats||[]).filter(c=>!pedidosExcluidos.has(c.orderId));
    S.tx=(S.tx||[]).filter(t=>t.ownerId!==usuario.id);
    S.notifs=(S.notifs||[]).filter(n=>n.userId!==usuario.id);
    if(usuario.proId)S.pros=S.pros.filter(p=>p.id!==usuario.proId);
    S.session=null;Store.save();closeModal();
    toast('Conta excluída.');go('/home');
  }catch(erro){
    if(botao)botao.disabled=false;
    toast(erro instanceof TypeError
      ?'Sem conexão com o servidor. A conta não foi excluída.'
      :(erro.message||'Não foi possível excluir a conta.'),'err');
  }
}


/* Baixa o arquivo com os dados da conta (LGPD). */
async function baixarMeusDados(botao){
  if(DEMO_MODE)return toast('Na demonstração não há dados reais para baixar.');
  botao.disabled=true;
  try{const r=await fetch(API_BASE_URL+'/api/conta/meus-dados');if(!r.ok)throw await erroDaResposta(r,'Não foi possível gerar o arquivo.');
    const url=URL.createObjectURL(new Blob([JSON.stringify(await r.json(),null,2)],{type:'application/json'}));
    const a=document.createElement('a');a.href=url;a.download='taskgo-meus-dados.json';document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
    toast('Arquivo baixado.','ok');}
  catch(error){toast(erroLegivel(error,'Não foi possível gerar o arquivo.'),'err');}
  finally{botao.disabled=false;}
}
