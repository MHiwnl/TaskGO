/* ---------- PARA PROFISSIONAIS ---------- */
/* Taxa da TaskGO pelo valor do serviço comum: 15% até R$ 999,99 e 12% de R$ 1.000 em diante (obras têm faixas próprias). */
const taxaServico=v=>v>=1000?.12:.15;
function pageProfissionais(){
  const firstService=SERVICES[0],initialServicesPerWeek=10,initialTicket=Math.max(firstService.min,Math.min(firstService.max,225));
  const simulador='<div><h2 class="bloco-titulo">simule seus ganhos</h2>'+
      seletorServico({id:'simSvc',rotulo:'Serviço',atual:firstService.id,aoMudar:'simular'})+
      '<div class="field"><label for="simQtd">Serviços por semana: <b id="simQtdL">'+initialServicesPerWeek+'</b></label><input class="sim-range" type="range" id="simQtd" min="1" max="30" value="'+initialServicesPerWeek+'" oninput="simular()" aria-label="Quantidade de serviços por semana"></div>'+
      '<div class="field"><label for="simTk">Valor médio por serviço: <b id="simTkL"></b></label><input class="sim-range" type="range" id="simTk" data-service-id="'+firstService.id+'" min="'+firstService.min+'" max="'+firstService.max+'" step="5" value="'+initialTicket+'" oninput="simular()" aria-label="Valor médio por serviço"><small class="sim-help" id="simTicketHint"></small></div>'+
      '<div id="simOut"></div></div>';
  return folha('para profissionais','<div class="duas-colunas"><div>'+duo('mais clientes,','sem mensalidade obrigatória',1)+
  '<p class="corpo">Pedidos da sua região chegam na hora, com o serviço, o bairro e o valor. Você aceita o que faz sentido, combina pelo chat e recebe direto do cliente, por Pix ou em dinheiro.</p>'+
  '<p class="corpo">A TaskGO só cobra quando você recebe: 15% nos serviços comuns, 12% a partir de R$ 1.000, e de 12% a 6% nas obras.</p>'+
  '<div class="setas">'+linkSeta('#/entrar?tipo=profissional','Criar conta de profissional',1)+linkSeta('#/como-funciona','Ver como funciona')+'</div></div>'+simulador+'</div>','cabeca')+
  folha('recursos','<h2 class="bloco-titulo">tudo o que você precisa para atender</h2><div class="recursos">'+
  [['Pedidos na sua região','Defina de onde sai e até quantos km atende. Fique online e os pedidos chegam na hora.'],
   ['Até 10 categorias','Marque tudo o que você sabe fazer e receba pedidos de todas elas.'],
   ['Você define o preço','Aceite o valor do cliente ou mande outra proposta.'],
   ['Agenda organizada','Os serviços marcados bloqueiam a agenda, e você bloqueia os horários de folga.'],
   ['Obras e serviços recorrentes','Orçamento em etapas, pagas uma a uma, e clientes fixos que contratam várias vezes por semana.'],
   ['Receba direto','O cliente paga a você, por Pix ou dinheiro. A taxa fica na sua Carteira, com extrato.'],
   ['Reputação','Cada cliente avalia o atendimento, e o selo de verificado aparece no seu perfil.'],
   ['Chegada sem atrito','O Waze leva você até o endereço, e o código de 4 números confirma o início.']
  ].map(f=>'<div class="recurso"><h3>'+f[0]+'</h3><p>'+f[1]+'</p></div>').join('')+'</div>','folha-densa');
}
function simular(){
  const select=$('#simSvc'),quantity=$('#simQtd'),ticket=$('#simTk'),output=$('#simOut');
  if(!select||!quantity||!ticket||!output)return;
  const service=svc(select.value),min=Number(service.min),max=Number(service.max),step=5;
  ticket.min=min;ticket.max=max;ticket.step=step;
  if(ticket.dataset.serviceId!==service.id){
    ticket.dataset.serviceId=service.id;
    ticket.value=Math.max(min,Math.min(max,Math.round(((min+max)/2)/step)*step));
  }
  const q=Math.max(1,Math.min(30,Number(quantity.value)||1));
  const t=Math.max(min,Math.min(max,Number(ticket.value)||min));
  quantity.value=q;ticket.value=t;
  $('#simQtdL').textContent=q;
  $('#simTkL').textContent=money(t);
  $('#simTicketHint').textContent='Referência para '+service.n+': '+money(min)+' a '+money(max)+' por '+service.unit+'.';
  quantity.style.setProperty('--range-progress',((q-1)/(Number(quantity.max)-1)*100)+'%');
  ticket.style.setProperty('--range-progress',((t-min)/(max-min||1)*100)+'%');

  const weeksPerMonth=4.33,feeRate=taxaServico(t),servicesPerMonth=q*weeksPerMonth;
  const bruto=servicesPerMonth*t,taxa=bruto*feeRate,liq=bruto-taxa;
  const low=servicesPerMonth*min*(1-taxaServico(min)),high=servicesPerMonth*max*(1-taxaServico(max));
  const pct=Math.round(feeRate*100);
  output.innerHTML='<div class="sim-results">'+
    '<div class="sim-result-highlight"><span>Você recebe por mês</span><b>'+money(liq)+'</b></div>'+
    '<p class="sim-result-context">Com cerca de '+Math.round(servicesPerMonth)+' serviços de '+service.n+' por mês, a '+money(t)+' cada.</p>'+
    '<div class="sim-result-row"><span>Total pago pelos clientes</span><b>'+money(bruto)+'</b></div>'+
    '<div class="sim-result-row"><span>Taxa TaskGO ('+pct+'%)</span><b class="sim-fee">- '+money(taxa)+'</b></div>'+
    '<div class="sim-result-range">Com o mesmo volume, entre <b>'+money(low)+' e '+money(high)+'</b> por mês.</div></div>'+
    '<p class="hint" style="margin-top:12px">Estimativa com 4,33 semanas por mês e os valores de referência da categoria.</p>';
}
