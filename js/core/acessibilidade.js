/* ---------- ACESSIBILIDADE ----------
   Carregado no <head>: aplica as escolhas salvas antes da primeira pintura.
   O painel (botão fixo "Acessibilidade") usa openModal, com foco preso, Esc e retorno do foco.
   Só as preferências de tela ficam no navegador (localStorage); nenhum dado pessoal. */
const A11Y_CHAVE='taskgo-acessibilidade';
const A11Y_ESCALAS=[.9,1,1.15,1.3,1.5];
const A11Y_PADRAO={escala:1,contraste:false,semAnimacao:false,links:false,espaco:false};
const A11Y_NOMES={contraste:'Alto contraste',semAnimacao:'Reduzir animações',links:'Sublinhar links',espaco:'Espaçamento maior'};
function a11yLer(){try{return Object.assign({},A11Y_PADRAO,JSON.parse(localStorage.getItem(A11Y_CHAVE))||{});}catch(e){return Object.assign({},A11Y_PADRAO);}}
function a11yAplicar(p){
  const h=document.documentElement;p=p||a11yLer();
  h.style.fontSize=(A11Y_ESCALAS[p.escala]*100)+'%';
  h.classList.toggle('a11y-contraste',!!p.contraste);
  h.classList.toggle('a11y-sem-animacao',!!p.semAnimacao);
  h.classList.toggle('a11y-links',!!p.links);
  h.classList.toggle('a11y-espaco',!!p.espaco);
  a11yAtualizarPainel(p);
}
function a11ySalvar(p){try{localStorage.setItem(A11Y_CHAVE,JSON.stringify(p));}catch(e){}a11yAplicar(p);}
function a11yAtualizarPainel(p){
  const estado=document.getElementById('a11y-estado');if(!estado)return;
  Object.keys(A11Y_NOMES).forEach(k=>{const b=document.querySelector('[data-a11y="'+k+'"]');if(b)b.setAttribute('aria-pressed',String(!!p[k]));});
  estado.textContent='Texto em '+Math.round(A11Y_ESCALAS[p.escala]*100)+'%.';
}
function a11yAcao(acao){
  const p=a11yLer();
  if(acao==='aumentar')p.escala=Math.min(A11Y_ESCALAS.length-1,p.escala+1);
  else if(acao==='diminuir')p.escala=Math.max(0,p.escala-1);
  else if(acao==='restaurar')return a11ySalvar(Object.assign({},A11Y_PADRAO));
  else p[acao]=!p[acao];
  a11ySalvar(p);
}
function a11yAbrirPainel(){
  const botoes=Object.keys(A11Y_NOMES).map(k=>'<button type="button" class="a11y-op" data-a11y="'+k+'" aria-pressed="false">'+A11Y_NOMES[k]+'</button>').join('');
  openModal('<h2 class="a11y-titulo">Acessibilidade</h2>'+
    '<p class="muted">As escolhas ficam salvas neste aparelho.</p>'+
    '<div class="a11y-opcoes">'+
      '<button type="button" class="a11y-op" data-a11y="aumentar">Aumentar texto</button>'+
      '<button type="button" class="a11y-op" data-a11y="diminuir">Diminuir texto</button>'+
      botoes+
      '<button type="button" class="a11y-op" data-a11y="restaurar">Restaurar padrão</button>'+
    '</div>'+
    '<p id="a11y-estado" class="muted" role="status"></p>'+
    '<div class="a11y-rodape"><button type="button" class="btn btn-ghost btn-sm" onclick="closeModal()">Fechar</button></div>',{wide:true});
  a11yAtualizarPainel(a11yLer());
}
/* Erro ligado ao campo: a mensagem fica logo abaixo, com aria-invalid e aria-describedby, e o foco vai para o campo. */
let a11ySeq=0;
function marcarCampo(campo,msg){
  if(!campo)return;
  if(!campo.id)campo.id='campo-'+(++a11ySeq);
  const id=campo.id+'-erro';let p=document.getElementById(id);
  if(!p){p=document.createElement('p');p.id=id;p.className='campo-erro';campo.insertAdjacentElement('afterend',p);}
  p.textContent=msg;campo.setAttribute('aria-invalid','true');campo.setAttribute('aria-describedby',id);campo.focus();
}
/* Ao corrigir o campo, a mensagem de erro some. */
document.addEventListener('input',e=>{
  const p=e.target.id&&document.getElementById(e.target.id+'-erro');
  if(p){p.remove();e.target.removeAttribute('aria-invalid');e.target.removeAttribute('aria-describedby');}
});
document.addEventListener('click',e=>{const b=e.target.closest('[data-a11y]');if(b)a11yAcao(b.dataset.a11y);});
document.addEventListener('DOMContentLoaded',()=>{
  const b=document.createElement('button');
  b.type='button';b.id='a11yAbrir';b.className='a11y-abrir';b.textContent='Acessibilidade';
  b.setAttribute('aria-haspopup','dialog');b.onclick=a11yAbrirPainel;
  document.body.appendChild(b);
});
a11yAplicar();
