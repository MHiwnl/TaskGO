/* =========================================================
   ÁREA DE ATENDIMENTO (profissional)
   Raio em km e ponto de partida. Online, a distância conta de onde ele está;
   offline ou sem GPS, do ponto de partida. As oportunidades e os avisos de pedido novo respeitam o raio.
   ========================================================= */
let areaAtend=null,areaEdit=null,areaMapa=null,areaCirculo=null,areaPino=null;

async function carregarAreaAtendimento(){
  if(DEMO_MODE){areaAtend=areaAtend||{raioKm:15,baseLat:null,baseLng:null,baseTexto:null,medindoDe:null};return areaAtend;}
  try{areaAtend=await apiJSON('/api/profissionais/eu/area');}catch(e){areaAtend=null;}
  const l=$('#linhaArea');if(l)l.innerHTML=linhaArea();
  return areaAtend;
}
function linhaArea(){
  if(!areaAtend)return '<span class="muted">Conferindo sua área de atendimento...</span>';
  const a=areaAtend;
  const de=a.medindoDe==='agora'?'de onde você está agora':a.baseLat!=null?'de '+(a.baseTexto?esc(a.baseTexto):'seu ponto de partida'):null;
  return ic('mapPin')+'<span>'+(de?'Pedidos até <b>'+Math.round(a.raioKm)+' km</b> '+de+'.':'<b>Sem ponto de partida.</b> Marque de onde você sai para ver só os pedidos perto.')+'</span>'+
    '<button type="button" class="btn btn-ghost btn-sm" onclick="ajustarRaio()">'+ic('edit')+'Ajustar área</button>';
}
function ajustarRaio(){
  const a=areaAtend||{raioKm:15};
  areaEdit={raioKm:Math.round(a.raioKm||15),baseLat:a.baseLat,baseLng:a.baseLng,baseTexto:a.baseTexto||''};
  openModal('<div class="modal-head"><div><h3>Área de atendimento</h3><p>Você vê e recebe aviso só dos pedidos dentro do raio.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<div class="field"><label for="raioArea">Distância máxima: <b id="raioL">'+areaEdit.raioKm+' km</b></label>'+
    '<input id="raioArea" class="area-raio" type="range" min="1" max="50" step="1" value="'+areaEdit.raioKm+'" oninput="mudarRaioArea(this.value)"></div>'+
    '<div class="area-ponto"><b>Ponto de partida</b><button type="button" class="btn btn-ghost btn-sm" onclick="pontoAreaPelaPosicao()">'+ic('nav')+'Usar minha localização</button></div>'+
    '<p class="hint" id="areaDica">'+(areaEdit.baseLat!=null?'Toque no mapa para mudar o ponto.':'Toque no mapa onde você costuma sair (casa, oficina).')+'</p>'+
    '<div id="mapaArea" class="area-mapa" role="application" aria-label="Mapa para marcar o ponto de partida"></div>'+
    '<div class="field"><label for="areaTexto">Nome do ponto (opcional)</label><input class="input" id="areaTexto" maxlength="60" placeholder="Ex.: Casa, Boa Viagem" value="'+esc(areaEdit.baseTexto)+'" oninput="areaEdit.baseTexto=this.value"></div>'+
    '<p class="hint">Quando você está online com o site aberto, a distância conta de onde você está.</p>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Cancelar</button><button class="btn btn-primary" id="areaSalvar" onclick="salvarAreaAtendimento()">Salvar área</button></div>',
    {wide:true,onOpen:()=>setTimeout(montarMapaArea,30)});
}
function montarMapaArea(){
  const no=$('#mapaArea');if(!no||!window.L)return;
  if(areaMapa){areaMapa.remove();areaMapa=null;areaCirculo=null;areaPino=null;}
  const centro=areaEdit.baseLat!=null?[areaEdit.baseLat,areaEdit.baseLng]:[-8.0476,-34.8770];
  areaMapa=L.map(no,{scrollWheelZoom:true}).setView(centro,11);
  L.tileLayer(MAPA_TILES,{maxZoom:19,attribution:MAPA_CREDITO}).addTo(areaMapa);
  areaMapa.on('click',e=>marcarPontoArea(e.latlng.lat,e.latlng.lng));
  if(areaEdit.baseLat!=null)marcarPontoArea(areaEdit.baseLat,areaEdit.baseLng,true);
  setTimeout(()=>{if(areaMapa&&no.isConnected)areaMapa.invalidateSize();},80);
}
function marcarPontoArea(lat,lng,inicial){
  areaEdit.baseLat=Math.round(lat*1e5)/1e5;areaEdit.baseLng=Math.round(lng*1e5)/1e5;
  const ll=[areaEdit.baseLat,areaEdit.baseLng];
  if(!areaPino)areaPino=L.marker(ll,{icon:L.divIcon({className:'pino-voce',html:'<span>'+ic('home')+'</span>',iconSize:[40,40],iconAnchor:[20,20]}),keyboard:false}).addTo(areaMapa);else areaPino.setLatLng(ll);
  if(!areaCirculo)areaCirculo=L.circle(ll,{radius:areaEdit.raioKm*1000,color:'#2b59ff',weight:2,fillOpacity:.1,interactive:false}).addTo(areaMapa);else areaCirculo.setLatLng(ll);
  enquadrarArea();
  const d=$('#areaDica');if(d&&!inicial)d.textContent='Ponto marcado. Toque de novo para mudar.';
}
function enquadrarArea(){if(areaMapa&&areaCirculo)areaMapa.fitBounds(areaCirculo.getBounds().pad(.1));}
function mudarRaioArea(v){
  areaEdit.raioKm=Number(v);const l=$('#raioL');if(l)l.textContent=v+' km';
  if(areaCirculo){areaCirculo.setRadius(areaEdit.raioKm*1000);clearTimeout(mudarRaioArea.t);mudarRaioArea.t=setTimeout(enquadrarArea,250);}
}
function pontoAreaPelaPosicao(){
  if(!navigator.geolocation)return toast('Este navegador não informa a localização.','err');
  const d=$('#areaDica');if(d)d.textContent='Buscando sua localização...';
  navigator.geolocation.getCurrentPosition(p=>marcarPontoArea(p.coords.latitude,p.coords.longitude),
    ()=>{if(d)d.textContent='Não foi possível pegar a localização. Toque no mapa para marcar o ponto.';},{enableHighAccuracy:true,timeout:15000,maximumAge:60000});
}
async function salvarAreaAtendimento(){
  const b=$('#areaSalvar');if(b)b.disabled=true;
  const corpo={raioKm:areaEdit.raioKm,baseTexto:String(areaEdit.baseTexto||'').trim()};
  if(areaEdit.baseLat!=null){corpo.baseLat=areaEdit.baseLat;corpo.baseLng=areaEdit.baseLng;}
  try{
    if(DEMO_MODE)areaAtend=Object.assign({},areaAtend,corpo);
    else areaAtend=await apiJSON('/api/profissionais/eu/area','PUT',corpo,'Não foi possível salvar a área.');
    closeModal();toast('Área salva: pedidos até '+Math.round(areaAtend.raioKm)+' km','ok');
    const l=$('#linhaArea');if(l)l.innerHTML=linhaArea();
    carregarPedidos();
  }catch(e){toast(erroLegivel(e,'Não foi possível salvar a área.'),'err');if(b)b.disabled=false;}
}
function distanciaOportunidade(p){
  if(p.km==null)return '';
  const km=Number(p.km);
  return km<1?'a menos de 1 km':'a '+String(km.toFixed(km<10?1:0)).replace('.',',')+' km';
}
