/* ---------- AVISOS DO SISTEMA (administrador) ----------
   O administrador escreve um aviso e ele chega para todas as contas, sem exceção,
   na categoria Sistema das notificações. A lista mostra quantas contas já leram. */
let avisosAdmin={lista:null,carregando:false,erro:'',enviando:false};
function adminAvisos(){
  if(!avisosAdmin.lista&&!avisosAdmin.carregando&&!avisosAdmin.erro)setTimeout(carregarAvisosAdmin,0);
  const lista=avisosAdmin.lista;
  return '<div class="avisos-grade">'+
  '<form class="card avisos-form" onsubmit="enviarAvisoSistema(event)">'+
    '<h2 class="sub">Novo aviso para todos</h2><p class="muted">Chega para clientes, profissionais, suporte e administradores, em Notificações &gt; Sistema, na hora.</p>'+
    '<div class="field"><label for="aviso-titulo">Título</label><input class="input" id="aviso-titulo" name="titulo" required minlength="3" maxlength="120" placeholder="Ex.: Manutenção no domingo às 2h" oninput="contarAviso()"></div>'+
    '<div class="field"><label for="aviso-texto">Aviso</label><textarea class="textarea" id="aviso-texto" name="texto" required minlength="5" maxlength="2000" rows="5" placeholder="Escreva o que todo mundo precisa saber e o que fazer." oninput="contarAviso()"></textarea><div class="hint" id="aviso-conta">0 de 2000 caracteres</div></div>'+
    '<button class="btn btn-grad" type="submit"'+(avisosAdmin.enviando?' disabled aria-busy="true"':'')+'>'+ic('megafone')+(avisosAdmin.enviando?' Enviando...':' Enviar para todas as contas')+'</button>'+
  '</form>'+
  '<section class="avisos-hist"><h2 class="sub">Avisos enviados</h2>'+
    (avisosAdmin.erro?'<p class="muted">'+esc(avisosAdmin.erro)+'</p>':!lista?'<p class="muted">Carregando...</p>':!lista.length?'<div class="card emptystate">'+ic('megafone')+'<b>Nenhum aviso enviado ainda</b></div>':
    '<ul class="avisos-lista">'+lista.map(a=>'<li><div><b>'+esc(a.titulo)+'</b><p>'+esc(a.texto)+'</p><small class="muted">'+dt(a.enviadoEm)+'</small></div>'+
      '<div class="avisos-leitura"><b>'+numeroBR(a.lidas)+'</b><span>de '+numeroBR(a.contas)+' leram</span><div class="bar"><i style="width:'+(a.contas?a.lidas/a.contas*100:0)+'%"></i></div></div></li>').join('')+'</ul>')+
  '</section></div>';
}
function contarAviso(){const t=$('#aviso-texto'),c=$('#aviso-conta');if(t&&c)c.textContent=t.value.length+' de 2000 caracteres';}
async function carregarAvisosAdmin(){
  avisosAdmin.carregando=true;
  try{avisosAdmin.lista=await apiJSON('/api/admin/avisos');avisosAdmin.erro='';}
  catch(e){avisosAdmin.erro=erroLegivel(e,'Não foi possível carregar os avisos.');}
  avisosAdmin.carregando=false;if(route.id==='avisos')render();
}
async function enviarAvisoSistema(ev){
  ev.preventDefault();
  const titulo=$('#aviso-titulo').value.trim(),texto=$('#aviso-texto').value.trim();
  if(titulo.length<3)return toast('Escreva um título com pelo menos 3 caracteres.','err');
  if(texto.length<5)return toast('Escreva o aviso com pelo menos 5 caracteres.','err');
  avisosAdmin.enviando=true;render();
  try{const r=await apiJSON('/api/admin/avisos','POST',{titulo,texto},'Não foi possível enviar o aviso.');
    toast('Aviso enviado para '+numeroBR(r.contas)+' contas.','ok');avisosAdmin.lista=null;}
  catch(e){toast(erroLegivel(e,'Não foi possível enviar o aviso.'),'err');}
  avisosAdmin.enviando=false;render();
}
