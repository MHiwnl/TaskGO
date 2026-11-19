/* =========================================================
   RASTREIO DO PEDIDO (a caminho)
   O mapa abre na hora com o local do atendimento. O profissional manda a posição sozinho
   enquanto a página do pedido está aberta (o navegador pede a permissão); o cliente recebe
   a cada 5 segundos. A rota segue as ruas (OSRM, OpenStreetMap) e dá minutos e km.
   Posição imprecisa (computador, Wi-Fi) aparece como "aproximada", sem fingir exatidão.
   ========================================================= */
const MAPA_TILES='https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const MAPA_CREDITO='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';
const IMPRECISA_M=200;
let rastreio=null;

function destinoDoPedido(o){const a=Number(o.lat),b=Number(o.lng);return o.lat!=null&&isFinite(a)&&isFinite(b)?[a,b]:null;}
function linkNavegacao(o,app){
  const d=destinoDoPedido(o);const alvo=d?d.join(','):encodeURIComponent(o.address||'');
  return app==='waze'?(d?'https://waze.com/ul?ll='+alvo+'&navigate=yes':'https://waze.com/ul?q='+alvo+'&navigate=yes')
    :'https://www.google.com/maps/dir/?api=1&destination='+alvo+'&travelmode=driving';
}

/* Cartão do mapa dentro do pedido */
function mapaRastreioCard(o,isPro){
  /* Profissional a caminho: só o que ajuda a chegar (tempo, Waze e Cheguei). A posição segue sendo enviada até a chegada. */
  const nav=isPro?'<div class="rastreio-acoes">'+
      '<a class="btn btn-ghost btn-sm" href="'+esc(linkNavegacao(o,'waze'))+'" target="_blank" rel="noopener">'+ic('route')+' Abrir no Waze</a>'+
      '<button class="btn btn-grad btn-sm" data-chegada onclick="informarChegada(\''+esc(o.id)+'\')"'+(chegando===o.id?' disabled aria-busy="true"':'')+'>'+(chegando===o.id?'<span class="pag-ponto"></span> Confirmando...':ic('mapPin')+' Cheguei')+'</button></div>':'';
  return '<div class="rastreio">'+
    '<div class="rastreio-painel" id="rastreio-painel" aria-live="polite">'+painelRastreio(isPro)+'</div>'+
    '<div class="map tracking-map"><div id="mapa-rastreio" class="leaflet-tracking-map" role="application" aria-label="Mapa com '+(isPro?'você':'o profissional')+' e o local do atendimento"></div>'+
    '<button type="button" class="rastreio-centro" onclick="centralizarRastreio()" aria-label="Centralizar no trajeto" title="Centralizar no trajeto">'+ic('target')+'</button></div>'+
    '<p id="gps-status" class="rastreio-status"></p>'+nav+'</div>';
}
function painelRastreio(isPro){
  const r=rastreio,info=r&&r.info;
  if(!info){
    return '<div class="rastreio-eta"><b>—</b><span>min</span></div><div class="rastreio-texto"><b>'+(isPro?'Procurando sua posição...':'Esperando a posição do profissional')+'</b>'+
      '<small>'+(isPro?'Permita a localização no navegador. Ela vai só para o cliente deste pedido, enquanto você estiver a caminho.':'Assim que o aparelho dele mandar a posição, a rota e o tempo aparecem aqui.')+'</small></div>';
  }
  const aprox=info.precisao>IMPRECISA_M;
  return '<div class="rastreio-eta'+(aprox?' aprox':'')+'"><b>'+(aprox?'~':'')+info.min+'</b><span>min</span></div>'+
    '<div class="rastreio-texto"><b>'+String(info.km.toFixed(1)).replace('.',',')+' km '+(info.pelaRua?'pelas ruas':'em linha reta')+'</b>'+
    '<small>'+(isPro?'Até o local do atendimento':'O profissional está a caminho')+' · atualizado '+haQuantoSeg(info.em)+'</small></div>';
}
function haQuantoSeg(t){const s=Math.max(0,Math.round((Date.now()-t)/1000));return s<10?'agora':s<60?'há '+s+' s':'há '+Math.round(s/60)+' min';}
function pintarPainelRastreio(){const n=$('#rastreio-painel');if(n&&rastreio)n.innerHTML=painelRastreio(rastreio.isPro);}

/* Liga (ou religa, depois de um render) o mapa do pedido aberto. */
function ligarRastreio(o){
  const no=$('#mapa-rastreio');if(!no||!window.L)return;
  const isPro=ladoAtende(o);
  if(rastreio&&rastreio.id!==o.id)pararRastreio();
  if(!rastreio)rastreio={id:o.id,isPro,pro:null,info:null,seguir:true,rotaDe:null,rotaEm:0,timer:null,watch:null,enviadoEm:0,compartilhando:false,relogio:null};
  const r=rastreio;
  if(r.mapa&&r.no===no)return;
  if(r.mapa){r.mapa.remove();}
  const destino=destinoDoPedido(o);
  const mapa=L.map(no,{zoomControl:true,scrollWheelZoom:true}).setView(destino||[-8.0476,-34.877],destino?15:12);
  L.tileLayer(MAPA_TILES,{maxZoom:19,attribution:MAPA_CREDITO}).addTo(mapa);
  r.mapa=mapa;r.no=no;r.destino=destino;r.marcaPro=null;r.area=null;r.linha=null;
  if(destino)L.marker(destino,{icon:L.divIcon({className:'pino-destino',html:'<span>'+ic('home')+'</span>',iconSize:[38,38],iconAnchor:[19,19]}),title:'Local do atendimento',keyboard:false}).addTo(mapa);
  mapa.on('dragstart',()=>{r.seguir=false;});
  setTimeout(()=>mapa.invalidateSize(),60);
  if(r.pro){r.rotaDe=null;desenharPro();calcularRota();}
  pintarPainelRastreio();
  clearInterval(r.relogio);r.relogio=setInterval(pintarPainelRastreio,5000);
  if(isPro){if(!r.compartilhando&&r.compartilhar!==false)comecarCompartilhamento(o.id);}
  else{clearInterval(r.timer);buscarPosicaoPro(o.id);r.timer=setInterval(()=>buscarPosicaoPro(o.id),5000);}
}
function pararRastreio(){
  if(!rastreio)return;
  clearInterval(rastreio.timer);clearInterval(rastreio.relogio);clearInterval(rastreio.envio);
  if(rastreio.watch!=null&&navigator.geolocation)navigator.geolocation.clearWatch(rastreio.watch);
  if(rastreio.mapa)rastreio.mapa.remove();
  rastreio=null;
}
/* Compatível com quem já chamava o nome antigo (chegada, troca de página). */
function pararCompartilhamentoGPS(){if(!rastreio)return;clearInterval(rastreio.envio);if(rastreio.watch!=null&&navigator.geolocation)navigator.geolocation.clearWatch(rastreio.watch);rastreio.watch=null;rastreio.compartilhando=false;}

/* Cliente: posição enviada pelo profissional neste pedido; sem ela, a última posição de quando ele ficou online. */
async function buscarPosicaoPro(id){
  try{
    const r=await fetch(API_BASE_URL+'/api/pedidos/contratados/'+encodeURIComponent(id)+'/localizacao');
    if(r.status===409){pararRastreio();const email=me()?.email;if(email)syncPedidos(email).then(render).catch(()=>{});return;}
    if(r.ok&&r.status!==204){const p=await r.json();atualizarPro(p.latitude,p.longitude,p.accuracy,p.measuredAt,'pedido');return;}
    const d=await apiJSON('/api/chamar-agora/'+encodeURIComponent(id)).catch(()=>null);
    if(d&&d.profissional&&d.profissional.lat!=null){atualizarPro(d.profissional.lat,d.profissional.lng,300,Date.now(),'presenca');return;}
    gpsStatus('O profissional ainda não mandou a posição. Se demorar, mande uma mensagem para ele.');
  }catch(e){gpsStatus('Sem conexão para atualizar a posição. Tentando de novo...');}
}

/* Profissional: o próprio aparelho mostra a posição na hora e manda ao servidor a cada 10 s. */
function comecarCompartilhamento(id){
  const r=rastreio;if(!r||!navigator.geolocation){gpsStatus('Este aparelho não informa a localização. Use os botões do Google Maps ou do Waze para ir até o local.');return;}
  r.compartilhando=true;r.compartilhar=true;
  const b=$('#btn-gps');if(b)b.textContent='Parar de compartilhar';
  const enviar=async()=>{
    const pos=r.ultimaPos;if(!pos||!rastreio||rastreio.id!==id||!r.compartilhando)return;
    if(Date.now()-r.enviadoEm<10500)return;r.enviadoEm=Date.now();
    try{
      const resp=await fetch(API_BASE_URL+'/api/pedidos/contratados/'+encodeURIComponent(id)+'/localizacao',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({latitude:pos.coords.latitude,longitude:pos.coords.longitude,accuracy:Math.min(pos.coords.accuracy||50,9999),measuredAt:Date.now()})});
      if(resp.status===409)pararCompartilhamentoGPS();
    }catch(e){/* tenta de novo no próximo envio */}
  };
  /* Manda a cada 10 s a posição mais recente, mesmo parado: o cliente sempre vê uma posição atual. */
  clearInterval(r.envio);r.envio=setInterval(enviar,11000);
  r.watch=navigator.geolocation.watchPosition(pos=>{
    if(!rastreio||rastreio.id!==id)return;
    r.ultimaPos=pos;
    atualizarPro(pos.coords.latitude,pos.coords.longitude,pos.coords.accuracy,Date.now(),'aparelho');
    enviar();
  },err=>{
    gpsStatus(err.code===1?'Você bloqueou a localização. O cliente não vê onde você está; libere no cadeado da barra de endereço.':'Não foi possível pegar sua localização agora. Tentando de novo...');
  },{enableHighAccuracy:true,maximumAge:3000,timeout:20000});
}
async function alternarCompartilhamento(id){
  const r=rastreio;if(!r)return;
  if(r.compartilhando){
    pararCompartilhamentoGPS();r.compartilhar=false;
    try{await fetch(API_BASE_URL+'/api/pedidos/contratados/'+encodeURIComponent(id)+'/localizacao',{method:'DELETE'});}catch(e){}
    const b=$('#btn-gps');if(b)b.textContent='Voltar a compartilhar';
    gpsStatus('Compartilhamento parado. O cliente não vê mais a sua posição.');
  }else{comecarCompartilhamento(id);gpsStatus('Compartilhando de novo com o cliente.');}
}

function atualizarPro(lat,lng,precisao,em,fonte){
  const r=rastreio;if(!r)return;
  r.pro={lat:Number(lat),lng:Number(lng),precisao:Number(precisao)||0,em:Number(em)||Date.now(),fonte};
  desenharPro();calcularRota();
  const prec=r.pro.precisao;
  if(fonte==='presenca')gpsStatus('Posição de quando o profissional ficou online. Ela atualiza quando ele abrir o pedido.');
  else if(prec>IMPRECISA_M)gpsStatus((r.isPro?'Seu aparelho':'O aparelho do profissional')+' está dando uma posição aproximada (margem de '+(prec>=1000?String((prec/1000).toFixed(1)).replace('.',',')+' km':Math.round(prec)+' m')+'). Num celular com GPS ligado, fica precisa.');
  else gpsStatus(r.isPro?'O cliente está vendo você chegar.':'Posição do GPS do profissional, com margem de '+Math.round(prec)+' m.');
}
function desenharPro(){
  const r=rastreio,p=r&&r.pro;if(!r||!r.mapa||!p)return;
  const ll=[p.lat,p.lng];
  if(!r.marcaPro){
    const o=S.orders.find(x=>x.id===r.id),s=svc(o&&o.service);
    r.marcaPro=L.marker(ll,{icon:L.divIcon({className:'pino-pro escolhido',html:'<span>'+ic(r.isPro?'nav':s.ico)+'</span>',iconSize:[40,40],iconAnchor:[20,20]}),title:r.isPro?'Você':'Profissional',keyboard:false,zIndexOffset:500}).addTo(r.mapa);
  }else r.marcaPro.setLatLng(ll);
  /* Margem grande não vira um círculo de quilômetros na tela: só aparece até 1 km. */
  if(r.area){r.area.remove();r.area=null;}
  if(p.precisao>30&&p.precisao<=1000)r.area=L.circle(ll,{radius:p.precisao,color:'#2b59ff',weight:1,fillOpacity:.08,interactive:false}).addTo(r.mapa);
  enquadrar();
}
function enquadrar(forcar){
  const r=rastreio;if(!r||!r.mapa||(!r.seguir&&!forcar))return;
  const pts=[];if(r.pro)pts.push([r.pro.lat,r.pro.lng]);if(r.destino)pts.push(r.destino);
  if(pts.length===2)r.mapa.fitBounds(L.latLngBounds(pts).pad(.25),{maxZoom:17,animate:true});
  else if(pts.length)r.mapa.setView(pts[0],16,{animate:true});
}
function centralizarRastreio(){if(!rastreio)return;rastreio.seguir=true;enquadrar(true);}

/* Rota pelas ruas (servidor público do OSRM). Refaz quando o profissional anda 40 m ou a cada minuto. */
async function calcularRota(){
  const r=rastreio;if(!r||!r.pro||!r.destino)return;
  const p=r.pro,mudou=!r.rotaDe||ChamarDistKm(r.rotaDe,[p.lat,p.lng])>.04||Date.now()-r.rotaEm>60000;
  const reta=ChamarDistKm([p.lat,p.lng],r.destino);
  if(!mudou){if(r.info){r.info.em=p.em;r.info.precisao=p.precisao;}pintarPainelRastreio();return;}
  r.rotaDe=[p.lat,p.lng];r.rotaEm=Date.now();
  let geo=null,km=reta,min=Math.max(1,Math.round(reta/25*60)),pelaRua=false;
  try{
    const url='https://router.project-osrm.org/route/v1/driving/'+p.lng+','+p.lat+';'+r.destino[1]+','+r.destino[0]+'?overview=full&geometries=geojson';
    const d=await (await fetch(url)).json();
    if(d&&d.code==='Ok'&&d.routes&&d.routes[0]){const rt=d.routes[0];geo=rt.geometry.coordinates.map(c=>[c[1],c[0]]);km=rt.distance/1000;min=Math.max(1,Math.round(rt.duration*1.25/60));pelaRua=true;}
  }catch(e){/* sem rota: fica a linha reta */}
  if(!rastreio||rastreio!==r||!r.mapa)return;
  if(r.linha)r.linha.remove();
  r.linha=L.polyline(geo||[[p.lat,p.lng],r.destino],{color:'#2b59ff',weight:geo?6:3,opacity:.9,dashArray:geo?null:'6 8',lineCap:'round',interactive:false}).addTo(r.mapa);
  r.info={km,min,pelaRua,em:p.em,precisao:p.precisao};
  pintarPainelRastreio();
}
function ChamarDistKm(a,b){const R=6371,dLat=(b[0]-a[0])*Math.PI/180,dLng=(b[1]-a[1])*Math.PI/180;const x=Math.sin(dLat/2)**2+Math.cos(a[0]*Math.PI/180)*Math.cos(b[0]*Math.PI/180)*Math.sin(dLng/2)**2;return 2*R*Math.atan2(Math.sqrt(x),Math.sqrt(1-x));}

window.addEventListener('hashchange',()=>{if(rastreio&&!location.hash.includes('/app/pedido/'+rastreio.id))pararRastreio();});
