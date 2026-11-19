/* =========================================================
   ENTRAR / CRIAR CONTA COM GOOGLE
   Entrar: só entra numa conta que já existe. Criar conta: só cria conta nova (pede o tipo e o telefone).
   Com GOOGLE_CLIENT_ID no servidor, o botão abre o Google; sem ele, avisa que ainda não foi ativado.
   ========================================================= */
let googleCfg=null,googleCredencial=null,googleModo='entrar';
const LOGO_GOOGLE='<svg class="g-logo" viewBox="0 0 48 48" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.1H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.6-.4-3.9z"/><path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.1H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.6-.4-3.9z"/></svg>';

async function carregarGoogleCfg(){
  if(googleCfg)return googleCfg;
  try{googleCfg=await (await fetch(API_BASE_URL+'/api/auth/google/config')).json();}catch(e){googleCfg={clientId:''};}
  return googleCfg;
}
function blocoGoogle(){
  if(DEMO_MODE)return '';
  const criar=A.mode!=='login';
  setTimeout(montarBotaoGoogle,0);
  const ou='<div class="google-ou"><span>'+(criar?'ou preencha os dados':'ou')+'</span></div>';
  return '<div id="googleBloco" class="google-bloco'+(criar?' no-topo':'')+'">'+(criar?'':ou)+
    '<button type="button" class="btn btn-block btn-lg btn-google" id="googleProprio" onclick="googleSemConfiguracao()">'+LOGO_GOOGLE+'<span>'+(criar?'Criar conta com Google':'Entrar com Google')+'</span></button>'+
    '<div id="googleBotao" class="google-botao" hidden></div>'+
    (criar?'<p class="hint google-dica">Cria a conta com o e-mail do Google. Depois, entre por "Entrar com Google".</p>':'<p class="hint google-dica">Para contas criadas com o Google.</p>')+(criar?ou:'')+'</div>';
}
/* Com a chave do Google, troca o botão próprio pelo oficial (é ele que abre a janela do Google). */
async function montarBotaoGoogle(){
  const cfg=await carregarGoogleCfg();
  if(!cfg.clientId||!$('#googleBotao'))return;
  if(!window.google||!google.accounts){
    if(!document.getElementById('gsiScript')){const s=document.createElement('script');s.id='gsiScript';s.src='https://accounts.google.com/gsi/client';s.async=true;s.onload=montarBotaoGoogle;document.head.appendChild(s);}
    return;
  }
  if(!window.__googleIniciado){google.accounts.id.initialize({client_id:cfg.clientId,callback:r=>entrarComGoogle(r.credential),ux_mode:'popup',auto_select:false});window.__googleIniciado=true;}
  const alvo=$('#googleBotao'),proprio=$('#googleProprio');if(!alvo)return;
  googleModo=A.mode==='login'?'entrar':'criar';
  alvo.hidden=false;if(proprio)proprio.hidden=true;
  google.accounts.id.renderButton(alvo,{theme:'outline',size:'large',shape:'rectangular',text:googleModo==='entrar'?'signin_with':'signup_with',locale:'pt-BR',logo_alignment:'center',width:Math.min(400,alvo.clientWidth||320)});
}
function googleSemConfiguracao(){
  toast(googleCfg&&googleCfg.clientId?'Abrindo o Google...':'Entrar com Google não está disponível agora. Use e-mail e senha.',googleCfg&&googleCfg.clientId?undefined:'err');
}
async function entrarComGoogle(credencial,extra){
  googleCredencial=credencial;
  try{
    const r=await fetch(API_BASE_URL+'/api/auth/google',{method:'POST',credentials:'include',headers:await obterCabecalhosCsrf(),body:JSON.stringify(Object.assign({credencial,modo:googleModo},extra||{}))});
    const corpo=await r.json().catch(()=>({}));
    if(r.status===409&&corpo.codigo==='CADASTRO_GOOGLE')return pedirTipoGoogle(corpo);
    if(r.status===404&&corpo.codigo==='SEM_CONTA'){toast(corpo.message,'err');A.mode='signup';render();return;}
    if(r.status===409&&corpo.codigo==='CONTA_EXISTE'){toast(corpo.message,'err');A.mode='login';render();return;}
    if(r.status===202){segundaEtapa.email=corpo.email||'';closeModal();go('/segunda-etapa');return;}
    if(!r.ok)throw new Error(corpo.message||'Não foi possível entrar com o Google.');
    closeModal();
    return entrarComUsuarioApi(corpo,corpo.email);
  }catch(e){toast(erroLegivel(e,'Não foi possível entrar com o Google.'),'err');}
}
function pedirTipoGoogle(d){
  const pro=A.role==='profissional';
  openModal('<div class="modal-head"><div><h3>Falta pouco'+(d.nome?', '+esc(d.nome.split(' ')[0]):'')+'</h3><p>Conta nova com '+esc(d.email)+'.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<form onsubmit="concluirCadastroGoogle(event)">'+
    '<div class="role-pick" role="radiogroup" aria-label="Como você quer usar a TaskGO">'+
      '<label class="role-opt"><input type="radio" name="gTipo" value="cliente" class="leitor"'+(pro?'':' checked')+' onchange="document.getElementById(\'gCategoria\').hidden=true">'+ic('user')+'<b>Quero contratar</b><span>Sou cliente</span></label>'+
      '<label class="role-opt"><input type="radio" name="gTipo" value="profissional" class="leitor"'+(pro?' checked':'')+' onchange="document.getElementById(\'gCategoria\').hidden=false">'+ic('wrench')+'<b>Quero trabalhar</b><span>Sou profissional</span></label>'+
    '</div>'+
    '<div id="gCategoria"'+(pro?'':' hidden')+'>'+seletorServico({id:'gServico',rotulo:'Categoria principal',atual:SERVICES[0].id})+'</div>'+
    '<div class="field"><label for="gTelefone">Telefone / WhatsApp</label><input class="input" id="gTelefone" type="tel" autocomplete="tel" required placeholder="(81) 90000-0000" oninput="this.value=maskPhone(this.value)"></div>'+
    '<label class="muted lp-auth-check lp-auth-terms"><input type="checkbox" required> Li e aceito os <a href="#/legal/termos" class="lp-auth-link">Termos de Uso</a> e a <a href="#/legal/privacidade" class="lp-auth-link">Política de Privacidade</a>.</label>'+
    '<button class="btn btn-grad btn-block btn-lg" style="margin-top:14px">Criar conta</button></form>');
}
function concluirCadastroGoogle(e){
  e.preventDefault();
  const tipo=(document.querySelector('input[name="gTipo"]:checked')||{}).value||'cliente';
  if(tipo==='profissional'&&!$('#gServico').value)return toast('Escolha a sua categoria principal.','err');
  entrarComGoogle(googleCredencial,{tipo,telefone:$('#gTelefone').value,service:tipo==='profissional'?$('#gServico').value:null});
}
