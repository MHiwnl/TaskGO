/* =========================================================
   AVISOS COM DESTINO
   Quando um aviso diz o que falta para uma ação (chave Pix, verificação, CPF, ficar online,
   pendência), o site leva a pessoa direto ao lugar onde isso se resolve e destaca o campo.
   Depois de resolver a chave Pix, volta para a tela de onde veio.
   ========================================================= */
const AVISOS_COM_DESTINO=[
  {re:/cadastre sua chave pix/i,rota:'/app/carteira',alvo:'#chTipo, .form-chave, .resgate-chave',voltar:true},
  {re:/verifique sua identidade/i,rota:'/painel',alvo:'#seloConta',depois:()=>{const v=typeof minhaVerificacao!=='undefined'?minhaVerificacao:null;if(typeof abrirVerificacao==='function'&&!(v&&(v.status==='pendente'||v.verificado)))abrirVerificacao();}},
  {re:/cpf em configura/i,rota:'/painel',alvo:'input[name="cpf"]'},
  {re:/fique online/i,rota:'/app/oportunidades',alvo:'#disponibilidade'},
  {re:/ligue a ajuda rápida/i,rota:'/app/inicio',alvo:'.ajuda-bloco'},
  {re:/(em aberto|pendência).*(pague|carteira)|pague na carteira/i,rota:'/app/carteira',alvo:'.pend-carteira'}
];
let ultimoDestino={re:null,em:0};

/* Chamado pelo toast de erro: se o aviso tem destino, leva até ele. */
function destinoDoAviso(msg){
  if(!me())return false;
  const a=AVISOS_COM_DESTINO.find(x=>x.re.test(String(msg||'')));
  if(!a)return false;
  /* O mesmo aviso repetido em seguida (atualização automática) não arrasta a pessoa de novo. */
  if(ultimoDestino.re===a.re&&Date.now()-ultimoDestino.em<15000)return false;
  ultimoDestino={re:a.re,em:Date.now()};
  irParaResolver(a);
  return true;
}
function irParaResolver(a){
  const aqui=location.hash.replace(/^#/,'');
  if(a.voltar&&aqui&&aqui!==a.rota)window.__voltarDepoisDoAviso=aqui;
  if(aqui!==a.rota)go(a.rota);
  /* Espera a tela desenhar o campo (alguns blocos carregam do servidor) e destaca. */
  let tentativas=0;
  const procurar=()=>{
    const el=document.querySelector(a.alvo);
    if(!el){if(++tentativas<30)setTimeout(procurar,150);return;}
    el.scrollIntoView({behavior:'smooth',block:'center'});
    el.classList.add('destaque-aviso');setTimeout(()=>el.classList.remove('destaque-aviso'),2600);
    const campo=el.matches('input,select,textarea,button')?el:el.querySelector('input,select,textarea');
    if(campo)campo.focus({preventScroll:true});
    if(a.depois)a.depois();
  };
  setTimeout(procurar,120);
}
/* Depois de resolver (ex.: chave Pix salva), volta para onde a pessoa estava. */
function voltarDepoisDoAviso(texto){
  const v=window.__voltarDepoisDoAviso;if(!v)return false;
  window.__voltarDepoisDoAviso=null;
  toast(texto||'Pronto. Voltando para onde você estava.','ok');
  setTimeout(()=>go(v),600);
  return true;
}
