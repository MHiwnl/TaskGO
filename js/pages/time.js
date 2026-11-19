/* ---------- TIME FUNDADOR ----------
   Carrossel de fotos do time junto. Cada rosto vira um botão que abre o card da pessoa,
   e a lista ao lado mostra quem está na foto atual.

   Para adicionar uma foto em grupo:
   1. Salve o arquivo em assets/equipe/grupo/.
   2. Acrescente um item em FOTOS_TIME com o caminho, uma descrição curta e, para cada pessoa,
      a posição do centro do rosto em porcentagem da largura (x) e da altura (y) da foto,
      e o tamanho do rosto em porcentagem da largura (r).
   Sem fotos em FOTOS_TIME, a seção mostra a lista do time, que também abre os cards. */
const TIME=[
  {id:'joabe',nome:'Joabe Junior',cargo:'CTO & Cofundador',area:'Front-end',foto:'assets/equipe/joabe.jpg',sobre:''},
  {id:'arthur',nome:'Arthur Mendes',cargo:'CEO & Cofundador',area:'Full Stack',foto:'assets/equipe/arthur.jpg',sobre:''},
  {id:'thiago',nome:'Thiago Vinicius',cargo:'Head de Operações & Cofundador',foto:'assets/equipe/thiago.jpg',sobre:''},
  {id:'vinicius',nome:'Vinicius Ziano',cargo:'Head de Confiança e Segurança & Cofundador',foto:'assets/equipe/vinicius.jpg',sobre:''},
  {id:'matheus-albuquerque',nome:'Matheus Albuquerque',curto:'Matheus A.',cargo:'Cofundador',area:'Documentação',foto:'assets/equipe/matheus-albuquerque.jpg',sobre:''},
  {id:'ruan',nome:'Ruan Estevan',cargo:'Cofundador',area:'Documentação',foto:'assets/equipe/ruan.jpg',sobre:''},
  {id:'matheus-henrique',nome:'Matheus Henrique',curto:'Matheus H.',cargo:'Cofundador',area:'Back-end',foto:'assets/equipe/matheus-henrique.jpg',sobre:''},
  /* Não aparece nas fotos em grupo nem tem foto individual: o card usa as iniciais. */
  {id:'luiz',nome:'Luiz Otávio',cargo:'Cofundador',area:'Back-end',foto:'',sobre:''}
];
const FOTOS_TIME=[
  /* Na foto da sala, o rosto de uma pessoa de fora do time, ao fundo à esquerda, foi desfocado. */
  {src:'assets/equipe/grupo/sala.jpg',w:1600,h:1200,alt:'Selfie do time em uma sala da UNIBRA, com janelas de vidro ao fundo',
   pessoas:[{id:'thiago',x:21.5,y:30,r:20},{id:'matheus-albuquerque',x:39.2,y:32,r:6},{id:'vinicius',x:54.8,y:29,r:7},{id:'matheus-henrique',x:62.5,y:55.7,r:8},{id:'ruan',x:67.7,y:41.3,r:7},{id:'arthur',x:74,y:32.5,r:6.5},{id:'joabe',x:87.3,y:49.5,r:8.5}]},
  {src:'assets/equipe/grupo/portao.jpg',w:1200,h:1600,alt:'Selfie do time na frente de um portão, com um guarda-sol ao fundo',
   pessoas:[{id:'arthur',x:19,y:39,r:11},{id:'thiago',x:32,y:31,r:9.5},{id:'vinicius',x:30.5,y:60,r:15},{id:'ruan',x:43.6,y:41.5,r:12.5},{id:'matheus-albuquerque',x:58.2,y:56.9,r:13},{id:'joabe',x:80,y:50,r:26}]}
];
const TIME_INTERVALO=10000; /* troca a cada 10 s; depois da última foto, volta para a primeira */
let timeFoto=0,timePausado=false,timeTimer=null,timeObservador=null;
function pessoaTime(id){return TIME.find(p=>p.id===id);}
function secaoTime(){
  const fotos=FOTOS_TIME;
  if(!fotos.length)return '<div class="pessoas" style="margin-top:36px">'+TIME.map(p=>
    '<button type="button" class="pessoa pessoa-botao" onclick="abrirCardPessoa(\''+p.id+'\',this)">'+avatar({name:p.nome,photo:p.foto},72)+'<span><b>'+p.nome+'</b><span>'+p.cargo+'</span></span></button>').join('')+'</div>';
  if(timeFoto>=fotos.length)timeFoto=0;
  timePausado=timePausado||matchMedia('(prefers-reduced-motion: reduce)').matches;
  return '<div class="time" role="region" aria-roledescription="carrossel" aria-label="Fotos do time">'+
    '<div class="time-palco" onmouseenter="pausarTimeTemporario(true)" onmouseleave="pausarTimeTemporario(false)" onfocusin="pausarTimeTemporario(true)" onfocusout="pausarTimeTemporario(false)">'+
      fotos.map((f,i)=>'<figure class="time-foto'+(i===timeFoto?' ativa':'')+'" aria-hidden="'+(i!==timeFoto)+'"'+(i!==timeFoto?' inert':'')+'>'+
        /* Fundo desfocado da própria foto: a foto inteira aparece, vertical ou horizontal, sem cortar ninguém. */
        '<img class="time-fundo" src="'+f.src+'" alt="" aria-hidden="true"'+(i?' loading="lazy"':'')+'>'+
        '<div class="time-moldura" style="aspect-ratio:'+(f.w||4)+'/'+(f.h||3)+'"><img src="'+f.src+'" alt="'+esc(f.alt)+'"'+(i?' loading="lazy"':'')+'>'+
        f.pessoas.map(p=>{const pe=pessoaTime(p.id);return pe?'<button type="button" class="time-rosto" data-pessoa="'+p.id+'" style="left:'+p.x+'%;top:'+p.y+'%;width:'+(p.r||10)+'%" aria-label="Ver '+esc(pe.nome)+'" onclick="abrirCardPessoa(\''+p.id+'\',this)" onmouseenter="destacarPessoa(\''+p.id+'\',true)" onmouseleave="destacarPessoa(\''+p.id+'\',false)"><span>'+esc(pe.curto||pe.nome.split(' ')[0])+'</span></button>':'';}).join('')+
      '</div></figure>').join('')+
    '</div>'+
    '<div class="time-lado">'+
      '<p class="time-contagem" aria-live="polite">Foto '+(timeFoto+1)+' de '+fotos.length+'</p>'+
      '<h3 class="bloco-titulo">nesta foto</h3>'+
      '<ul class="time-lista">'+fotos[timeFoto].pessoas.map(p=>{const pe=pessoaTime(p.id);return pe?'<li><button type="button" data-pessoa="'+p.id+'" onclick="abrirCardPessoa(\''+p.id+'\',this)" onmouseenter="destacarPessoa(\''+p.id+'\',true)" onmouseleave="destacarPessoa(\''+p.id+'\',false)" onfocus="destacarPessoa(\''+p.id+'\',true)" onblur="destacarPessoa(\''+p.id+'\',false)">'+avatar({name:pe.nome,photo:pe.foto},36)+'<span><b>'+pe.nome+'</b><small>'+pe.cargo+'</small></span>'+ic('arrow')+'</button></li>':'';}).join('')+'</ul>'+
      foraDasFotos()+
      (fotos.length>1?'<div class="time-controles">'+
        '<button type="button" class="icon-btn" aria-label="Foto anterior" onclick="mudarFotoTime(-1)">'+ic('arrowLeft')+'</button>'+
        '<div class="time-pontos">'+fotos.map((f,i)=>'<button type="button" class="'+(i===timeFoto?'ativa':'')+'" aria-label="Foto '+(i+1)+'"'+(i===timeFoto?' aria-current="true"':'')+' onclick="irFotoTime('+i+')"></button>').join('')+'</div>'+
        '<button type="button" class="icon-btn" aria-label="Próxima foto" onclick="mudarFotoTime(1)">'+ic('arrow')+'</button>'+
        '<button type="button" class="hero-pausa" aria-pressed="'+timePausado+'" onclick="alternarTime(this)">'+ic(timePausado?'play':'pause')+'<span>'+(timePausado?'Reproduzir':'Pausar')+'</span></button>'+
      '</div>':'')+
    '</div></div>';
}
/* Quem é do time mas não aparece em nenhuma foto em grupo continua com o card ao alcance. */
function foraDasFotos(){
  const nasFotos=new Set(FOTOS_TIME.flatMap(f=>f.pessoas.map(p=>p.id)));
  const fora=TIME.filter(p=>!nasFotos.has(p.id));if(!fora.length)return '';
  return '<p class="time-fora">Também no time, fora das fotos: '+fora.map(p=>'<button type="button" onclick="abrirCardPessoa(\''+p.id+'\',this)">'+esc(p.nome)+'</button>').join(', ')+'</p>';
}
function destacarPessoa(id,ligar){document.querySelectorAll('.time [data-pessoa="'+id+'"]').forEach(e=>e.classList.toggle('destaque',ligar));}
/* Troca de foto sem redesenhar a página inteira: só a seção do time. */
function atualizarTime(){const alvo=document.querySelector('.time');if(!alvo)return;const foco=document.activeElement?.closest?.('.time')?document.activeElement.getAttribute('aria-label'):null;alvo.outerHTML=secaoTime();if(foco)document.querySelector('.time [aria-label="'+foco+'"]')?.focus();}
function irFotoTime(i){timeFoto=(i+FOTOS_TIME.length)%FOTOS_TIME.length;atualizarTime();}
function mudarFotoTime(passo){irFotoTime(timeFoto+passo);}
function alternarTime(){timePausado=!timePausado;atualizarTime();ligarTime();}
let timeEmUso=false;
function pausarTimeTemporario(sim){timeEmUso=sim;}
function ligarTime(){
  clearInterval(timeTimer);timeTimer=null;
  if(timeObservador){timeObservador.disconnect();timeObservador=null;}
  const caixa=document.querySelector('.time');if(!caixa||FOTOS_TIME.length<2||timePausado)return;
  let visivel=false;
  if('IntersectionObserver' in window){timeObservador=new IntersectionObserver(i=>{visivel=i[0].isIntersecting;},{threshold:.3});timeObservador.observe(caixa);}else visivel=true;
  timeTimer=setInterval(()=>{
    if(!document.querySelector('.time')){clearInterval(timeTimer);timeTimer=null;return;}
    if(visivel&&!timeEmUso&&!document.hidden&&!modalOpen)mudarFotoTime(1);
  },TIME_INTERVALO);
}
/* Card da pessoa */
function abrirCardPessoa(id){
  const p=pessoaTime(id);if(!p)return;
  openModal('<div class="modal-head"><div></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="card-pessoa">'+avatar({name:p.nome,photo:p.foto},120)+
    '<h3>'+p.nome+'</h3><p class="card-pessoa-cargo">'+p.cargo+'</p>'+
    (p.area?'<p class="card-pessoa-area"><span>Atua em</span> '+esc(p.area)+'</p>':'')+
    (p.sobre?'<p class="card-pessoa-sobre">'+esc(p.sobre)+'</p>':'')+
    '<p class="card-pessoa-meta">Time fundador da TaskGO · Estudante da UNIBRA · Recife</p></div>',{className:'modal-pessoa'});
}
