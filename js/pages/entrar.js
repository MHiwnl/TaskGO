/* =========================================================
   AUTENTICAÇÃO
   ========================================================= */
let A={mode:'login',role:'cliente'};
/* Para onde ir depois de entrar: o pedido de volta (?next=) vence; senão, o Início da área de uso. */
function destinoAposEntrar(){
  const u=me();
  /* O administrador entra direto na administração, sem passar pelo perfil. */
  if(u&&u.role==='administrador')return '/app/admin/visao-geral';
  if(u&&u.role==='suporte')return '/app/suporte';
  /* Todo mundo cai no Início: as abas e as bolinhas de pedidos novos ficam à vista.
     Completar o perfil está na lista "Sua conta" do próprio Início. */
  const proximo=String(route.q.next||'').replace(/[^a-z0-9\/-]/gi,'');
  if(proximo&&proximo!=='contratar')return '/'+proximo;
  return '/app/inicio';
}
function pageEntrar(){
  if(route.q.tipo&&!A._init){A.role=route.q.tipo;A.mode='signup';A._init=1;}
  if(me()){go(destinoAposEntrar());return '';}
  const isLogin=A.mode==='login';
  return '<div class="auth-wrap">'+
  '<div class="auth-side">'+
  '<p class="lp-auth-slogan">'+(isLogin?'Entre e continue de onde parou na TaskGO.':(A.role==='profissional'?'Crie sua conta e comece a receber pedidos da sua região.':'Crie sua conta e contrate quem resolve, perto de você.'))+'</p>'+
  '<h2 class="bloco-titulo auth-hoje-titulo">com a sua conta</h2><ul class="auth-hoje">'+[
    ['search','Contrate entre mais de 100 serviços, para agora ou para outro dia'],
    ['shield','Profissionais com identidade verificada e avaliações reais'],
    ['mapPin','Acompanhe a chegada no mapa e pague só depois do serviço'],
    ['wrench','É profissional? Receba pedidos da sua região, sem mensalidade obrigatória']
  ].map(t=>'<li>'+ic(t[0])+'<span>'+t[1]+'</span></li>').join('')+'</ul>'+
  '<p class="auth-hoje-nota">Quer saber mais? <a href="#/como-funciona">Veja como funciona</a>.</p>'+
  '</div>'+
  '<div class="auth-main"><div class="auth-card">'+
  '<h1 class="lp-auth-title">'+(isLogin?'Entrar na sua conta':'Criar sua conta')+'</h1>'+
  /* Modo demonstração: as contas fictícias existem só no navegador, então entram por aqui, sem senha. */
  (DEMO_MODE?'<div class="demo-contas"><p class="aviso-linha" role="note"><b>Modo demonstração.</b> Entre numa conta fictícia, sem senha:</p>'+
    '<a class="btn btn-primary btn-block" href="?demo=1&role=cliente#/app/inicio">'+ic('user')+'Entrar como cliente (Ana)</a>'+
    '<a class="btn btn-ghost btn-block" href="?demo=1&role=profissional#/app/inicio">'+ic('wrench')+'Entrar como profissional (Carlos)</a></div>':'')+
  (/contratar/.test(route.q.next||'')?'<p class="aviso-linha" role="status">Entre na sua conta para contratar um serviço.</p>':'')+
  '<p class="muted lp-auth-sub">'+(isLogin?'Acesse seu painel TaskGO.':'Leva menos de 1 minuto e é grátis.')+'</p>'+
  '<div class="tabs"><button data-fk="aba-entrar" aria-pressed="'+isLogin+'" class="'+(isLogin?'active':'')+'" onclick="A.mode=\'login\';render()">Entrar</button>'+
  '<button data-fk="aba-criar" aria-pressed="'+!isLogin+'" class="'+(!isLogin?'active':'')+'" onclick="A.mode=\'signup\';render()">Criar conta</button></div>'+

  (!isLogin?blocoGoogle():'')+
  '<form onsubmit="doAuth(event)">'+
  (!isLogin?'<div class="role-pick" role="group" aria-label="Como você quer usar a TaskGO">'+
    '<button type="button" data-fk="papel-cliente" aria-pressed="'+(A.role==='cliente')+'" class="role-opt '+(A.role==='cliente'?'sel':'')+'" onclick="A.role=\'cliente\';render()">'+ic('user')+'<b>Quero contratar</b><span>Sou cliente</span></button>'+
    '<button type="button" data-fk="papel-profissional" aria-pressed="'+(A.role==='profissional')+'" class="role-opt '+(A.role==='profissional'?'sel':'')+'" onclick="A.role=\'profissional\';render()">'+ic('wrench')+'<b>Quero trabalhar</b><span>Sou profissional</span></button>'+
  '</div>'+
  '<div class="field"><label for="auth-name">Nome completo</label><input class="input" id="auth-name" name="name" autocomplete="name" required placeholder="Como no seu documento"></div>'+
  '<div class="field"><label for="auth-cpf">CPF</label><input class="input" id="auth-cpf" name="cpf" inputmode="numeric" required placeholder="000.000.000-00" oninput="this.value=maskCPF(this.value)" aria-describedby="auth-cpf-dica"><small class="hint" id="auth-cpf-dica">Usado na verificação de identidade.</small></div>'+
  '<div class="field"><label for="auth-phone">Telefone / WhatsApp</label><input class="input" id="auth-phone" name="phone" type="tel" autocomplete="tel" required placeholder="(81) 90000-0000" oninput="this.value=maskPhone(this.value)"></div>'+
  (A.role==='profissional'?seletorServico({id:'auth-service',nome:'service',rotulo:'Categoria principal',dica:'Depois, em Configurações, você marca as outras categorias que sabe fazer.'}):'')
  :'')+
  '<div class="field"><label for="auth-email">E-mail</label><input class="input" id="auth-email" type="email" name="email" autocomplete="email" required placeholder="voce@email.com"></div>'+
  '<div class="field"><label for="auth-pass">Senha</label><div class="password-field"><input class="input" id="auth-pass" type="password" name="pass" required'+(isLogin?' autocomplete="current-password"':' minlength="12" maxlength="128" autocomplete="new-password" placeholder="12 caracteres ou mais" oninput="conferirSenhaCampo(this,(document.getElementById(\'auth-email\')||{}).value,(document.getElementById(\'auth-name\')||{}).value)"')+'><button class="password-toggle" type="button" aria-label="Mostrar senha" aria-pressed="false" onclick="alternarVisibilidadeSenha(this)">'+ic('eye')+'</button></div>'+(isLogin?'':blocoRegrasSenha('auth-pass'))+'</div>'+
  (isLogin?'<div class="lp-auth-row"><label class="muted lp-auth-check"><input type="checkbox" checked> Lembrar de mim</label><a href="#/recuperar" class="lp-auth-link">Esqueci a senha</a></div>'
  :'<label class="muted lp-auth-check lp-auth-terms"><input type="checkbox" required> Li e aceito os <a href="#/legal/termos" class="lp-auth-link">Termos de Uso</a> e a <a href="#/legal/privacidade" class="lp-auth-link">Política de Privacidade</a>.</label>')+
  '<button class="btn btn-grad btn-block btn-lg">'+(isLogin?'Entrar':'Criar conta')+'</button></form>'+
  (isLogin?blocoGoogle():'')+
  '</div></div></div>';
}
function alternarVisibilidadeSenha(botao){
  const campo=botao.closest('.password-field')?.querySelector('input[name="pass"]');
  if(!campo)return;
  const mostrar=campo.type==='password';
  campo.type=mostrar?'text':'password';
  botao.setAttribute('aria-pressed',String(mostrar));
  botao.setAttribute('aria-label',mostrar?'Ocultar senha':'Mostrar senha');
  botao.innerHTML=ic(mostrar?'eyeOff':'eye');
}
async function obterCabecalhosCsrf(){
  const response=await fetch(API_BASE_URL+'/api/auth/csrf',{credentials:'include'});
  if(!response.ok)throw new Error('Não foi possível iniciar uma solicitação segura.');
  const cookie=document.cookie.split('; ').find(item=>item.startsWith('XSRF-TOKEN='));
  if(!cookie)throw new Error('Não foi possível iniciar uma solicitação segura.');
  return {'Content-Type':'application/json','X-XSRF-TOKEN':decodeURIComponent(cookie.slice('XSRF-TOKEN='.length))};
}
/* Depois de pedir o link, a tela troca o formulário por um passo a passo claro do que fazer. */
let recuperacaoEnviada='',recuperacaoEspera=0,recuperacaoTimer=null;
function telaRecuperacaoEnviada(){
  const falta=Math.max(0,Math.ceil((recuperacaoEspera-Date.now())/1000));
  return '<div class="auth-wrap"><div class="auth-side"><p class="lp-auth-slogan">Recupere o acesso à sua conta</p><p class="muted">Os links são individuais, temporários e podem ser usados uma única vez.</p></div><div class="auth-main"><div class="auth-card recuperacao-ok" role="status" aria-live="polite">'+
    '<span class="recuperacao-ico" aria-hidden="true">'+ic('mail')+'</span>'+
    '<h1 class="lp-auth-title">Confira seu e-mail</h1>'+
    '<p class="lp-auth-sub">Se existir uma conta com <b class="recuperacao-email">'+esc(recuperacaoEnviada)+'</b>, o link para criar uma senha nova acabou de ser enviado.</p>'+
    '<ol class="recuperacao-passos">'+
      '<li><b>Abra o e-mail "Redefinição de senha TaskGO"</b><span>Se não estiver na caixa de entrada, olhe em Spam ou em Promoções.</span></li>'+
      '<li><b>Toque no link do e-mail</b><span>Ele vale por 30 minutos e funciona uma vez só.</span></li>'+
      '<li><b>Crie a senha nova</b><span>Depois, entre na TaskGO com ela.</span></li>'+
    '</ol>'+
    '<a href="#/entrar" class="btn btn-grad btn-block">Voltar para entrar</a>'+
    '<div class="recuperacao-de-novo"><button type="button" class="btn btn-ghost btn-sm" '+(falta?'disabled':'')+' onclick="reenviarRecuperacao()">'+(falta?'Enviar de novo em '+falta+' s':'Não chegou? Enviar de novo')+'</button>'+
    '<button type="button" class="btn btn-ghost btn-sm" onclick="recuperacaoEnviada=\'\';render()">Usar outro e-mail</button></div>'+
    '<p id="recovery-status" role="status" aria-live="polite" class="lp-auth-status"></p></div></div></div>';
}
function contarEsperaRecuperacao(){
  clearInterval(recuperacaoTimer);
  recuperacaoTimer=setInterval(()=>{if(route.page!=='recuperar'||Date.now()>=recuperacaoEspera)clearInterval(recuperacaoTimer);if(route.page==='recuperar')render();},1000);
}
async function pedirLinkRecuperacao(email){
  const headers=await obterCabecalhosCsrf();
  const response=await fetch(API_BASE_URL+'/api/auth/recuperacao',{method:'POST',credentials:'include',headers,body:JSON.stringify({email})});
  if(response.status===429)throw new Error('Muitos pedidos seguidos. Espere alguns minutos e tente de novo.');
  if(!response.ok)throw new Error('Confira o e-mail digitado e tente de novo.');
}
async function reenviarRecuperacao(){
  try{await pedirLinkRecuperacao(recuperacaoEnviada);recuperacaoEspera=Date.now()+60000;toast('Enviamos o link de novo.','ok');render();contarEsperaRecuperacao();}
  catch(error){const st=$('#recovery-status');if(st){st.textContent=error.message;st.className='lp-auth-status recuperacao-erro';st.setAttribute('role','alert');}}
}
function pageRecuperar(){
  const token=route.q.token||'';
  if(!token&&recuperacaoEnviada)return telaRecuperacaoEnviada();
  return '<div class="auth-wrap"><div class="auth-side"><p class="lp-auth-slogan">Recupere o acesso à sua conta</p><p class="muted">Os links são individuais, temporários e podem ser usados uma única vez.</p></div><div class="auth-main"><div class="auth-card"><h1 class="lp-auth-title">'+(token?(route.q.convite?'Bem-vindo à equipe':'Criar nova senha'):'Esqueceu sua senha?')+'</h1><p class="muted lp-auth-sub">'+(token?(route.q.convite?'Crie a senha da sua conta de suporte. Depois é só entrar com o seu e-mail e ela.':'Escolha uma nova senha para sua conta.'):'Digite o e-mail da sua conta. Vamos mandar um link para você criar uma senha nova.')+'</p>'+
  (token?'<form onsubmit="redefinirSenha(event)"><div class="field"><label for="nova-senha">Nova senha</label><input class="input" id="nova-senha" name="novaSenha" type="password" required minlength="12" maxlength="128" autocomplete="new-password" placeholder="12 caracteres ou mais" oninput="conferirSenhaCampo(this)">'+blocoRegrasSenha('nova-senha')+'</div><div class="field"><label for="confirmar-senha">Confirme a nova senha</label><input class="input" id="confirmar-senha" name="confirmarSenha" type="password" required minlength="12" maxlength="128" autocomplete="new-password"></div><button class="btn btn-grad btn-block" type="submit">Redefinir senha</button></form>'
  :'<form onsubmit="solicitarRecuperacao(event)"><div class="field"><label for="email-recuperacao">E-mail</label><input class="input" id="email-recuperacao" name="email" type="email" maxlength="254" required autocomplete="email" placeholder="voce@email.com"></div><button class="btn btn-grad btn-block" type="submit">Enviar link</button></form>')+
  '<p id="recovery-status" role="status" aria-live="polite" class="muted lp-auth-status"></p><a href="#/entrar" class="btn btn-ghost btn-block">Voltar para entrar</a></div></div></div>';
}
async function solicitarRecuperacao(event){
  event.preventDefault();const form=event.currentTarget,button=form.querySelector('button[type="submit"]'),status=$('#recovery-status');button.disabled=true;button.setAttribute('aria-busy','true');
  const email=String(new FormData(form).get('email')||'').trim();
  try{await pedirLinkRecuperacao(email);recuperacaoEnviada=email;recuperacaoEspera=Date.now()+60000;render();contarEsperaRecuperacao();document.querySelector('.recuperacao-ok h1')?.focus?.();return;}
  catch(error){status.textContent=error.message||'Não foi possível enviar o pedido. Tente de novo.';status.className='lp-auth-status recuperacao-erro';status.setAttribute('role','alert');}
  finally{button.disabled=false;button.removeAttribute('aria-busy');}
}
async function redefinirSenha(event){
  event.preventDefault();const form=event.currentTarget,button=form.querySelector('button[type="submit"]'),status=$('#recovery-status');button.disabled=true;button.setAttribute('aria-busy','true');
  try{const data=Object.fromEntries(new FormData(form));if(data.novaSenha!==data.confirmarSenha)throw new Error('As senhas não coincidem.');const falta=faltaNaSenha(data.novaSenha);if(falta)throw new Error(falta);const headers=await obterCabecalhosCsrf();
    const response=await fetch(API_BASE_URL+'/api/auth/recuperacao/redefinir',{method:'POST',credentials:'include',headers,body:JSON.stringify({...data,token:route.q.token})});
    if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.detail||body.message||'Link inválido ou expirado. Solicite uma nova recuperação.');}
    const usuarioAtual=me();if(usuarioAtual?.id?.startsWith('db_'))S.users=S.users.filter(usuario=>usuario!==usuarioAtual);S.session=null;Store.save();
    status.className='lp-auth-status recuperacao-sucesso';status.textContent='Senha trocada. Agora entre com a senha nova.';setTimeout(()=>go('/entrar'),1200);
  }catch(error){
    const msg=error.message||'Não foi possível redefinir a senha.',linkRuim=/link|token/i.test(msg);
    /* Link vencido ou já usado: o erro fica em destaque e o caminho é pedir outro link. */
    status.className='lp-auth-status recuperacao-erro';status.setAttribute('role','alert');
    status.innerHTML='<b>'+esc(linkRuim?'Este link não funciona mais.':msg)+'</b>'+(linkRuim?'<span>Ele venceu (vale 30 minutos) ou a senha já foi trocada com outro link. Peça um link novo e use o e-mail mais recente.</span><a class="btn btn-primary btn-sm" href="#/recuperar" onclick="recuperacaoEnviada=\'\'">Pedir um link novo</a>':'');
  }
  finally{button.disabled=false;button.removeAttribute('aria-busy');}
}
function mostrarCadastroConcluido({nome='',aguardar,onConcluir,onErro}={}){
  const reduzir=matchMedia('(prefers-reduced-motion: reduce)').matches;
  const espera=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const podeTentar=typeof aguardar==='function';
  const primeiroNome=String(nome).trim().split(/\s+/)[0];
  let erroAtual;
  const dlg=document.createElement('dialog');
  dlg.className='tg-success';
  dlg.tabIndex=-1;
  dlg.setAttribute('aria-labelledby','tg-success-title');
  dlg.setAttribute('aria-describedby','tg-success-msg');
  dlg.innerHTML='<svg class="tg-success__mark" viewBox="0 0 64 64" aria-hidden="true">'+
    '<circle class="tg-success__track" cx="32" cy="32" r="30"/>'+
    '<circle class="tg-success__fill" cx="32" cy="32" r="30"/>'+
    '<circle class="tg-success__arc" cx="32" cy="32" r="30"/>'+
    '<path class="tg-success__tick" d="M20 33l8 8 16-18"/>'+
    '<path class="tg-success__bang" d="M32 20v15"/>'+
    '<circle class="tg-success__dot" cx="32" cy="43.5" r="2"/>'+
    '</svg><div class="tg-success__texto" role="status" aria-live="polite">'+
    '<h2 class="tg-success__title" id="tg-success-title"></h2>'+
    '<p class="tg-success__msg" id="tg-success-msg"></p></div>'+
    '<div class="tg-success__actions"><div><div class="tg-success__actions-in">'+
    '<button type="button" class="tg-success__btn tg-success__btn--primary" data-acao="tentar">Tentar novamente</button>'+
    '<button type="button" class="tg-success__btn tg-success__btn--ghost" data-acao="voltar">Voltar ao formulário</button>'+
    '</div></div></div>';
  const texto=$('.tg-success__texto',dlg),titulo=$('.tg-success__title',dlg),msg=$('.tg-success__msg',dlg);
  const acoes=$('.tg-success__actions',dlg),btnTentar=$('[data-acao="tentar"]',dlg),btnVoltar=$('[data-acao="voltar"]',dlg);
  if(!podeTentar){btnTentar.hidden=true;btnVoltar.classList.replace('tg-success__btn--ghost','tg-success__btn--primary');}
  const definir=(estado,t,m)=>{
    dlg.dataset.estado=estado;
    dlg.setAttribute('aria-busy',String(estado==='carregando'));
    texto.setAttribute('aria-live',estado==='erro'?'assertive':'polite');
    acoes.inert=estado!=='erro';
    [[titulo,t],[msg,m]].forEach(([el,valor])=>{
      el.textContent=valor;
      if(!reduzir&&typeof el.animate==='function')el.animate(
        [{opacity:0,transform:'translateY(4px)'},{opacity:1,transform:'none'}],
        {duration:220,easing:'ease-out'});
    });
  };
  const textoDoErro=erro=>erro instanceof TypeError
    ?'Sem conexão com o servidor. Verifique sua internet e tente de novo.'
    :(erro&&erro.message)||'Não foi possível concluir o cadastro. Tente novamente.';
  const remover=()=>{if(dlg.open)dlg.close();dlg.remove();};
  async function executar(){
    definir('carregando','Criando sua conta','Estamos finalizando seu cadastro.');
    const [resultado]=await Promise.all([
      Promise.resolve().then(()=>podeTentar?aguardar():aguardar).then(()=>({}),erro=>({erro:erro||new Error()})),
      espera(1600)
    ]);
    if(resultado.erro){
      erroAtual=resultado.erro;
      definir('erro','Não foi possível criar a conta',textoDoErro(resultado.erro));
      requestAnimationFrame(()=> (podeTentar?btnTentar:btnVoltar).focus());
      return;
    }
    definir('pronto','Conta criada',primeiroNome
      ?'Bem-vindo à TaskGO, '+primeiroNome+'. Abrindo seu painel.'
      :'Bem-vindo à TaskGO. Abrindo seu painel.');
    await espera(reduzir?1200:1700);
    dlg.dataset.saindo='';
    await espera(reduzir?0:180);
    remover();
    if(typeof onConcluir==='function')onConcluir();
  }
  btnTentar.addEventListener('click',executar);
  btnVoltar.addEventListener('click',()=>{
    remover();
    requestAnimationFrame(()=>{if(typeof onErro==='function')onErro(erroAtual);});
  });
  dlg.addEventListener('cancel',evento=>{
    evento.preventDefault();
    if(dlg.dataset.estado==='erro')btnVoltar.click();
  });
  document.body.appendChild(dlg);
  dlg.showModal();
  executar();
}
/* Sessão aberta no servidor: monta a conta local, sincroniza e leva para o lugar certo. */
async function entrarComUsuarioApi(usuarioApi,email){
  const emailApi=String(usuarioApi.email||email).trim().toLowerCase();
  limparCachesDaConta(window.__contaDoPedido===emailApi);window.__contaDoPedido=null;
  /* O tipo vem do servidor e é usado como está: cada papel tem o seu tipo de conta. */
  const role=String(usuarioApi.tipo||'').toLowerCase();
  if(!TIPOS_CONTA.includes(role))throw new Error('Tipo de conta desconhecido. Fale com o suporte.');
  const nome=usuarioApi.nome||emailApi.split('@')[0];
  let usuarioLocal=S.users.find(u=>String(u.email||'').toLowerCase()===emailApi);
  if(!usuarioLocal){
    usuarioLocal={
      id:usuarioApi.id!=null?'db_'+usuarioApi.id:'u_'+uid(),role:role,name:nome,email:emailApi,
      cpf:usuarioApi.cpf||'',phone:usuarioApi.telefone||'',photo:null,city:'',address:'',bio:'',
      balance:0,pending:0,rating:0,since:Date.now(),plan:'free',
      refCode:null,referredBy:null,pixKey:null,
      notif:{push:true,email:true,promo:true,sms:false},twofa:false,paymentMethods:[],
    verified:false,jobs:0,acceptRadius:10
    };
    S.users.push(usuarioLocal);
  }
  Object.assign(usuarioLocal,{
    role:role,name:nome,email:emailApi,
    cpf:usuarioApi.cpf||usuarioLocal.cpf||'',phone:usuarioApi.telefone||usuarioLocal.phone||'',
    proId:usuarioApi.proId||usuarioLocal.proId,service:usuarioApi.tipo==='profissional'?(usuarioApi.service||usuarioLocal.service):null,
    birth:usuarioApi.birth||usuarioLocal.birth||'',
    city:usuarioApi.cidade||usuarioLocal.city,address:usuarioApi.endereco||usuarioLocal.address,bio:usuarioApi.bio||usuarioLocal.bio,
    balance:usuarioApi.balance??usuarioLocal.balance,pending:usuarioApi.pending??usuarioLocal.pending,
    plan:usuarioApi.plan||usuarioLocal.plan,pixKey:usuarioApi.pixKey||usuarioLocal.pixKey,
    notif:usuarioApi.notif||usuarioLocal.notif,twofa:usuarioApi.twofa??usuarioLocal.twofa,
    photo:usuarioApi.photo||usuarioLocal.photo,available:usuarioApi.available??usuarioLocal.available,
    acceptRadius:usuarioApi.acceptRadius??usuarioLocal.acceptRadius,verified:!!usuarioApi.verificado
  });
  if(usuarioApi.id!=null)usuarioLocal.id='db_'+usuarioApi.id;
  S.users=[usuarioLocal];S.orders=[];S.tx=[];S.chats=[];S.notifs=[];
  window.__taskgoHydrating=true;
  S.session=usuarioLocal.id;Store.save();
  try{await carregarProfissionaisPersistidos();}catch(erro){console.error('Falha ao carregar profissionais cadastrados:',erro);}
  try{await syncPedidos(emailApi);}catch(erro){console.error('Falha ao sincronizar pedidos:',erro);}
  try{await syncDadosConta(emailApi);}catch(erro){console.error('Falha ao sincronizar dados da conta:',erro);}
  window.__taskgoHydrating=false;Store.save();
  toast('Bem-vindo, '+String(nome).split(' ')[0]+'!','ok');
  return go(destinoAposEntrar());
}
/* ---------- Confirmação de e-mail ---------- */
let confirmacaoEmail={email:'',espera:0,timer:null};
function abrirConfirmacaoEmail(email,doLogin){
  confirmacaoEmail.email=email;confirmacaoEmail.espera=Date.now()+60000;confirmacaoEmail.doLogin=!!doLogin;
  go('/confirmar-email');
  clearInterval(confirmacaoEmail.timer);
  confirmacaoEmail.timer=setInterval(()=>{const b=document.getElementById('btnReenviarCodigo');if(!b){clearInterval(confirmacaoEmail.timer);return;}
    const falta=Math.ceil((confirmacaoEmail.espera-Date.now())/1000);b.disabled=falta>0;b.textContent=falta>0?'Mandar outro código em '+falta+' s':'Não chegou? Mandar outro código';},1000);
}
function pageConfirmarEmail(){
  const c=confirmacaoEmail;
  if(!c.email){go('/entrar');return '';}
  const falta=Math.max(0,Math.ceil((c.espera-Date.now())/1000));
  return '<div class="auth-wrap"><div class="auth-side"><p class="lp-auth-slogan">Falta só confirmar o seu e-mail</p></div><div class="auth-main"><div class="auth-card recuperacao-ok">'+
    '<span class="recuperacao-ico" aria-hidden="true">'+ic('mail')+'</span>'+
    '<h1 class="lp-auth-title">Digite o código</h1>'+
    '<p class="lp-auth-sub">'+(c.doLogin?'Sua conta ainda não foi confirmada. ':'')+'Mandamos um código de 6 números para <b class="recuperacao-email">'+esc(c.email)+'</b>. Ele vale por 15 minutos. Se não estiver na caixa de entrada, olhe em Spam.</p>'+
    '<form onsubmit="confirmarCodigoEmail(event)" style="margin-top:22px"><div class="field"><label for="codigo-email">Código</label>'+
    '<input class="input codigo-email" id="codigo-email" name="codigo" inputmode="numeric" autocomplete="one-time-code" maxlength="7" required placeholder="000000" oninput="this.value=this.value.replace(/[^0-9]/g,\'\').slice(0,6);if(this.value.length===6)this.form.requestSubmit()"></div>'+
    '<button class="btn btn-grad btn-block" type="submit">Confirmar e entrar</button></form>'+
    '<p id="confirmacao-status" role="status" aria-live="polite" class="lp-auth-status"></p>'+
    '<div class="recuperacao-de-novo"><button type="button" id="btnReenviarCodigo" class="btn btn-ghost btn-sm" '+(falta?'disabled':'')+' onclick="reenviarCodigoEmail()">'+(falta?'Mandar outro código em '+falta+' s':'Não chegou? Mandar outro código')+'</button>'+
    '<a class="btn btn-ghost btn-sm" href="#/entrar">Usar outro e-mail</a></div></div></div></div>';
}
async function confirmarCodigoEmail(e){
  e.preventDefault();const botao=e.target.querySelector('button[type="submit"]'),st=$('#confirmacao-status');
  if(botao.disabled)return;botao.disabled=true;botao.textContent='Conferindo...';
  try{
    const r=await fetch(API_BASE_URL+'/api/auth/confirmar-email',{method:'POST',credentials:'include',headers:await obterCabecalhosCsrf(),body:JSON.stringify({email:confirmacaoEmail.email,codigo:new FormData(e.target).get('codigo')})});
    if(!r.ok)throw await erroDaResposta(r,'Não foi possível confirmar o código.');
    const usuarioApi=await r.json();const email=confirmacaoEmail.email;confirmacaoEmail.email='';
    toast('E-mail confirmado.','ok');
    return entrarComUsuarioApi(usuarioApi,email);
  }catch(error){
    st.className='lp-auth-status recuperacao-erro';st.setAttribute('role','alert');st.textContent=erroLegivel(error,'Não foi possível confirmar o código.');
    botao.disabled=false;botao.textContent='Confirmar e entrar';const c=$('#codigo-email');if(c){c.value='';c.focus();}
  }
}
async function reenviarCodigoEmail(){
  const st=$('#confirmacao-status');
  try{
    const r=await fetch(API_BASE_URL+'/api/auth/confirmar-email/reenviar',{method:'POST',credentials:'include',headers:await obterCabecalhosCsrf(),body:JSON.stringify({email:confirmacaoEmail.email})});
    if(!r.ok)throw await erroDaResposta(r,'Não foi possível mandar outro código.');
    confirmacaoEmail.espera=Date.now()+60000;st.className='lp-auth-status recuperacao-sucesso';st.removeAttribute('role');st.textContent='Mandamos um código novo. Use o mais recente.';
  }catch(error){st.className='lp-auth-status recuperacao-erro';st.setAttribute('role','alert');st.textContent=erroLegivel(error,'Não foi possível mandar outro código.');}
}
async function doAuth(e){
  e.preventDefault();
  const f=new FormData(e.target),email=(f.get('email')||'').trim().toLowerCase(),pass=f.get('pass');
  if(A.mode==='login'){
    const botao=e.submitter||e.target.querySelector('button[type="submit"]');
    if(botao)botao.disabled=true;
    try{
      const resposta=await fetch(API_BASE_URL+'/api/auth/login',{
        method:'POST',
        credentials:'include',headers:await obterCabecalhosCsrf(),
        body:JSON.stringify({email:email,senha:pass})
      });
      if(resposta.status===403){
        const corpoErro=await resposta.json().catch(()=>({}));
        /* Conta sem e-mail confirmado: vai para a tela do código (o servidor já mandou um). */
        if(corpoErro.codigo==='EMAIL_NAO_CONFIRMADO'){abrirConfirmacaoEmail(corpoErro.email||email,true);return;}
      }
      if(!resposta.ok)throw new Error('Credenciais inválidas');
      /* Verificação em duas etapas ligada: a sessão só abre depois do código do aplicativo. */
      if(resposta.status===202){segundaEtapa.email=email;go('/segunda-etapa');return;}
      return entrarComUsuarioApi(await resposta.json(),email);
    }catch(erro){
      console.error('Erro no login:',erro);
      toast('E-mail ou senha incorretos','err');
    }finally{
      if(botao)botao.disabled=false;
    }
    return;
  }
  if(A.role==='profissional'&&!f.get('service')){toast('Escolha a sua categoria principal.','err');const b=document.getElementById('auth-service-btn');if(b){b.scrollIntoView({block:'center'});alternarSeletorServico('auth-service');}return;}
  const falta=faltaNaSenha(pass,email,f.get('name'));
  if(falta){toast(falta,'err');const c=document.getElementById('auth-pass');if(c)c.focus();return;}
  const cpfDig=String(f.get('cpf')||'').replace(/\D/g,'');
  if(cpfDig.length!==11||(typeof cpfOk==='function'&&!cpfOk(cpfDig))){toast('CPF inválido. Confira os números.','err');const c=document.getElementById('auth-cpf');if(c){c.focus();c.scrollIntoView({block:'center'});}return;}
  const u={id:'u_'+uid(),role:A.role,name:f.get('name'),email:email,cpf:f.get('cpf'),phone:f.get('phone'),
    photo:null,city:'',address:'',bio:'',balance:0,pending:0,rating:0,since:Date.now(),plan:'free',
    refCode:'',referredBy:null,pixKey:'',
    notif:{push:true,email:true,promo:true,sms:false},twofa:false,paymentMethods:[],service:A.role==='profissional'?(f.get('service')||''):null,verified:false,jobs:0,acceptRadius:10};
  if(A.role==='profissional')u.proId=null;
  const enviarCadastro=continuarSemCodigo=>{
    /* Com e-mail a confirmar, não há sessão ainda: vai para a tela do código. */
    const continuar=()=>u.emailPendente?abrirConfirmacaoEmail(u.email,false):continuarSemCodigo();
    const usuario={
      nome:u.name,
      cpf:u.cpf,
      telefone:u.phone,
      email:u.email,
      senha:pass,
      tipo:A.role,
      service:u.role==='profissional'?u.service:null,
      proId:u.proId,
      cidade:u.city,
      endereco:u.address,
      bio:u.bio
    };
    const criarConta=async()=>{
      const response=await fetch(API_BASE_URL+'/api/auth/registrar',{
        method:'POST',
        credentials:'include',headers:await obterCabecalhosCsrf(),
        body:JSON.stringify(usuario)
      });
      if(!response.ok){
        let dadosErro={};
        try{dadosErro=await response.json();}catch(_){/* resposta sem JSON */}
        if(response.status===409)throw new Error('Este e-mail já está cadastrado.');
        throw new Error(dadosErro.detail||dadosErro.message||'Não foi possível criar a conta no servidor. Tente novamente.');
      }
      const criado=await response.json();
      if(criado.id!=null)u.id='db_'+criado.id;
      u.emailPendente=criado.emailConfirmado===false;
      if(criado.proId)u.proId=criado.proId;
      /* E-mail de administrador vira conta de administrador no servidor, qualquer que seja o tipo escolhido. */
      if(criado.tipo&&criado.tipo!==u.role){u.role=criado.tipo;if(u.role==='administrador'){u.service=null;u.proId=null;}}
      return criado;
    };
    return mostrarCadastroConcluido({
      nome:u.name,
      aguardar:criarConta,
      onConcluir:continuar,
      onErro:erro=>{
        const campoEmail=e.target.elements.namedItem('email');
        const campoInvalido=e.target.querySelector(':invalid');
        const campoProblema=/e-?mail|cadastrad/i.test((erro&&erro.message)||'')?campoEmail:(campoInvalido||campoEmail);
        if(campoProblema)campoProblema.focus();
      }
    });
  };
  if(u.role==='profissional')return enviarCadastro(async()=>{
    S.users.push(u);S.session=u.id;Store.save();
    try{await carregarProfissionaisPersistidos();}catch(error){console.error(error);}
    go(destinoAposEntrar());
  });
  return enviarCadastro(()=>{
    u.since=Date.now();
    S.users.push(u);
    S.session=u.id;Store.save();
    go(destinoAposEntrar());
  });
}
/* Tudo que é de uma conta e fica na memória da aba: zera ao sair, quando a sessão vence e antes de entrar com outra conta.
   manterPedido: o pedido em preenchimento fica para a mesma conta continuar depois de entrar de novo.
   Cada arquivo dono do estado carrega depois deste, por isso o typeof. */
function limparCachesDaConta(manterPedido){
  if(!manterPedido&&typeof W_VAZIO==='function')W=W_VAZIO();
  if(typeof pertoAgora!=='undefined')pertoAgora=null;
  if(typeof RESGATE!=='undefined')RESGATE={chave:undefined,enviando:false};
  if(typeof PGD!=='undefined'){PGD.pend=null;PGD.troca=null;}
  if(typeof PG!=='undefined'){PG.cartoes=null;PG.saldo=null;}
  if(typeof CATS!=='undefined')CATS.lista=null;
  if(typeof AJ!=='undefined')AJ.estado=null;
  if(typeof pararAlerta==='function')pararAlerta();
  if(typeof chamadosSuporte!=='undefined')chamadosSuporte=null;
  if(typeof financePageData!=='undefined'){financePageData=null;financePageUserId=null;financePageError='';}
  clearInterval(window.__taskgoNotificationPoll);window.__taskgoNotificationPoll=null;
  clearInterval(window.__taskgoOrderSync);window.__taskgoOrderSync=null;
  if(typeof OB!=='undefined'){clearInterval(OB.pixTimer);OB.pixTimer=null;OB.dados=null;OB.pedido=null;OB.pag=null;OB.id=null;}
  if(typeof FIN!=='undefined')FIN.dados=null;
  if(typeof avisosAdmin!=='undefined')avisosAdmin.lista=null;
  if(typeof JA_DENUNCIADOS!=='undefined')JA_DENUNCIADOS=null;
}
async function logout(){
  /* Na demonstração, sair só tira a conta do endereço; os dados fictícios ficam. */
  if(DEMO_MODE){S.session=null;Store.save();location.href=location.pathname+'?demo=1#/home';return;}
  try{const response=await fetch(API_BASE_URL+'/api/auth/logout',{method:'POST',credentials:'include',headers:await obterCabecalhosCsrf()});if(!response.ok&&response.status!==401)throw new Error('HTTP '+response.status);}
  catch(error){toast('Não foi possível encerrar a sessão no servidor. Tente novamente.','err');return;}
  limparCachesDaConta();
  const current=me();if(current?.id?.startsWith('db_'))S.users=S.users.filter(user=>user!==current);
  desligarEventos();desligarPresenca();convitesAgora=[];AJ.estado=null;pintarConvites();segurancaConta=null;S.situacaoConta=null;window.__situacaoCarregada=null;PAINEL.hoje=null;PAINEL.atencao=null;PAINEL.pagamentos=null;PAINEL.exportacoes=null;PAINEL.busca={q:'',res:null,carregando:false,erro:''};PAINEL.auditoria.itens=null;adminDados={recursos:null,usuarios:null,erro:''};
  S.session=null;S.orders=[];S.tx=[];S.chats=[];S.notifs=[];
  try{await carregarProfissionaisPersistidos();}catch(error){console.error(error);}
  fecharMenuConta();Store.save();toast('Você saiu da conta');
  /* Já na Início, o endereço não muda; então é preciso redesenhar para tirar a conta do cabeçalho. */
  if(location.hash==='#/home')render();else go('/home');
}

