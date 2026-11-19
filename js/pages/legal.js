/* ---------- LEGAL ---------- */
/* O prazo escrito na política vem do servidor, então o texto acompanha a configuração. */
function preencherPrazos(){
  if(DEMO_MODE)return;
  fetch(API_BASE_URL+'/api/privacidade/prazos').then(r=>r.ok?r.json():null).then(p=>{const el=document.querySelector('[data-prazo]');if(!p||!el)return;
    el.textContent='Conversas de pedidos concluídos ou cancelados: '+p.conversasDias+' dias depois do fim. Notificações já lidas: '+p.notificacoesDias+' dias. Histórico da conta e registros do log: '+p.historicoDias+' dias.';}).catch(()=>{});
}
function pageLegal(){
  if(route.sub==='privacidade')setTimeout(preencherPrazos,50);
  const docs={
    termos:['Termos de Uso',[
      ['1. Objeto','A TaskGO é uma plataforma que conecta pessoas que precisam de serviços a profissionais autônomos que os oferecem. A TaskGO não executa os serviços: o contrato de prestação é entre cliente e profissional.'],
      ['2. Cadastro e verificação','O usuário fornece informações verdadeiras e atualizadas. Para fazer pedidos ou receber oportunidades, a identidade é verificada pela equipe, com documento e selfie. Contas com dados falsos podem ser suspensas ou banidas.'],
      ['3. Pagamento do serviço','O cliente paga o valor combinado diretamente ao profissional, por Pix na chave dele ou em dinheiro, depois que o serviço é concluído. Em obras, cada etapa é paga depois de aprovada pelo cliente; no serviço recorrente, cada vez depois de realizada. A TaskGO não recebe nem guarda o valor do serviço.'],
      ['4. Taxa da plataforma','O profissional paga à TaskGO uma taxa sobre o valor recebido: 15% nos serviços até R$ 999,99; 12% de R$ 1.000 a R$ 2.999,99; nas obras, 12%, 10%, 8% ou 6%, conforme o valor total; 10% na ajuda rápida; e 15% na visita técnica. A taxa é paga pela Carteira, por Pix ou cartão, processados pelo Asaas. Taxas em aberto são lembradas e cobradas no balanço semanal.'],
      ['5. Conduta','É proibido combinar contato ou pagamento fora da plataforma para fugir da taxa, ofender, ameaçar ou enganar a outra parte. A conversa do pedido é moderada automaticamente.'],
      ['6. Moderação','Denúncias são analisadas pela equipe da TaskGO, que pode advertir, suspender ou banir contas. Quem foi penalizado pode enviar um recurso para a equipe rever a decisão.'],
      ['7. Responsabilidades','O profissional responde pela execução técnica do serviço e pelo material que fornecer. O cliente responde pelas informações do pedido e pelo pagamento combinado. A TaskGO responde pelo funcionamento da plataforma, pela verificação de identidade e pela moderação.']
    ]],
    privacidade:['Política de Privacidade',[
      ['1. Quem cuida dos dados','A TaskGO, com sede em Recife/PE. Para qualquer assunto de privacidade, escreva para taskgo.adm@gmail.com.'],
      ['2. Dados que guardamos','Cadastro (nome, CPF, e-mail, telefone, cidade, foto e endereços salvos); pedidos, propostas e avaliações; mensagens trocadas na conversa de cada pedido; notificações; a última posição do profissional enquanto ele está a caminho; o histórico da conta (confirmação de e-mail, avisos, suspensões, banimentos, recursos, denúncias e cancelamentos); as fotos do documento e a selfie enviadas para a verificação de identidade, vistas só pela equipe; e a chave Pix de quem atende.'],
      ['3. Para que usamos','Para criar e proteger a sua conta, ligar cliente e profissional, mostrar o andamento do atendimento, moderar a conversa contra combinações por fora e cumprir obrigações legais. Não vendemos dados e não usamos para publicidade.'],
      ['4. Com quem compartilhamos','O endereço e o contato do cliente só aparecem para o profissional que aceitou o pedido. Os e-mails da TaskGO saem pelo Gmail (Google). O mapa usa o OpenStreetMap, que também recebe o endereço do pedido para posicionar o marcador, e a busca de CEP usa o ViaCEP, que recebe o CEP digitado. A chave Pix de quem atende aparece para o cliente do pedido na hora de pagar. Os pagamentos de taxas são processados pelo Asaas, que recebe só os dados necessários à cobrança. No Entrar com Google, o Google confirma o seu e-mail.'],
      ['5. Localização','O profissional decide compartilhar a localização durante um atendimento a caminho. Só a última posição fica guardada, e ela é apagada quando o atendimento sai dessa etapa.'],
      ['6. Por quanto tempo','<span data-prazo>Conversas de pedidos concluídos ou cancelados: 180 dias depois do fim. Notificações já lidas: 365 dias. Histórico da conta e registros do log: 730 dias.</span> Depois disso, o sistema apaga sozinho, todo dia de madrugada. Os dados da conta ficam enquanto ela existir.'],
      ['7. Seus direitos','Pela LGPD, você pode ver, corrigir, levar e apagar os seus dados. Corrigir: em Perfil. Levar: Perfil, botão Baixar meus dados (um arquivo com tudo o que guardamos sobre você). Apagar: Perfil, Excluir minha conta, que remove a conta, os pedidos, as conversas e o histórico. Outros pedidos: taskgo.adm@gmail.com.'],
      ['8. Segurança','A senha é guardada só em forma cifrada. O e-mail é confirmado por código no cadastro. A sessão encerra quando a senha muda.']
    ]],
    cancelamento:['Política de Cancelamento',[
      ['1. Antes de alguém aceitar','O cliente cancela de graça, a qualquer momento.'],
      ['2. Depois do aceite, antes da chegada','O cancelamento continua gratuito para o cliente. Como nada é pago antes do serviço, não há valor a devolver.'],
      ['3. Depois que o profissional chegou','Se o atendimento não acontecer por recusa do cliente, fica uma taxa de compensação para o profissional de 20% do valor combinado, entre R$ 10 e R$ 50.'],
      ['4. Serviço feito e não pago','O cliente fica devendo o valor ao profissional e não faz novos pedidos até pagar. O profissional confirma o recebimento no pedido.'],
      ['5. Obras e serviço recorrente','Cancelar interrompe as próximas etapas ou vezes. O que já foi feito e aprovado é pago ao profissional; o que não começou não é cobrado.'],
      ['6. Problema com o serviço','O cliente pode denunciar no pedido. A equipe analisa o caso com o histórico, as fotos e a conversa e pode advertir, suspender ou banir a conta do profissional.']
    ]]
  };
  const d=docs[route.sub]||docs.termos;
  return folha('documentos','<h1 class="duo"><strong>'+d[0].toLowerCase()+'</strong></h1>'+
  '<p class="t2" style="margin:14px 0 26px;font-size:.88rem">Atualizado em novembro de 2026.</p>'+
  '<div class="filtros">'+[['termos','Termos'],['privacidade','Privacidade'],['cancelamento','Cancelamento']].map(x=>'<a class="pill '+(route.sub===x[0]||(!route.sub&&x[0]==='termos')?'pill-brand':'')+'" href="#/legal/'+x[0]+'">'+x[1]+'</a>').join('')+'</div>'+
  '<div class="lista-legal corpo" id="lista-legal">'+d[1].map(x=>'<section><h2>'+x[0]+'</h2><p>'+x[1]+'</p></section>').join('')+'</div>','cabeca');
}
