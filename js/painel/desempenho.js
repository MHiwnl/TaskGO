/* ---------- DESEMPENHO ---------- */
/* Tudo calculado dos atendimentos reais do profissional: ganhos, conclusão, nota e onde mais atende. */
const MESES_CURTOS=['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez'];
function inicioDoMes(d){return new Date(d.getFullYear(),d.getMonth(),1).getTime();}

function dashDesempenho(){
  const u=me(),isPro=u.role==='profissional';
  const os=myOrders();
  const concluidos=os.filter(o=>o.status==='concluido');
  const quando=o=>Number(o.completedAt)||Number(o.created)||0;
  const cancelados=os.filter(o=>o.status==='cancelado'&&(o.proEmail||o.proId));
  const fechados=concluidos.length+cancelados.length;
  const hoje=new Date();
  const meses=[];
  for(let i=5;i>=0;i--){
    const ini=inicioDoMes(new Date(hoje.getFullYear(),hoje.getMonth()-i,1));
    const fim=inicioDoMes(new Date(hoje.getFullYear(),hoje.getMonth()-i+1,1));
    const doMes=concluidos.filter(o=>quando(o)>=ini&&quando(o)<fim);
    meses.push({rot:MESES_CURTOS[new Date(ini).getMonth()],valor:doMes.reduce((a,o)=>a+(Number(o.price)||0),0),qtd:doMes.length});
  }
  const atual=meses[5],anterior=meses[4];
  const variacao=anterior.valor?((atual.valor-anterior.valor)/anterior.valor*100):null;
  const notas=concluidos.map(o=>avaliacaoRecebida(o,isPro).nota).filter(Boolean);
  const media=notas.length?notas.reduce((a,b)=>a+b,0)/notas.length:null;
  const total=concluidos.reduce((a,o)=>a+(Number(o.price)||0),0);
  const ticket=concluidos.length?total/concluidos.length:null;
  const porBairro={};
  concluidos.forEach(o=>{const b=o.bairro||(String(o.address||'').split(/[—,-]/)[1]||'').trim()||'Sem bairro';porBairro[b]=(porBairro[b]||0)+1;});
  const bairros=Object.entries(porBairro).sort((a,b)=>b[1]-a[1]).slice(0,5);
  const maxMes=Math.max(1,...meses.map(m=>m.valor));
  const verbo=isPro?'ganhou':'gastou';

  if(!os.length)return dashHead('Desempenho','Seus números aparecem aqui a partir do primeiro atendimento.')+
    '<div class="card emptystate">'+ic('trendUp')+'<b>Ainda não há atendimentos</b><p>Quando você concluir um serviço, ganhos, nota e taxa de conclusão aparecem aqui.</p>'+
    (isPro?'<a class="btn btn-primary" href="#/app/oportunidades">Ver oportunidades</a>':'')+'</div>';

  const stat=(rot,val,nota,cor)=>'<div class="stat stat-'+cor+'"><div class="lbl">'+rot+'</div><div class="val">'+val+'</div><div class="delta">'+nota+'</div></div>';
  return dashHead('Desempenho',isPro?'Seus resultados com os atendimentos feitos pela TaskGO.':'Seu histórico de serviços contratados.')+
  '<div class="grid g4 desemp-stats">'+
    stat('Neste mês',money(atual.valor),variacao==null?atual.qtd+' '+(atual.qtd===1?'serviço':'serviços'):(variacao>=0?'+':'')+variacao.toFixed(0).replace('.',',')+'% sobre '+anterior.rot,'ganho')+
    stat('Serviços concluídos',String(concluidos.length),money(total)+' no total','ok')+
    stat('Taxa de conclusão',fechados?Math.round(concluidos.length/fechados*100)+'%':'—',fechados?cancelados.length+' '+(cancelados.length===1?'cancelado':'cancelados'):'sem pedidos encerrados','info')+
    stat('Nota média',media==null?'—':media.toFixed(1).replace('.',',')+' de 5',notas.length?notas.length+' '+(notas.length===1?'avaliação':'avaliações'):'sem avaliações ainda','nota')+
  '</div>'+
  '<div class="desemp-grade">'+
  '<section class="card"><h2 class="sub">Quanto você '+verbo+' nos últimos 6 meses</h2>'+
  '<div class="desemp-barras" role="img" aria-label="'+meses.map(m=>m.rot+': '+money(m.valor)).join('; ')+'">'+
  meses.map((m,i)=>'<div class="desemp-col'+(i===5?' atual':'')+'"><b>'+(m.valor?money(m.valor).replace(',00',''):'')+'</b>'+
    '<div class="desemp-barra'+(m.valor?'':' zero')+'" style="--h:'+(m.valor/maxMes*100)+'%"></div><span>'+m.rot+'</span></div>').join('')+'</div>'+
  '<p class="muted desemp-rodape">Ticket médio: <b>'+(ticket==null?'—':money(ticket))+'</b></p></section>'+
  '<section class="card"><h2 class="sub">Onde você mais '+(isPro?'atende':'contrata')+'</h2>'+
  (bairros.length?'<ul class="desemp-bairros">'+bairros.map(b=>'<li><span>'+esc(b[0])+'</span><div class="bar"><i style="width:'+(b[1]/bairros[0][1]*100)+'%"></i></div><b>'+b[1]+'</b></li>').join('')+'</ul>'
    :'<p class="muted">Aparece depois do primeiro serviço concluído.</p>')+
  '</section></div>';
}
