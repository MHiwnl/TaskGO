/* =========================================================
   SELETOR DE SERVIÇO
   Troca a lista nativa (105 serviços, 15 grupos) por uma lista do próprio site: busca pelas
   palavras do dia a dia, grupos, ícone de cada serviço, teclado (setas, Enter, Esc) e o valor
   num campo escondido, então formulários e leituras por id continuam iguais.
   Uso: seletorServico({id:'auth-service', nome:'service', rotulo:'Categoria principal', atual:'eletrica', aoMudar:'simular'})
   ========================================================= */
const SELS={};
function seletorServico(o){
  const s=SELS[o.id]=Object.assign(SELS[o.id]||{},o);
  if(s.valor==null)s.valor=o.atual||'';
  return '<div class="field sel-svc" id="'+o.id+'-caixa">'+
    '<label id="'+o.id+'-rot" for="'+o.id+'-btn">'+esc(o.rotulo||'Serviço')+'</label>'+
    '<input type="hidden" id="'+o.id+'"'+(o.nome?' name="'+o.nome+'"':'')+' value="'+esc(s.valor)+'">'+
    '<button type="button" class="sel-svc-gatilho" id="'+o.id+'-btn" aria-haspopup="listbox" aria-expanded="false" aria-controls="'+o.id+'-painel" onclick="alternarSeletorServico(\''+o.id+'\')">'+
      conteudoGatilhoServico(s.valor)+'</button>'+
    '<div class="sel-svc-painel" id="'+o.id+'-painel" hidden></div>'+
    (o.dica?'<div class="hint">'+o.dica+'</div>':'')+'</div>';
}
function conteudoGatilhoServico(valor){
  const x=valor?SERVICES.find(s=>s.id===valor):null;
  return (x?ic(x.ico,'ic-svc-'+x.id)+'<span>'+esc(x.n)+'</span>':'<span class="sel-svc-vazio">Escolha o serviço</span>')+ic('chevDown','sel-svc-seta');
}
function alternarSeletorServico(id){
  const p=$('#'+id+'-painel');if(!p)return;
  if(!p.hidden)return fecharSeletorServico(id,true);
  const s=SELS[id];s.busca='';s.ativo=s.valor||null;
  p.hidden=false;$('#'+id+'-btn').setAttribute('aria-expanded','true');
  p.innerHTML='<div class="sel-svc-busca">'+ic('search')+
    '<input class="input" id="'+id+'-q" type="search" role="combobox" aria-expanded="true" aria-controls="'+id+'-lista" aria-autocomplete="list" aria-label="Buscar serviço" placeholder="Buscar: tomada, pia, aula de inglês..." autocomplete="off" spellcheck="false" '+
    'oninput="SELS[\''+id+'\'].busca=this.value;pintarListaServicos(\''+id+'\')" onkeydown="teclaSeletorServico(event,\''+id+'\')"></div>'+
    '<ul class="sel-svc-lista" id="'+id+'-lista" role="listbox" aria-labelledby="'+id+'-rot"></ul>';
  pintarListaServicos(id);
  $('#'+id+'-q').focus({preventScroll:true});
  /* Clicar fora fecha. */
  s.fora=ev=>{const c=$('#'+id+'-caixa');if(!c||!c.contains(ev.target))fecharSeletorServico(id);};
  document.addEventListener('pointerdown',s.fora,true);
}
function fecharSeletorServico(id,voltarFoco){
  const s=SELS[id]||{},p=$('#'+id+'-painel'),b=$('#'+id+'-btn');
  if(s.fora){document.removeEventListener('pointerdown',s.fora,true);s.fora=null;}
  if(p){p.hidden=true;p.innerHTML='';}
  if(b){b.setAttribute('aria-expanded','false');if(voltarFoco)b.focus();}
}
function pintarListaServicos(id){
  const s=SELS[id],lista=$('#'+id+'-lista');if(!lista)return;
  /* Cada palavra buscada precisa casar com o começo de uma palavra do serviço: "pia" acha pia entupida, não fisioterapia. */
  const termos=semAcentoBusca(s.busca).split(/\s+/).filter(Boolean);
  const casa=x=>{if(!termos.length)return true;const palavras=semAcentoBusca(x.n+' '+x.d+' '+(TERMOS_SERVICO[x.id]||[]).join(' ')).split(/[^a-z0-9]+/);
    return termos.every(t=>palavras.some(p=>p.startsWith(t)));};
  const grupos=GRUPOS_SERVICOS.map(g=>[g[0],g[1].map(i=>SERVICES.find(x=>x.id===i)).filter(x=>x&&casa(x))]).filter(g=>g[1].length);
  s.visiveis=grupos.flatMap(g=>g[1].map(x=>x.id));
  if(!s.visiveis.includes(s.ativo))s.ativo=s.visiveis[0]||null;
  lista.innerHTML=grupos.length?grupos.map(g=>'<li class="sel-svc-grupo" role="presentation">'+esc(g[0])+'</li>'+
    g[1].map(x=>'<li role="option" id="'+id+'-op-'+x.id+'" class="sel-svc-op'+(x.id===s.valor?' marcado':'')+(x.id===s.ativo?' ativo':'')+'" aria-selected="'+(x.id===s.valor)+'" '+
      'onpointerdown="event.preventDefault()" onpointermove="ativarOpcaoServico(\''+id+'\',\''+x.id+'\')" onclick="escolherServicoSel(\''+id+'\',\''+x.id+'\')">'+
      ic(x.ico,'ic-svc-'+x.id)+'<span>'+esc(x.n)+'</span>'+(x.id===s.valor?ic('check','sel-svc-ok'):'')+'</li>').join('')).join(''):
    '<li class="sel-svc-nada" role="presentation">Nenhum serviço com "'+esc(s.busca)+'". Tente outra palavra.</li>';
  marcarOpcaoAtiva(id,true);
}
function ativarOpcaoServico(id,servico){const s=SELS[id];if(s.ativo===servico)return;s.ativo=servico;marcarOpcaoAtiva(id,false);}
function marcarOpcaoAtiva(id,rolar){
  const s=SELS[id],lista=$('#'+id+'-lista'),q=$('#'+id+'-q');if(!lista)return;
  lista.querySelectorAll('.sel-svc-op.ativo').forEach(e=>e.classList.remove('ativo'));
  const el=s.ativo?$('#'+id+'-op-'+s.ativo):null;
  if(el){el.classList.add('ativo');if(rolar)el.scrollIntoView({block:'nearest'});}
  if(q){if(el)q.setAttribute('aria-activedescendant',el.id);else q.removeAttribute('aria-activedescendant');}
}
function teclaSeletorServico(ev,id){
  const s=SELS[id],v=s.visiveis||[],i=v.indexOf(s.ativo);
  if(ev.key==='ArrowDown'||ev.key==='ArrowUp'){
    ev.preventDefault();if(!v.length)return;
    s.ativo=v[ev.key==='ArrowDown'?Math.min(v.length-1,i+1):Math.max(0,i-1)];marcarOpcaoAtiva(id,true);
  }else if(ev.key==='Enter'){ev.preventDefault();if(s.ativo)escolherServicoSel(id,s.ativo);}
  else if(ev.key==='Escape'){ev.preventDefault();ev.stopPropagation();fecharSeletorServico(id,true);}
  else if(ev.key==='Tab')fecharSeletorServico(id);
}
function escolherServicoSel(id,servico){
  const s=SELS[id];s.valor=servico;
  const campo=$('#'+id);if(campo){campo.value=servico;campo.dispatchEvent(new Event('change',{bubbles:true}));}
  const b=$('#'+id+'-btn');if(b)b.innerHTML=conteudoGatilhoServico(servico);
  fecharSeletorServico(id,true);
  if(s.aoMudar&&typeof window[s.aoMudar]==='function')window[s.aoMudar]();
}
