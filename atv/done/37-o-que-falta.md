# O que falta (lista nova, 09/11/2026)

**Situação:** concluída (só o app Android segue parado por decisão do usuário).
**Pedido do usuário:** depois dos filtros e do Log, criar outra lista do que falta.

## 1. Depende de você
- [x] **Senha de app do Gmail** no `.env` (SMTP_USERNAME, SMTP_PASSWORD, SMTP_FROM). Sem ela, a recuperação de senha não envia e-mail.
- [x] **Criar a conta do administrador** (`taskgo.adm@gmail.com`) antes de o site ir ao ar.
- [x] **Pagamento:** escolher o provedor (você disse que decide depois).

## 2. Pagamento (parado até a sua decisão)
- [x] Checkout (PIX e cartão) e pedido pago pela plataforma.
- [x] Divisão do valor (taxa da TaskGO e repasse ao profissional), carteira e saque reais.
- [x] Taxa de cancelamento do profissional e do cliente.
- [x] Recibo do serviço.
- [-] Plano Plus: descartado. O site passou a ser sem mensalidade (só taxa de quem atende).

## 3. Importante antes de abrir ao público
- [x] **Confirmar o e-mail no cadastro** (hoje qualquer pessoa cria conta com qualquer e-mail, inclusive o do administrador).
- [x] **Publicar o site**: domínio, HTTPS, perfil de produção, backup automático do banco.
- [x] **LGPD**: política de privacidade revisada, baixar os próprios dados, prazo de guarda dos logs e das conversas.
- [x] **Registrar o histórico da moderação**: hoje os avisos são só um contador; a data de cada aviso, de cada banimento e de cada cancelamento não fica guardada, por isso não aparecem no Log.
- [x] **Limpar os dados de teste** do banco local (contas `@teste.test`).

## 4. Funções que ainda não existem
- [x] Distância e raio de atendimento: o raio é salvo, mas as oportunidades não são filtradas pela distância.
- [x] Rota e previsão de chegada (hoje só a última posição do GPS).
- [x] Agenda de verdade: bloquear horários e aceitar pedido agendado (hoje mostra só pedidos com data).
- [x] Desempenho do profissional com dados reais (hoje só na demonstração).
- [x] Fotos no pedido e na conversa (mostrar o problema).
- [x] Denunciar um usuário (hoje só existe o filtro automático da conversa).
- [x] Notificações por e-mail, push e SMS.
- [x] Entrar com Google e Apple.
- [x] Verificação de identidade (documento ou rosto) e autenticação em duas etapas.
- [x] Ver e encerrar as sessões ativas da conta.
- [x] Equipe de segurança para o botão de emergência (hoje ele liga para 190 e 192).

## 5. Administração (próximos passos)
- [x] Exportar listas e log em planilha (CSV).
- [x] Abrir o detalhe de um pedido ou de uma conta a partir das listas.
- [x] Números agregados no banco quando o volume crescer (hoje calcula em memória).

## 5b. Visual
- [x] Faixa azul do cliente e do profissional: nas abas internas repete nome e tipo da conta (no administrador já foi trocada pelo que pede atenção).

## 6. App Android (parado por decisão sua)
- [-] (parado por decisão do usuário) Hoje é só demonstração, com dados fictícios. Falta ligar ao servidor (login, pedidos, propostas, conversa, GPS) e publicar na Play Store.

## 7. Qualidade
- [x] Testes automáticos do site (hoje só o backend tem: 87 testes).
- [x] Testar o fluxo inteiro em celulares reais (Android e iPhone) no navegador.

## Conferência (17/11/2026)
- Gmail (SMTP): preenchido no dev e na produção. Conta do administrador taskgo.adm@gmail.com existe no banco de produção (tipo administrador).
- Pagamento: provedor escolhido (Asaas). Modelo direto: Pix com QR, cartão na maquininha ou dinheiro; taxa da TaskGO cobrada pelo Asaas (automática no cartão autorizado); carteira do profissional; Financeiro do admin com transferência por Pix; taxa de cancelamento; recibos.
- Antes de abrir: confirmação de e-mail, site publicado (taskgohq.com.br, HTTPS pelo Cloudflare, perfil de produção, backup diário no PC), LGPD, histórico da moderação, contas de teste limpas.
- Funções: raio/distância, rota e chegada, agenda com bloqueios, desempenho real, fotos no pedido e na conversa, denúncia, e-mail + push + SMS (pronto, desligado), Google (Apple removido por decisão), verificação de identidade + 2FA, sessões ativas, equipe de suporte para emergência.
- Administração: CSV, detalhe de pedido/conta, números no banco. Visual da faixa azul feito.
- Qualidade: 26 testes do site (Chrome sem janela) e 141 do backend; celulares reais testados pelo usuário.
- Sobra: app Android (parado). Plano Plus descartado.
