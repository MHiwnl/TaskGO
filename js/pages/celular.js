/* =========================================================
   CELULAR SIMULADO (página inicial, "para profissionais")
   O app do profissional dentro de um celular desenhado em CSS. Protótipo:
   dados fictícios, nada é enviado ao servidor. Mostra a regra do produto:
   o endereço e a conversa só abrem depois do aceite.
   ========================================================= */
const CEL_PEDIDOS=[
  {id:'p1',servico:'Troca de chuveiro',ico:'drop',bairro:'Boa Viagem',dist:'2,1 km',ha:'há 3 min',valor:'R$ 120 a R$ 180',cliente:'Marina',endereco:'Rua de Exemplo, 100 · apto 302',nota:'O chuveiro desarma o disjuntor quando liga no quente.',fala:'Oi! O chuveiro fica no banheiro de cima.'},
  {id:'p2',servico:'Tomada queimada',ico:'bolt',bairro:'Pina',dist:'3,4 km',ha:'há 12 min',valor:'R$ 80 a R$ 130',cliente:'Rafael',endereco:'Rua da Aurora, 45 · casa',nota:'Tomada da cozinha escureceu e parou de funcionar.',fala:'Boa tarde! É a tomada atrás da geladeira.'},
  {id:'p3',servico:'Instalar ventilador de teto',ico:'wind',bairro:'Espinheiro',dist:'4,8 km',ha:'há 26 min',valor:'R$ 150 a R$ 220',cliente:'Sônia',endereco:'Rua Inventada, 12 · apto 1101',nota:'Já tenho o ventilador, falta só instalar no quarto.',fala:'Olá! O ventilador já está na caixa, no quarto.'}
];
const CEL_STATUS=['Aceito','A caminho','No local','Em andamento','Concluído'];
/* O botão principal diz o que o toque faz, a partir do status atual. */
const CEL_ACAO=['Estou a caminho','Cheguei ao local','Digitar código','Concluir serviço'];
/* Código de 4 dígitos que o cliente informa na chegada; sem ele, o serviço não começa. */
const CEL_CODIGO='2217';
const CEL_AVALIACAO={p1:'Chegou no horário e explicou o que era o problema.',p2:'Resolveu rápido e deixou tudo limpo.',p3:'Ventilador instalado e funcionando. Recomendo.'};
const CEL_RESPOSTAS=['Estou a caminho.','Chego em 20 minutos.','Pode mandar uma foto?'];
const CEL_VOLTA={'Estou a caminho.':'Perfeito, estou em casa.','Chego em 20 minutos.':'Combinado, te espero.','Pode mandar uma foto?':'Mando sim, um minuto.'};
/* Começa com um pedido aberto: a primeira coisa que a pessoa vê é a decisão de aceitar. */
let cel={tela:'pedido',pedido:'p1',recusados:[],aceito:null,status:0,msgs:[],escrevendo:false,aviso:'',tocou:false};

/* Livre: sem atendimento ou com o atendimento já concluído. */
function celLivre(){return !cel.aceito||cel.status===CEL_STATUS.length-1;}
function celPedido(){return CEL_PEDIDOS.find(p=>p.id===cel.pedido);}
function celIr(tela,id){if(id)cel.pedido=id;cel.tela=tela;cel.aviso='';celAtualizar(true);}
/* O aviso some sozinho depois de alguns segundos. */
let celAvisoTimer=null;
/* Respostas e avaliação chegam por timer; Recomeçar cancela os que ainda não dispararam. */
let celTimers=[];
function celDepois(f,ms){celTimers.push(setTimeout(f,ms));}
function celAvisar(msg){cel.aviso=msg;clearTimeout(celAvisoTimer);celAvisoTimer=setTimeout(()=>{cel.aviso='';document.querySelector('#celApp .cel-aviso')?.remove();},2800);}
function celRecusar(){cel.recusados.push(cel.pedido);cel.tela='lista';celAvisar('Pedido recusado. Ele volta para outros profissionais.');celAtualizar(true);}
/* Aba travada responde ao toque dizendo o que a destrava. */
function celTravada(){celAvisar('Aceite um pedido para abrir o atendimento e a conversa.');celAtualizar(false);}
function celAceitar(){cel.aceito=cel.pedido;cel.status=0;cel.msgs=[{eu:false,t:celPedido().fala}];cel.aviso='';celAtualizar(false,'aceite');}
function celAvancar(){if(cel.status<CEL_STATUS.length-1){cel.status++;celAtualizar(false,'status');
  if(cel.status===CEL_STATUS.length-1){cel.avaliado=false;celDepois(()=>{cel.avaliado=true;if(document.querySelector('.cel'))celAtualizar(false,'avaliacao');},1400);}}}
function celRecomecar(){celTimers.forEach(clearTimeout);celTimers=[];cel={tela:'pedido',pedido:'p1',recusados:[],aceito:null,status:0,msgs:[],escrevendo:false,aviso:'',tocou:true};celAtualizar(true);}
function celResponder(t){
  if(cel.escrevendo)return;
  cel.msgs.push({eu:true,t});cel.escrevendo=true;celAtualizar(false);
  celDepois(()=>{cel.msgs.push({eu:false,t:CEL_VOLTA[t]||'Certo!'});cel.escrevendo=false;if(document.querySelector('.cel'))celAtualizar(false);},1100);
}

/* Barra de abas do app: Atendimento e Conversa só abrem depois do aceite. */
function celAbas(){
  const travado=!cel.aceito;
  const atual=cel.tela==='pedido'&&cel.pedido!==cel.aceito?'lista':cel.tela;
  const aba=(tela,rotulo,icone,bloq)=>'<button type="button" class="cel-aba'+(atual===tela?' on':'')+'"'+(atual===tela?' aria-current="page"':'')+(bloq?' aria-disabled="true" onclick="celTravada()"':' onclick="celIr(\''+tela+'\''+(tela!=='lista'?',\''+cel.aceito+'\'':'')+')"')+'>'+ic(bloq?'lock':icone)+'<span>'+rotulo+'</span></button>';
  return '<nav class="cel-abas" aria-label="Abas do aplicativo simulado">'+aba('lista','Pedidos','target',false)+aba('pedido','Atendimento','list',travado)+aba('conversa','Conversa','chat',travado)+'</nav>';
}
function celLista(){
  const lista=CEL_PEDIDOS.filter(p=>!cel.recusados.includes(p.id)&&p.id!==cel.aceito);
  const mexeu=cel.aceito||cel.recusados.length;
  return '<div class="cel-tela-in"><h3 class="cel-titulo">Oportunidades</h3><p class="cel-sub">Elétrica · até 5 km de você</p>'+
    (cel.aceito?'<button type="button" class="cel-ativo" onclick="celIr(\'pedido\',\''+cel.aceito+'\')"><span>'+(celLivre()?'Concluído hoje':'Em atendimento')+'</span><b>'+celPedidoPor(cel.aceito).servico+' · '+CEL_STATUS[cel.status].toLowerCase()+'</b>'+ic('arrow')+'</button>':'')+
    (lista.length?'<ul class="cel-lista">'+lista.map(p=>'<li><button type="button" onclick="celIr(\'pedido\',\''+p.id+'\')">'+ic(p.ico)+'<span><b>'+p.servico+'</b><small>'+p.bairro+' · '+p.dist+' · '+p.ha+'</small></span><em>'+p.valor.replace(' a ','–')+'</em></button></li>').join('')+'</ul>':
      '<p class="cel-vazio">Nenhum pedido novo agora. Os próximos aparecem aqui.</p>')+
    (mexeu?'<button type="button" class="cel-recomecar" onclick="celRecomecar()">'+ic('refresh')+'Recomeçar demonstração</button>':'')+
  '</div>';
}
function celPedidoPor(id){return CEL_PEDIDOS.find(p=>p.id===id);}
/* As 4 casas do código de chegada: vazias até o cliente informar, depois preenchidas uma a uma. */
function celCasas(cheio,animar){return '<span class="cel-casas" role="group" aria-label="'+(cheio?'Código '+CEL_CODIGO.split('').join(' '):'Código ainda não informado')+'">'+
  CEL_CODIGO.split('').map((d,i)=>'<b'+(animar?' style="animation-delay:'+(i*90)+'ms" class="nova"':'')+'>'+(cheio?d:'')+'</b>').join('')+'</span>';}
function celDetalhe(animar){
  const p=celPedido(),meu=cel.aceito===p.id,outro=!meu&&!celLivre();
  const concluido=meu&&cel.status===CEL_STATUS.length-1;
  const linha=(chave,feita,valor,cls)=>'<li class="ticket-linha'+(feita?' feita':'')+(cls?' '+cls:'')+'"><span class="ticket-chave">'+chave+'</span><span>'+valor+'</span></li>';
  const novo=animar==='aceite',trava='<span class="cel-trava">'+ic('lock')+'</span>';
  return '<div class="cel-tela-in">'+
    '<button type="button" class="cel-voltar" onclick="celIr(\'lista\')">'+ic('arrowLeft')+'Oportunidades</button>'+
    '<h3 class="cel-titulo">'+p.servico+'</h3><p class="cel-sub">'+p.nota+'</p>'+
    (meu?'<div class="cel-etapas" role="img" aria-label="Andamento: '+CEL_STATUS[cel.status]+'">'+CEL_STATUS.map((_,i)=>'<i'+(i<=cel.status?' class="feita'+(i===cel.status&&animar==='status'?' nova':'')+'"':'')+'></i>').join('')+'</div>':'')+
    '<ul class="ticket-linhas cel-linhas">'+
      linha('Bairro',true,p.bairro+' · '+p.dist)+
      linha('Valor',true,meu?'A combinar · ref. '+p.valor.replace(' a R$ ','–'):p.valor)+
      linha('Cliente',meu,meu?p.cliente:trava+'Aparece depois do aceite',novo?'nova':'')+
      linha('Endereço',meu,meu?p.endereco:trava+'Só aparece depois do aceite',novo?'nova tarja':'')+
      (meu&&cel.status>=2?linha('Código',cel.status>=3,celCasas(cel.status>=3,animar==='status'&&cel.status===3)+'<small>'+(cel.status>=3?'Confirmado. Serviço liberado.':'Peça os 4 números a '+p.cliente+'.')+'</small>'):'')+
      linha('Status',meu,meu?CEL_STATUS[cel.status]:'Aguardando sua resposta',animar==='status'?'nova':'')+
    '</ul>'+
    (meu?(concluido?'<p class="cel-fim">'+ic('checkCircle')+'Atendimento concluído.</p>'+(cel.avaliado?'<div class="cel-avaliacao"><span>'+p.cliente+' avaliou</span><b aria-label="5 de 5 estrelas">'+ic('star').repeat(5)+'</b><p>“'+CEL_AVALIACAO[p.id]+'”</p></div>':'<p class="cel-vazio">Esperando a avaliação de '+p.cliente+'…</p>')+'<button type="button" class="btn btn-ghost btn-block" onclick="celRecomecar()">'+ic('refresh')+'Recomeçar</button>':
        '<div class="cel-acoes"><button type="button" class="btn btn-ghost" onclick="celIr(\'conversa\')">'+ic('chat')+'Conversar</button><button type="button" class="btn btn-primary" onclick="celAvancar()">'+CEL_ACAO[cel.status]+ic('arrow')+'</button></div>'):
     outro?'<p class="cel-vazio">Você já está em um atendimento. Termine antes de aceitar outro.</p>':
     '<div class="cel-acoes"><button type="button" class="btn btn-ghost" onclick="celRecusar()">Recusar</button><button type="button" class="btn btn-primary'+(cel.tocou?'':' cel-convite')+'" onclick="celAceitar()">'+ic('check')+'Aceitar</button></div>')+
  '</div>';
}
function celConversa(){
  const p=celPedidoPor(cel.aceito);
  return '<div class="cel-tela-in cel-chat-in">'+
    '<button type="button" class="cel-voltar" onclick="celIr(\'pedido\')">'+ic('arrowLeft')+p.servico+'</button>'+
    '<div class="cel-chat-quem">'+avatar({name:p.cliente},28)+'<b>'+p.cliente+'</b><span>Cliente</span></div>'+
    '<div class="cel-msgs" aria-live="polite">'+cel.msgs.map(m=>'<p class="cel-msg'+(m.eu?' eu':'')+'">'+esc(m.t)+'</p>').join('')+
      (cel.escrevendo?'<p class="cel-msg cel-digitando" aria-label="'+p.cliente+' está escrevendo"><i></i><i></i><i></i></p>':'')+'</div>'+
    '<div class="cel-respostas" role="group" aria-label="Respostas rápidas">'+CEL_RESPOSTAS.map(t=>'<button type="button" class="cel-chip"'+(cel.escrevendo?' disabled':'')+' onclick="celResponder(\''+t+'\')">'+t+'</button>').join('')+'</div>'+
  '</div>';
}
function celTela(animar){
  if(cel.tela==='pedido'&&celPedido())return celDetalhe(animar);
  if(cel.tela==='conversa'&&cel.aceito)return celConversa();
  return celLista();
}
function celConteudo(animar){
  return '<div class="cel-status-barra" aria-hidden="true"><span>14:20</span><span class="cel-ilha"></span><span class="cel-bateria"><i></i></span></div>'+
    '<div class="cel-app-topo"><span class="cel-marca">Task<b>GO</b></span><span class="cel-proto">Prévia</span></div>'+
    '<div class="cel-tela'+(animar===true?' entra':'')+'" data-tela="'+cel.tela+'">'+celTela(animar)+'</div>'+
    (cel.aviso?'<p class="cel-aviso" role="status">'+cel.aviso+'</p>':'')+
    celAbas();
}
/* Redesenha só a tela do celular, sem mexer no resto da página; o foco volta ao lugar equivalente. */
function celAtualizar(trocouTela,animar){
  const caixa=document.getElementById('celApp');if(!caixa)return;
  cel.tocou=true;
  const foco=document.activeElement&&caixa.contains(document.activeElement)?document.activeElement.textContent:null;
  caixa.innerHTML=celConteudo(trocouTela?true:animar);
  const msgs=caixa.querySelector('.cel-msgs');if(msgs)msgs.scrollTop=msgs.scrollHeight;
  /* A avaliação chega embaixo do pedido: a tela rola até ela. */
  if(animar==='avaliacao'){const tela=caixa.querySelector('.cel-tela');if(tela)tela.scrollTo({top:tela.scrollHeight,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'});}
  if(foco!==null){const alvo=[...caixa.querySelectorAll('button:not([disabled])')].find(b=>b.textContent===foco)||caixa.querySelector('.cel-acoes .btn-primary')||caixa.querySelector('.cel-tela button');alvo?.focus({preventScroll:true});}
  const voz=document.getElementById('celVoz');
  if(voz)voz.textContent=cel.aviso?cel.aviso:animar==='aceite'?'Pedido aceito. Endereço e conversa liberados.':animar==='avaliacao'&&cel.aceito?celPedidoPor(cel.aceito).cliente+' avaliou o atendimento com 5 estrelas.':animar==='status'?(cel.status===3?'Código confirmado. Serviço em andamento.':'Status: '+CEL_STATUS[cel.status]+'.'):trocouTela?({lista:'Oportunidades',pedido:'Detalhes do pedido',conversa:'Conversa com o cliente'}[cel.tela]||''):'';
}
function celularSimulado(){
  return '<figure class="cel-palco"><div class="cel-fundo">'+
    '<div class="cel" role="region" aria-label="Prévia do aplicativo TaskGO para profissionais">'+
      '<div class="cel-tela-moldura" id="celApp">'+celConteudo(false)+'</div>'+
    '</div></div>'+
    '<p class="leitor" role="status" id="celVoz"></p>'+
  '</figure>';
}
