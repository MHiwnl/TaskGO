# Backend que falta (sem pagamento, só site)

**Situação:** concluída.
**Pedido do usuário:** construir o backend que falta, em fases, sem perguntar entre fases. Pagamento e tudo ligado a ele fica por último (o usuário decide). App Android fica para depois.

## Fase 1: base
- [x] Contratar envia o pedido de verdade (sem pagamento), com valor oferecido e urgência. Passos: serviço, detalhes, valor, confirmação.
- [x] Código de chegada gerado no servidor, visível só para o cliente e conferido pelo servidor ao iniciar o serviço.
- [x] E-mail (SMTP): Gmail configurado no .env e no compose; falta o usuário colocar SMTP_USERNAME, SMTP_PASSWORD (senha de app) e SMTP_FROM.

## Fase 2: regras do app
- [x] Propostas e contrapropostas (enviar, aceitar, recusar, retirar), com a nota do profissional.
- [x] Pedido urgente (destaque e primeiro da lista em Oportunidades).
- [x] Profissional online/offline (offline não vê nem aceita pedidos).
- [x] Nota do cliente visível antes do aceite.

## Fase 3: moderação e segurança
- [x] Filtro do chat (números, valores, contatos), com o aviso dentro da conversa.
- [x] Avisos, suspensão de 24 h, banimento e recurso (página Situação da conta).
- [x] Administrador: aba Moderação (recursos e banimentos). E-mail definido: taskgo.adm@gmail.com (TASKGO_ADMIN_EMAILS no .env).
- [x] Conversa só durante o atendimento; depois fica para consulta.
- [x] Chegada validada pelo GPS: o cliente marca o ponto no mapa ao contratar; "Cheguei" só vale com posição recente, precisão até 100 m e a até 150 m do ponto.

## Fase 4: tempo real
- [x] Oportunidades, propostas, pedidos, mensagens e moderação ao vivo (SSE em /api/eventos).

## Testado
- 82 testes do backend passando.
- Administrador no navegador: aprovar recurso retira o aviso.
- GPS: sem posição, longe (1112 m) e perto, contra o servidor real.
- Fluxo real no navegador: pedido urgente, proposta de outra sessão aparecendo sem recarregar, aceite, código errado barrado, chat barrando número.
- Demonstração: cliente e profissional, desktop e celular.

## Fora do escopo agora
- Pagamento, split, carteira, taxa e cobrança de cancelamento.
- App Android.

## Conferência (17/11/2026)
- SMTP preenchido no .env (dev) e no .env.producao; e-mail de confirmação e recuperação funcionando no ar.
- Pagamento e carteira foram feitos depois (modelo direto + taxa pelo Asaas). App Android segue parado por decisão do usuário.
