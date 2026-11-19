/* =========================================================
   AVISOS DA CONTA (Configurações)
   E-mail e push do navegador. O push usa o service worker js/sw.js.
   ========================================================= */
let avisosConta=null,avisosOcupado=false;

function pushSuportado(){return 'serviceWorker' in navigator&&'PushManager' in window&&'Notification' in window&&window.isSecureContext;}
async function carregarAvisosConta(){
  if(DEMO_MODE)return;
  try{avisosConta=await apiJSON('/api/conta/avisos');}catch(e){avisosConta={erro:erroLegivel(e,'Não foi possível carregar os avisos.')};}
  avisosConta.nesteNavegador=await inscricaoAtual().then(s=>!!s).catch(()=>false);
  const c=$('#secaoAvisos');if(c)c.outerHTML=secaoAvisos();
}
async function inscricaoAtual(){
  if(!pushSuportado())return null;
  const reg=await navigator.serviceWorker.getRegistration('/js/');
  return reg?reg.pushManager.getSubscription():null;
}
function secaoAvisos(){
  if(DEMO_MODE)return '';
  if(!avisosConta){setTimeout(carregarAvisosConta,0);return '<div class="card" id="secaoAvisos" style="margin-top:20px"><h2 class="sub" style="font-size:1.05rem">Avisos</h2><p class="muted" style="margin-top:8px">Carregando...</p></div>';}
  if(avisosConta.erro)return '<div class="card" id="secaoAvisos" style="margin-top:20px"><h2 class="sub" style="font-size:1.05rem">Avisos</h2><p class="muted" style="margin-top:8px">'+esc(avisosConta.erro)+'</p></div>';
  const a=avisosConta,perm=('Notification' in window)?Notification.permission:'default';
  const pushLigado=a.push&&a.nesteNavegador&&perm==='granted';
  const linhaPush=!pushSuportado()?'<small class="muted">Este navegador não aceita avisos.</small>':
    perm==='denied'?'<small class="muted">Bloqueado no navegador. Libere as notificações nas configurações do site (cadeado ao lado do endereço).</small>':
    '<small class="muted">'+(pushLigado?'Ligado neste navegador.':'Avisa mesmo com o site fechado.')+(a.navegadores>(a.nesteNavegador?1:0)?' Também ligado em '+(a.navegadores-(a.nesteNavegador?1:0))+' outro(s) aparelho(s).':'')+'</small>';
  return '<div class="card" id="secaoAvisos" style="margin-top:20px"><h2 class="sub" style="font-size:1.05rem;margin-bottom:6px">Avisos</h2>'+
    '<p class="muted" style="font-size:.88rem;margin-bottom:8px">Pedidos, propostas, chegada, pagamento e avisos da conta.</p>'+
    '<div class="check-row"><div><b style="font-size:.92rem;display:block">E-mail</b><small class="muted">'+(a.emailDisponivel?'Para '+esc(me().email)+'. Mensagens da conversa não vão por e-mail.':'O envio de e-mail não está configurado no servidor.')+'</small></div>'+
      '<button class="switch'+(a.email&&a.emailDisponivel?' on':'')+'" role="switch" aria-checked="'+!!(a.email&&a.emailDisponivel)+'" aria-label="Avisos por e-mail" '+(a.emailDisponivel?'':'disabled ')+'onclick="alternarAvisoEmail()"><span></span></button></div>'+
    '<div class="check-row"><div><b style="font-size:.92rem;display:block">Notificações do navegador</b>'+linhaPush+'</div>'+
      '<button class="switch'+(pushLigado?' on':'')+'" role="switch" aria-checked="'+pushLigado+'" aria-label="Notificações do navegador" '+(pushSuportado()&&perm!=='denied'?'':'disabled ')+'onclick="alternarAvisoPush()"><span></span></button></div>'+
    (a.smsDisponivel?'<div class="check-row"><div><b style="font-size:.92rem;display:block">SMS</b><small class="muted">'+(me().phone?'Só pedidos novos e chamados, para '+esc(me().phone)+'.':'Cadastre um telefone no Perfil para receber.')+'</small></div>'+
      '<button class="switch'+(a.sms&&me().phone?' on':'')+'" role="switch" aria-checked="'+!!(a.sms&&me().phone)+'" aria-label="Avisos por SMS" '+(me().phone?'':'disabled ')+'onclick="alternarAvisoSms()"><span></span></button></div>':'')+
    (pushLigado?'<button class="btn btn-ghost btn-sm" style="margin-top:10px" onclick="testarPush()">'+ic('bell')+'Mandar um aviso de teste</button>':'')+
  '</div>';
}
async function salvarPreferenciasAvisos(email,push){
  avisosConta=Object.assign(avisosConta,await apiJSON('/api/conta/avisos','PUT',{email,push},'Não foi possível salvar.'));
}
async function alternarAvisoEmail(){
  if(avisosOcupado)return;avisosOcupado=true;
  try{await salvarPreferenciasAvisos(!avisosConta.email,avisosConta.push);toast(avisosConta.email?'Avisos por e-mail ligados':'Avisos por e-mail desligados','ok');}
  catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');}
  avisosOcupado=false;const c=$('#secaoAvisos');if(c)c.outerHTML=secaoAvisos();
}
async function alternarAvisoSms(){
  if(avisosOcupado)return;avisosOcupado=true;
  try{avisosConta=Object.assign(avisosConta,await apiJSON('/api/conta/avisos','PUT',{email:avisosConta.email,push:avisosConta.push,sms:!avisosConta.sms},'Não foi possível salvar.'));
    toast(avisosConta.sms?'Avisos por SMS ligados':'Avisos por SMS desligados','ok');}
  catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');}
  avisosOcupado=false;const c=$('#secaoAvisos');if(c)c.outerHTML=secaoAvisos();
}
async function alternarAvisoPush(){
  if(avisosOcupado||!pushSuportado())return;avisosOcupado=true;
  try{
    const atual=await inscricaoAtual();
    if(atual&&avisosConta.push&&Notification.permission==='granted'){
      await apiJSON('/api/conta/avisos/push','DELETE',{endpoint:atual.endpoint}).catch(()=>{});
      await atual.unsubscribe();
      avisosConta.nesteNavegador=false;toast('Notificações desligadas neste navegador','ok');
    }else{
      const perm=await Notification.requestPermission();
      if(perm!=='granted'){toast('O navegador não deu permissão para notificações.','err');}
      else{
        const reg=await navigator.serviceWorker.register('/js/sw.js',{scope:'/js/'});
        await navigator.serviceWorker.ready;
        const s=atual||await reg.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:base64ParaBytes(avisosConta.chavePush)});
        const j=s.toJSON();
        await apiJSON('/api/conta/avisos/push','POST',{endpoint:j.endpoint,keys:j.keys},'Não foi possível ligar as notificações.');
        if(!avisosConta.push)await salvarPreferenciasAvisos(avisosConta.email,true);
        avisosConta.nesteNavegador=true;avisosConta.navegadores=(avisosConta.navegadores||0)+(atual?0:1);
        toast('Notificações ligadas neste navegador','ok');
      }
    }
  }catch(e){toast(erroLegivel(e,'Não foi possível mudar as notificações.'),'err');}
  avisosOcupado=false;const c=$('#secaoAvisos');if(c)c.outerHTML=secaoAvisos();
}
async function testarPush(){
  try{const r=await apiJSON('/api/conta/avisos/push/teste','POST');toast(r.entregues?'Aviso enviado. Ele aparece em alguns segundos.':'Nenhum navegador recebeu. Desligue e ligue de novo.',r.entregues?'ok':'err');}
  catch(e){toast(erroLegivel(e,'Não foi possível enviar o teste.'),'err');}
}
function base64ParaBytes(b64){
  const p='='.repeat((4-b64.length%4)%4),s=atob((b64+p).replace(/-/g,'+').replace(/_/g,'/'));
  return Uint8Array.from(s,c=>c.charCodeAt(0));
}
