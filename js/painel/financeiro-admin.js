/* =========================================================
   FINANCEIRO (administrador)
   O que a TaskGO recebeu (taxas e Plano Pro), os assinantes do Pro, o saldo real da conta no Asaas e a transferência
   desse dinheiro para a chave Pix que o administrador escolher.
   ========================================================= */
const FIN={dados:null,erro:'',carregando:false,enviando:false,tipo:'CPF'};
async function carregarFinanceiro(forcar){
  if(FIN.carregando||(!forcar&&(FIN.dados||FIN.erro)))return;
  FIN.carregando=true;
  try{FIN.dados=await apiJSON('/api/admin/financeiro');FIN.erro='';}
  catch(e){FIN.erro=erroLegivel(e,'Não foi possível carregar o financeiro.');}
  finally{FIN.carregando=false;if(route.sub==='admin'&&route.id==='financeiro')render();}
}
function painelFinanceiro(){
  if(!FIN.dados&&!FIN.erro){carregarFinanceiro();return '<div class="card" aria-busy="true"><p class="muted">Carregando…</p></div>';}
  if(FIN.erro)return '<div class="card"><p class="pag-recusa" role="alert">'+esc(FIN.erro)+'</p><button class="btn btn-ghost btn-sm" onclick="FIN.erro=\'\';carregarFinanceiro(true)">Tentar de novo</button></div>';
  const d=FIN.dados,saldo=d.saldo;
  const numero=(rot,val,nota)=>'<div><span class="t2">'+rot+'</span><b>'+val+'</b>'+(nota?'<small>'+nota+'</small>':'')+'</div>';
  const dados=(d.porDia||[]).map(x=>({dia:x.dia,recebido:Math.round(Number(x.valor)*100)/100}));
  return (d.teste?'<p class="aviso-linha" role="note">Conta Asaas de teste: os valores não são dinheiro de verdade.</p>':'')+
    ((d.resgatesVerificar||[]).length?'<div class="aviso-linha" role="alert"><p>Resgates sem resposta do Asaas. Confira no painel do Asaas e marque o que aconteceu:</p>'+d.resgatesVerificar.map(x=>'<p>'+esc(x.profissional)+' · '+money(x.valor)+' · '+dataHora(x.em)+' <button type="button" class="btn btn-ghost btn-sm" data-resgate="'+esc(x.id)+'" data-saiu="1" onclick="resolverResgate(this)">Saiu</button> <button type="button" class="btn btn-ghost btn-sm" data-resgate="'+esc(x.id)+'" data-saiu="0" onclick="resolverResgate(this)">Não saiu, devolver</button></p>').join('')+'</div>':'')+
    '<div class="fin-numeros">'+
      numero('Recebido hoje',money(d.hoje))+numero('Recebido neste mês',money(d.mes),money(d.planosMes)+' do Plano Pro')+numero('Assinantes Pro',String(d.assinantesPro||0),'com o mês pago')+
      numero('Livre para transferir',saldo!=null?money(saldo):'—',saldo!=null?money(d.saldoAsaas)+' no Asaas, o resto é dos usuários':(d.saldoErro||'Asaas desligado'))+
    '</div>'+
    blocoGrafico('g-taxas','Recebido por dia','Taxas e Plano Pro pagos pelos profissionais à TaskGO, em reais. '+money(d.trintaDias)+' nos últimos 30 dias.',[['recebido','Recebido (R$)',1]],dados,'colunas')+
    secaoTransferencia(d)+
    '<section class="card"><h2 class="sub admin-ttl">Últimos recebimentos</h2>'+
    (d.ultimas&&d.ultimas.length?'<div class="tabela-rolagem"><table class="adm-tabela"><thead><tr><th>Quando</th><th>Profissional</th><th>Origem</th><th>Forma</th><th class="num">Valor</th></tr></thead><tbody>'+
      d.ultimas.map(x=>'<tr><td>'+dataHora(x.em)+'</td><td>'+esc(x.profissional)+'</td><td>'+esc(x.origem||'Taxa')+'</td><td>'+esc({cartao:'Cartão',pix:'Pix',boleto:'Boleto',carteira:'Carteira'}[x.metodo]||x.metodo||'—')+'</td><td class="num">'+money(x.valor)+'</td></tr>').join('')+'</tbody></table></div>':
      '<p class="muted">Nada recebido nos últimos 30 dias.</p>')+'</section>';
}
function secaoTransferencia(d){
  const envios=d.transferencias||[];
  const historico=envios.length?'<ul class="lista-painel fin-envios">'+envios.map(t=>'<li><div><b>'+money(t.valor)+'</b><small class="muted">'+dataHora(t.em)+' · '+esc(t.destino||'')+'</small></div><span class="tag '+(t.status==='DONE'?'tag-ok':t.status==='FAILED'||t.status==='CANCELLED'?'tag-danger':'tag-warn')+'">'+esc({DONE:'Concluída',PENDING:'Em andamento',BANK_PROCESSING:'No banco',FAILED:'Falhou',FALHOU:'Não saiu',CANCELLED:'Cancelada',VERIFICAR:'Sem resposta'}[t.status]||t.status)+'</span>'+
    (t.status==='VERIFICAR'?' <button type="button" class="btn btn-ghost btn-sm" data-transf="'+esc(t.id)+'" data-saiu="1" onclick="resolverTransferencia(this)">Saiu</button> <button type="button" class="btn btn-ghost btn-sm" data-transf="'+esc(t.id)+'" data-saiu="0" onclick="resolverTransferencia(this)">Não saiu</button>':'')+'</li>').join('')+'</ul>':'';
  if(!d.asaas)return '<section class="card"><h2 class="sub admin-ttl">Transferir</h2><p class="muted">A transferência fica disponível com o Asaas ligado.</p>'+historico+'</section>';
  const f=FORMATO_CHAVE[FIN.tipo];
  return '<section class="card"><h2 class="sub admin-ttl">Transferir para uma chave Pix</h2>'+
    '<form class="fin-transferir" onsubmit="pedirTransferencia(event)" novalidate>'+
    '<div class="field"><label for="finValor">Valor (R$)</label><input class="input" id="finValor" type="number" inputmode="decimal" min="1" step="0.01" max="'+(Number(d.saldo)||0)+'" value="'+(Number(d.saldo)>0?Number(d.saldo).toFixed(2):'')+'" required></div>'+
    '<div class="field"><label for="finTipo">Tipo de chave</label><select class="select" id="finTipo" onchange="FIN.tipo=this.value;render()">'+Object.entries(TIPOS_CHAVE).map(e=>'<option value="'+e[0]+'"'+(e[0]===FIN.tipo?' selected':'')+'>'+e[1]+'</option>').join('')+'</select></div>'+
    '<div class="field"><label for="finChave">Chave Pix</label><input class="input" id="finChave" type="'+(f.tipo||'text')+'" inputmode="'+f.modo+'" maxlength="'+f.max+'" placeholder="'+f.ph+'" autocomplete="off" spellcheck="false" oninput="this.value=FORMATO_CHAVE[FIN.tipo].mascara(this.value)"></div>'+
    '<div class="field"><label for="finDesc">Descrição (opcional)</label><input class="input" id="finDesc" maxlength="80" placeholder="Ex.: retirada de novembro"></div>'+
    '<p id="finErro" class="pag-recusa" role="alert" hidden></p>'+
    '<div><button class="btn btn-primary"'+(FIN.enviando||!(Number(d.saldo)>0)?' disabled':'')+'>'+ic('pix')+(FIN.enviando?'Transferindo…':'Transferir')+'</button></div></form>'+
    historico+'</section>';
}
function pedirTransferencia(e){
  e.preventDefault();
  const valor=Number($('#finValor').value),chave=$('#finChave').value,f=FORMATO_CHAVE[FIN.tipo],erro=$('#finErro');
  const falha=!(valor>0)?'Informe o valor.':valor>Number(FIN.dados.saldo)?'O valor passa do saldo disponível ('+money(FIN.dados.saldo)+').':!f.ok(chave)?(chave?f.erro:'Digite a chave Pix.'):'';
  if(falha){erro.textContent=falha;erro.hidden=false;return;}
  erro.hidden=true;
  openModal('<div class="modal-head"><div><h3>Confirmar transferência</h3><p>O dinheiro sai da conta da TaskGO no Asaas. Não dá para desfazer.</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
    '<dl class="resumo-pedido"><div><dt>Valor</dt><dd>'+money(valor)+'</dd></div><div><dt>Para</dt><dd>'+esc(TIPOS_CHAVE[FIN.tipo])+' '+esc(chave)+'</dd></div></dl>'+
    '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">Voltar</button><button class="btn btn-primary" id="finOk" onclick="transferirAgora()">'+ic('check')+'Transferir '+money(valor)+'</button></div>');
}
/* Resgate em conferência: depois de olhar no painel do Asaas, a equipe conclui ou devolve o saldo. */
async function resolverResgate(b){
  const saiu=b.dataset.saiu==='1';
  if(!confirm(saiu?'Confirmar que a transferência saiu no Asaas?':'Confirmar que a transferência NÃO saiu? O valor volta para o saldo do profissional.'))return;
  b.disabled=true;
  try{await apiJSON('/api/admin/financeiro/resgates/'+encodeURIComponent(b.dataset.resgate)+'/resolver','POST',{saiu},'Não foi possível salvar.');toast(saiu?'Resgate concluído.':'Valor devolvido ao profissional.','ok');}
  catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');}
  finally{FIN.dados=null;carregarFinanceiro(true);render();}
}
/* Transferência da TaskGO sem resposta do Asaas: enquanto não for marcada, nenhuma outra sai. */
async function resolverTransferencia(b){
  const saiu=b.dataset.saiu==='1';
  if(!confirm(saiu?'Confirmar que esta transferência saiu no Asaas?':'Confirmar que esta transferência NÃO saiu no Asaas?'))return;
  b.disabled=true;
  try{await apiJSON('/api/admin/financeiro/transferencias/'+encodeURIComponent(b.dataset.transf)+'/resolver','POST',{saiu},'Não foi possível salvar.');toast('Transferência marcada.','ok');}
  catch(e){toast(erroLegivel(e,'Não foi possível salvar.'),'err');}
  finally{FIN.dados=null;carregarFinanceiro(true);render();}
}
async function transferirAgora(){
  if(FIN.enviando)return;
  const b=$('#finOk');if(b)b.disabled=true;
  FIN.enviando=true;
  try{
    const r=await apiJSON('/api/admin/financeiro/transferir','POST',{valor:Number($('#finValor').value),tipo:FIN.tipo,chave:$('#finChave').value,descricao:$('#finDesc').value},'Não foi possível transferir.');
    closeModal();if(r.aviso)toast(r.aviso,'err');else toast('Transferência de '+money(r.valor)+' enviada para '+r.destino+'.','ok');
    FIN.dados=null;
  }catch(e){closeModal();toast(erroLegivel(e,'Não foi possível transferir.'),'err');}
  finally{FIN.enviando=false;carregarFinanceiro(true);render();}
}
