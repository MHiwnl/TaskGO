# 42 — Funções que faltam (para quem usa, administração e qualidade)

**Pedido do usuário:** fazer "Para quem usa" inteiro e "Administração e qualidade". Pagamento real fica para depois.

## Para quem usa
- [x] 1. Oportunidades filtradas pela distância e pelo raio de atendimento do profissional
- [x] 2. Avisos fora do site: e-mail e push do navegador
- [x] 3. Fotos no pedido e na conversa
- [x] 4. Agenda de verdade: bloquear horários e aceitar pedido agendado sem conflito
- [x] 5. Entrar com Google (precisa do ID do cliente OAuth do usuário)
- [x] 6. Verificação de identidade: documento e selfie revisados pelo administrador, selo no perfil

## Administração e qualidade
- [x] 12. Números do painel do administrador calculados no banco
- [x] 13. Testes automáticos do site

Testado: 118 testes do backend, 23 do site (npm test), 48 checagens pela API e telas conferidas no navegador.
Entrar com Google precisa do GOOGLE_CLIENT_ID do usuário para aparecer.
