/* ---------- SEGURANÇA DA CONTA ----------
   Verificação em duas etapas (aplicativo autenticador) e sessões abertas, no Perfil.
   Também a tela da segunda etapa no login. */
let segurancaConta=null;
async function carregarSegurancaConta(){
  if(DEMO_MODE){segurancaConta={doisFatores:false,codigosRecuperacaoRestantes:0,sessoes:[{id:'demo',dispositivo:'Este navegador',ip:'',criadaEm:Date.now(),vistaEm:Date.now(),atual:true}]};return;}
  try{segurancaConta=await apiJSON('/api/conta/seguranca','GET',undefined,'Não foi possível carregar a segurança da conta.');}
  catch(e){segurancaConta={erro:erroLegivel(e,'Não foi possível carregar a segurança da conta.'),sessoes:[]};}
  if(route.page==='painel')render();
}
function linhaDoisFatores(){
  const s=segurancaConta;
  if(!s){setTimeout(carregarSegurancaConta,0);return '<div class="check-row"><div><b style="font-size:.92rem;display:block">Verificação em duas etapas</b><small class="muted">Carregando...</small></div></div>';}
  return '<div class="check-row"><div><b style="font-size:.92rem;display:block">Verificação em duas etapas</b><small class="muted">'+
    (s.doisFatores?'Ligada. Ao entrar, além da senha, você digita o código do aplicativo autenticador.'+(s.codigosRecuperacaoRestantes<3?' Restam '+s.codigosRecuperacaoRestantes+' códigos de recuperação.':''):'Além da senha, peça um código do aplicativo autenticador (Google Authenticator, Microsoft Authenticator, Authy) ao entrar.')+'</small></div>'+
    (s.doisFatores?'<span class="tag tag-ok">Ligada</span><button class="btn btn-ghost btn-sm" onclick="desligarDoisFatores()">Desligar</button>':'<button class="btn btn-primary btn-sm" onclick="ligarDoisFatores()">Ligar</button>')+'</div>';
}
function blocoSessoes(){
  const s=segurancaConta;if(!s)return '';
  const outras=(s.sessoes||[]).filter(x=>!x.atual).length;
  return '<div class="check-row sessoes"><div style="flex:1"><b style="font-size:.92rem;display:block">Onde sua conta está aberta</b>'+
    (s.erro?'<small class="muted">'+esc(s.erro)+'</small>':'<small class="muted">Se não reconhecer algum aparelho, encerre a sessão e troque a senha.</small>'+
    '<ul class="lista-sessoes">'+(s.sessoes||[]).map(x=>'<li><span>'+ic(/Android|iPhone/.test(x.dispositivo)?'phone':'grid')+'</span><div><b>'+esc(x.dispositivo||'Navegador')+(x.atual?' <span class="tag tag-ok">Este aparelho</span>':'')+'</b>'+
      '<small>'+(x.ip?'IP '+esc(x.ip)+' · ':'')+'entrou '+dataHora(x.criadaEm)+' · última atividade '+dataHora(x.vistaEm)+'</small></div>'+
      (x.atual?'':'<button class="btn btn-ghost btn-sm" onclick="encerrarSessao(\''+esc(x.id)+'\')">Encerrar</button>')+'</li>').join('')+'</ul>'+
    (outras?'<button class="btn btn-ghost btn-sm" onclick="encerrarOutrasSessoes()">Encerrar todas as outras ('+outras+')</button>':''))+'</div></div>';
}
async function encerrarSessao(id){
  try{const r=await fetch(API_BASE_URL+'/api/conta/sessoes/'+encodeURIComponent(id),{method:'DELETE'});if(!r.ok)throw await erroDaResposta(r,'Não foi possível encerrar.');toast('Sessão encerrada.','ok');carregarSegurancaConta();}
  catch(e){toast(erroLegivel(e,'Não foi possível encerrar.'),'err');}
}
async function encerrarOutrasSessoes(){
  try{const d=await apiJSON('/api/conta/sessoes','DELETE',undefined,'Não foi possível encerrar.');toast(d.encerradas+(d.encerradas===1?' sessão encerrada.':' sessões encerradas.'),'ok');carregarSegurancaConta();}
  catch(e){toast(erroLegivel(e,'Não foi possível encerrar.'),'err');}
}
/* QR code desenhado no navegador (biblioteca pequena, carregada só aqui). */
function carregarQr(){
  if(window.qrcode)return Promise.resolve();
  return new Promise((ok,erro)=>{const s=document.createElement('script');s.src='https://cdnjs.cloudflare.com/ajax/libs/qrcode-generator/1.4.4/qrcode.min.js';s.onload=ok;s.onerror=erro;document.head.appendChild(s);});
}
async function ligarDoisFatores(){
  if(DEMO_MODE)return toast('Na demonstração não dá para ligar a verificação em duas etapas.');
  try{
    const d=await apiJSON('/api/conta/2fa/iniciar','POST',undefined,'Não foi possível começar.');
    let qr='';try{await carregarQr();const q=qrcode(0,'M');q.addData(d.uri);q.make();qr=q.createSvgTag({cellSize:5,margin:2,scalable:true});}catch(e){}
    openModal('<div class="modal-head"><div><h3>Ligar a verificação em duas etapas</h3><p>Leva um minuto. Você vai precisar do celular.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
      '<ol class="recuperacao-passos">'+
      '<li><b>Instale um aplicativo autenticador</b><span>Google Authenticator, Microsoft Authenticator ou Authy, grátis na loja do celular.</span></li>'+
      '<li><b>Leia o QR code com o aplicativo</b><span>'+(qr?'<span class="qr-2fa" role="img" aria-label="QR code para o aplicativo autenticador">'+qr+'</span>':'')+'Sem câmera? Toque em <a href="'+esc(d.uri)+'">abrir no aplicativo</a> ou digite a chave: <code class="chave-2fa">'+esc(d.segredo.replace(/(.{4})/g,'$1 ').trim())+'</code></span></li>'+
      '<li><b>Digite o código de 6 números que aparece</b><span><form onsubmit="ativarDoisFatores(event)" class="form-2fa"><label class="leitor" for="codigo-2fa">Código</label><input class="input codigo-email" id="codigo-2fa" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required placeholder="000000" oninput="this.value=this.value.replace(/[^0-9]/g,\'\')"><button class="btn btn-primary">Ligar</button></form></span></li>'+
      '</ol>');
  }catch(e){toast(erroLegivel(e,'Não foi possível começar.'),'err');}
}
async function ativarDoisFatores(ev){
  ev.preventDefault();
  try{
    const d=await apiJSON('/api/conta/2fa/ativar','POST',{codigo:$('#codigo-2fa').value},'Código incorreto.');
    $('.modal').innerHTML='<div class="modal-head"><div><h3>Verificação ligada</h3><p>Guarde estes códigos de recuperação. Cada um vale uma vez e serve para entrar se você perder o celular.</p></div></div>'+
      '<ul class="codigos-recuperacao">'+d.codigosRecuperacao.map(c=>'<li><code>'+esc(c)+'</code></li>').join('')+'</ul>'+
      '<div style="display:flex;gap:10px;flex-wrap:wrap"><button class="btn btn-ghost" onclick="baixarCodigos()">'+ic('download')+' Baixar códigos</button><button class="btn btn-primary" onclick="closeModal();carregarSegurancaConta()">Já guardei</button></div>';
    window.__codigos2fa=d.codigosRecuperacao;
  }catch(e){toast(erroLegivel(e,'Código incorreto.'),'err');const c=$('#codigo-2fa');if(c){c.value='';c.focus();}}
}
function baixarCodigos(){
  const txt='Códigos de recuperação da TaskGO ('+(me()?me().email:'')+')\r\nCada código vale uma vez.\r\n\r\n'+(window.__codigos2fa||[]).join('\r\n')+'\r\n';
  const url=URL.createObjectURL(new Blob([txt],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='taskgo-codigos-recuperacao.txt';document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
function desligarDoisFatores(){
  openModal('<div class="modal-head"><div><h3>Desligar a verificação em duas etapas</h3><p>Sua conta volta a pedir só a senha. Confirme com a senha atual.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<form onsubmit="confirmarDesligar(event)"><div class="field"><label for="senha-2fa">Senha atual</label><input class="input" type="password" id="senha-2fa" required autocomplete="current-password"></div>'+
    '<div style="display:flex;gap:10px"><button type="button" class="btn btn-ghost btn-block" onclick="closeModal()">Voltar</button><button class="btn btn-danger btn-block">Desligar</button></div></form>');
}
async function confirmarDesligar(ev){
  ev.preventDefault();
  try{await apiJSON('/api/conta/2fa/desativar','POST',{senha:$('#senha-2fa').value},'Não foi possível desligar.');closeModal();toast('Verificação em duas etapas desligada.','ok');carregarSegurancaConta();}
  catch(e){toast(erroLegivel(e,'Não foi possível desligar.'),'err');}
}

/* ---------- Login: segunda etapa ---------- */
let segundaEtapa={email:''};
function pageSegundaEtapa(){
  if(!segundaEtapa.email){go('/entrar');return '';}
  return '<div class="auth-wrap"><div class="auth-side"><p class="lp-auth-slogan">Mais uma etapa para proteger a sua conta</p></div><div class="auth-main"><div class="auth-card recuperacao-ok">'+
    '<span class="recuperacao-ico" aria-hidden="true">'+ic('shield')+'</span><h1 class="lp-auth-title">Digite o código do aplicativo</h1>'+
    '<p class="lp-auth-sub">Abra o aplicativo autenticador e digite o código de 6 números da TaskGO. Perdeu o celular? Use um dos códigos de recuperação.</p>'+
    '<form onsubmit="enviarSegundaEtapa(event)" style="margin-top:22px"><div class="field"><label for="codigo-login-2fa">Código</label>'+
    '<input class="input codigo-email" id="codigo-login-2fa" autocomplete="one-time-code" maxlength="11" required placeholder="000000" oninput="if(/^[0-9]{6}$/.test(this.value))this.form.requestSubmit()"></div>'+
    '<button class="btn btn-grad btn-block" type="submit">Entrar</button></form>'+
    '<p id="segunda-status" role="status" aria-live="polite" class="lp-auth-status"></p><a class="btn btn-ghost btn-block" href="#/entrar" style="margin-top:12px">Voltar</a></div></div></div>';
}
async function enviarSegundaEtapa(ev){
  ev.preventDefault();const b=ev.target.querySelector('button'),st=$('#segunda-status');if(b.disabled)return;b.disabled=true;
  try{
    const r=await fetch(API_BASE_URL+'/api/auth/2fa',{method:'POST',credentials:'include',headers:await obterCabecalhosCsrf(),body:JSON.stringify({codigo:$('#codigo-login-2fa').value})});
    if(!r.ok)throw await erroDaResposta(r,'Código incorreto.');
    const email=segundaEtapa.email;segundaEtapa.email='';return entrarComUsuarioApi(await r.json(),email);
  }catch(e){st.className='lp-auth-status recuperacao-erro';st.setAttribute('role','alert');st.textContent=erroLegivel(e,'Código incorreto.');b.disabled=false;const c=$('#codigo-login-2fa');if(c){c.value='';c.focus();}}
}
