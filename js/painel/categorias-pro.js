/* =========================================================
   CATEGORIAS DO PROFISSIONAL
   O profissional marca tudo o que sabe fazer (até 10). A primeira é a principal.
   Ele recebe oportunidades, avisos e Chamar agora de todas.
   ========================================================= */
const CATS={lista:null,carregando:false,edicao:null};
let MAX_CATEGORIAS=10;
const CATEGORIAS_PRO=20;

async function carregarCategoriasPro(forcar){
  const u=me();
  if(DEMO_MODE||!u||u.role!=='profissional'||CATS.carregando||(CATS.lista&&!forcar))return;
  CATS.carregando=true;
  try{const r=await apiJSON('/api/profissionais/eu/servicos');CATS.lista=r.servicos||[];if(r.maximo)MAX_CATEGORIAS=r.maximo;}catch(e){CATS.lista=u.service?[u.service]:[];}
  finally{CATS.carregando=false;}
  u.servicos=CATS.lista.slice();Store.save();
  if(route.page==='painel'||route.page==='app')render();
}
function categoriasDoPro(){const u=me();return (CATS.lista||u.servicos||(u.service?[u.service]:[])).slice();}

/* Bloco em Configurações. */
function blocoCategoriasPro(){
  const u=me();
  if(DEMO_MODE||!u||u.role!=='profissional')return '';
  if(!CATS.lista){carregarCategoriasPro();}
  const l=categoriasDoPro();
  return '<div class="card" style="margin-top:20px"><div class="inicio-cab"><h2 class="sub" style="font-size:1.05rem">O que você sabe fazer</h2><span class="muted">'+l.length+' de '+MAX_CATEGORIAS+'</span></div>'+
    '<p class="muted" style="font-size:.88rem;margin:6px 0 12px">Você recebe pedidos de todas as categorias marcadas. A primeira é a principal, que aparece no seu perfil.</p>'+
    (l.length?'<ul class="cats-pro">'+l.map((id,i)=>{const s=svc(id);return '<li>'+ic(s.ico,'ic-svc-'+s.id)+'<span>'+esc(s.n)+'</span>'+(i===0?'<span class="tag tag-info">Principal</span>':'')+'</li>';}).join('')+'</ul>':'<p class="muted">Nenhuma categoria.</p>')+
    '<button class="btn btn-ghost btn-sm" onclick="abrirCategoriasPro()">'+ic('edit')+'Escolher categorias</button></div>';
}
function abrirCategoriasPro(){
  CATS.edicao=categoriasDoPro();CATS.busca='';
  openModal(htmlCategoriasPro(),{wide:true,onOpen:()=>{const b=$('#catBusca');if(b)b.focus();}});
}
function htmlCategoriasPro(){
  const sel=CATS.edicao,q=semAcentoBusca(CATS.busca||'');
  const casa=s=>!q||semAcentoBusca(s.n+' '+s.d+' '+(TERMOS_SERVICO[s.id]||[]).join(' ')).includes(q);
  const grupos=GRUPOS_SERVICOS.map(g=>[g[0],g[1].map(svc).filter(casa)]).filter(g=>g[1].length);
  return '<div class="modal-head"><div><h3>O que você sabe fazer</h3><p>Marque até '+MAX_CATEGORIAS+'. A primeira marcada é a principal.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="opt-busca">'+ic('search')+'<label class="leitor" for="catBusca">Procurar categoria</label><input class="input" id="catBusca" type="search" placeholder="Ex.: chaveiro, bolo, pneu" value="'+esc(CATS.busca||'')+'" oninput="CATS.busca=this.value;pintarListaCategorias()" autocomplete="off"></div>'+
    '<p class="cats-contagem" id="catConta" aria-live="polite">'+contagemCategorias()+'</p>'+
    '<div class="cats-escolha" id="catLista">'+listaCategorias(grupos,sel)+'</div>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" id="catSalvar" onclick="salvarCategoriasPro()">'+ic('check')+'Salvar</button></div>';
}
function contagemCategorias(){const s=CATS.edicao;return s.length?s.length+' de '+MAX_CATEGORIAS+' · principal: <b>'+esc(svc(s[0]).n)+'</b>':'Nenhuma marcada';}
function listaCategorias(grupos,sel){
  if(!grupos.length)return '<p class="muted">Nenhuma categoria encontrada.</p>';
  return grupos.map(g=>'<fieldset class="cats-grupo"><legend>'+esc(g[0])+'</legend>'+g[1].map(s=>
    '<label class="chip-check"><input type="checkbox" value="'+s.id+'"'+(sel.includes(s.id)?' checked':'')+' onchange="marcarCategoria(this)"><span>'+ic(s.ico)+esc(s.n)+'</span></label>').join('')+'</fieldset>').join('');
}
function pintarListaCategorias(){
  const q=semAcentoBusca(CATS.busca||'');
  const casa=s=>!q||semAcentoBusca(s.n+' '+s.d+' '+(TERMOS_SERVICO[s.id]||[]).join(' ')).includes(q);
  const grupos=GRUPOS_SERVICOS.map(g=>[g[0],g[1].map(svc).filter(casa)]).filter(g=>g[1].length);
  $('#catLista').innerHTML=listaCategorias(grupos,CATS.edicao);
}
function marcarCategoria(input){
  const id=input.value;
  if(input.checked){
    if(CATS.edicao.length>=MAX_CATEGORIAS){input.checked=false;return toast('No máximo '+MAX_CATEGORIAS+' categorias.'+(MAX_CATEGORIAS<CATEGORIAS_PRO?' Com o Plano Pro, até '+CATEGORIAS_PRO+'.':''),'err');}
    if(!CATS.edicao.includes(id))CATS.edicao.push(id);
  }else CATS.edicao=CATS.edicao.filter(x=>x!==id);
  $('#catConta').innerHTML=contagemCategorias();
}
async function salvarCategoriasPro(){
  if(!CATS.edicao.length)return toast('Marque pelo menos uma categoria.','err');
  const b=$('#catSalvar');if(b)b.disabled=true;
  try{
    const r=await apiJSON('/api/profissionais/eu/servicos','PUT',{servicos:CATS.edicao},'Não foi possível salvar.');
    CATS.lista=r.servicos;const u=me();u.servicos=r.servicos.slice();u.service=r.servicos[0];Store.save();
    closeModal();toast('Categorias salvas. As oportunidades de todas já aparecem.','ok');render();
  }catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');if(b)b.disabled=false;}
}
