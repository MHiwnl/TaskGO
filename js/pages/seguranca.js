/* ---------- SEGURANÇA ---------- */
function pageSeguranca(){
  return folha('segurança',duo('segurança em','cada etapa do pedido',1)+
    '<p class="corpo">Da verificação de quem atende à equipe que analisa cada denúncia.</p>','cabeca')+
  folha('recursos','<div class="recursos">'+[
    ['Identidade verificada','Quem pede e quem atende envia documento e selfie, conferidos pela equipe da TaskGO antes de usar a plataforma.'],
    ['Endereço protegido','O endereço e o telefone do cliente só aparecem para o profissional que ele confirmou. Antes disso, só o bairro.'],
    ['Código de chegada','Cada pedido tem um código de 4 números que fica no celular do cliente. O serviço só começa com ele.'],
    ['Chegada conferida','O profissional informa a chegada perto do local marcado, conferida pela localização do celular.'],
    ['Localização só a caminho','A posição do profissional aparece no mapa enquanto ele vai até você e é apagada quando ele chega. O trajeto não fica guardado.'],
    ['Emergência','O botão de emergência do pedido avisa a equipe de suporte da TaskGO na hora, com a sua localização, e leva às ligações para 190 e 192.'],
    ['Pagamento depois do serviço','Você paga direto a quem atendeu, só depois do serviço pronto. Nada é cobrado antes.'],
    ['Conversa protegida','O chat do pedido barra tentativas de combinar contato ou pagamento por fora.'],
    ['Moderação','Denúncias analisadas pela equipe, com advertência, suspensão ou banimento, e direito de recurso para quem foi penalizado.'],
    ['Conta protegida','Senha guardada cifrada, e-mail confirmado por código, limite de tentativas de login e verificação em duas etapas opcional.']
  ].map(x=>'<div class="recurso"><h2 class="sub" style="margin-bottom:10px">'+x[0]+'</h2><p>'+x[1]+'</p></div>').join('')+'</div>'+
  '<p class="aviso-linha mt-grande"><b>Em perigo?</b> Ligue para a Polícia Militar (190) ou o SAMU (192). Para outros assuntos, fale com a equipe.</p>'+
  '<div class="setas">'+linkSeta('#/contato','Falar com a equipe')+'</div>','folha-densa');
}
