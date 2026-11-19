/* =========================================================
   PAINEL DA EQUIPE (administrador, moderador, financeiro)
   Feito para milhões de contas: mostra o que precisa de ação e responde a buscas; nunca lista a base inteira.
   Hoje · Filas · Buscar · Atenção · Números · Auditoria · Exportar · Equipe · Avisos (cada papel vê só as suas).
   ========================================================= */
const SECOES_PAINEL=[['hoje','Hoje','home',['HOJE']],['filas','Filas','list',['MODERACAO','PAGAMENTOS']],['buscar','Buscar','search',['BUSCAR']],
  ['atencao','Atenção','flag',['ATENCAO']],['numeros','Números','trendUp',['NUMEROS']],['auditoria','Auditoria','clock',['AUDITORIA']],
  ['exportar','Exportar','download',['EXPORTAR']],['equipe','Equipe','users',['EQUIPE']],['avisos','Avisos','megafone',['AVISOS']],['financeiro','Financeiro','wallet',['FINANCEIRO']]];
/* Mesmas regras do servidor (PapelEquipe); o servidor confere de novo em cada chamada. */
/* Administrador analisa e administra; quem decide os casos é o suporte (e moderador/financeiro, cada um na sua parte). */
const AREAS_PAPEL={administrador:['HOJE','BUSCAR','ATENCAO','NUMEROS','AUDITORIA','EXPORTAR','EQUIPE','AVISOS','FINANCEIRO'],
  moderador:['HOJE','MODERACAO','BUSCAR','AUDITORIA'],financeiro:['HOJE','PAGAMENTOS','BUSCAR','ATENCAO','NUMEROS','EXPORTAR'],
  suporte:['MODERACAO','PAGAMENTOS','BUSCAR','ATENCAO']};
const NOME_PAPEL={administrador:'Administrador',moderador:'Moderador',financeiro:'Financeiro',suporte:'Suporte'};
const pode=area=>(AREAS_PAPEL[me()?.role]||[]).includes(area);
function secoesDaConta(u){const a=AREAS_PAPEL[u.role]||[];return SECOES_PAINEL.filter(s=>s[3].some(x=>a.includes(x)));}
const ETAPAS_ADMIN={aguardando_pagamento:'Esperando pagamento',aguardando:'Esperando proposta',a_caminho:'A caminho',chegou:'Chegou',em_andamento:'Em andamento',concluido:'Concluído',cancelado:'Cancelado'};
const TIPOS_AUDITORIA={pedido:'Pedido',pagamento:'Pagamento',cancelamento:'Cancelamento',aviso:'Aviso',suspensao:'Suspensão',banimento:'Banimento',desbanimento:'Desbanimento',
  recurso:'Recurso',denuncia:'Denúncia',verificacao:'Verificação',email:'Conta',conta:'Conta',equipe:'Ação da equipe',acesso:'Acesso a dados',suporte:'Suporte',identidade:'Identidade'};

let PAINEL={hoje:null,erroHoje:'',busca:{q:'',res:null,carregando:false,erro:''},atencao:null,numeros:{dias:30,dados:null,erro:''},
  auditoria:{f:{conta:'',pedido:'',tipo:'',de:'',ate:''},itens:null,proximo:null,carregando:false,erro:''},exportacoes:null,fila:null,pagamentos:null};

function dashAdministracao(){
  const u=me();
  if(!ehAdministrador(u)&&!ehSuporte(u))return dashHead('Administração','Esta área é só para a equipe.');
  const secoes=secoesDaConta(u);
  const secao=secoes.some(s=>s[0]===route.id)?route.id:secoes[0][0];
  const cab={hoje:['Hoje','O que precisa de ação agora e como o site está nas últimas 24 horas.'],
    filas:['Filas','Tudo o que espera uma decisão da equipe, do mais antigo para o mais novo.'],
    buscar:['Buscar','Ache uma conta ou um pedido pelo e-mail, CPF, telefone, número do pedido ou nome.'],
    atencao:['Pedidos com atenção','Pedidos parados ou com problema. Só aparecem os que precisam de alguém.'],
    numeros:['Números','Resumo por dia, calculado à noite; o dia de hoje é parcial.'],
    auditoria:['Auditoria','O que aconteceu e quem fez, do mais novo ao mais antigo. Filtre por conta, pedido, tipo ou período.'],
    exportar:['Exportar','Planilhas geradas em segundo plano. Você recebe um aviso quando ficam prontas; o arquivo fica 7 dias.'],
    equipe:['Equipe','Quem trabalha no painel e no suporte.'],
    avisos:['Avisos do sistema','Um aviso escrito aqui chega para todas as contas, sem exceção.'],
    financeiro:['Financeiro','As taxas que a TaskGO recebeu e o saldo da conta no Asaas, para transferir quando quiser.']}[secao];
  const corpo=secao==='filas'?painelFilas():secao==='buscar'?painelBuscar():secao==='atencao'?painelAtencao():secao==='numeros'?painelNumeros():
    secao==='auditoria'?painelAuditoria():secao==='exportar'?painelExportar():secao==='equipe'?adminEquipe():secao==='avisos'?adminAvisos():secao==='financeiro'?painelFinanceiro():painelHoje();
  return dashHead(cab[0],cab[1])+corpo;
}
/* Depois de cada render: carrega o que a seção aberta precisa e desenha os gráficos. */
function carregarSecaoAdmin(){
  if(!ehAdministrador(me())&&!ehSuporte(me()))return;
  const secao=route.id||'hoje';
  if(secao==='numeros'||secao==='financeiro')desenharGraficosAdmin();
  if(secao==='filas'&&PAINEL.fila==='moderacao'&&adminDados.recursos==null)carregarAdmin();
}
const numeroBR=n=>n==null?'—':Number(n).toLocaleString('pt-BR');
const nomeServico=id=>{const s=SERVICES.find(x=>x.id===id);return s?s.n:id;};
function minutosLegiveis(m){if(m==null)return '—';if(m<1)return 'menos de 1 min';if(m<60)return m+' min';const h=Math.floor(m/60);return h<48?h+' h'+(m%60?' '+(m%60)+' min':''):Math.round(h/24)+' dias';}
function indicadorAdmin(rotulo,valor,detalhe,destaque){
  return '<div class="stat'+(destaque?' stat-alerta':'')+'"><span class="lbl">'+rotulo+'</span><div class="val">'+valor+'</div>'+(detalhe?'<div class="delta">'+detalhe+'</div>':'')+'</div>';
}
async function carregarPainel(chave,url,depois){
  try{const d=await apiJSON(url,'GET',undefined,'Não foi possível carregar.');depois(d);}
  catch(e){depois(null,erroLegivel(e,'Não foi possível carregar.'));}
  if(route.sub==='admin')render();
}
const carregando=t=>'<div class="card center" role="status"><p class="muted">'+(t||'Carregando...')+'</p></div>';
const falhou=(t,acao)=>'<div class="card emptystate" role="alert">'+ic('x')+'<b>'+esc(t)+'</b>'+(acao?'<button class="btn btn-ghost btn-sm" style="margin-top:12px" onclick="'+acao+'">Tentar de novo</button>':'')+'</div>';

/* ================= Hoje ================= */
function painelHoje(){
  const h=PAINEL.hoje;
  if(!h&&!PAINEL.erroHoje){setTimeout(()=>carregarPainel('hoje','/api/admin/painel/hoje',(d,e)=>{PAINEL.hoje=d;PAINEL.erroHoje=e||'';}),0);return carregando('Conferindo o que precisa de ação...');}
  if(!h)return falhou(PAINEL.erroHoje,"PAINEL.erroHoje='';render()");
  const p=h.pendencias;
  const pend=[['denuncias','Denúncias','flag','filas','moderacao'],['recursos','Recursos','shield','filas','moderacao'],['verificacoes','Verificações','checkCircle','filas','verificacoes'],
    ['pagamentos','Problemas de pagamento','wallet','filas','pagamentos'],['emergencias','Emergências abertas','phone',null,null]].filter(x=>p[x[0]]!=null);
  const variacao=(v,ref)=>{if(v==null||ref==null||Number(ref)===0)return '';const d=Math.round(100*(Number(v)-Number(ref))/Number(ref));return (d>0?'+':'')+d+'%';};
  const fmt=(n,v)=>n.chave==='valor'?money(v||0):n.chave==='aceite'?minutosLegiveis(v):numeroBR(v);
  return '<p class="admin-carimbo">Atualizado às '+new Date(h.geradoEm).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'})+
    ' · <button type="button" class="link-btn" onclick="PAINEL.hoje=null;render()">atualizar</button></p>'+
    (h.alertas.length?'<section class="painel-alertas" aria-label="Alertas">'+h.alertas.map(a=>'<p class="painel-alerta nivel-'+esc(a.nivel)+'">'+ic(a.nivel==='alto'?'bolt':'flag')+'<span>'+esc(a.texto)+'</span>'+
      (a.conta?'<button class="btn btn-ghost btn-sm" onclick="verUsuarioAdmin('+Number(a.conta)+')">Abrir conta</button>':a.area==='ATENCAO'?'<a class="btn btn-ghost btn-sm" href="#/app/admin/atencao">Ver</a>':'')+'</p>').join('')+'</section>':'')+
    '<section aria-label="Pendências"><h2 class="sub admin-ttl">Esperando a equipe</h2><div class="pendencias">'+pend.map(x=>{
      const n=p[x[0]]||0,tag=x[3]?'a':'div';
      return '<'+tag+' class="pendencia'+(n?' tem':'')+'"'+(x[3]?' href="#/app/admin/'+x[3]+'" onclick="PAINEL.fila=\''+x[4]+'\'"':'')+'>'+ic(x[2])+'<b>'+numeroBR(n)+'</b><span>'+x[1]+'</span></'+tag+'>';}).join('')+'</div></section>'+
    (h.numeros.length?'<section aria-label="Últimas 24 horas" style="margin-top:24px"><h2 class="sub admin-ttl">Últimas 24 horas</h2><div class="inds">'+h.numeros.map(n=>{
      const vo=variacao(n.valor,n.ontem),vs=variacao(n.valor,n.semanaPassada);
      return indicadorAdmin(n.rotulo,fmt(n,n.valor),'ontem '+fmt(n,n.ontem)+(vo?' ('+vo+')':'')+' · semana passada '+fmt(n,n.semanaPassada)+(vs?' ('+vs+')':''),n.chave==='cancelados'&&vo&&parseInt(vo)>50);}).join('')+'</div></section>':'');
}

/* ================= Filas ================= */
function painelFilas(){
  const abas=[['moderacao','Denúncias e recursos','MODERACAO'],['verificacoes','Verificações','MODERACAO'],['pagamentos','Problemas de pagamento','PAGAMENTOS']].filter(a=>pode(a[2]));
  if(!abas.some(a=>a[0]===PAINEL.fila))PAINEL.fila=abas[0][0];
  const corpo=PAINEL.fila==='moderacao'?dashAdmin():PAINEL.fila==='verificacoes'?adminVerificacoes():filaPagamentos();
  return '<div class="pag-abas fila-abas" role="tablist">'+abas.map(a=>'<button type="button" role="tab" aria-selected="'+(PAINEL.fila===a[0])+'" class="'+(PAINEL.fila===a[0]?'sel':'')+'" onclick="PAINEL.fila=\''+a[0]+'\';render()">'+a[1]+'</button>').join('')+'</div>'+corpo;
}
function filaPagamentos(){
  const l=PAINEL.pagamentos;
  if(!l){setTimeout(()=>carregarPainel('pag','/api/admin/painel/pagamentos',(d,e)=>{PAINEL.pagamentos=d||{erro:e};}),0);return carregando();}
  if(l.erro)return falhou(l.erro,'PAINEL.pagamentos=null;render()');
  const obras=filaObras()+filaRecorrentes();
  if(!l.length)return obras+(obras?'':'<div class="card emptystate">'+ic('wallet')+'<b>Nenhum problema de pagamento esperando decisão</b></div>');
  return obras+'<ul class="lista-painel">'+l.map(p=>'<li><div><b>'+esc(nomeServico(p.servico))+' · '+money(p.valor)+'</b><p>'+esc(p.problema||'')+'</p><small class="muted">Relatado '+(p.problemaEm?dataHora(p.problemaEm):'')+' · '+esc(p.id)+'</small></div>'+
    '<button class="btn btn-primary btn-sm" onclick="verPedidoAdmin(\''+esc(p.id)+'\')">Analisar</button></li>').join('')+'</ul>';
}

/* ================= Buscar ================= */
function painelBuscar(){
  const b=PAINEL.busca;
  const res=!b.res?'':
    '<p class="admin-carimbo">Buscou por '+esc(b.res.como)+'.</p>'+
    (b.res.contas.length?'<section class="card"><h2 class="sub admin-ttl">Contas ('+b.res.contas.length+(b.res.contas.length>=20?', as 20 primeiras':'')+')</h2><ul class="lista-painel">'+b.res.contas.map(c=>
      '<li><div><b>'+esc(c.nome||'Sem nome')+(c.verificado?seloVerificado(true,true):'')+'</b><small class="muted">'+esc(c.email||'')+' · '+esc(NOME_PAPEL[c.tipo]||c.tipo||'')+(c.cidade?' · '+esc(c.cidade):'')+' · '+esc(c.situacao)+'</small></div>'+
      '<button class="btn btn-ghost btn-sm" onclick="verUsuarioAdmin('+Number(c.id)+')">Abrir ficha</button></li>').join('')+'</ul></section>':'')+
    (b.res.pedidos.length?'<section class="card" style="margin-top:16px"><h2 class="sub admin-ttl">Pedidos</h2><ul class="lista-painel">'+b.res.pedidos.map(linhaPedidoPainel).join('')+'</ul></section>':'')+
    (!b.res.contas.length&&!b.res.pedidos.length?'<div class="card emptystate">'+ic('search')+'<b>Nada encontrado</b><p style="margin-top:6px">A busca é exata para e-mail, CPF, telefone e número do pedido; para nome, procura pelo começo.</p></div>':'');
  return '<form class="busca-painel" onsubmit="buscarPainel(event)" role="search"><label for="buscaPainel" class="leitor">Buscar</label>'+
    '<input class="input" id="buscaPainel" type="search" autocomplete="off" minlength="3" value="'+esc(b.q)+'" placeholder="ana@email.com · 123.456.789-01 · (81) 99999-0000 · o_abc123 · Ana Souza">'+
    '<button class="btn btn-primary"'+(b.carregando?' disabled aria-busy="true"':'')+'>'+ic('search')+'Buscar</button></form>'+
    (b.erro?'<p class="pag-recusa" role="alert">'+esc(b.erro)+'</p>':'')+res;
}
async function buscarPainel(e){
  e.preventDefault();
  const b=PAINEL.busca;b.q=$('#buscaPainel').value.trim();
  if(b.q.length<3){b.erro='Digite pelo menos 3 caracteres.';render();return;}
  b.carregando=true;b.erro='';render();
  try{b.res=await apiJSON('/api/admin/painel/buscar?q='+encodeURIComponent(b.q));}catch(err){b.erro=erroLegivel(err,'A busca falhou.');b.res=null;}
  b.carregando=false;render();
}
function linhaPedidoPainel(p){
  return '<li><div><b>'+esc(nomeServico(p.servico))+' · '+(p.valor==null?'—':money(p.valor))+(p.urgente?' <span class="tag tag-warn">Urgente</span>':'')+'</b>'+
    '<small class="muted">'+esc(ETAPAS_ADMIN[p.status]||p.status)+(p.bairro?' · '+esc(p.bairro):'')+(p.criadoEm?' · criado '+dataHora(p.criadoEm):'')+(p.aceitoEm?' · aceito '+dataHora(p.aceitoEm):'')+' · '+esc(p.id)+'</small></div>'+
    '<button class="btn btn-ghost btn-sm" onclick="verPedidoAdmin(\''+esc(p.id)+'\')">Abrir</button></li>';
}

/* ================= Atenção ================= */
function painelAtencao(){
  const a=PAINEL.atencao;
  if(!a){setTimeout(()=>carregarPainel('at','/api/admin/painel/atencao',(d,e)=>{PAINEL.atencao=d||{erro:e};}),0);return carregando();}
  if(a.erro)return falhou(a.erro,'PAINEL.atencao=null;render()');
  const grupos=[a.problemas,a.pagamentoParado,a.semAceite,a.aCaminho];
  return '<p class="admin-carimbo"><button type="button" class="link-btn" onclick="PAINEL.atencao=null;render()">Atualizar</button></p>'+
    grupos.map(g=>'<section class="card atencao-grupo"><h2 class="sub admin-ttl">'+esc(g.titulo)+' <span class="tag '+(g.total?'tag-warn':'tag-muted')+'">'+numeroBR(g.total)+'</span></h2>'+
      (g.itens.length?'<ul class="lista-painel">'+g.itens.map(linhaPedidoPainel).join('')+'</ul>'+(g.total>g.itens.length?'<p class="hint">Mostrando os '+g.itens.length+' mais antigos de '+numeroBR(g.total)+'.</p>':''):'<p class="muted">Nenhum.</p>')+'</section>').join('');
}

/* ================= Números ================= */
function painelNumeros(){
  const n=PAINEL.numeros;
  const periodos='<div class="admin-periodo" role="group" aria-label="Período">'+[7,30,90,365].map(d=>'<button type="button" class="pill'+(n.dias===d?' pill-brand':'')+'" aria-pressed="'+(n.dias===d)+'" onclick="PAINEL.numeros={dias:'+d+',dados:null,erro:\'\'};render()">'+(d===365?'1 ano':d+' dias')+'</button>').join('')+'</div>';
  if(!n.dados&&!n.erro){setTimeout(()=>carregarPainel('num','/api/admin/painel/numeros?dias='+n.dias,(d,e)=>{n.dados=d;n.erro=e||'';}),0);return periodos+carregando();}
  if(!n.dados)return periodos+falhou(n.erro,"PAINEL.numeros.erro='';render()");
  const d=n.dados,p=d.periodo;
  return periodos+
    '<section class="inds" aria-label="No período">'+
      indicadorAdmin('Pedidos',numeroBR(p.pedidos),'em '+d.dias+' dias')+
      indicadorAdmin('Concluídos',numeroBR(p.concluidos),p.taxaConclusao==null?'—':p.taxaConclusao.toLocaleString('pt-BR')+'% dos encerrados')+
      indicadorAdmin('Valor concluído',money(p.valor||0),p.ticketMedio==null?'—':'ticket médio '+money(p.ticketMedio))+
      indicadorAdmin('Contas novas',numeroBR(p.contasNovas),'total: '+numeroBR(d.contas.clientes)+' clientes · '+numeroBR(d.contas.profissionais)+' profissionais')+
    '</section>'+
    '<div class="admin-graficos">'+
      blocoGrafico('g-pedidos','Pedidos por dia','Criados, concluídos e cancelados.',[['pedidos','Criados',1],['concluidos','Concluídos',2],['cancelados','Cancelados',3]],d.serie,'linhas')+
      blocoGrafico('g-cadastros','Contas novas por dia','Clientes e profissionais.',[['novosClientes','Clientes',1],['novosProfissionais','Profissionais',2]],d.serie,'colunas')+
    '</div>'+
    '<section class="card" style="margin-top:16px"><h2 class="sub admin-ttl">Pedidos por categoria no período</h2>'+
      (d.categorias.length?barrasHorizontais(d.categorias.slice(0,14).map(c=>[nomeServico(c.servico),c.pedidos])):'<p class="muted">Nenhum pedido no período.</p>')+'</section>';
}

/* ================= Auditoria ================= */
function painelAuditoria(){
  const A=PAINEL.auditoria,f=A.f;
  if(A.itens===null&&!A.carregando&&!A.erro)setTimeout(()=>carregarAuditoria(false),0);
  return '<form class="filtros-auditoria" onsubmit="filtrarAuditoria(event)">'+
      '<div class="field"><label for="audConta">Conta (id)</label><input class="input" id="audConta" inputmode="numeric" value="'+esc(f.conta)+'" placeholder="Ex.: 1234"></div>'+
      '<div class="field"><label for="audPedido">Pedido</label><input class="input" id="audPedido" value="'+esc(f.pedido)+'" placeholder="o_..."></div>'+
      '<div class="field"><label for="audTipo">Tipo</label><select class="select" id="audTipo"><option value="">Todos</option>'+Object.entries(TIPOS_AUDITORIA).filter((e,i,a)=>a.findIndex(x=>x[1]===e[1])===i||e[0]==='email').filter(e=>e[0]!=='email').map(e=>'<option value="'+e[0]+'"'+(f.tipo===e[0]?' selected':'')+'>'+e[1]+'</option>').join('')+'</select></div>'+
      '<div class="field"><label for="audDe">De</label><input class="input" id="audDe" type="date" value="'+esc(f.de)+'"></div>'+
      '<div class="field"><label for="audAte">Até</label><input class="input" id="audAte" type="date" value="'+esc(f.ate)+'"></div>'+
      '<div class="filtros-acoes"><button class="btn btn-primary">'+ic('filter')+'Filtrar</button><button type="button" class="btn btn-ghost" onclick="PAINEL.auditoria.f={conta:\'\',pedido:\'\',tipo:\'\',de:\'\',ate:\'\'};PAINEL.auditoria.itens=null;render()">Limpar</button></div>'+
    '</form>'+
    (A.erro?falhou(A.erro,"PAINEL.auditoria.erro='';PAINEL.auditoria.itens=null;render()"):A.itens===null?carregando():
     !A.itens.length?'<div class="card emptystate">'+ic('clock')+'<b>Nada registrado com esses filtros</b></div>':
     '<ol class="auditoria">'+A.itens.map(e=>'<li class="aud-'+esc(e.tipo)+'"><time>'+dataHora(e.quando)+'</time><div><span class="aud-tipo">'+esc(TIPOS_AUDITORIA[e.tipo]||e.tipo)+'</span><p>'+esc(textoComServicos(e.texto))+'</p>'+
       '<small class="muted">'+(e.autorId&&e.autorId!==e.contaId?'Por <button type="button" class="log-quem" onclick="verUsuarioAdmin('+Number(e.autorId)+')">'+esc(e.autor||'')+'</button>'+(e.autorTipo&&NOME_PAPEL[e.autorTipo]?' ('+NOME_PAPEL[e.autorTipo].toLowerCase()+')':'')+' · conta de ':'Conta de ')+
       '<button type="button" class="log-quem" onclick="verUsuarioAdmin('+Number(e.contaId)+')">'+esc(e.conta||'')+'</button>'+
       (e.pedidoId?' · <button type="button" class="log-quem" onclick="verPedidoAdmin(\''+esc(e.pedidoId)+'\')">pedido</button>':'')+'</small></div></li>').join('')+'</ol>'+
     (A.proximo?'<button class="btn btn-ghost btn-block" style="margin-top:14px" onclick="carregarAuditoria(true)"'+(A.carregando?' disabled':'')+'>'+(A.carregando?'Carregando...':'Carregar mais')+'</button>':'<p class="hint center" style="margin-top:12px">Fim da lista.</p>'));
}
/* Eventos antigos guardaram o código do serviço ("marido", "eletrica"): mostra o nome. */
function textoComServicos(t){return SERVICES.reduce((acc,x)=>acc.replace(new RegExp('\\b(de|do) '+x.id+'\\b','g'),'$1 '+x.n.toLowerCase()),String(t||''));}
function filtrarAuditoria(e){
  e.preventDefault();
  PAINEL.auditoria.f={conta:$('#audConta').value.replace(/\D/g,''),pedido:$('#audPedido').value.trim(),tipo:$('#audTipo').value,de:$('#audDe').value,ate:$('#audAte').value};
  PAINEL.auditoria.itens=null;PAINEL.auditoria.erro='';render();
}
async function carregarAuditoria(mais){
  const A=PAINEL.auditoria;if(A.carregando)return;A.carregando=true;if(mais)render();
  const qs=Object.entries(A.f).filter(e=>e[1]).map(e=>e[0]+'='+encodeURIComponent(e[1]));
  if(mais&&A.proximo)qs.push('antes='+encodeURIComponent(A.proximo));
  try{const r=await apiJSON('/api/admin/painel/auditoria?'+qs.join('&'));A.itens=(mais?A.itens||[]:[]).concat(r.itens);A.proximo=r.proximo;A.erro='';}
  catch(e){A.erro=erroLegivel(e,'Não foi possível carregar a auditoria.');}
  A.carregando=false;if(route.sub==='admin')render();
}
function verAuditoriaDe(campo,valor){
  PAINEL.auditoria.f={conta:'',pedido:'',tipo:'',de:'',ate:''};PAINEL.auditoria.f[campo]=String(valor);PAINEL.auditoria.itens=null;PAINEL.auditoria.erro='';
  closeModal();go('/app/admin/auditoria');
}

/* ================= Exportar ================= */
let exportarTimer=null;
function painelExportar(){
  const l=PAINEL.exportacoes;
  if(!l){setTimeout(()=>carregarPainel('exp','/api/admin/painel/exportacoes',(d,e)=>{PAINEL.exportacoes=d||{erro:e};}),0);}
  else if(Array.isArray(l)&&l.some(x=>x.status==='gerando')){clearTimeout(exportarTimer);exportarTimer=setTimeout(()=>{if(route.id==='exportar'){PAINEL.exportacoes=null;render();}},4000);}
  const hoje=new Date().toISOString().slice(0,10),mes=new Date(Date.now()-29*864e5).toISOString().slice(0,10);
  return '<form class="card form-exportar" onsubmit="pedirExportacao(event)"><h2 class="sub admin-ttl">Nova planilha</h2>'+
      '<div class="grid g3" style="gap:0 16px"><div class="field"><label for="expTipo">O quê</label><select class="select" id="expTipo"><option value="pedidos">Pedidos (pela data de criação)</option><option value="contas">Contas (pela data de cadastro)</option>'+(pode('AUDITORIA')?'<option value="auditoria">Auditoria</option>':'')+'</select></div>'+
      '<div class="field"><label for="expDe">De</label><input class="input" id="expDe" type="date" required value="'+mes+'" max="'+hoje+'"></div>'+
      '<div class="field"><label for="expAte">Até</label><input class="input" id="expAte" type="date" required value="'+hoje+'" max="'+hoje+'"></div></div>'+
      '<p class="hint">Até 1 ano por planilha e no máximo 500 mil linhas. Abre no Excel e no Google Planilhas (separado por ponto e vírgula).</p>'+
      '<button class="btn btn-primary" id="expPedir">'+ic('download')+'Gerar planilha</button></form>'+
    '<section class="card" style="margin-top:16px"><h2 class="sub admin-ttl">Suas planilhas</h2>'+
    (!l?'<p class="muted">Carregando...</p>':l.erro?'<p class="muted">'+esc(l.erro)+'</p>':!l.length?'<p class="muted">Nenhuma ainda.</p>':
     '<ul class="lista-painel">'+l.map(x=>'<li><div><b>'+esc({pedidos:'Pedidos',contas:'Contas',auditoria:'Auditoria'}[x.tipo]||x.tipo)+' · '+esc(x.de.split('-').reverse().join('/'))+' a '+esc(x.ate.split('-').reverse().join('/'))+'</b>'+
       '<small class="muted">Pedida '+dataHora(x.criadaEm)+' · '+(x.status==='pronta'?numeroBR(x.linhas)+' linhas':x.status==='erro'?'falhou: '+esc(x.erro||''):'gerando...')+'</small></div>'+
       (x.status==='pronta'?'<a class="btn btn-primary btn-sm" href="'+API_BASE_URL+'/api/admin/painel/exportacoes/'+encodeURIComponent(x.id)+'/arquivo" download>'+ic('download')+'Baixar</a>':
        x.status==='gerando'?'<span class="tag tag-warn">'+ic('clock')+' Gerando</span>':'<span class="tag tag-danger">Erro</span>')+'</li>').join('')+'</ul>')+'</section>';
}
async function pedirExportacao(e){
  e.preventDefault();
  const b=$('#expPedir');b.disabled=true;
  try{await apiJSON('/api/admin/painel/exportacoes','POST',{tipo:$('#expTipo').value,de:$('#expDe').value,ate:$('#expAte').value},'Não foi possível pedir a planilha.');toast('Gerando. Você recebe um aviso quando ficar pronta.','ok');PAINEL.exportacoes=null;render();}
  catch(err){toast(erroLegivel(err,'Não foi possível pedir a planilha.'),'err');b.disabled=false;}
}

/* ================= Ficha da conta e do pedido ================= */
async function verUsuarioAdmin(id){
  openModal('<div class="modal-head"><div><h3>Conta</h3><p>Carregando...</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>',{wide:true});
  try{
    const u=await apiJSON('/api/admin/usuarios/'+encodeURIComponent(id),'GET',undefined,'Não foi possível abrir a conta.');
    const linhas=[['E-mail',u.email+(u.emailConfirmado?'':' (não confirmado)')],['Tipo',u.tipo==='profissional'?'Profissional'+(u.servico?' · '+nomeServico(u.servico):''):(NOME_PAPEL[u.tipo]||'Cliente')],
      ['Telefone',u.telefone||'—'],['CPF',u.cpf||'—'],['Cidade',u.cidade||'—'],['Cadastro',u.criadoEm?new Date(u.criadoEm).toLocaleDateString('pt-BR'):'sem data'],
      ['Identidade',u.verificado?'Verificada':'Não verificada'],
      ['Situação',u.banido?'Banida':u.suspenso?'Suspensa até '+dataHora(u.suspensoAte):u.avisos?u.avisos+' aviso(s)':'Ativa'],
      ['Avaliação',u.avaliacoes?u.notaMedia.toLocaleString('pt-BR')+' ('+u.avaliacoes+')':'Sem avaliações'],['Denúncias contra',String(u.denunciasContra)]].concat(u.online==null?[]:[['Agora',u.online?'Online':'Offline']]);
    $('.modal').innerHTML='<div class="modal-head"><div><h3>'+esc(u.nome||'Sem nome')+'</h3><p>Conta '+u.id+' · esta abertura fica registrada na auditoria</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
      '<dl class="admin-linhas">'+linhas.map(r=>'<div><dt>'+r[0]+'</dt><dd>'+esc(r[1])+'</dd></div>').join('')+'</dl>'+
      '<h4 class="recursos-ttl">Pedidos ('+numeroBR(u.totalPedidos)+(u.totalPedidos>u.pedidos.length?', os '+u.pedidos.length+' mais recentes':'')+')</h4>'+
      (u.pedidos.length?'<ul class="lista-recursos">'+u.pedidos.slice(0,15).map(p=>'<li><button type="button" class="log-quem" onclick="verPedidoAdmin(\''+esc(p.id)+'\')">'+esc(nomeServico(p.servico))+'</button> · '+esc(ETAPAS_ADMIN[p.status]||p.status)+' · '+(p.valor==null?'—':money(p.valor))+'<small>como '+p.papel+(p.criadoEm?' · '+dataHora(p.criadoEm):'')+'</small></li>').join('')+'</ul>':'<p class="muted">Nenhum pedido.</p>')+
      '<h4 class="recursos-ttl">Histórico recente</h4>'+(u.historico.length?'<ul class="lista-recursos">'+u.historico.slice(0,12).map(h=>'<li><p>'+esc(h.texto)+'</p><small>'+dataHora(h.quando)+'</small></li>').join('')+'</ul>':'<p class="muted">Nada registrado ainda.</p>')+
      '<div style="display:flex;gap:10px;margin-top:18px;flex-wrap:wrap">'+(pode('AUDITORIA')?'<button class="btn btn-ghost btn-sm" onclick="verAuditoriaDe(\'conta\','+Number(u.id)+')">'+ic('clock')+'Ver na auditoria</button>':'')+
      (pode('MODERACAO')&&!AREAS_PAPEL[u.tipo]?'<button class="btn btn-'+(u.banido?'ghost':'danger')+' btn-sm" onclick="banirConta('+Number(u.id)+','+(!u.banido)+').then(()=>verUsuarioAdmin('+Number(u.id)+'))">'+(u.banido?'Desbanir':'Banir')+'</button>':'')+'</div>';
  }catch(error){const p=$('.modal p');if(p)p.textContent=erroLegivel(error,'Não foi possível abrir a conta.');}
}
async function verPedidoAdmin(id){
  openModal('<div class="modal-head"><div><h3>Pedido</h3><p>Carregando...</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>',{wide:true});
  try{
    const p=await apiJSON('/api/admin/pedidos/'+encodeURIComponent(id),'GET',undefined,'Não foi possível abrir o pedido.');
    const pessoa=x=>x?'<button type="button" class="log-quem" onclick="verUsuarioAdmin('+Number(x.id)+')">'+esc(x.nome)+'</button>':'—';
    $('.modal').innerHTML='<div class="modal-head"><div><h3>'+esc(nomeServico(p.servico))+(p.urgente?' <span class="tag tag-warn">Urgente</span>':'')+'</h3><p>'+esc(p.id)+'</p></div><button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
      '<dl class="admin-linhas"><div><dt>Etapa</dt><dd>'+esc(ETAPAS_ADMIN[p.status]||p.status)+'</dd></div><div><dt>Valor</dt><dd>'+(p.valor==null?'—':money(p.valor))+'</dd></div>'+
      '<div><dt>Pagamento</dt><dd>'+esc(p.pagamento||'—')+'</dd></div>'+
      '<div><dt>Cliente</dt><dd>'+pessoa(p.cliente)+'</dd></div><div><dt>Profissional</dt><dd>'+pessoa(p.profissional)+'</dd></div>'+
      '<div><dt>Endereço</dt><dd>'+esc(p.endereco||'—')+'</dd></div><div><dt>Nota</dt><dd>'+(p.nota||'—')+'</dd></div>'+
      '<div><dt>Mensagens</dt><dd>'+p.mensagens+'</dd></div><div><dt>Denúncias</dt><dd>'+p.denuncias+'</dd></div></dl>'+
      (p.descricao?'<p style="margin-top:14px">'+esc(p.descricao)+'</p>':'')+
      (p.liberacao==='problema'&&p.pagamento==='retido'&&pode('PAGAMENTOS')?'<div class="pag-problema-adm"><h4 class="recursos-ttl">Problema aberto pelo cliente</h4><p>'+esc(p.problema||'')+'</p><p class="hint">O pagamento está retido. Decida para onde ele vai.</p><div class="pag-acoes"><button class="btn btn-ghost" onclick="decidirProblemaAdmin(\''+esc(p.id)+'\',\'devolver\')">Devolver ao cliente</button><button class="btn btn-primary" onclick="decidirProblemaAdmin(\''+esc(p.id)+'\',\'liberar\')">Liberar ao profissional</button></div></div>':'')+
      '<h4 class="recursos-ttl">Linha do tempo</h4><ul class="lista-recursos">'+p.etapas.map(e=>'<li><p>'+esc(e.texto)+'</p>'+(e.quando?'<small>'+dataHora(e.quando)+'</small>':'')+'</li>').join('')+'</ul>'+
      '<h4 class="recursos-ttl">Propostas ('+p.propostas.length+')</h4>'+(p.propostas.length?'<ul class="lista-recursos">'+p.propostas.map(x=>'<li><p>'+(x.autor==='cliente'?'Cliente contrapropôs':esc(x.profissional||'Profissional')+' propôs')+' '+money(x.valor)+' · '+esc(x.status)+'</p><small>'+dataHora(x.quando)+'</small></li>').join('')+'</ul>':'<p class="muted">Nenhuma proposta.</p>')+
      (pode('AUDITORIA')?'<button class="btn btn-ghost btn-sm" style="margin-top:16px" onclick="verAuditoriaDe(\'pedido\',\''+esc(p.id)+'\')">'+ic('clock')+'Ver na auditoria</button>':'');
  }catch(error){const q=$('.modal p');if(q)q.textContent=erroLegivel(error,'Não foi possível abrir o pedido.');}
}
async function decidirProblemaAdmin(id,decisao){
  if(!await confirmar(decisao==='liberar'?{titulo:'Liberar o pagamento ao profissional?',botao:'Liberar',icone:'checkCircle'}:{titulo:'Devolver o pagamento ao cliente?',botao:'Devolver',perigo:true}))return;
  try{await apiJSON('/api/pagamentos/admin/pedidos/'+encodeURIComponent(id)+'/'+decisao,'POST');toast(decisao==='liberar'?'Pagamento liberado ao profissional':'Pagamento devolvido ao cliente','ok');PAINEL.pagamentos=null;PAINEL.hoje=null;verPedidoAdmin(id);}
  catch(e){toast(erroLegivel(e,'Não foi possível decidir.'),'err');}
}

/* ================= Gráficos ================= */
/* Lista de barras horizontais em HTML: rótulo, barra e valor escrito (nada depende só da cor). */
function barrasHorizontais(linhas,unidade){
  const max=Math.max(1,...linhas.map(l=>l[1]));
  return '<ul class="barras-h">'+linhas.map(l=>'<li><span class="bh-rotulo">'+esc(l[0])+'</span>'+
    '<span class="bh-trilho"><span class="bh-barra'+(l[2]?' apagada':'')+'" style="width:'+(l[1]?Math.max(1.5,100*l[1]/max):0)+'%"></span></span>'+
    '<span class="bh-valor">'+numeroBR(l[1])+(l[3]?'<small>'+esc(l[3])+'</small>':'')+'</span></li>').join('')+'</ul>';
}

/* ---------- Gráficos por dia (SVG desenhado com a largura real) ---------- */
function blocoGrafico(id,titulo,descricao,series,dados,forma){
  window.__graficosAdmin=window.__graficosAdmin||{};window.__graficosAdmin[id]={series,dados,forma};
  const total=series.map(s=>dados.reduce((a,d)=>a+(d[s[0]]||0),0));
  const vazio=total.every(t=>!t);
  return '<section class="card grafico" aria-labelledby="t-'+id+'"><div class="grafico-cab"><div><h2 class="sub admin-ttl" id="t-'+id+'">'+titulo+'</h2><p class="muted grafico-desc">'+descricao+'</p></div>'+
    '<ul class="legenda">'+series.map((s,i)=>'<li><i class="leg-'+s[2]+(forma==='linhas'?' leg-linha':'')+'" aria-hidden="true"></i>'+s[1]+' <b>'+numeroBR(total[i])+'</b></li>').join('')+'</ul></div>'+
    '<div class="grafico-area" id="'+id+'" role="img" aria-label="'+titulo+': '+series.map((s,i)=>s[1]+' '+total[i]).join(', ')+' no período">'+(vazio?'<p class="grafico-vazio">Nada no período.</p>':'')+'</div>'+
    '<details class="grafico-tabela"><summary>Ver em tabela</summary><div class="tabela-rolagem"><table class="adm-tabela"><thead><tr><th>Dia</th>'+series.map(s=>'<th class="num">'+s[1]+'</th>').join('')+'</tr></thead><tbody>'+
    dados.filter(d=>series.some(s=>d[s[0]])).map(d=>'<tr><td>'+diaCurto(d.dia)+'</td>'+series.map(s=>'<td class="num">'+d[s[0]]+'</td>').join('')+'</tr>').join('')+
    '</tbody></table></div></details></section>';
}
const diaCurto=iso=>{const [a,m,d]=iso.split('-');return d+'/'+m;};
function desenharGraficosAdmin(){
  const g=window.__graficosAdmin||{};
  Object.keys(g).forEach(id=>{const no=document.getElementById(id);if(no&&!no.querySelector('.grafico-vazio'))desenharSerieTempo(no,g[id]);});
  if(!window.__redesenhoAdmin){window.__redesenhoAdmin=true;let t;addEventListener('resize',()=>{clearTimeout(t);t=setTimeout(()=>{if(route.sub==='admin')desenharGraficosAdmin();},150);});}
}
function desenharSerieTempo(no,{series,dados,forma}){
  const W=Math.max(280,no.clientWidth),H=W<560?190:230,m={t:12,r:12,b:26,l:34},iw=W-m.l-m.r,ih=H-m.t-m.b;
  const empilhado=forma==='colunas';
  const topo=Math.max(1,...dados.map(d=>empilhado?series.reduce((a,s)=>a+(d[s[0]]||0),0):Math.max(...series.map(s=>d[s[0]]||0))));
  /* Escala com passos inteiros e redondos: contagens não têm meio pedido. */
  const mag=Math.pow(10,Math.floor(Math.log10(topo))),passo=topo<=5?1:[1,2,2.5,5,10].map(k=>k*mag).find(k=>topo/k<=5)||10*mag;
  const ymax=Math.ceil(topo/passo)*passo,y=v=>m.t+ih-ih*v/ymax,n=dados.length,banda=iw/n,x=i=>m.l+banda*i+banda/2;
  let svg='<svg width="'+W+'" height="'+H+'" viewBox="0 0 '+W+' '+H+'" aria-hidden="true">';
  for(let v=0;v<=ymax;v+=passo)svg+='<line class="viz-grade" x1="'+m.l+'" x2="'+(W-m.r)+'" y1="'+y(v)+'" y2="'+y(v)+'"/><text class="viz-eixo" x="'+(m.l-8)+'" y="'+(y(v)+4)+'" text-anchor="end">'+v+'</text>';
  const cada=Math.ceil(n/(W<560?4:7));
  dados.forEach((d,i)=>{if(i%cada===0||i===n-1)svg+='<text class="viz-eixo" x="'+x(i)+'" y="'+(H-6)+'" text-anchor="middle">'+diaCurto(d.dia)+'</text>';});
  if(empilhado){
    const larg=Math.max(2,Math.min(18,banda-3));
    dados.forEach((d,i)=>{let base=0;series.forEach(s=>{const v=d[s[0]]||0;if(!v)return;
      const y1=y(base+v),y0=y(base);svg+='<rect class="viz-'+s[2]+'" x="'+(x(i)-larg/2)+'" y="'+y1+'" width="'+larg+'" height="'+Math.max(1,y0-y1-(base?2:0))+'"/>';base+=v;});});
  }else{
    series.forEach(s=>{svg+='<polyline class="viz-linha viz-'+s[2]+'" points="'+dados.map((d,i)=>x(i)+','+y(d[s[0]]||0)).join(' ')+'"/>';});
    /* Rótulo direto no fim de cada linha. */
  }
  svg+='<line class="viz-mira" x1="0" x2="0" y1="'+m.t+'" y2="'+(m.t+ih)+'" visibility="hidden"/>';
  if(!empilhado)series.forEach(s=>{svg+='<circle class="viz-ponto viz-'+s[2]+'" r="4.5" cx="0" cy="0" visibility="hidden" data-s="'+s[0]+'"/>';});
  svg+='<rect class="viz-alvo" x="'+m.l+'" y="'+m.t+'" width="'+iw+'" height="'+ih+'"/></svg><div class="viz-dica" hidden></div>';
  no.innerHTML=svg;
  const alvo=no.querySelector('.viz-alvo'),mira=no.querySelector('.viz-mira'),dica=no.querySelector('.viz-dica'),pontos=no.querySelectorAll('.viz-ponto');
  const mostrar=ev=>{
    const r=no.querySelector('svg').getBoundingClientRect(),px=ev.clientX-r.left,i=Math.max(0,Math.min(n-1,Math.floor((px-m.l)/banda))),d=dados[i];
    mira.setAttribute('x1',x(i));mira.setAttribute('x2',x(i));mira.setAttribute('visibility','visible');
    pontos.forEach(p=>{p.setAttribute('cx',x(i));p.setAttribute('cy',y(d[p.dataset.s]||0));p.setAttribute('visibility','visible');});
    dica.innerHTML='<b>'+new Date(d.dia+'T12:00:00').toLocaleDateString('pt-BR',{weekday:'short',day:'2-digit',month:'short'})+'</b>'+series.map(s=>'<span><i class="leg-'+s[2]+(empilhado?'':' leg-linha')+'"></i>'+s[1]+'<em>'+d[s[0]]+'</em></span>').join('');
    dica.hidden=false;const dl=dica.offsetWidth;dica.style.left=Math.min(Math.max(0,x(i)+12),W-dl)+'px';dica.style.top=m.t+'px';
  };
  const esconder=()=>{mira.setAttribute('visibility','hidden');pontos.forEach(p=>p.setAttribute('visibility','hidden'));dica.hidden=true;};
  alvo.addEventListener('pointermove',mostrar);alvo.addEventListener('pointerdown',mostrar);alvo.addEventListener('pointerleave',esconder);
}
