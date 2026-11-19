/* ---------- AGENDA ----------
   Próximos 14 dias: atendimentos com data marcada (contam 2 horas) e horários bloqueados.
   Pedido agendado que bate com a agenda não pode ser aceito (o servidor confere). */
let agendaDia=0,agendaDados=null,agendaCarregando=false;
const AGENDA_DIAS=14;
const AGENDA_DEMO=[
  [0,'09:00','Instalação de split — Ana Beatriz','Boa Viagem','confirmado',320],
  [0,'14:30','Manutenção elétrica — Condomínio Aurora','Pina','confirmado',480],
  [1,'08:00','Troca de disjuntores — Roberto Farias','Espinheiro','pendente',260],
  [1,'15:00','Orçamento — Reforma elétrica','Casa Forte','pendente',0],
  [2,'10:00','Higienização de 3 splits — Escritório Meta','Ilha do Leite','confirmado',540],
  [3,'09:30','Instalação de tomadas — Fernanda Lopes','Boa Viagem','confirmado',190],
  [4,'16:00','Revisão de quadro — Padaria São José','Graças','pendente',420],
  [5,'11:00','Manutenção preventiva — Studio Vitória','Olinda','confirmado',300],
  [6,'08:30','Instalação de CFTV — Clínica Vida','Espinheiro','confirmado',860]
];
function inicioDoDia(d){const x=new Date(d);x.setHours(0,0,0,0);return x;}
function horaCurta(ms){return new Date(ms).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});}

async function carregarAgenda(){
  if(DEMO_MODE||agendaCarregando)return;
  agendaCarregando=true;
  const de=inicioDoDia(Date.now()).getTime(),ate=de+AGENDA_DIAS*864e5;
  try{agendaDados=await apiJSON('/api/agenda?de='+de+'&ate='+ate);}catch(e){agendaDados={erro:erroLegivel(e,'Não foi possível carregar a agenda.')};}
  agendaCarregando=false;
  if(route.sub==='agenda')render();
}
/* Itens do dia i: atendimentos e bloqueios que tocam aquele dia. */
function agendaItens(i){
  const ini=inicioDoDia(Date.now()).getTime()+i*864e5,fim=ini+864e5;
  if(DEMO_MODE)return AGENDA_DEMO.filter(e=>e[0]===i).map(e=>({tipo:'atendimento',h:e[1],t:e[2],b:e[3],st:e[4],v:e[5]}));
  if(!agendaDados||agendaDados.erro)return [];
  const at=(agendaDados.atendimentos||[]).filter(a=>a.inicio<fim&&a.fim>ini).map(a=>({tipo:'atendimento',ms:a.inicio,h:horaCurta(a.inicio)+'–'+horaCurta(a.fim),
    t:svc(a.servico).n,b:a.bairro||'',st:a.status==='a_caminho'?'confirmado':'em andamento',v:a.valor,id:a.id}));
  const bl=(agendaDados.bloqueios||[]).filter(b=>b.inicio<fim&&b.fim>ini).map(b=>{
    const diaTodo=b.inicio<=ini&&b.fim>=fim;
    return {tipo:'bloqueio',ms:Math.max(b.inicio,ini),h:diaTodo?'Dia todo':horaCurta(Math.max(b.inicio,ini))+'–'+(b.fim>=fim?'24:00':horaCurta(b.fim)),t:b.motivo||'Horário bloqueado',id:b.id};
  });
  return at.concat(bl).sort((a,b)=>a.ms-b.ms);
}
function dashAgenda(){
  if(!DEMO_MODE&&!agendaDados)carregarAgenda();
  const hoje=inicioDoDia(Date.now());
  const n=DEMO_MODE?7:AGENDA_DIAS;
  const dias=Array.from({length:n},(_,i)=>new Date(hoje.getTime()+i*864e5));
  if(agendaDia>=n)agendaDia=0;
  const itens=agendaItens(agendaDia),sel=dias[agendaDia];
  const atend=itens.filter(e=>e.tipo==='atendimento'),total=atend.reduce((a,b)=>a+(Number(b.v)||0),0);
  const corpo=!DEMO_MODE&&!agendaDados?'<div class="card center" role="status">Carregando a agenda...</div>':
    agendaDados&&agendaDados.erro?'<div class="card emptystate" role="alert">'+ic('cal')+'<b>'+esc(agendaDados.erro)+'</b><button class="btn btn-ghost" style="margin-top:12px" onclick="agendaDados=null;render()">Tentar de novo</button></div>':
    itens.length?itens.map(e=>e.tipo==='bloqueio'?
      '<div class="rowitem agenda-bloqueio"><div class="agenda-hora"><b>'+esc(e.h)+'</b></div>'+
      '<div class="grow"><b>'+ic('lock')+' '+esc(e.t)+'</b><small>Nenhum pedido agendado entra neste horário.</small></div>'+
      (e.id?'<button type="button" class="btn btn-ghost btn-sm" onclick="desbloquearHorario(\''+esc(e.id)+'\')">Desbloquear</button>':'')+'</div>':
      '<div class="rowitem"'+(e.id?' role="button" tabindex="0" style="cursor:pointer" onclick="go(\'/app/pedido/'+esc(e.id)+'\')"':'')+'>'+
      '<div class="agenda-hora"><b>'+esc(e.h)+'</b></div>'+
      '<div class="grow"><b>'+esc(e.t)+'</b><small>'+ic('mapPin')+' '+esc(e.b||'—')+(e.v?' · '+money(e.v):'')+'</small></div>'+
      '<span class="tag '+(e.st==='confirmado'?'tag-ok':'tag-warn')+'">'+esc(e.st)+'</span></div>').join(''):
    '<div class="card emptystate">'+ic('cal')+'<b>Dia livre</b><p style="margin-top:6px">Pedidos agendados para este dia aparecem em Oportunidades.</p>'+
    '<a class="btn btn-primary" style="margin-top:16px" href="#/app/oportunidades">Ver oportunidades</a></div>';
  return dashHead('Agenda','Atendimentos marcados e horários bloqueados. Cada atendimento ocupa 2 horas.','<button class="btn btn-ghost" onclick="bloquearHorario()">'+ic('lock')+' Bloquear horário</button>')+
  '<div style="margin-bottom:22px"><div class="grid agenda-dias">'+
  dias.map((d,i)=>{const it=agendaItens(i),q=it.filter(e=>e.tipo==='atendimento').length,b=it.some(e=>e.tipo==='bloqueio');
    return '<button type="button" data-fk="dia-'+i+'" aria-pressed="'+(i===agendaDia)+'" class="dia '+(i===agendaDia?'sel':'')+(b?' tem-bloqueio':'')+'" onclick="agendaDia='+i+';render()">'+
    '<span class="muted" style="display:block;font-size:.72rem">'+(i===0?'HOJE':d.toLocaleDateString('pt-BR',{weekday:'short'}).replace('.','').toUpperCase())+'</span>'+
    '<div style="font-family:var(--font-mono);font-size:1.3rem;font-weight:600;margin:4px 0">'+d.getDate()+'</div>'+
    '<span class="tag '+(q?'tag-info':'tag-muted')+'">'+q+'</span></button>';}).join('')+'</div></div>'+
  '<div style="display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap;margin-bottom:14px">'+
  '<h2 class="sub">'+sel.toLocaleDateString('pt-BR',{weekday:'long',day:'2-digit',month:'long'})+'</h2>'+
  (atend.length?'<span class="pill">'+ic('wallet')+' Previsto: '+money(total)+' · '+atend.length+' atendimento'+(atend.length>1?'s':'')+'</span>':'')+'</div>'+corpo;
}
function bloquearHorario(){
  const d=new Date(inicioDoDia(Date.now()).getTime()+agendaDia*864e5),iso=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
  openModal('<div class="modal-head"><div><h3>Bloquear horário</h3><p>Pedidos agendados nesse período não chegam para você.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
  '<div class="field"><label for="blData">Data</label><input class="input" id="blData" type="date" value="'+iso+'" min="'+new Date().toISOString().slice(0,10)+'"></div>'+
  '<label class="pag-check"><input type="checkbox" id="blDia" onchange="document.getElementById(\'blHoras\').hidden=this.checked"> Dia todo</label>'+
  '<div class="grid g2" id="blHoras" style="gap:0 14px"><div class="field"><label for="blDe">Das</label><input class="input" id="blDe" type="time" value="12:00"></div>'+
  '<div class="field"><label for="blAte">Até</label><input class="input" id="blAte" type="time" value="13:00"></div></div>'+
  '<div class="field"><label for="blMotivo">Motivo (opcional)</label><input class="input" id="blMotivo" maxlength="80" placeholder="Almoço, consulta, folga..."></div>'+
  '<p id="blErro" class="pag-recusa" role="alert" hidden></p>'+
  '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Cancelar</button><button class="btn btn-primary" id="blSalvar" onclick="salvarBloqueio()">'+ic('lock')+'Bloquear</button></div>');
}
async function salvarBloqueio(){
  const data=$('#blData').value,diaTodo=$('#blDia').checked,erro=$('#blErro');
  if(!data){erro.textContent='Escolha a data.';erro.hidden=false;return;}
  const base=new Date(data+'T00:00:00');
  const inicio=diaTodo?base.getTime():new Date(data+'T'+($('#blDe').value||'00:00')+':00').getTime();
  const fim=diaTodo?base.getTime()+864e5:new Date(data+'T'+($('#blAte').value||'00:00')+':00').getTime();
  if(!(fim>inicio)){erro.textContent='O fim precisa ser depois do início.';erro.hidden=false;return;}
  if(DEMO_MODE){closeModal();toast('Horário bloqueado na sua agenda','ok');return;}
  $('#blSalvar').disabled=true;
  try{await apiJSON('/api/agenda/bloqueios','POST',{inicio,fim,motivo:$('#blMotivo').value},'Não foi possível bloquear.');closeModal();toast('Horário bloqueado','ok');agendaDados=null;render();}
  catch(e){erro.textContent=erroLegivel(e,'Não foi possível bloquear.');erro.hidden=false;$('#blSalvar').disabled=false;}
}
async function desbloquearHorario(id){
  try{await apiJSON('/api/agenda/bloqueios/'+encodeURIComponent(id),'DELETE');toast('Horário liberado','ok');agendaDados=null;render();}
  catch(e){toast(erroLegivel(e,'Não foi possível desbloquear.'),'err');}
}
