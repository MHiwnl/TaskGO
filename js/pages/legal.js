/* ---------- LEGAL ---------- */
/* O prazo escrito na política vem do servidor, então o texto acompanha a configuração. */
function preencherPrazos(){
  if(DEMO_MODE)return;
  apiJSON('/api/privacidade/prazos').then(p=>{const el=document.querySelector('[data-prazo]');if(!p||!el)return;
    el.textContent='Conversas de pedidos concluídos ou cancelados: '+p.conversasDias+' dias depois do fim. Notificações já lidas: '+p.notificacoesDias+' dias. Histórico da conta e registros do log: '+p.historicoDias+' dias.';}).catch(()=>{});
}
function pageLegal(){
  if(route.sub==='privacidade')setTimeout(preencherPrazos,50);
  const docs={
    termos:['Termos de Uso',[
      ['1. Objeto','A TaskGO é uma plataforma que conecta pessoas que precisam de serviços a profissionais autônomos que os oferecem. A TaskGO não executa os serviços: o contrato de prestação é entre cliente e profissional. Não há vínculo de emprego entre a TaskGO e os profissionais.'],
      ['2. Quem pode usar','Só maiores de 18 anos. No cadastro, você declara ter 18 anos ou mais; na verificação, a equipe confere a data de nascimento do documento. Conta de menor encontrada na verificação é banida.'],
      ['3. Cadastro e verificação','O usuário fornece informações verdadeiras e atualizadas. Para fazer pedidos ou receber oportunidades, a identidade é verificada pela equipe, com documento e selfie. Contas com dados falsos podem ser suspensas ou banidas.'],
      ['4. Pagamento do serviço','O cliente paga o valor combinado diretamente ao profissional, por Pix na chave dele ou em dinheiro, depois que o serviço é concluído. Em obras, cada etapa é paga depois de aprovada pelo cliente; no serviço recorrente, cada vez depois de realizada. A TaskGO não recebe nem guarda o valor do serviço.'],
      ['5. Taxa da plataforma','O profissional paga à TaskGO uma taxa sobre o valor recebido: 15% nos serviços até R$ 999,99; 12% de R$ 1.000 a R$ 2.999,99; nas obras, 12%, 10%, 8% ou 6%, conforme o valor total; 10% na ajuda rápida; e 15% na visita técnica. A taxa é paga pela Carteira, por Pix ou cartão, processados pelo Asaas. Taxas em aberto são lembradas e cobradas no balanço semanal. Cada profissional responde pelos próprios impostos sobre o que recebe.'],
      ['6. Plano Pro','Opcional, para profissionais: R$ 29,90 por mês. No cartão, renova sozinho todo mês até ser cancelado; no Pix, cada mês é pago à parte. Cancelar, em Plano Pro no painel, encerra a renovação: o plano vale até o fim do mês já pago. Em até 7 dias da contratação, você pode desistir e receber de volta todo o valor pago, pelo botão Desistir na tela Plano Pro (Código de Defesa do Consumidor, art. 49).'],
      ['7. Conduta','É proibido combinar contato ou pagamento fora da plataforma para fugir da taxa, ofender, ameaçar ou enganar a outra parte.'],
      ['8. Moderação','A conversa do pedido passa por um filtro automático que bloqueia contato e pagamento por fora e ofensas; o terceiro aviso bane a conta automaticamente. Denúncias são analisadas pela equipe, que pode advertir, suspender ou banir contas. Toda penalidade, inclusive as automáticas, pode ser revista por uma pessoa da equipe: envie um recurso pela conta.'],
      ['9. Conteúdo publicado','Cada usuário responde pelo que publica: mensagens, fotos, descrições e avaliações. A TaskGO remove conteúdo ilegal ou que viole estes termos e cumpre ordens judiciais de remoção. Imagens íntimas divulgadas sem autorização são retiradas assim que a pessoa atingida avisar (Marco Civil da Internet, art. 21).'],
      ['10. Responsabilidades','O profissional responde pela execução técnica do serviço e pelo material que fornecer. O cliente responde pelas informações do pedido e pelo pagamento combinado. A TaskGO responde pelo funcionamento da plataforma, pela verificação de identidade e pela moderação.'],
      ['11. Mudanças nestes termos','Mudanças são avisadas por e-mail e no painel antes de valer. A data da versão atual fica no topo desta página.'],
      ['12. Lei e foro','Valem as leis brasileiras. Quem é consumidor pode entrar na Justiça no foro do próprio domicílio.'],
      ['13. Contato','TaskGO, Recife/PE. E-mail: taskgo.adm@gmail.com.']
    ]],
    privacidade:['Política de Privacidade',[
      ['1. Quem cuida dos dados','A TaskGO, com sede em Recife/PE. Para qualquer assunto de privacidade, escreva para taskgo.adm@gmail.com.'],
      ['2. Dados que guardamos','Cadastro (nome, CPF, e-mail, telefone, cidade, foto e endereços salvos); pedidos, propostas e avaliações; mensagens trocadas na conversa de cada pedido; notificações; a última posição do profissional enquanto ele está a caminho; o histórico da conta (confirmação de e-mail, avisos, suspensões, banimentos, recursos, denúncias e cancelamentos); as fotos do documento e a selfie enviadas para a verificação de identidade, vistas só pela equipe; e a chave Pix de quem atende.'],
      ['3. Para que usamos','Para executar o serviço que você contratou (criar a conta, ligar cliente e profissional, mostrar o andamento do atendimento); para prevenir fraude e proteger os usuários (verificação de identidade, moderação da conversa contra combinações por fora e ofensas, histórico da conta); e para cumprir obrigações legais, como guardar os registros de acesso por pelo menos 6 meses (Marco Civil da Internet, art. 15). Não vendemos dados e não usamos para publicidade.'],
      ['4. Documento e selfie','A selfie é dado biométrico, um dado sensível. Ela e as fotos do documento servem só para confirmar a identidade no cadastro e prevenir fraude (LGPD, art. 11, II, g); são vistas apenas pela equipe e não são usadas para mais nada.'],
      ['5. Com quem compartilhamos','O endereço e o contato do cliente só aparecem para o profissional que aceitou o pedido. Os e-mails da TaskGO saem pelo Gmail (Google). O mapa usa o OpenStreetMap, que também recebe o endereço do pedido para posicionar o marcador, e a busca de CEP usa o ViaCEP, que recebe o CEP digitado. A chave Pix de quem atende aparece para o cliente do pedido na hora de pagar. Os pagamentos de taxas são processados pelo Asaas, que recebe só os dados necessários à cobrança. No Entrar com Google, o Google confirma o seu e-mail. O Google e o OpenStreetMap podem tratar esses dados em servidores fora do Brasil, sob as garantias da LGPD (art. 33).'],
      ['6. Localização','O profissional decide compartilhar a localização durante um atendimento a caminho; o envio para quando ele registra a chegada ou fecha a página. Só a última posição fica guardada, e ela é apagada quando o atendimento sai dessa etapa.'],
      ['7. Cookies','Só os necessários para manter a sessão aberta e proteger contra ataques. Nenhum de publicidade ou de análise de uso. Na tela Entrar com Google, o próprio Google pode usar os cookies dele.'],
      ['8. Por quanto tempo','<span data-prazo>Conversas de pedidos concluídos ou cancelados: 180 dias depois do fim. Notificações já lidas: 365 dias. Histórico da conta e registros do log: 730 dias.</span> Depois disso, o sistema apaga sozinho, todo dia de madrugada. Os dados da conta ficam enquanto ela existir.'],
      ['9. Seus direitos','Pela LGPD (art. 18), você pode ver, corrigir, levar e apagar os seus dados. Corrigir: em Perfil. Levar: Perfil, botão Baixar meus dados (um arquivo com tudo o que guardamos sobre você). Apagar: Perfil, Excluir minha conta, que remove a conta, os pedidos, as conversas e o histórico. Pelo e-mail, você também pode pedir a confirmação de que tratamos seus dados, saber com quem foram compartilhados, pedir o bloqueio ou a anonimização de dados desnecessários e pedir que uma pessoa reveja decisões automáticas, como bloqueios da moderação (art. 20). Se não for atendido, pode reclamar na ANPD (Autoridade Nacional de Proteção de Dados).'],
      ['10. Encarregado de dados','Pedidos sobre os seus dados e comunicações da ANPD vão para taskgo.adm@gmail.com, respondidos pelo encarregado da TaskGO.'],
      ['11. Menores de idade','A TaskGO não é para menores de 18 anos. Conta de menor encontrada na verificação é banida, e o responsável legal pode pedir a exclusão dos dados pelo e-mail taskgo.adm@gmail.com.'],
      ['12. Segurança','A senha é guardada só em forma cifrada. O e-mail é confirmado por código no cadastro. A sessão encerra quando a senha muda. Se houver um incidente de segurança com risco para você, avisamos você e a ANPD (LGPD, art. 48).']
    ]],
    cancelamento:['Política de Cancelamento',[
      ['1. Antes de alguém aceitar','O cliente cancela de graça, a qualquer momento.'],
      ['2. Depois do aceite, antes da chegada','O cancelamento continua gratuito para o cliente. Como nada é pago antes do serviço, não há valor a devolver.'],
      ['3. Depois que o profissional chegou','Se o atendimento não acontecer por recusa do cliente, fica uma taxa de compensação para o profissional de 20% do valor combinado, entre R$ 10 e R$ 50.'],
      ['4. Serviço feito e não pago','O cliente fica devendo o valor ao profissional e não faz novos pedidos até pagar. O profissional confirma o recebimento no pedido.'],
      ['5. Obras e serviço recorrente','Cancelar interrompe as próximas etapas ou vezes. O que já foi feito e aprovado é pago ao profissional; o que não começou não é cobrado.'],
      ['6. Problema com o serviço','O cliente pode denunciar no pedido. A equipe analisa o caso com o histórico, as fotos e a conversa e pode advertir, suspender ou banir a conta do profissional. Isso não tira os direitos do Código de Defesa do Consumidor frente ao profissional.'],
      ['7. Plano Pro','Cancelar encerra a renovação; o plano vale até o fim do mês já pago. Em até 7 dias da contratação, a desistência devolve todo o valor pago: peça pelo botão Desistir na tela Plano Pro.']
    ]]
  };
  const d=docs[route.sub]||docs.termos;
  return folha('documentos','<h1 class="duo"><strong>'+d[0].toLowerCase()+'</strong></h1>'+
  '<p class="t2" style="margin:14px 0 26px;font-size:.88rem">Atualizado em novembro de 2026.</p>'+
  '<div class="filtros">'+[['termos','Termos'],['privacidade','Privacidade'],['cancelamento','Cancelamento']].map(x=>'<a class="pill '+(route.sub===x[0]||(!route.sub&&x[0]==='termos')?'pill-brand':'')+'" href="#/legal/'+x[0]+'">'+x[1]+'</a>').join('')+'</div>'+
  '<div class="lista-legal corpo" id="lista-legal">'+d[1].map(x=>'<section><h2>'+x[0]+'</h2><p>'+x[1]+'</p></section>').join('')+'</div>','cabeca');
}
