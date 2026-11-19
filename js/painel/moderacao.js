/* ---------- MODERAÇÃO, RECURSOS, ADMINISTRAÇÃO E EVENTOS AO VIVO ----------
   Regra da conversa: nada de números, valores ou contatos. 1º aviso; 2º suspensão de 24 horas; 3º banimento.
   Toda decisão pode ser contestada por recurso, que um administrador aprova ou nega. */

/* ================= Situação da conta ================= */
function situacaoConta(){
  if(DEMO_MODE){const d=S.demoModeracao||{avisos:0,suspensoAte:0,banido:false,recursos:[]};
    return Object.assign({},d,{suspenso:!d.banido&&d.suspensoAte>Date.now(),administrador:false});}
  return S.situacaoConta||{avisos:0,suspensoAte:0,suspenso:false,banido:false,administrador:false,recursos:[]};
}
async function carregarSituacaoConta(){
  if(DEMO_MODE||!me())return situacaoConta();
  try{S.situacaoConta=await apiJSON('/api/moderacao/eu','GET',undefined,'Não foi possível consultar a situação da conta.');window.__situacaoCarregada=me().id;}
  catch(error){console.warn('Situação da conta:',error);}
  return situacaoConta();
}
const dataHora=ts=>new Date(ts).toLocaleString('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});
/* Faixa no topo da área quando a conta tem aviso, suspensão ou banimento. */
function faixaSituacao(){
  const s=situacaoConta();
  if(!s.avisos&&!s.banido&&!s.suspenso)return '';
  const titulo=s.banido?'Conta banida':s.suspenso?'Conta suspensa até '+dataHora(s.suspensoAte):s.avisos+' de 3 avisos na conta';
  const texto=s.banido?'Pedidos, propostas e conversas estão bloqueados.':s.suspenso?'Até lá, pedidos, propostas e conversas ficam bloqueados.':'No 2º aviso a conta fica suspensa por 24 horas; no 3º, é banida.';
  return '<a class="faixa-situacao'+(s.banido||s.suspenso?' grave':'')+'" href="#/app/conta-situacao">'+ic(s.banido||s.suspenso?'lock':'flag')+
    '<span><b>'+esc(titulo)+'</b><small>'+texto+' Ver detalhes e recorrer.</small></span>'+ic('arrow')+'</a>';
}
const ROTULO_HIST_CONTA={aviso:'Aviso',suspensao:'Suspensão',banimento:'Banimento',desbanimento:'Conta liberada',recurso:'Recurso'};
function historicoConta(s){
  const hist=s.historico||[];
  if(!hist.length)return '';
  return '<section class="card" style="margin-top:16px"><h2 class="sub" style="font-size:1.05rem;margin-bottom:4px">O que aconteceu na sua conta</h2>'+
    '<p class="muted" style="font-size:.88rem;margin-bottom:12px">O motivo e a data de cada aviso, suspensão ou decisão.</p>'+
    '<ul class="lista-recursos">'+hist.map(h=>'<li><span class="tag '+(h.tipo==='desbanimento'?'tag-ok':h.tipo==='recurso'?'tag-muted':'tag-warn')+'">'+esc(ROTULO_HIST_CONTA[h.tipo]||h.tipo)+'</span>'+
      '<p>'+esc(h.texto)+'</p><small>'+dataHora(h.quando)+'</small></li>').join('')+'</ul></section>';
}
function dashSituacaoConta(){
  const s=situacaoConta(),emAnalise=(s.recursos||[]).some(r=>r.status==='em_analise');
  const passos=[['1º aviso','A mensagem não é enviada e a conta recebe um aviso.'],['2º aviso','A conta fica suspensa por 24 horas.'],['3º aviso','A conta é banida.']];
  return '<a href="#/app/inicio" class="pill" style="margin-bottom:18px">'+ic('arrowLeft')+' Voltar</a>'+
  dashHead('Situação da conta','A conversa da TaskGO não aceita números, valores nem contatos.')+
  '<div class="grid g2" style="grid-template-columns:1.1fr .9fr;align-items:start">'+
  '<section class="card"><h2 class="sub" style="font-size:1.05rem">'+(s.banido?'Conta banida':s.suspenso?'Conta suspensa':s.avisos?'Conta com avisos':'Conta sem avisos')+'</h2>'+
    '<p class="muted" style="margin:6px 0 18px;font-size:.9rem">'+(s.banido?'Pedidos, propostas e conversas estão bloqueados. Se foi um engano, envie um recurso.':s.suspenso?'Bloqueada até '+dataHora(s.suspensoAte)+'. Depois disso, tudo volta ao normal.':s.avisos?'Você pode usar a TaskGO normalmente. Evite enviar números, valores ou contatos pela conversa.':'Nenhuma mensagem sua foi barrada.')+'</p>'+
    '<ol class="escada-avisos">'+passos.map((p,i)=>'<li class="'+(i<s.avisos?'feito':'')+'"><b>'+p[0]+'</b><span>'+p[1]+'</span></li>').join('')+'</ol></section>'+
  '<section class="card"><h2 class="sub" style="font-size:1.05rem">Recurso</h2>'+
    (!s.avisos?'<p class="muted" style="margin-top:6px;font-size:.9rem">Sem avisos, não há o que contestar.</p>':
     emAnalise?'<p class="muted" style="margin-top:6px;font-size:.9rem">Seu recurso está em análise. A resposta chega nas notificações.</p>':
     '<form onsubmit="enviarRecurso(event)" style="margin-top:10px"><div class="field"><label for="textoRecurso">O que aconteceu?</label>'+
     '<textarea class="textarea" id="textoRecurso" minlength="10" maxlength="2000" required placeholder="Ex.: eu escrevi o número do apartamento, não um telefone."></textarea>'+
     '<div class="hint">Um administrador lê e decide. Aprovado, o aviso sai da conta junto com a punição dele.</div></div>'+
     '<button class="btn btn-primary" id="btnRecurso">Enviar recurso</button></form>')+
    ((s.recursos||[]).length?'<h3 class="recursos-ttl">Seus recursos</h3><ul class="lista-recursos">'+s.recursos.map(r=>'<li><span class="tag '+(r.status==='aprovado'?'tag-ok':r.status==='negado'?'tag-danger':'tag-muted')+'">'+({em_analise:'Em análise',aprovado:'Aprovado',negado:'Negado'}[r.status]||r.status)+'</span>'+
      '<p>'+esc(r.texto)+'</p>'+(r.resposta?'<small>Resposta: '+esc(r.resposta)+'</small>':'')+'<small>'+dataHora(r.criadoEm)+'</small></li>').join('')+'</ul>':'')+
  '</section></div>'+historicoConta(s);
}
async function enviarRecurso(e){
  e.preventDefault();
  const texto=($('#textoRecurso')||{}).value.trim(),botao=$('#btnRecurso');
  if(texto.length<10)return toast('Explique o que aconteceu com pelo menos 10 caracteres.','err');
  if(botao){botao.disabled=true;botao.textContent='Enviando...';}
  try{
    if(DEMO_MODE){const d=S.demoModeracao;d.recursos=[{id:'r_'+uid(),texto,status:'em_analise',criadoEm:Date.now()}].concat(d.recursos||[]);Store.save();}
    else{await apiJSON('/api/moderacao/recursos','POST',{texto},'O recurso não foi enviado.');await carregarSituacaoConta();}
    toast('Recurso enviado. A resposta chega nas notificações.','ok');render();
  }catch(error){toast(erroLegivel(error,'O recurso não foi enviado.'),'err');if(botao){botao.disabled=false;botao.textContent='Enviar recurso';}}
}

/* ================= Filtro da conversa no modo demonstração (mesma regra do servidor) ================= */
const NUMEROS_EXTENSO=new Set('zero um uma dois duas tres quatro cinco seis sete oito nove dez onze doze treze catorze quatorze quinze vinte trinta quarenta cinquenta sessenta setenta oitenta noventa cem cento duzentos trezentos quatrocentos quinhentos mil'.split(' '));
const PALAVRAS_CONTATO=['whatsapp','whats','zap','wpp','telefone','celular','cpf','pix','instagram','insta','e-mail','email','@','.com','por fora','fora do app','fora do aplicativo','meu numero','me liga'];
function motivoBloqueioLocal(texto){
  const t=semAcento(texto);
  if(/\d/.test(t))return 'números';
  if(/r\$|\breais\b|\bcontos?\b|\bpilas?\b/.test(t))return 'valores';
  if((t.match(/[a-z]+/g)||[]).filter(w=>NUMEROS_EXTENSO.has(w)).length>=2)return 'números escritos por extenso';
  if(PALAVRAS_CONTATO.some(c=>t.includes(c)))return 'contato fora do app';
  return null;
}
function infracaoDemo(motivo){
  const d=S.demoModeracao=S.demoModeracao||{avisos:0,suspensoAte:0,banido:false,recursos:[]};
  d.avisos++;if(d.avisos===2)d.suspensoAte=Date.now()+864e5;if(d.avisos>=3)d.banido=true;Store.save();
  const punicao=d.banido?'Com 3 avisos, a conta foi banida.':d.avisos===2?'Aviso 2 de 3: a conta fica suspensa por 24 horas.':'Aviso 1 de 3.';
  const erro=new Error('Mensagem não enviada: ela tinha '+motivo+'. Combinar contato, valores ou serviço por fora não é permitido. '+punicao+' Você pode recorrer da decisão.');erro.status=422;return erro;
}

/* ================= Administração ================= */
let adminDados={recursos:null,usuarios:null,erro:''};
function dashAdmin(){
  if(!ehAdministrador(me())&&!ehSuporte(me()))return '';
  const r=adminDados.recursos;
  const pendentes=(r||[]).filter(x=>x.status==='em_analise'),decididos=(r||[]).filter(x=>x.status!=='em_analise');
  /* O return precisa estar na mesma linha da expressão, senão devolve undefined. */
  return (adminDados.erro?'<p class="painel-aviso" role="alert">'+esc(adminDados.erro)+'</p>':'')+
  '<section class="card"><h2 class="sub" style="font-size:1.05rem;margin-bottom:12px">Recursos em análise'+(r?' ('+pendentes.length+')':'')+'</h2>'+
  (r==null?'<p class="muted">Carregando...</p>':pendentes.length?pendentes.map(x=>
    '<article class="adm-recurso"><div class="adm-quem"><b>'+esc(x.usuario?.nome||'Conta')+'</b><small>'+esc(x.usuario?.email||'')+' · '+esc(x.usuario?.tipo||'')+' · '+(x.usuario?.avisos||0)+' aviso(s)'+(x.usuario?.banido?' · banida':x.usuario?.suspenso?' · suspensa':'')+'</small></div>'+
    '<p>'+esc(x.texto)+'</p><small class="muted">'+dataHora(x.criadoEm)+'</small>'+
    '<div class="field" style="margin:12px 0 10px"><label for="resp-'+esc(x.id)+'">Resposta para a pessoa (opcional)</label><input class="input" id="resp-'+esc(x.id)+'" maxlength="500"></div>'+
    '<div style="display:flex;gap:10px;flex-wrap:wrap"><button class="btn btn-ghost btn-sm" onclick="decidirRecurso(\''+esc(x.id)+'\',\'negar\')">Negar</button><button class="btn btn-primary btn-sm" onclick="decidirRecurso(\''+esc(x.id)+'\',\'aprovar\')">'+ic('check')+' Aprovar e retirar um aviso</button></div></article>').join(''):
    '<p class="muted">Nenhum recurso esperando decisão.</p>')+'</section>'+
  /* Banir e ver as contas fica em Usuários; aqui é só a fila de recursos. */
  secaoDenunciasAdmin()+
  (decididos.length?'<section class="card" style="margin-top:16px"><h2 class="sub" style="font-size:1.05rem;margin-bottom:12px">Decididos</h2><ul class="lista-recursos">'+decididos.slice(0,20).map(x=>'<li><span class="tag '+(x.status==='aprovado'?'tag-ok':'tag-danger')+'">'+(x.status==='aprovado'?'Aprovado':'Negado')+'</span><p><b>'+esc(x.usuario?.nome||'')+'</b>: '+esc(x.texto)+'</p><small>'+dataHora(x.decididoEm||x.criadoEm)+'</small></li>').join('')+'</ul></section>':'');
}
/* Abre Usuários já filtrado pelas contas com aviso. */
async function carregarAdmin(){
  try{
    adminDados={recursos:await apiJSON('/api/admin/recursos'),erro:''};
  }catch(error){adminDados={recursos:[],erro:erroLegivel(error,'Não foi possível carregar a moderação.')};}
  if(route.sub==='admin')render();
}
async function decidirRecurso(id,decisao){
  const resposta=(($('#resp-'+id)||{}).value||'').trim();
  try{await apiJSON('/api/admin/recursos/'+encodeURIComponent(id)+'/'+decisao,'POST',resposta?{resposta}:{},'A decisão não foi salva.');
    toast(decisao==='aprovar'?'Recurso aprovado. Um aviso saiu da conta.':'Recurso negado.','ok');PAINEL.hoje=null;carregarAdmin();}
  catch(error){toast(erroLegivel(error,'A decisão não foi salva.'),'err');}
}
async function banirConta(id,banir){
  if(banir&&!await confirmar({titulo:'Banir esta conta?',texto:'Ela perde acesso a pedidos, propostas e conversas.',botao:'Banir',perigo:true}))return;
  try{await apiJSON('/api/admin/usuarios/'+id+'/'+(banir?'banir':'desbanir'),'POST',undefined,'A ação não foi salva.');toast(banir?'Conta banida.':'Conta desbanida.','ok');carregarAdmin();}
  catch(error){toast(erroLegivel(error,'A ação não foi salva.'),'err');}
}

/* ================= Eventos ao vivo (SSE) ================= */
let fonteEventos=null;
function ligarEventos(){
  if(DEMO_MODE||fonteEventos||!me()||typeof EventSource==='undefined')return;
  fonteEventos=new EventSource(API_BASE_URL+'/api/eventos',{withCredentials:true});
  const ouvir=(tipo,fn)=>fonteEventos.addEventListener(tipo,e=>{try{fn(e.data||'');}catch(err){console.warn('Evento '+tipo+':',err);}});
  ouvir('oportunidade',()=>{carregarContadores();if(route.sub==='inicio')carregarMelhoresOportunidades();if(route.sub==='oportunidades')carregarPedidos();else toast('Pedido novo na sua categoria. Veja em Oportunidades.','ok');});
  ouvir('proposta',pedidoId=>{
    if(route.sub==='pedido'&&route.id===pedidoId)carregarPropostasPedido(pedidoId);
    else if(route.sub==='oportunidades')carregarPedidos();
    else toast('Nova proposta em um pedido seu.','ok');
  });
  ouvir('pedido',()=>{if(route.sub==='chamando'){atualizarChamando();return;}if(route.sub==='pedido'&&typeof REC!=='undefined')REC.chave=null;if(route.sub==='pedido'&&route.id&&typeof carregarFotosPedido==='function')carregarFotosPedido(route.id);const email=me()?.email;if(email)syncPedidos(email).then(()=>{if(route.page==='app')render();}).catch(()=>{});});
  /* Chamar Agora: o chamado aparece em tela cheia para o profissional; some quando outro aceita. */
  ouvir('chamar-agora',()=>carregarConvites());
  ouvir('chamar-agora-encerrado',id=>chamadoEncerrado(id));
  /* Aviso novo (inclusive do sistema): atualiza a lista e o número no sino. */
  ouvir('notificacao',()=>{window.__taskgoNotificationsLoaded=false;carregarNotificacoes().then(()=>{toast('Novo aviso do sistema. Veja em Notificações.','ok');if(route.page==='app')render();}).catch(()=>{});});
  /* Pagamento confirmado, diferença cobrada, repasse ou devolução. */
  ouvir('pagamento',pid=>{
    if(route.sub==='pagar'&&route.id===pid){carregarPagar();return;}
    if(route.sub==='pedido'&&typeof REC!=='undefined'&&REC.id===pid)REC.chave=null;
    if(route.sub==='pedido'&&route.id===pid)carregarPagamentoPedido(pid,me()?.role==='profissional',true);
    if(route.sub==='carteira'){financePageData=null;render();}
    const email=me()?.email;if(email)syncPedidos(email).then(()=>{if(route.page==='app'&&route.sub!=='pagar')render();}).catch(()=>{});
  });
  /* Plano Pro pago ou renovado. */
  ouvir('plano',()=>{if(typeof carregarPlano==='function')carregarPlano(true);});
  ouvir('mensagem',()=>{carregarContadores();if(route.sub==='mensagens'){window.__taskgoChatsLoadedKey=null;render();}});
  /* Suporte: chamado novo toca o alarme; mudança em chamado atualiza a fila ou o status de quem pediu ajuda. */
  ouvir('emergencia',()=>{if(ehSuporte(me())){carregarChamados();alertarChamadoNovo();}});
  ouvir('chamado',()=>{if(ehSuporte(me()))carregarChamados();else if(route.sub==='pedido'&&document.getElementById('emerg-status'))mostrarMeuChamado(route.id);});
  ouvir('moderacao',()=>carregarSituacaoConta().then(()=>{if(route.page==='app')render();}));
  fonteEventos.onerror=()=>{if(fonteEventos&&fonteEventos.readyState===EventSource.CLOSED){fonteEventos=null;setTimeout(ligarEventos,15000);}};
}
function desligarEventos(){if(fonteEventos){fonteEventos.close();fonteEventos=null;}}
