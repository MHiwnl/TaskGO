# Rumo ao site completo

**Situação:** concluída
**Pedido do usuário:** fazer os itens abaixo e, depois, listar o que ainda falta para o site ficar completo.

## Segurança e conta
- [x] Confirmação de e-mail no cadastro (ninguém usa um e-mail que não é seu, inclusive o do administrador).
- [x] Entrar com Google (só Google; o Apple sai).
- [x] Verificação de identidade: documento e selfie revisados pelo administrador (sem serviço pago).
- [x] Autenticação em duas etapas por aplicativo autenticador (grátis).
- [x] Ver e encerrar as sessões ativas da conta.

## LGPD e moderação
- [x] Política de privacidade revisada.
- [x] Baixar os próprios dados.
- [x] Prazo de guarda do log e das conversas (com limpeza automática).
- [x] Histórico da moderação com data (avisos, banimentos, cancelamentos) e no Log.
- [x] Denunciar um usuário.
- [x] Limpar as contas de teste (@teste.test) do banco local.

## Atendimento
- [x] Filtrar oportunidades pela distância e pelo raio de atendimento.
- [x] Rota e previsão de chegada.
- [x] Agenda de verdade: bloquear horários e aceitar pedido agendado.
- [x] Desempenho do profissional com dados reais.
- [x] Fotos no pedido e na conversa.
- [x] Notificações por e-mail e push do navegador; SMS pronto para ligar a um serviço, mas desligado.
- [x] Emergência: conta de suporte e tela própria; o botão abre um ticket que chega ao suporte em tempo real e com urgência.

## Administração
- [x] Página Equipe: o administrador cria contas de suporte (convite por e-mail para criar a senha).
- [x] Exportar listas e log em CSV.
- [x] Abrir o detalhe de um pedido ou de uma conta a partir das listas.
- [x] Números calculados no banco.

## Visual
- [x] Faixa azul do cliente e do profissional sem repetir nome e tipo nas abas internas.

## Depois
- Mandar na conversa a lista do que falta (não é tarefa).

## Fechamento (17/11/2026)
- Conferido no código: Google (Apple removido; "apple-touch-icon" é só o ícone do iPhone), verificação doc+selfie, oportunidades por raio/distância, rota e chegada (OSRM), agenda com bloqueios e aceite que respeita a agenda, fotos no pedido e na conversa, números do admin no banco, e-mail e push — já estavam feitos em sessões posteriores.
- Feito agora: SMS pronto e desligado (SmsService: desligado | log | http; só avisos de pedido; preferência em Configurações > Avisos, visível só com o canal ligado). SmsServiceTest (5). Backend 141/0; front 26/0.
- Lista do que falta: enviada no chat.
