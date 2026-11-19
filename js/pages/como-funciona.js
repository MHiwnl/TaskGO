/* ---------- COMO FUNCIONA ---------- */
function pageComo(){
  const blocos=[
    ['para quem contrata',[
      ['Diga o que precisa','Escolha entre mais de 100 serviços, marque o local no mapa e diga quanto quer pagar. Pode ser para agora, para outro dia ou várias vezes por semana.'],
      ['Receba quem aceita','Os profissionais da região aceitam o seu valor ou mandam outra proposta. Você vê o perfil, a nota e as avaliações antes de confirmar.'],
      ['Acompanhe a chegada','No mapa do pedido, a posição do profissional, a rota pelas ruas e os minutos até ele chegar.'],
      ['Confirme o código','Na chegada, você passa os 4 números do seu celular. Só com eles o serviço começa.'],
      ['Pague depois','Com o serviço pronto, pague direto ao profissional, por Pix na chave dele ou em dinheiro.'],
      ['Avalie','Sua nota ajuda os próximos clientes a escolher.']
    ]],
    ['para quem trabalha',[
      ['Crie a conta e verifique','Cadastro em poucos minutos e verificação de identidade com documento e selfie.'],
      ['Escolha o que faz e onde','Marque até 10 categorias e defina de onde sai e até quantos km atende.'],
      ['Fique online','Os pedidos da sua região chegam na hora, com o serviço, o bairro e o valor.'],
      ['Aceite ou proponha','Aceite o valor do cliente ou mande outro. A agenda bloqueia os horários dos serviços marcados.'],
      ['Vá até o cliente','O endereço aparece depois da confirmação; o Waze leva você até lá.'],
      ['Receba direto','O cliente paga a você. A taxa da TaskGO fica na sua Carteira para pagar por Pix ou cartão.']
    ]]
  ];
  return folha('passo a passo',duo('como funciona','a TaskGO',1)+
    '<p class="corpo">Um pedido só, do primeiro contato ao pagamento. Também para obras em etapas, serviços que se repetem na semana e ajuda rápida para emergências.</p>','cabeca')+
  folha('etapas','<div class="etapas-grupos">'+blocos.map(b=>'<div><h2 class="grupo-titulo">'+b[0]+'</h2><ol class="etapas">'+
    b[1].map(x=>'<li><div><h3>'+x[0]+'</h3><p>'+x[1]+'</p></div></li>').join('')+'</ol></div>').join('')+'</div>','folha-densa')+
  folha('comece agora',titulo('pronto para começar?')+
    '<p class="corpo">Criar a conta é grátis e leva menos de um minuto.</p>'+
    '<div class="setas">'+linkSeta('#/entrar?tipo=cliente','Contratar um serviço',1)+linkSeta('#/entrar?tipo=profissional','Quero atender pela TaskGO')+'</div>');
}
