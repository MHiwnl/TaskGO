/* =========================================================
   CONFIRMAÇÃO NO VISUAL DO SITE
   Substitui a caixa padrão do navegador ("taskgohq.com.br diz").
   await confirmar({titulo, texto, itens:[...], botao, perigo:true, valor}) -> true ou false.
   Fechar, Esc, clicar fora ou Voltar dão false.
   ========================================================= */
let confirmarPendente=null;
function confirmar(o){
  if(typeof o==='string')o={texto:o};
  return new Promise(ok=>{
    if(confirmarPendente){const r=confirmarPendente;confirmarPendente=null;r(false);}
    openModal('<div class="confirma'+(o.perigo?' perigo':'')+'">'+
      '<div class="confirma-topo"><span class="confirma-ico" aria-hidden="true">'+ic(o.icone||(o.perigo?'alert':'help'))+'</span>'+
      '<div><h3>'+esc(o.titulo||'Confirmar?')+'</h3>'+(o.texto?'<p>'+esc(o.texto)+'</p>':'')+'</div>'+
      '<button class="icon-btn" aria-label="Fechar" onclick="closeModal()">'+ic('x')+'</button></div>'+
      (o.valor!=null?'<div class="confirma-valor"><span>'+esc(o.valorRotulo||'Valor')+'</span><b>'+money(o.valor)+'</b></div>':'')+
      (o.itens&&o.itens.length?'<ul class="confirma-itens">'+o.itens.map(t=>'<li>'+ic('arrow')+'<span>'+esc(t)+'</span></li>').join('')+'</ul>':'')+
      '<div class="pag-form-acoes"><button class="btn btn-ghost" onclick="closeModal()">'+esc(o.voltar||'Voltar')+'</button>'+
      '<button class="btn '+(o.perigo?'btn-danger':'btn-primary')+'" id="confirmaOk" onclick="responderConfirmacao()">'+esc(o.botao||'Confirmar')+'</button></div></div>',
      {className:'modal-confirma',onOpen:()=>{const b=$('#confirmaOk');if(b)b.focus();}});
    confirmarPendente=ok;
  });
}
function responderConfirmacao(){const r=confirmarPendente;confirmarPendente=null;closeModal();if(r)r(true);}
