/* =========================================================
   FOTOS DO PEDIDO E DA CONVERSA
   A foto é reduzida no navegador (até 1600 px, JPEG) e o servidor recodifica de novo, sem metadados.
   ========================================================= */
const FOTOS={},FOTOS_MAX_PEDIDO=10;

function urlFotoPedido(id){return API_BASE_URL+'/api/fotos-pedido/'+encodeURIComponent(id);}
function comprimirFoto(file){
  return new Promise((ok,falha)=>{
    if(!file||!/^image\//.test(file.type))return falha(new Error('Escolha um arquivo de imagem.'));
    const url=URL.createObjectURL(file),img=new Image();
    img.onload=()=>{
      const k=Math.min(1,1600/Math.max(img.naturalWidth,img.naturalHeight));
      const c=document.createElement('canvas');c.width=Math.round(img.naturalWidth*k);c.height=Math.round(img.naturalHeight*k);
      const g=c.getContext('2d');g.fillStyle='#fff';g.fillRect(0,0,c.width,c.height);g.drawImage(img,0,0,c.width,c.height);
      URL.revokeObjectURL(url);ok(c.toDataURL('image/jpeg',.82));
    };
    img.onerror=()=>{URL.revokeObjectURL(url);falha(new Error('Não foi possível abrir esta imagem.'));};
    img.src=url;
  });
}
function abrirFotoGrande(src,alt){
  openModal('<div class="modal-head"><div><h3>Foto</h3></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<img class="foto-grande" src="'+esc(src)+'" alt="'+esc(alt||'Foto do pedido')+'">',{wide:true});
}
function miniatura(src,alt,extra){
  return '<button type="button" class="foto-mini" onclick="abrirFotoGrande(\''+esc(src)+'\',\''+esc(alt||'')+'\')" aria-label="Ver foto ampliada">'+
    '<img src="'+esc(src)+'" alt="'+esc(alt||'Foto do pedido')+'" loading="lazy"></button>'+(extra||'');
}

/* ---------- No pedido ---------- */
function blocoFotosPedido(o){
  if(DEMO_MODE||!o)return '';
  const lista=FOTOS[o.id];
  if(lista===undefined)setTimeout(()=>carregarFotosPedido(o.id),0);
  /* Fotos do pedido são do cliente (mostrar o problema). Quem atende só vê, e só quando existem.
     Encerrado o pedido, viram histórico: nada de enviar nem apagar. */
  const atende=ladoAtende(o),pode=!atende&&!['cancelado','concluido'].includes(o.status);
  if(!pode&&(lista===undefined||!lista.length))return '';
  return '<div class="card fotos-pedido" id="fotosPedido" style="margin-top:20px"><div class="fotos-cab"><h2 class="sub">'+ic('camera')+(atende?'Fotos do cliente':'Fotos')+'</h2>'+
    (pode&&(lista||[]).length<FOTOS_MAX_PEDIDO?'<label class="btn btn-ghost btn-sm">'+ic('plus')+'Adicionar foto<input type="file" accept="image/*" class="leitor" onchange="enviarFotoPedido(\''+esc(o.id)+'\',this)"></label>':'')+'</div>'+
    (lista===undefined?'<p class="muted">Carregando...</p>':
     !lista.length?'<p class="muted">Envie fotos do problema.</p>':
     '<div class="fotos-grade">'+lista.map(f=>'<div class="foto-item">'+miniatura(urlFotoPedido(f.id),'Foto do pedido')+
       (f.minha&&pode?'<button type="button" class="foto-apagar" aria-label="Apagar foto" onclick="apagarFotoPedido(\''+esc(o.id)+'\',\''+esc(f.id)+'\')">'+ic('trash')+'</button>':'')+'</div>').join('')+'</div>')+
  '</div>';
}
async function carregarFotosPedido(id){
  try{const l=await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)+'/fotos');const eu=String(me()?.id||'').replace(/^db_/,'');FOTOS[id]=l.map(f=>Object.assign(f,{minha:String(f.autorId)===eu}));}
  catch(e){FOTOS[id]=[];}
  const o=S.orders.find(x=>x.id===id),c=$('#fotosPedido');if(o&&c)c.outerHTML=blocoFotosPedido(o);
}
async function enviarFotoPedido(id,input){
  const file=input.files&&input.files[0];input.value='';if(!file)return;
  toast('Enviando foto...');
  try{const foto=await comprimirFoto(file);await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)+'/fotos','POST',{foto},'Não foi possível enviar a foto.');toast('Foto enviada','ok');await carregarFotosPedido(id);}
  catch(e){toast(erroLegivel(e,'Não foi possível enviar a foto.'),'err');}
}
async function apagarFotoPedido(id,fotoId){
  if(!await confirmar({titulo:'Apagar esta foto?',botao:'Apagar',perigo:true,icone:'trash'}))return;
  try{await apiJSON('/api/fotos-pedido/'+encodeURIComponent(fotoId),'DELETE');await carregarFotosPedido(id);}
  catch(e){toast(erroLegivel(e,'Não foi possível apagar a foto.'),'err');}
}

/* ---------- No Contratar: escolhidas antes, enviadas depois que o pedido existe ---------- */
function campoFotosContratar(){
  if(DEMO_MODE)return '';
  const l=W.fotos||[];
  return '<div class="field"><span class="rotulo-campo">Fotos do problema (opcional)</span>'+
    '<div class="fotos-grade fotos-contratar">'+l.map((f,i)=>'<div class="foto-item">'+miniatura(f,'Foto '+(i+1))+
      '<button type="button" class="foto-apagar" aria-label="Tirar foto '+(i+1)+'" onclick="W.fotos.splice('+i+',1);render()">'+ic('x')+'</button></div>').join('')+
    (l.length<5?'<label class="foto-add">'+ic('camera')+'<span>Adicionar</span><input type="file" accept="image/*" multiple class="leitor" onchange="adicionarFotosContratar(this)"></label>':'')+'</div>'+
    '<div class="hint">Até 5 fotos. Os profissionais da categoria veem antes de aceitar.</div></div>';
}
async function adicionarFotosContratar(input){
  const files=[...(input.files||[])];input.value='';
  W.fotos=W.fotos||[];
  for(const f of files){if(W.fotos.length>=5)break;try{W.fotos.push(await comprimirFoto(f));}catch(e){toast(e.message,'err');}}
  render();
}
async function enviarFotosDoPedido(id,fotos){
  let falhas=0;
  for(const foto of fotos||[]){try{await apiJSON('/api/pedidos/contratados/'+encodeURIComponent(id)+'/fotos','POST',{foto});}catch(e){falhas++;}}
  if(falhas)toast(falhas+' foto(s) não foram enviadas. Você pode mandar de novo na página do pedido.','err');
}

/* ---------- Nas oportunidades ---------- */
function fotosOportunidade(p){
  if(!p.fotos||!p.fotos.length)return '';
  return '<div class="fotos-grade fotos-op">'+p.fotos.slice(0,4).map(id=>miniatura(urlFotoPedido(id),'Foto do pedido')).join('')+
    (p.fotos.length>4?'<span class="fotos-mais">+'+(p.fotos.length-4)+'</span>':'')+'</div>';
}

/* ---------- Na conversa ---------- */
async function enviarFotoConversa(input){
  const file=input.files&&input.files[0];input.value='';if(!file)return;
  const chat=myChats().find(item=>String(item.id)===String(chatAtivo));if(!chat)return;
  try{
    const foto=await comprimirFoto(file);
    const sent=await enviarMensagemPedido(chat.orderId,'',crypto.randomUUID(),foto);
    guardarMsgChat(chat.orderId,sent);
    render();
  }catch(e){toast(erroLegivel(e,'Não foi possível enviar a foto.'),'err');}
}
/* Ao abrir um pedido, busca as fotos de novo (a outra pessoa pode ter enviado); a lista antiga fica na tela até chegar a nova. */
if(typeof window.addEventListener==='function')window.addEventListener('hashchange',()=>{const m=location.hash.match(/\/app\/pedido\/([^/?#]+)/);if(m&&me())carregarFotosPedido(decodeURIComponent(m[1]));});
