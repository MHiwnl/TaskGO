/* =========================================================
   AJUDA RÁPIDA
   Qualquer pessoa com identidade verificada liga a ajuda rápida e passa a receber,
   por perto, pedidos de ajuda do dia a dia (pneu furado, bateria, carregar algo...).
   No primeiro clique em ligar, um passo a passo explica como funciona.
   ========================================================= */
const AJ={estado:null,carregando:false,passo:0,salvando:false};
const AJUDA_MINIMO=50,AJUDA_TAXA_PCT=10;

function ajudanteAtivo(){return !!(AJ.estado&&AJ.estado.ativo);}
function podeTerAjudaRapida(u){return !!u&&(u.role==='cliente'||u.role==='profissional');}

async function carregarAjudaRapida(forcar){
  const u=me();
  if(DEMO_MODE||!podeTerAjudaRapida(u)||AJ.carregando||(AJ.estado&&!forcar))return;
  AJ.carregando=true;
  try{AJ.estado=await apiJSON('/api/ajuda-rapida');}catch(e){AJ.estado=AJ.estado||{ativo:false,conhece:false,verificado:false};}
  finally{AJ.carregando=false;}
  if(ajudanteAtivo())ligarPresenca();
  if(route.page==='app'&&['inicio','carteira'].includes(route.sub))render();
}

/* Bloco do Início: liga e desliga. */
function blocoAjudaRapida(){
  const u=me();
  if(DEMO_MODE||!podeTerAjudaRapida(u))return '';
  if(!AJ.estado){carregarAjudaRapida();return '';}
  const on=ajudanteAtivo();
  return '<section class="inicio-bloco ajuda-bloco'+(on?' ligada':'')+'" aria-labelledby="ini-ajuda"><div class="inicio-cab"><h2 class="sub" id="ini-ajuda">'+ic('boia','ic-svc-ajuda')+'Ajuda rápida</h2>'+
      '<button class="switch'+(on?' on':'')+'" role="switch" aria-checked="'+on+'" aria-label="Receber pedidos de ajuda rápida"'+(AJ.salvando?' disabled':'')+' onclick="alternarAjudaRapida()"><span></span></button></div>'+
    '<p class="ajuda-estado">'+(on?'<b>Ligada.</b> Pedidos de ajuda a até 40 km chegam para você enquanto esta área estiver aberta.':
      '<b>Desligada.</b> Ligue para receber pedidos de ajuda de pessoas perto de você, a partir de '+money(AJUDA_MINIMO)+'.')+'</p>'+
    (AJ.estado.conhece?'<button type="button" class="link-btn" onclick="abrirPassoAjuda(0)">Ver como funciona</button>':'')+
  '</section>';
}

async function alternarAjudaRapida(){
  if(!AJ.estado||AJ.salvando)return;
  if(AJ.estado.ativo)return salvarAjudaRapida(false);
  if(!AJ.estado.conhece)return abrirPassoAjuda(0);
  if(!AJ.estado.verificado)return pedirVerificacaoAjuda();
  salvarAjudaRapida(true);
}
async function salvarAjudaRapida(ligar){
  AJ.salvando=true;render();
  try{
    AJ.estado=await apiJSON('/api/ajuda-rapida','PUT',{ativo:ligar},'Não foi possível mudar a ajuda rápida.');
    if(ligar){ligarPresenca();toast('Ajuda rápida ligada','ok');}
    else{if(me().role!=='profissional')desligarPresenca();toast('Ajuda rápida desligada','ok');}
  }catch(e){toast(erroLegivel(e,'Não foi possível mudar a ajuda rápida.'),'err');}
  finally{AJ.salvando=false;render();}
}
function pedirVerificacaoAjuda(){
  closeModal();
  toast('Para ajudar, verifique sua identidade primeiro.','err');
  if(typeof abrirVerificacao==='function')abrirVerificacao();
}

/* ---------- Passo a passo (primeiro clique) ---------- */
const PASSOS_AJUDA=[
  {foto:'pneu.jpg',alt:'Roda de carro com o pneu furado, encostada na calçada',
   leve:'alguém perto',forte:'precisa de uma mão agora.',
   texto:'Pneu furado, bateria arriada, um móvel para carregar, uma porta trancada. Quem pede conta o problema, marca onde está e oferece um valor, a partir de '+'R$ 50'+'.'},
  {foto:'celular.jpg',alt:'Pessoa olhando o celular na rua, à noite',
   leve:'o pedido',forte:'chega no seu celular.',
   texto:'Com a ajuda rápida ligada, os pedidos a até 40 km aparecem na tela com o problema, a distância e o valor. Aceite se souber resolver; se não, toque em Agora não.'},
  {foto:'atolado.jpg',alt:'Duas pessoas ajudando a tirar um carro vermelho preso no cascalho',
   leve:'vá até lá',forte:'e resolva.',
   texto:'Quando a pessoa confirmar você, o endereço aparece e o caminho é acompanhado no mapa. Ao chegar, peça o código de 4 números para começar.'},
  {foto:'entrega.jpg',alt:'Duas pessoas segurando uma caixa de papelão, uma entregando para a outra',
   leve:'o dinheiro',forte:'cai na sua carteira.',
   texto:'A pessoa confirma que deu certo e você recebe o valor menos 10% da TaskGO. Na Carteira, você resgata por Pix.'},
  {foto:'caixas.jpg',alt:'Casal carregando caixas e uma planta para dentro de casa',
   leve:'só aceite',forte:'o que sabe fazer.',
   lista:['Gás, parte elétrica da casa e motor do carro ficam com os profissionais das categorias.','Combine os detalhes pela conversa do pedido.','Se algo der errado, use o botão de emergência do pedido.']}
];
function abrirPassoAjuda(i){
  AJ.passo=Math.max(0,Math.min(PASSOS_AJUDA.length-1,i));
  openModal(htmlPassoAjuda(),{className:'modal-passos',onOpen:()=>{const m=$('#modal-root .modal');if(m)m.addEventListener('keydown',teclaPassoAjuda);}});
}
function teclaPassoAjuda(e){
  if(e.key==='ArrowRight'&&AJ.passo<PASSOS_AJUDA.length-1){e.preventDefault();irPassoAjuda(AJ.passo+1);}
  if(e.key==='ArrowLeft'&&AJ.passo>0){e.preventDefault();irPassoAjuda(AJ.passo-1);}
}
function irPassoAjuda(i){
  AJ.passo=i;
  const corpo=$('#modal-root .modal');if(!corpo)return;
  corpo.innerHTML=htmlPassoAjuda();
  const t=corpo.querySelector('h2');if(t)corpo.setAttribute('aria-label',t.textContent.trim());
  const b=corpo.querySelector('.passos-acoes .btn-primary');if(b)b.focus({preventScroll:true});
}
function htmlPassoAjuda(){
  const p=PASSOS_AJUDA[AJ.passo],n=PASSOS_AJUDA.length,ultimo=AJ.passo===n-1,verificado=!!(AJ.estado&&AJ.estado.verificado);
  const fim=AJ.estado&&AJ.estado.conhece?'<button type="button" class="btn btn-primary" onclick="closeModal()">Entendi</button>':
    verificado?'<button type="button" class="btn btn-primary" onclick="closeModal();salvarAjudaRapida(true)"'+(AJ.salvando?' disabled':'')+'>'+ic('boia')+'Ligar ajuda rápida</button>':
    '<button type="button" class="btn btn-primary" onclick="pedirVerificacaoAjuda()">'+ic('shield')+'Verificar minha identidade</button>';
  return '<figure class="passo-foto"><img src="assets/ajuda/'+p.foto+'" alt="'+esc(p.alt)+'" width="900" height="600" decoding="async"></figure>'+
    '<div class="passo-corpo">'+
      '<ol class="passo-marcas" aria-label="Passo '+(AJ.passo+1)+' de '+n+'">'+PASSOS_AJUDA.map((_,k)=>'<li'+(k===AJ.passo?' class="atual" aria-current="step"':k<AJ.passo?' class="feito"':'')+'><button type="button" onclick="irPassoAjuda('+k+')" aria-label="Ir para o passo '+(k+1)+'"></button></li>').join('')+'</ol>'+
      '<h2 class="passo-titulo"><span>'+p.leve+'</span> <strong>'+p.forte+'</strong></h2>'+
      (p.texto?'<p class="passo-texto">'+esc(p.texto)+'</p>':'')+
      (p.lista?'<ul class="passo-lista">'+p.lista.map(x=>'<li>'+ic('check')+'<span>'+esc(x)+'</span></li>').join('')+'</ul>':'')+
      (ultimo&&!verificado&&!(AJ.estado&&AJ.estado.conhece)?'<p class="passo-nota">'+ic('shield')+'<span>Só quem tem a identidade verificada pode ajudar. A verificação leva um documento e uma selfie.</span></p>':'')+
      '<div class="passos-acoes">'+
        (AJ.passo?'<button type="button" class="btn btn-ghost" onclick="irPassoAjuda('+(AJ.passo-1)+')">'+ic('arrowLeft')+'Voltar</button>':'<button type="button" class="btn btn-ghost" onclick="closeModal()">Agora não</button>')+
        (ultimo?fim:'<button type="button" class="btn btn-primary" onclick="irPassoAjuda('+(AJ.passo+1)+')">Próximo'+ic('arrow')+'</button>')+
      '</div>'+
    '</div>';
}
