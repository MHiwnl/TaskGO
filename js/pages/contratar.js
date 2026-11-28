/* =========================================================
   CONTRATAR
   Primeiro a pessoa escolhe: agendar (o pedido vai para o mural da categoria e os
   profissionais aceitam ou mandam proposta) ou chamar agora (o pedido vai em ondas
   para os profissionais online mais perto, como um aplicativo de corrida).
   Sem numeração de etapas: cada tela tem um título e um Voltar bem visível.
   ========================================================= */
const W_VAZIO=()=>({fotos:[],pag:null,modo:null,step:1,service:'',urgency:'hoje',addr:'',addrId:'',notes:'',date:'',oferta:null,presetService:'',enviando:false,lat:null,lng:null,geoEstado:'',reaproveitado:false,proEmail:'',proId:'',proNome:'',respostas:{}});
let W=W_VAZIO();
const PASSOS={agendar:['servico','local','detalhes','valor','confirmar'],agora:['servico','valor','local'],ajuda:['problema','valor','local'],obra:['servico','local','detalhes','valor','confirmar']};
/* Ajuda rápida e Chamar agora saem na hora, em ondas, para quem está perto. */
const modoNaHora=()=>W.modo==='agora'||W.modo==='ajuda';
function passoAtual(){return W.modo?PASSOS[W.modo][W.step-1]:null;}

function pageContratar(){
  /* Contratar exige conta de cliente: sem login, vai para Entrar e volta para cá depois. */
  if(!me()){go('/entrar?next=app/contratar');return '';}
  if(ehEquipe(me()))return dashHead('Contratar','Pedir serviços é com uma conta de cliente. Esta é uma conta de '+(NOME_TIPO[me().role]||'equipe').toLowerCase()+'.')+'<a class="btn btn-primary" href="#/app">Ir para minha área '+ic('arrow')+'</a>';
  if(me().role==='profissional')return dashHead('Contratar','Pedir serviços é com uma conta de cliente. Na sua conta de profissional, os pedidos chegam em Oportunidades.')+'<a class="btn btn-primary" href="#/app/oportunidades">Ver oportunidades '+ic('arrow')+'</a>';
  /* Para fazer pedidos, a identidade precisa estar verificada. */
  if(precisaVerificacao())return route.page==='app'?dashHead('Contratar','Para fazer pedidos, a TaskGO confere a sua identidade uma vez.')+painelVerificacaoServico('pedir'):folha('contratar',duo('verifique sua identidade','para fazer pedidos',1),'cabeca')+'<section class="folha-densa" style="padding-top:0"><div class="wrap folha-in"><div class="folha-corpo">'+painelVerificacaoServico('pedir')+'</div></div></section>';
  if(route.q.servico&&W.step===1&&W.presetService!==route.q.servico){W.service=route.q.servico;W.presetService=route.q.servico;W.proEmail=W.proId='';W.respostas={};}
  const p=passoAtual();
  const body=!p?telaModo():p==='servico'?wServico():p==='problema'?wProblemaAjuda():p==='local'?(W.modo==='agora'||W.modo==='ajuda'?wLocalAgora():wLocalAgendar()):p==='detalhes'?wDetalhes():p==='valor'?wValor():p==='confirmar'?wConfirmar():wLocalAgora();
  const aviso=W.reaproveitado&&p?'<div class="aviso-dados" role="status">'+ic('refresh')+'<p><b>Seus dados continuam aqui.</b> Ajuste o que quiser e envie de novo.</p><button type="button" class="icon-btn" aria-label="Fechar aviso" onclick="W.reaproveitado=false;render()">'+ic('x')+'</button></div>':'';
  const paraPro=W.proEmail&&W.modo==='agendar'&&p?'<div class="aviso-dados" role="status">'+ic('user')+'<p>O pedido vai direto para <b>'+esc(W.proNome)+'</b>. Se não puder atender, você é avisado.</p><button type="button" class="link-btn" onclick="W.proEmail=W.proId=\'\';render()">Enviar para todos</button></div>':'';
  const sub=!p?'Você escolhe: marcar para depois, chamar um profissional agora ou pedir uma ajuda rápida.':W.modo==='ajuda'?'Ajuda rápida: o pedido vai para pessoas verificadas perto de você.':W.modo==='obra'?'Obra: os profissionais da categoria mandam orçamentos com etapas, prazo e valor.':W.modo==='agora'?'Chamar agora: o pedido vai para os profissionais online mais perto de você.':'Agendar: os profissionais da categoria aceitam o seu valor ou mandam outra proposta.';
  if(route.page==='app')return dashHead('Contratar',sub)+avisoPendenciaContratar()+aviso+paraPro+'<div class="card contratar-card" data-passo="'+(p||'modo')+'">'+body+'</div>';
  return folha('contratar',duo('descreva o serviço','que você precisa',1)+'<p class="corpo">'+sub+'</p>','cabeca')+
  '<section class="folha-densa" style="padding-top:0"><div class="wrap folha-in"><div class="folha-corpo">'+aviso+paraPro+'<div class="card contratar-card">'+body+'</div></div></div></section>';
}
function wSet(k,v){
  W[k]=v;
  if(k==='service'){W.oferta=null;W.proEmail=W.proId='';W.respostas={};if(!servicoRecorrente(v))W.rec=null;}
  render();
}
/* Cabeçalho de cada tela: Voltar bem à vista, à esquerda do título. */
function cabecalhoPasso(titulo,texto){
  return '<div class="passo-cab"><button type="button" class="btn-voltar" onclick="wVoltar()">'+ic('arrowLeft')+'<span>Voltar</span></button>'+
    '<div class="passo-tit"><h2 class="sub">'+titulo+'</h2>'+(texto?'<p class="muted">'+texto+'</p>':'')+'</div></div>';
}
function rodapePasso(botao){
  return '<div class="passo-rodape"><button type="button" class="btn-voltar" onclick="wVoltar()">'+ic('arrowLeft')+'<span>Voltar</span></button>'+botao+'</div>';
}
function wVoltar(){
  if(W.step>1){W.step--;render();return;}
  W.modo=null;render();
}
/* Aviso do passo: marca o campo com o erro (aria-invalid, aria-describedby) e também mostra o toast. */
function avisoCampo(id,msg,tipo){marcarCampo(document.getElementById(id),msg);return toast(msg,tipo||'err');}
function wGo(n){
  const destino=PASSOS[W.modo][n-1];
  if(n>W.step){
    if(!W.service)return toast('Escolha um serviço para continuar','err');
    const passou=PASSOS[W.modo].slice(0,n-1);
    if(passou.includes('local')){
      if(!String(W.addr||'').trim())return avisoCampo('wAddr','Informe o endereço do atendimento');
      if(W.lat==null)return toast('Marque no mapa o local do atendimento','err');
    }
    if(passou.includes('detalhes')&&W.modo!=='obra'&&W.urgency==='agendar'&&!W.date)return avisoCampo('wData','Escolha a data e a hora do atendimento');
    if(passou.includes('problema')&&String(W.notes||'').trim().length<10)return avisoCampo('wNotas','Conte em poucas palavras do que você precisa');
    if(passou.includes('valor')&&!recValido())return;
    if(passou.includes('valor')){const v=Number(W.oferta),min=W.modo==='ajuda'?AJUDA_MINIMO:1;if(!(v>=min&&v<=100000))return avisoCampo('wOferta',W.modo==='ajuda'?'Na ajuda rápida, o valor mínimo é '+money(AJUDA_MINIMO):'Informe quanto você oferece, de R$ 1 a R$ 100.000','err');
      if(W.modo!=='obra'&&v>=3000){sugerirObra();return;}}
    if(W.modo==='obra'&&passou.includes('detalhes')&&String(W.notes||'').trim().length<20)return avisoCampo('wNotas','Descreva a obra em pelo menos 20 letras: o que fazer, medidas e o que já existe.');
  }
  W.step=n;render();
  const card=document.querySelector('.contratar-card');if(card&&card.getBoundingClientRect().top<0)card.scrollIntoView({block:'start',behavior:'smooth'});
}

/* ---------- Agendar ou chamar agora ---------- */
function telaModo(){
  return '<div class="modo-escolha"><h2 class="sub">Você deseja agendar ou chamar um trabalhador agora?</h2>'+
  '<div class="modo-opcoes">'+
  '<button type="button" class="modo-opcao" onclick="escolherModo(\'agendar\')">'+ic('cal')+'<b>Agendar</b><span>Escolha o dia e a hora. Os profissionais da categoria aceitam o seu valor ou mandam outra proposta.</span><i class="modo-seta">'+ic('arrow')+'</i></button>'+
  '<button type="button" class="modo-opcao agora" onclick="escolherModo(\'agora\')">'+ic('bolt')+'<b>Chamar trabalhador AGORA</b><span>O pedido vai para os profissionais online mais perto. O primeiro que aceitar vai até você, e você vê quanto tempo falta.</span><i class="modo-seta">'+ic('arrow')+'</i></button>'+
  '<button type="button" class="modo-opcao obra" onclick="escolherModo(\'obra\')">'+ic('hammer')+'<b>Obra ou serviço de vários dias</b><span>Pedreiro, reforma, pintura da casa toda, acima de R$ 1.000. Você recebe orçamentos com etapas e paga uma etapa por vez, só liberada quando você aprova.</span><i class="modo-seta">'+ic('arrow')+'</i></button>'+
  '<button type="button" class="modo-opcao ajuda" onclick="escolherModo(\'ajuda\')">'+ic('boia','ic-svc-ajuda')+'<b>Pedir ajuda rápida</b><span>Pneu furado, bateria arriada, algo para carregar, porta trancada. Pessoas verificadas por perto recebem o pedido e uma delas vai até você.</span><i class="modo-seta">'+ic('arrow')+'</i></button>'+
  '</div></div>';
}
/* A partir de R$ 1.000, o pedido vira obra (orçamento em etapas). */
function sugerirObra(dias){
  openModal('<div class="modal-head"><div><h3>'+(dias?'Serviço de vários dias é obra':'A partir de R$ 3.000, peça orçamento')+'</h3><p>'+(dias?'Serviços de mais de um dia':'Serviços a partir de R$ 3.000')+' são contratados como obra: os profissionais mandam orçamentos com etapas e você paga uma etapa por vez, liberada só quando aprovar.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Mudar o valor</button><button class="btn btn-primary" onclick="closeModal();virarObra()">'+ic('hammer')+'Pedir como obra</button></div>');
}
function virarObra(){const s=W.service==='ajuda'?'':W.service;W.maisDeUmDia=false;W.modo='obra';W.service=s;W.urgency='hoje';W.step=s?2:1;render();}
function escolherModo(m){
  if(W.modo==='ajuda'&&m!=='ajuda'&&W.service==='ajuda')W.service='';
  W.modo=m;W.step=1;if(m!=='agendar')W.proEmail=W.proId='';
  if(m==='ajuda'){W.service='ajuda';W.oferta=W.oferta!=null&&W.oferta<AJUDA_MINIMO?null:W.oferta;}
  if(m==='agora'||m==='ajuda')W.urgency='agora';else if(W.urgency==='agora')W.urgency='hoje';
  if(m==='obra'&&!W.inicioObra)W.inicioObra=new Date(Date.now()+7*864e5).toISOString().slice(0,10);
  render();
}

/* ---------- Serviço ---------- */
/* Começa pelas 6 mais pedidas; a busca procura nas 14 pelo nome, pela descrição e pelas palavras do dia a dia. */
let buscaServico='',todasCategorias=false;
const semAcento=t=>semAcentoBusca(t);
function servicosVisiveis(){
  const q=semAcento(buscaServico);
  if(q)return SERVICES.filter(s=>semAcento(s.n+' '+s.d+' '+s.cat+' '+(TERMOS_SERVICO[s.id]||[]).join(' ')).includes(q));
  return todasCategorias?SERVICES:SERVICES.filter(s=>s.pop||s.id===W.service);
}
function sugestaoServico(){
  if(!buscaServico.trim()||servicosVisiveis().length)return null;
  return voceQuisDizer(buscaServico,SERVICES.map(s=>({rotulo:s.n,termos:TERMOS_SERVICO[s.id]||[],valor:s.id})));
}
function gradeServicos(){
  const lista=servicosVisiveis();
  if(!lista.length){
    const s=sugestaoServico();
    return '<div class="opt-vazio">'+(s?'<p class="quis-dizer">Você quis dizer: <button type="button" class="link-quis" onclick="aceitarSugestaoServico(\''+s.valor+'\')">'+esc(s.rotulo)+'?</button></p>':'')+
      '<p>Nenhuma categoria encontrada para "'+esc(buscaServico)+'". '+(s?'':'Tente outra palavra, como "chuveiro" ou "pintura".')+'</p></div>';
  }
  const botao=s=>'<button type="button" data-fk="opt-'+s.id+'" aria-pressed="'+(W.service===s.id)+'" class="opt '+(W.service===s.id?'sel':'')+'" onclick="wSet(\'service\',\''+s.id+'\')">'+ic(s.ico,'ic-svc-'+s.id)+'<b>'+s.n+'</b></button>';
  /* Todas as categorias: separadas pelos grupos do catálogo. */
  if(todasCategorias&&!buscaServico.trim())return GRUPOS_SERVICOS.map(g=>'<p class="opt-grupo">'+esc(g[0])+'</p>'+g[1].map(id=>botao(svc(id))).join('')).join('');
  return lista.map(botao).join('');
}
function aceitarSugestaoServico(id){buscaServico='';W.service=id;W.oferta=null;W.proEmail=W.proId='';W.respostas={};const b=$('#buscaServico');if(b)b.value='';render();toast('Escolhido: '+svc(id).n,'ok');}
/* A grade guarda a altura que tinha antes da busca: a tela não pula enquanto a pessoa digita. */
function buscarServico(v){
  buscaServico=v;
  const g=document.getElementById('optGrid');
  if(g){if(!g.dataset.alturaFixa){g.style.minHeight=g.offsetHeight+'px';g.dataset.alturaFixa='1';}g.innerHTML=gradeServicos();}
  const b=document.getElementById('optTodas');if(b)b.classList.toggle('oculto',!!buscaServico.trim());
}
function alternarCategorias(){todasCategorias=!todasCategorias;render();}
function wServico(){
  return cabecalhoPasso('Qual serviço você precisa?','Escolha a categoria mais próxima do seu problema ou procure pelo que está acontecendo.')+
  '<div class="opt-busca">'+ic('search')+'<label class="leitor" for="buscaServico">Procurar serviço</label><input class="input" id="buscaServico" type="search" placeholder="Ex.: chuveiro, vazamento, tomada" value="'+esc(buscaServico)+'" oninput="buscarServico(this.value)" autocomplete="off"></div>'+
  '<div class="opt-grid" id="optGrid" aria-live="polite">'+gradeServicos()+'</div>'+
  '<button type="button" class="opt-todas'+(buscaServico.trim()?' oculto':'')+'" id="optTodas" onclick="alternarCategorias()">'+(todasCategorias?'Mostrar só as mais pedidas':'Ver todas as '+SERVICES.length+' categorias')+ic(todasCategorias?'up':'down')+'</button>'+
  rodapePasso('<button class="btn btn-primary" onclick="wGo(2)">Continuar '+ic('arrow')+'</button>');
}

/* ---------- Problema (ajuda rápida) ---------- */
function wProblemaAjuda(){
  return cabecalhoPasso('Do que você precisa?','Conte em poucas palavras. Quem estiver perto lê isso antes de aceitar.')+
  '<div class="field"><label for="wNotas">O que está acontecendo</label><textarea class="textarea" id="wNotas" maxlength="1000" required placeholder="Ex.: pneu furado na Av. Boa Viagem, tenho o estepe mas não consigo soltar os parafusos" oninput="W.notes=this.value">'+esc(W.notes)+'</textarea>'+
  '<div class="hint">Diga o que precisa ser feito e o que você já tem no local (ferramenta, estepe, cabo).</div></div>'+
  campoFotosContratar()+
  rodapePasso('<button class="btn btn-primary" onclick="wGo(2)">Continuar '+ic('arrow')+'</button>');
}

/* ---------- Detalhes (agendar) ---------- */
function wDetalhes(){
  /* Só o que o cliente sabe responder: onde, quando e o que está acontecendo. O tamanho do serviço o profissional avalia. */
  if(W.modo==='obra')return cabecalhoPasso('Conte sobre a obra','Quando quer começar e o que precisa ser feito.')+
  '<div class="field"><label for="wInicioObra">Quando quer começar</label><input class="input" id="wInicioObra" type="date" min="'+new Date(Date.now()+864e5).toISOString().slice(0,10)+'" value="'+esc(W.inicioObra||'')+'" onchange="W.inicioObra=this.value"></div>'+
  '<div class="field"><label for="wNotas">O que precisa ser feito</label><textarea class="textarea" id="wNotas" required maxlength="3000" placeholder="'+esc(exemploObra(W.service))+'" oninput="W.notes=this.value">'+esc(W.notes)+'</textarea>'+
  '<div class="hint">Diga medidas, o que já existe e o que você quer no fim. Fotos ajudam muito no orçamento.</div></div>'+
  campoFotosContratar()+
  rodapePasso('<button class="btn btn-primary" onclick="wGo('+(W.step+1)+')">Continuar '+ic('arrow')+'</button>');
  return cabecalhoPasso('Conte os detalhes','Quando e o que está acontecendo.')+
  '<div class="grid g2" style="gap:0 16px">'+
  '<div class="field"><label for="wQuando">Quando</label><select class="select" id="wQuando" onchange="wSet(\'urgency\',this.value)">'+
  [['hoje','Ainda hoje'],['agendar','Escolher dia e hora']].map(o=>'<option value="'+o[0]+'" '+(W.urgency===o[0]?'selected':'')+'>'+o[1]+'</option>').join('')+'</select></div>'+
  (W.urgency==='agendar'?'<div class="field"><label for="wData">Data e hora desejadas</label><input class="input" id="wData" type="datetime-local" onchange="W.date=this.value" value="'+esc(W.date)+'"></div>':'')+
  '</div>'+
  '<fieldset class="field dias-servico"><legend class="rotulo-campo">O serviço leva mais de um dia?</legend><div class="formas" role="radiogroup">'+
    opcaoPag('wDias','nao',W.maisDeUmDia?'sim':'nao','Não, termina no mesmo dia','Pedido comum','clock','W.maisDeUmDia=false;marcarForma(this)')+
    opcaoPag('wDias','sim',W.maisDeUmDia?'sim':'nao','Sim, mais de um dia','Vira obra: orçamento em etapas','hammer','W.maisDeUmDia=true;marcarForma(this);sugerirObra(true)')+'</div></fieldset>'+
  '<div class="field"><label for="wNotas">Descreva o que você precisa (opcional)</label><textarea class="textarea" id="wNotas" placeholder="'+esc(exemploPedido(W.service))+'" oninput="W.notes=this.value">'+esc(W.notes)+'</textarea>'+
  '<div class="hint">Conte o que acontece, onde e quantas peças ou cômodos. O profissional usa isso para entender o tamanho do serviço.</div></div>'+
  wPerguntas()+campoFotosContratar()+
  rodapePasso('<button class="btn btn-primary" onclick="wGo('+(W.step+1)+')">Continuar '+ic('arrow')+'</button>');
}

/* Perguntas do serviço, opcionais: cada grupo aceita uma opção; marcar de novo desmarca. */
function wPerguntas(){
  return perguntasDoServico(W.service).map(p=>'<fieldset class="field obra-dias"><legend class="rotulo-campo">'+esc(p.texto)+'</legend>'+
    p.opcoes.map(o=>'<label class="chip-check"><input type="checkbox" '+(W.respostas[p.id]===o.id?'checked':'')+' onchange="wResposta(\''+p.id+'\',\''+o.id+'\',this.checked)"><span>'+esc(o.rotulo)+'</span></label>').join('')+'</fieldset>').join('');
}
function wResposta(pergunta,opcao,marcado){if(marcado)W.respostas[pergunta]=opcao;else delete W.respostas[pergunta];render();}

/* ---------- Local (agendar e obra): endereço e mapa, antes dos detalhes ---------- */
function wLocalAgendar(){
  const obra=W.modo==='obra';
  return cabecalhoPasso('Onde é o '+(obra?'serviço':'atendimento')+'?','Escolha um endereço salvo ou informe um novo e ajuste o ponto no mapa.')+
  campoEnderecoContratar()+campoMapaContratar()+
  rodapePasso('<button class="btn btn-primary" onclick="wGo('+(W.step+1)+')">Continuar '+ic('arrow')+'</button>');
}

/* Exemplo do campo "O que precisa ser feito" na obra: do serviço escolhido, ou do grupo dele. */
const EXEMPLO_OBRA={
  eletrica:'Ex.: refazer a fiação de uma casa de 3 quartos, trocar o quadro por um com disjuntores novos e instalar 6 tomadas',
  hidraulica:'Ex.: trocar os canos de água do banheiro e da cozinha, que são antigos de ferro, e instalar um registro geral',
  reforma:'Ex.: reformar a cozinha: trocar 12 m² de piso, azulejo da parede da pia e refazer a bancada',
  pintura:'Ex.: pintar a casa toda por dentro: 3 quartos, sala e cozinha, cerca de 180 m² de parede, com massa corrida nas rachaduras',
  gesso:'Ex.: forro de gesso na sala e nos 2 quartos, cerca de 45 m², com sanca e 8 spots embutidos',
  pisos:'Ex.: tirar o piso antigo e colocar porcelanato 60x60 em 50 m² (sala, cozinha e corredor)',
  marcenaria:'Ex.: armários planejados para a cozinha (3 m de parede) e um guarda-roupa de 2,5 m no quarto',
  telhado:'Ex.: trocar 40 m² de telhas quebradas, revisar o madeiramento e refazer as calhas do telhado',
  demolicao:'Ex.: derrubar uma parede de 4 m entre a sala e a cozinha e retirar o entulho',
  solar:'Ex.: instalar placas solares para uma conta de luz de cerca de R$ 400 por mês, em telhado de cerâmica',
  impermeabilizacao:'Ex.: impermeabilizar a laje de 60 m² que está infiltrando no quarto e na sala',
  serralheria:'Ex.: fazer um portão de garagem de 3 m e grades para 4 janelas',
  ar:'Ex.: instalar 3 aparelhos split (2 de 9.000 e 1 de 12.000 BTUs) com tubulação embutida na parede',
  cftv:'Ex.: instalar 6 câmeras na casa e no muro, com gravação e acesso pelo celular',
  paisagismo:'Ex.: montar o jardim da frente da casa, 30 m², com grama, canteiros e iluminação',
  piscina:'Ex.: reformar a piscina de 8x4 m: trocar o revestimento, a bomba e o filtro',
  pos_obra:'Ex.: limpeza pós-obra de um apartamento de 90 m², com retirada de restos de tinta e cimento',
};
function exemploObra(id){
  return EXEMPLO_OBRA[id]||exemploPedido(id);
}

/* ---------- Valor ---------- */
/* Faixa do serviço: com 5 ou mais serviços concluídos no último ano, a da TaskGO (/api/precos); antes disso, a referência do catálogo. */
const FAIXAS_PRECO={};
/* Minutos em "40 min" ou "1h30". */
function formatarMinutos(m){m=Math.round(m);return m<60?m+' min':Math.floor(m/60)+'h'+(m%60?String(m%60).padStart(2,'0'):'');}
function textoFaixaPreco(id){
  const s=SERVICES.find(x=>x.id===id),r=FAIXAS_PRECO[id];if(!s)return '';
  const tempo=r&&r.duracaoMedia!=null?' Costuma levar '+(Number(r.duracaoMin)>=5?formatarMinutos(r.duracaoMin)+' a ':'até ')+formatarMinutos(r.duracaoMax)+'.':'';
  return (r&&r.medio!=null?'Na TaskGO, '+esc(s.n)+' sai por <b>'+money(r.minimo)+' a '+money(r.maximo)+'</b> · metade até '+money(r.medio)+' ('+r.amostras+' serviços no último ano).':
    'Referência de mercado: <b>'+money(s.min)+' a '+money(s.max)+'</b> por '+esc(s.unit)+'.')+tempo;
}
function faixaPreco(id){
  if(!SERVICES.some(x=>x.id===id))return '';
  if(!DEMO_MODE&&!(id in FAIXAS_PRECO)){FAIXAS_PRECO[id]=null;
    apiJSON('/api/precos/'+encodeURIComponent(id)).then(r=>{FAIXAS_PRECO[id]=r;const n=document.getElementById('faixaPreco');if(n&&W.service===id)n.innerHTML=textoFaixaPreco(id);}).catch(()=>{delete FAIXAS_PRECO[id];});}
  return '<p class="hint" id="faixaPreco" aria-live="polite">'+textoFaixaPreco(id)+'</p>';
}
function wValor(){
  const v=W.oferta==null?'':W.oferta;
  if(W.modo==='obra')return cabecalhoPasso('Até quanto pretende gastar?','Os profissionais veem esse teto ao orçar. O valor final é o do orçamento que você escolher.')+
  '<div class="oferta"><label for="wOferta" class="leitor">Teto em reais</label><span class="oferta-moeda" aria-hidden="true">R$</span>'+
  '<input id="wOferta" class="oferta-campo" type="number" inputmode="decimal" min="1" max="100000" step="any" placeholder="0" value="'+esc(v)+'" oninput="W.oferta=this.value===\'\'?null:Number(this.value)"></div>'+
  rodapePasso('<button class="btn btn-primary" onclick="wGo('+(W.step+1)+')">Revisar pedido '+ic('arrow')+'</button>');
  return cabecalhoPasso('Quanto você oferece?',W.rec?'Valor de cada vez.':W.modo==='ajuda'?'A partir de '+money(AJUDA_MINIMO)+'. Quem aceitar já vai sabendo o valor.':W.modo==='agora'?'Quem aceitar já vai sabendo o valor.':'Cada profissional pode aceitar ou mandar outra proposta.')+
  '<div class="oferta"><label for="wOferta" class="leitor">Valor oferecido em reais</label><span class="oferta-moeda" aria-hidden="true">R$</span>'+
  '<input id="wOferta" class="oferta-campo" type="number" inputmode="decimal" min="'+(W.modo==='ajuda'?AJUDA_MINIMO:1)+'" max="100000" step="any" placeholder="0" value="'+esc(v)+'" oninput="W.oferta=this.value===\'\'?null:Number(this.value)"></div>'+
  faixaPreco(W.service)+
  blocoRecorrenteContratar()+
  rodapePasso('<button class="btn btn-primary" onclick="wGo('+(W.step+1)+')">'+(modoNaHora()?'Confirmar valor':'Revisar pedido')+' '+ic('arrow')+'</button>');
}

/* ---------- Confirmar (agendar) ---------- */
function quandoTexto(){return W.urgency==='agora'?'Agora (urgente)':W.urgency==='hoje'?'Ainda hoje':(W.date?new Date(W.date).toLocaleString('pt-BR',{dateStyle:'short',timeStyle:'short'}):'A agendar');}
function wConfirmar(){
  const s=svc(W.service);
  return cabecalhoPasso('Confira e envie','Toque em Voltar para mudar qualquer coisa.')+
  '<dl class="resumo-pedido">'+
  (W.modo==='obra'?[['Serviço',s.n,1],['Endereço',W.addr,2],['Quer começar',W.inicioObra?new Date(W.inicioObra+'T12:00').toLocaleDateString('pt-BR'):'A combinar',3],['Até quanto gastar',money(Number(W.oferta)),4],['O que fazer',W.notes,3]]:
  (W.rec?[['Serviço',s.n,1],['Endereço',W.addr,2],['Repetição',resumoRec(W.rec),4],['Cada vez',money(Number(W.oferta)),4],['Total',money(Number(W.oferta)*W.rec.vezes),4]]:
  [['Serviço',s.n,1],['Endereço',W.addr,2],['Quando',quandoTexto(),3],['Você oferece',money(Number(W.oferta)),4]]).concat(W.notes?[['Descrição',W.notes,3]]:[]).concat(respostasConhecidas(W.service,W.respostas).map(x=>[x[0],x[1],3])))
  .map(r=>'<div><dt>'+r[0]+'</dt><dd>'+esc(r[1])+'</dd><button type="button" class="resumo-mudar" onclick="W.step='+r[2]+';render()">Mudar</button></div>').join('')+'</dl>'+
  '<p class="hint" style="margin-top:16px">O endereço completo só aparece para o profissional que você confirmar. Antes disso, ele vê só o bairro.</p>'+
  (W.modo==='obra'?'<p class="hint">'+(modeloDireto()?'Nada é cobrado agora. Você paga cada etapa direto ao profissional, por Pix, depois de aprová-la.':'Nada é cobrado agora. Você paga cada etapa antes de ela começar, depois de escolher um orçamento.')+'</p>':
   W.rec?'<p class="hint">'+(modeloDireto()?'Nada é cobrado agora. Você paga cada vez direto ao profissional, por Pix, depois que ela acontecer.':'Nada é cobrado agora. Você paga cada semana antes de ela começar, com Pix, cartão ou a carteira.')+'</p>':escolhaPagamento(W.oferta))+
  rodapePasso('<button class="btn btn-grad btn-lg" onclick="enviarPedido()"'+(W.enviando?' disabled aria-busy="true"':'')+'>'+(W.enviando?'Enviando...':(W.modo==='obra'?'Pedir orçamentos ':W.rec?'Enviar pedido ':textoEnviarPedido('agendar'))+ic('arrow'))+'</button>');
}

/* ---------- Local e chamada (chamar agora) ---------- */
function wLocalAgora(){
  const s=svc(W.service),ajuda=W.modo==='ajuda';
  return cabecalhoPasso('Onde você está?',ajuda?'O mapa mostra você e as pessoas que ajudam por perto. Mova, aproxime e afaste o mapa à vontade.':'O mapa mostra você e os profissionais online por perto. Mova, aproxime e afaste o mapa à vontade.')+
  '<div class="agora-local-acoes"><button type="button" class="btn btn-primary" onclick="usarMinhaPosicao()">'+ic('nav')+' Usar minha localização</button>'+
  '<span class="muted">ou escolha um endereço abaixo</span></div>'+
  campoEnderecoContratar()+campoMapaContratar()+
  '<div class="agora-resumo"><div><span>Serviço</span><b>'+esc(s.n)+'</b></div><div><span>Você oferece</span><b>'+money(Number(W.oferta))+'</b></div>'+
  '<div id="pertoInfo" class="agora-perto" aria-live="polite">'+textoPerto()+'</div></div>'+
  (ajuda?'':'<div class="field"><label for="wNotas">O que está acontecendo? (opcional)</label><textarea class="textarea" id="wNotas" placeholder="'+esc(exemploPedido(W.service))+'" oninput="W.notes=this.value">'+esc(W.notes)+'</textarea></div>'+
  campoFotosContratar())+
  escolhaPagamento(W.oferta)+
  rodapePasso('<button class="btn btn-grad btn-lg" onclick="enviarPedido()"'+(W.enviando?' disabled aria-busy="true"':'')+'>'+(W.enviando?'Chamando...':ic(ajuda?'boia':'bolt')+textoEnviarPedido(W.modo))+'</button>');
}
let pertoAgora=null,pertoTimer=null,pertoCamada=null;
function textoPerto(){
  if(W.lat==null)return 'Marque onde você está para ver quem está perto.';
  if(!pertoAgora)return 'Procurando profissionais online por perto...';
  const ajuda=W.modo==='ajuda';
  if(!pertoAgora.total)return ajuda?'<b>Ninguém com a ajuda rápida ligada perto agora.</b> Você pode pedir mesmo assim: avisamos quem ligar.':'<b>Nenhum profissional online perto agora.</b> Você pode chamar mesmo assim: avisamos quem ficar online, ou volte e escolha Agendar.';
  return '<b>'+pertoAgora.total+' '+(ajuda?(pertoAgora.total===1?'pessoa que ajuda':'pessoas que ajudam'):(pertoAgora.total===1?'profissional online':'profissionais online'))+'</b> perto de você'+(pertoAgora.maisPertoMin?' · o mais perto a cerca de '+pertoAgora.maisPertoMin+' min':'')+'.';
}
function aoMarcarPonto(){
  if(!modoNaHora())return;
  clearTimeout(pertoTimer);pertoTimer=setTimeout(carregarPerto,500);
}
async function carregarPerto(){
  if(W.lat==null||!W.service)return;
  if(DEMO_MODE){pertoAgora={total:0,perto:[]};}
  else try{pertoAgora=await apiJSON('/api/chamar-agora/perto?servico='+encodeURIComponent(W.service)+'&lat='+W.lat+'&lng='+W.lng);}catch(e){pertoAgora={total:0,perto:[]};}
  const n=document.getElementById('pertoInfo');if(n)n.innerHTML=textoPerto();
  if(mapaContratar&&window.L){
    if(pertoCamada)pertoCamada.remove();
    pertoCamada=L.layerGroup((pertoAgora.perto||[]).map(p=>L.marker([p.lat,p.lng],{icon:iconeProfissionalMapa(),keyboard:false,title:'Profissional online a '+String(p.km).replace('.',',')+' km'}))).addTo(mapaContratar);
  }
}
function iconeProfissionalMapa(){return L.divIcon({className:'pino-pro',html:'<span>'+ic(svc(W.service).ico)+'</span>',iconSize:[34,34],iconAnchor:[17,17]});}
function usarMinhaPosicao(){
  if(!navigator.geolocation)return toast('Este navegador não informa a localização. Escolha um endereço.','err');
  const d=document.getElementById('mapaDica');if(d)d.textContent='Pedindo sua localização ao navegador...';
  navigator.geolocation.getCurrentPosition(pos=>{
    const ll={lat:pos.coords.latitude,lng:pos.coords.longitude};
    W.addrId='gps';W.addr='Minha localização atual';
    if(marcarPontoContratar)marcarPontoContratar(ll,'gps');else{W.lat=ll.lat;W.lng=ll.lng;}
    if(mapaContratar)mapaContratar.setView([ll.lat,ll.lng],pos.coords.accuracy>300?14:17);
    const dd=document.getElementById('mapaDica');if(dd)dd.textContent=pos.coords.accuracy>300?'Localização aproximada (margem de '+Math.round(pos.coords.accuracy)+' m). Arraste o marcador até onde você está.':'Você está aqui. Arraste o marcador se precisar ajustar.';
  },()=>{const dd=document.getElementById('mapaDica');if(dd)dd.textContent='Não foi possível pegar sua localização. Escolha um endereço ou toque no mapa.';},{enableHighAccuracy:true,timeout:12000,maximumAge:30000});
}

/* ---------- Envio ---------- */
async function enviarPedido(){
  const u=me();if(!u)return go('/entrar?next=app/contratar');
  if(W.enviando)return;
  const agora=modoNaHora();
  if(agora){
    if(W.lat==null)return toast('Marque no mapa onde você está','err');
    const v=Number(W.oferta);if(!(v>=(W.modo==='ajuda'?AJUDA_MINIMO:1)&&v<=100000))return toast('Volte e informe quanto você oferece','err');
  }
  if(W.modo==='ajuda'&&String(W.notes||'').trim().length<10)return toast('Volte e conte do que você precisa','err');
  const obra=W.modo==='obra';
  if(obra&&String(W.notes||'').trim().length<20)return toast('Volte e descreva a obra em pelo menos 20 letras.','err');
  if(!obra&&Number(W.oferta)>=3000)return sugerirObra();
  if(!obra&&W.maisDeUmDia)return sugerirObra(true);
  const rec=!obra&&!agora&&W.rec&&servicoRecorrente(W.service)?Object.assign({},W.rec):null;
  if(rec&&!recValido())return;
  if(!obra&&!rec&&!DEMO_MODE&&!pagamentoEscolhidoValido())return;
  const escolha=obra||rec?null:W.pag?Object.assign({},W.pag):null,fotosEscolhidas=(W.fotos||[]).slice();
  const end=listaEnderecos().find(e=>e.id===W.addrId);
  const endereco=String(W.addr||'').trim()||(agora?'Localização marcada no mapa':'');
  const pedido={id:'o_'+Date.now().toString(36)+uid().slice(0,4),service:W.service,price:Number(W.oferta),notes:String(W.notes||'').trim(),address:endereco,
    urgente:agora,lat:W.lat,lng:W.lng,formaPagamento:escolha&&['dinheiro','pix_direto','cartao_maquininha'].includes(escolha.metodo)?escolha.metodo:undefined,bairro:end&&end.bairro?end.bairro:null,quando:agora?'agora':W.urgency,modo:obra?'obra':agora?'agora':'agendar',inicioDesejado:obra?W.inicioObra||null:undefined,
    recorrente:rec||undefined,
    respostas:(W.modo==='agendar'||W.modo==='obra')&&Object.keys(W.respostas).length?Object.assign({},W.respostas):undefined,
    proEmail:W.modo==='agendar'&&!rec&&W.proEmail?W.proEmail:undefined,proId:W.modo==='agendar'&&!rec&&W.proEmail?W.proId||undefined:undefined,
    sched:rec?new Date(datasRecorrentes(rec.inicio,rec.dias,rec.vezes)[0]+'T'+rec.hora).getTime():!agora&&W.urgency==='agendar'&&W.date?new Date(W.date).getTime():null,
    clientId:u.id,clientEmail:u.email,clientName:u.name,status:'aguardando',created:Date.now()};
  W.enviando=true;render();
  try{
    if(DEMO_MODE){pedido.code=String(rand(1000,9999));S.orders.unshift(pedido);Store.save();propostasDemoChegando(pedido.id);}
    else{const salvo=await persistirPedido(pedido,u.email);if(!S.orders.some(o=>o.id===salvo.id))S.orders.unshift(salvo);Store.save();}
    if(!DEMO_MODE&&fotosEscolhidas.length)await enviarFotosDoPedido(pedido.id,fotosEscolhidas);
    W=W_VAZIO();pertoAgora=null;PG.saldo=null;
    const criado=S.orders.find(o=>o.id===pedido.id)||pedido;
    if(!DEMO_MODE&&criado.status==='aguardando_pagamento'){await pagarPedidoNovo(criado,escolha);return;}
    if(agora&&!DEMO_MODE){go('/app/chamando/'+encodeURIComponent(pedido.id));return;}
    if(obra){toast('Obra publicada. Os orçamentos chegam aqui.','ok');go('/app/obra/'+encodeURIComponent(pedido.id));return;}
    toast(rec?'Pedido recorrente enviado. Você paga a 1ª semana depois de confirmar o profissional.':'Pedido enviado. Os profissionais online já podem ver.','ok');go('/app/pedido/'+encodeURIComponent(pedido.id));
  }catch(error){
    W.enviando=false;render();
    toast(erroLegivel(error,'O pedido não foi enviado. Confira sua conexão e tente de novo.'),'err');
  }
}
/* Chamar de novo quem já atendeu: agendado, direto para ele (o servidor confere se ainda pode atender); a pessoa confere endereço, data e valor. */
function chamarDeNovo(id){
  const o=S.orders.find(x=>x.id===id);if(!o||!o.proEmail)return go('/app/contratar?servico='+encodeURIComponent(o?o.service:''));
  W=Object.assign(W_VAZIO(),{modo:'agendar',step:2,service:o.service,presetService:o.service,notes:o.notes||'',addr:o.address||'',addrId:'outro',
    lat:o.lat!=null?Number(o.lat):null,lng:o.lng!=null?Number(o.lng):null,geoEstado:o.lat!=null?'manual':'',proEmail:o.proEmail,proId:o.proId||'',proNome:o.professionalName||'o mesmo profissional',respostas:Object.assign({},o.respostas)});
  go('/app/contratar');
}
/* Pedido cancelado pelo cliente: volta ao Contratar com tudo preenchido, na tela de conferir. */
function reaproveitarPedido(o){
  const agora=o.quando==='agora'||o.modo==='agora';
  W=Object.assign(W_VAZIO(),{modo:o.obra?'obra':o.service==='ajuda'?'ajuda':agora?'agora':'agendar',inicioObra:o.inicioDesejado||'',service:o.service,oferta:Number(o.price)||null,notes:o.notes||'',addr:o.address&&o.address!=='Localização marcada no mapa'?o.address:'',addrId:'outro',
    rec:o.recorrente&&o.recorrente.vezes?{vezes:Number(o.recorrente.vezes),dias:(o.recorrente.dias||[]).map(Number),inicio:new Date(Date.now()+864e5).toISOString().slice(0,10),hora:o.recorrente.hora||'08:00'}:null,lat:o.lat!=null?Number(o.lat):null,lng:o.lng!=null?Number(o.lng):null,geoEstado:o.lat!=null?'manual':'',urgency:agora?'agora':(o.quando==='agendar'?'agendar':'hoje'),
    date:o.sched?new Date(Number(o.sched)-new Date().getTimezoneOffset()*60000).toISOString().slice(0,16):'',presetService:o.service,respostas:Object.assign({},o.respostas),reaproveitado:true});
  W.step=PASSOS[W.modo].length;
}
/* Ponto do atendimento no mapa: é contra ele que o servidor confere a chegada do profissional.
   Ao escolher ou digitar o endereço, o mapa procura o endereço (OpenStreetMap) e coloca o marcador lá; a pessoa só confere e ajusta. */
function campoMapaContratar(){
  return '<div class="field"><span class="rotulo-campo" id="mapaRotulo">Local exato no mapa</span>'+
  '<div id="mapaContratar" class="mapa-ponto" role="application" aria-labelledby="mapaRotulo" aria-describedby="mapaDica"></div>'+
  '<div class="mapa-ponto-rodape"><span class="hint" id="mapaDica" aria-live="polite">'+dicaMapa()+'</span>'+
  '<button type="button" class="btn btn-ghost btn-sm" onclick="ajustarLocalizacao()">'+ic('mapPin')+' Ajustar localização</button></div></div>';
}
function dicaMapa(){
  if(W.geoEstado==='buscando')return 'Procurando o endereço no mapa...';
  if(W.geoEstado==='naoachou')return 'Não achamos esse endereço no mapa. Toque no ponto exato do atendimento.';
  if(W.lat!=null&&W.geoEstado==='regiao')return 'Achamos só a região do endereço. Use Ajustar localização e arraste o marcador até a porta.';
  if(W.lat!=null)return W.geoEstado==='endereco'?'Marcador colocado pelo endereço. Confira se está na porta certa; se não, use Ajustar localização.':'Ponto marcado. Arraste o marcador se precisar ajustar.';
  return 'Escolha ou digite o endereço acima: o mapa vai até ele.';
}
function pintarDicaMapa(){const d=document.getElementById('mapaDica');if(d)d.textContent=dicaMapa();}
let mapaContratar=null,marcarPontoContratar=null,geoTimer=null,geoBusca=0;
function ligarMapaContratar(){
  const no=document.getElementById('mapaContratar');if(!no||!window.L)return;
  /* O mesmo mapa continua na tela (atualização sem piscar): não refaz. */
  if(mapaContratar&&mapaContratar.getContainer()===no&&no.isConnected){mapaContratar.invalidateSize();return;}
  if(mapaContratar){mapaContratar.remove();mapaContratar=null;}
  const temPonto=W.lat!=null,centro=temPonto?[W.lat,W.lng]:[-8.0476,-34.8770];
  const mapa=L.map(no,{scrollWheelZoom:modoNaHora()}).setView(centro,temPonto?17:12);
  L.tileLayer(MAPA_TILES,{maxZoom:19,attribution:MAPA_CREDITO}).addTo(mapa);
  let marca=null;
  const marcar=(ll,origem)=>{
    W.lat=+Number(ll.lat).toFixed(6);W.lng=+Number(ll.lng).toFixed(6);W.geoEstado=origem||'manual';
    if(!marca){marca=L.marker([W.lat,W.lng],{draggable:true,keyboard:true,title:'Local do atendimento'}).addTo(mapa);marca.on('dragend',e=>marcar(e.target.getLatLng(),'manual'));}
    else marca.setLatLng([W.lat,W.lng]);
    pintarDicaMapa();aoMarcarPonto();
  };
  if(temPonto)marcar({lat:W.lat,lng:W.lng},W.geoEstado);
  mapa.on('click',e=>marcar(e.latlng,'manual'));
  mapaContratar=mapa;marcarPontoContratar=marcar;
  setTimeout(()=>{if(mapaContratar===mapa&&mapa.getContainer().isConnected)mapa.invalidateSize();},60);
  /* Endereço já escolhido e ainda sem ponto: procura agora. */
  if(!temPonto&&String(W.addr||'').trim().length>=8&&W.geoEstado!=='naoachou')localizarEndereco();
}
/* O endereço mudou: o ponto antigo não vale mais. */
function enderecoMudou(){
  W.lat=null;W.lng=null;W.geoEstado='';clearTimeout(geoTimer);
  if(String(W.addr||'').trim().length<8){pintarDicaMapa();return;}
  geoTimer=setTimeout(localizarEndereco,900);
}
/* Procura o endereço em etapas, do mais exato ao mais amplo:
   1) rua, número, cidade e CEP separados; 2) pelo CEP (ViaCEP dá a rua e o bairro);
   3) o texto limpo (sem complemento e sem "CEP"); 4) só o CEP; 5) bairro e cidade.
   Nas duas últimas, o marcador cai na região e a dica pede para ajustar até a porta. */
async function buscaNominatim(params){
  const p=new URLSearchParams(Object.assign({format:'jsonv2',limit:'1',countrycodes:'br','accept-language':'pt-BR'},params));
  const r=await (await fetch('https://nominatim.openstreetmap.org/search?'+p)).json();
  return r&&r.length?{lat:Number(r[0].lat),lng:Number(r[0].lon)}:null;
}
function partesDoEndereco(){
  if(!DEMO_MODE&&!pagamentoEscolhidoValido())return;
  const escolha=W.pag?Object.assign({},W.pag):null;
  const end=listaEnderecos().find(e=>e.id===W.addrId);
  if(end&&end.rua)return {rua:end.rua,numero:end.numero,bairro:end.bairro,cidade:end.cidade,uf:end.uf,cep:end.cep};
  const t=String(W.addr||'');
  const cep=(t.match(/\b(\d{5})-?(\d{3})\b/)||[]);
  const sem=t.replace(/CEP\s*\d{5}-?\d{3}/i,'').replace(/\b\d{5}-?\d{3}\b/,'');
  const pedacos=sem.split(/\s*[·,;]\s*/).map(x=>x.trim()).filter(Boolean);
  const numero=(pedacos.find(x=>/^\d+[a-zA-Z]?$/.test(x))||(pedacos[0]||'').match(/\b(\d+)\s*$/)?.[1]||'');
  const rua=(pedacos[0]||'').replace(/,?\s*\d+[a-zA-Z]?\s*$/,'').trim();
  const cidUf=pedacos.find(x=>/\/[A-Za-z]{2}$/.test(x));
  return {rua,numero,cidade:cidUf?cidUf.split('/')[0]:'',uf:cidUf?cidUf.split('/')[1]:'',bairro:pedacos.length>2?pedacos[pedacos.length-(cidUf?2:1)]:'',cep:cep[1]?cep[1]+'-'+cep[2]:'',texto:pedacos.slice(0,2).join(', ')};
}
async function localizarEndereco(){
  const busca=++geoBusca,e=partesDoEndereco();
  W.geoEstado='buscando';pintarDicaMapa();
  let ll=null,exato=true;
  try{
    if(e.cep&&(!e.rua||!e.cidade)){
      const v=await (await fetch('https://viacep.com.br/ws/'+e.cep.replace(/\D/g,'')+'/json/')).json().catch(()=>null);
      if(v&&!v.erro){e.rua=e.rua||v.logradouro;e.bairro=e.bairro||v.bairro;e.cidade=e.cidade||v.localidade;e.uf=e.uf||v.uf;}
    }
    if(e.rua&&e.cidade)ll=await buscaNominatim(Object.assign({street:((e.numero||'')+' '+e.rua).trim(),city:e.cidade},e.uf?{state:e.uf}:{},e.cep?{postalcode:e.cep}:{}));
    if(!ll&&e.rua&&e.cidade)ll=await buscaNominatim(Object.assign({street:e.rua,city:e.cidade},e.uf?{state:e.uf}:{}));
    if(!ll&&e.texto)ll=await buscaNominatim({q:e.texto+(e.cidade?', '+e.cidade:'')});
    if(!ll&&e.cep){ll=await buscaNominatim({postalcode:e.cep,country:'Brasil'});exato=false;}
    if(!ll&&e.bairro&&e.cidade){ll=await buscaNominatim({q:e.bairro+', '+e.cidade+(e.uf?', '+e.uf:'')});exato=false;}
  }catch(err){ll=null;}
  if(busca!==geoBusca)return;
  if(!ll){W.geoEstado='naoachou';pintarDicaMapa();return;}
  if(marcarPontoContratar)marcarPontoContratar(ll,exato?'endereco':'regiao');else{W.lat=ll.lat;W.lng=ll.lng;W.geoEstado=exato?'endereco':'regiao';}
  if(mapaContratar)mapaContratar.setView([ll.lat,ll.lng],exato?18:16);
}
/* Ajustar: aproxima no marcador para a pessoa arrastar até a porta certa.
   Sem marcador ainda, procura o endereço primeiro (nunca marca um ponto qualquer). */
async function ajustarLocalizacao(){
  if(!mapaContratar)return;
  if(W.lat==null){if(String(W.addr||'').trim().length>=8)await localizarEndereco();
    if(W.lat==null){const d=document.getElementById('mapaDica');if(d)d.textContent='Não achamos o endereço no mapa. Toque no ponto exato do atendimento.';return;}}
  mapaContratar.setView([W.lat,W.lng],18);
  const no=document.getElementById('mapaContratar');if(no){no.scrollIntoView({block:'center',behavior:'smooth'});no.classList.add('ajustando');setTimeout(()=>no.classList.remove('ajustando'),1600);}
  const d=document.getElementById('mapaDica');if(d)d.textContent='Arraste o marcador até a porta do local, ou toque no ponto exato.';
}
