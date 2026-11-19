/* ---------- AJUDA ---------- */
function pageAjuda(){
  return folha('central de ajuda',duo('como podemos','ajudar?',1),'cabeca')+
  folha('perguntas','<h2 class="bloco-titulo">perguntas frequentes</h2><div class="faq-lista">'+faqList(FAQ)+'</div>'+
  '<p class="corpo" style="margin-top:36px">Não achou o que procurava? Mande sua mensagem: a equipe responde por e-mail. Durante um atendimento, use o botão de emergência do pedido.</p>'+
  '<div class="setas">'+linkSeta('#/contato','Falar com a equipe',1)+'</div>','folha-densa');
}
