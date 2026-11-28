/* ---------- ACESSIBILIDADE ---------- */
function pageAcessibilidade(){
  const recursos=[
    ['Teclado','Verificamos com o teclado o painel de acessibilidade (abrir, percorrer, Esc e retorno do foco) e o link Pular para o conteúdo.'],
    ['Leitor de tela','Verificamos automaticamente (axe-core) as telas públicas, de entrada, de cliente e de profissional. Trocas de página anunciam o título, e, nos formulários de entrada, cadastro, pedido, pagamento, configurações e verificação, o erro fica ligado ao campo. Não testamos com leitores de tela reais.'],
    ['Tamanho e zoom','Verificamos o layout em 320 px, 390 px e 640 px (equivale a 200% de zoom numa tela de 1280 px), sem rolagem lateral nas telas testadas. O painel aumenta o texto até 150%.'],
    ['Movimento','As animações respeitam a preferência de reduzir movimento do sistema, e o painel também as desliga.'],
    ['Libras','O botão do VLibras, do governo federal, traduz trechos da página para Libras com um avatar. O texto traduzido é enviado ao serviço do governo (ver a Política de Privacidade).']
  ];
  return folha('acessibilidade',duo('acessibilidade','para todos',1)+
    '<p class="corpo">Buscamos atender à WCAG 2.1 nível AA, a referência do eMAG e da Lei Brasileira de Inclusão (Lei 13.146/2015, art. 63). Esta página diz o que verificamos e o que ainda falta.</p>','cabeca')+
  folha('recursos','<div class="recursos">'+recursos.map(x=>'<div class="recurso"><h2 class="sub" style="margin-bottom:10px">'+x[0]+'</h2><p>'+x[1]+'</p></div>').join('')+'</div>'+
    '<div class="setas"><button type="button" class="btn btn-ghost" onclick="a11yAbrirPainel()">Abrir o painel de acessibilidade</button></div>','folha-densa')+
  folha('limites','<h2 class="sub">Limitações conhecidas</h2><ul class="corpo"><li>As telas da administração e da equipe interna (moderação, financeiro e verificação de identidade) ainda não passaram pela mesma revisão.</li><li>Não testamos todos os fluxos com leitores de tela reais (NVDA, VoiceOver ou TalkBack); a verificação foi automática e por teclado.</li><li>Os textos de algumas etiquetas pequenas e ícones usam medidas fixas e não crescem com o painel.</li><li>O pagamento por Pix foi conferido pelo código, não com um pagamento real.</li></ul>','folha-densa')+
  folha('relatar','<h2 class="sub">Se encontrar uma barreira</h2><p class="corpo">Descreva o que aconteceu, em qual página e com qual recurso (leitor de tela, teclado, zoom). Escreva para taskgo.adm@gmail.com ou use o formulário de contato. Vamos responder e corrigir o que for possível.</p><p class="muted">Última revisão: 27/11/2026.</p><div class="setas">'+linkSeta('#/contato','Falar com a equipe')+'</div>','folha-densa');
}
