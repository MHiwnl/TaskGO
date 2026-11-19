/* =========================================================
   ATUALIZAÇÃO SEM PISCAR
   Em vez de apagar a tela e desenhar tudo de novo, compara o HTML novo com o que já está na
   tela e troca só o que mudou. Elementos iguais continuam os mesmos: imagens não recarregam,
   animações de entrada não recomeçam, foco, rolagem e texto digitado ficam onde estavam.
   - Elementos com id (ou data-key) são casados pelo id, mesmo que mudem de posição.
   - Mapas (Leaflet) e blocos com data-morph-manter (preenchidos depois, pelo servidor) não são tocados.
   - Campos: o valor só muda quando o HTML novo traz um valor diferente do anterior; o campo
     em que a pessoa está digitando nunca é sobrescrito.
   ========================================================= */
function morfar(alvo,html){
  const modelo=document.createElement('template');
  modelo.innerHTML=html;
  morfarFilhos(alvo,modelo.content);
}
function chaveDoNo(n){return n.nodeType===1?(n.id||n.getAttribute('data-key')||null):null;}
function mesmoNo(a,b){return a.nodeType===b.nodeType&&a.nodeName===b.nodeName&&(a.nodeType!==1||chaveDoNo(a)===chaveDoNo(b));}
function morfarFilhos(de,para){
  const porChave=new Map();
  for(let c=de.firstChild;c;c=c.nextSibling){const k=chaveDoNo(c);if(k&&!porChave.has(k))porChave.set(k,c);}
  let atual=de.firstChild;
  for(const novo of Array.from(para.childNodes)){
    const k=chaveDoNo(novo);
    let casado=null;
    if(k){const velho=porChave.get(k);if(velho&&mesmoNo(velho,novo)){casado=velho;porChave.delete(k);}}
    else if(atual&&!chaveDoNo(atual)&&mesmoNo(atual,novo))casado=atual;
    if(casado){
      if(casado===atual)atual=atual.nextSibling;
      else de.insertBefore(casado,atual);
      morfarNo(casado,novo);
    }else de.insertBefore(novo,atual);
  }
  while(atual){const prox=atual.nextSibling;de.removeChild(atual);atual=prox;}
}
function morfarNo(de,para){
  if(de.nodeType!==1){if(de.nodeValue!==para.nodeValue)de.nodeValue=para.nodeValue;return;}
  /* Conteúdo montado por outro código depois do desenho: fica como está. */
  if(de.classList.contains('leaflet-container'))return;
  if(de.hasAttribute('data-morph-manter')&&para.hasAttribute('data-morph-manter')&&de.getAttribute('data-morph-manter')===para.getAttribute('data-morph-manter'))return;
  const tag=de.nodeName,ativo=de===document.activeElement;
  if(tag==='INPUT'){
    const valorAntes=de.getAttribute('value'),valorDepois=para.getAttribute('value'),marcadoAntes=de.hasAttribute('checked'),marcadoDepois=para.hasAttribute('checked');
    morfarAtributos(de,para);
    if(valorAntes!==valorDepois&&!ativo&&de.type!=='file')de.value=valorDepois==null?'':valorDepois;
    if(marcadoAntes!==marcadoDepois)de.checked=marcadoDepois;
    return;
  }
  if(tag==='TEXTAREA'){
    const antes=de.textContent,depois=para.textContent;
    morfarAtributos(de,para);
    if(antes!==depois){de.textContent=depois;if(!ativo)de.value=depois;}
    return;
  }
  if(tag==='SELECT'){
    const escolhido=s=>{const o=Array.from(s.querySelectorAll('option')).find(x=>x.hasAttribute('selected'));return o?o.getAttribute('value')??o.textContent:null;};
    const antes=escolhido(de),depois=escolhido(para);
    morfarAtributos(de,para);morfarFilhos(de,para);
    if(antes!==depois&&depois!=null&&!ativo)de.value=depois;
    return;
  }
  morfarAtributos(de,para);
  morfarFilhos(de,para);
}
function morfarAtributos(de,para){
  for(const a of Array.from(de.attributes))if(!para.hasAttribute(a.name))de.removeAttribute(a.name);
  for(const a of Array.from(para.attributes))if(de.getAttribute(a.name)!==a.value)de.setAttribute(a.name,a.value);
}
