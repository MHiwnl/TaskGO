# 69 — Correções da auditoria dos 5 revisores

Pedido: "Vc vai, tratar primeiro, urgente, alta e depois media"

## Urgente
- [x] 1. Confirmar e-mail não abre sessão de conta já confirmada
- [x] 2. Cadastro ignora id do corpo
- [x] 3. Id de pedido: formato validado (sobrescrita já era barrada)
- [x] 4. Endereço/ponto do cliente mascarados até a confirmação (PedidoRespostaAdvice)
- [x] 5. Nota só pela avaliação (PUT e criar não gravam rating)
- [x] 6. Logout/sessão vencida/login limpam caches (chave Pix, pendências, suporte, obra, polls)

## Alta
- [x] 7. Asaas: cartão em análise/sem resposta fica pendente e é conferido; pagar com trava; cancelamento só com confirmação
- [x] 8. Transferência do admin serializada, VERIFICAR no timeout, bloqueio até resolver
- [x] 9. Cancelamento que falha não marca CANCELADA (Asaas e Pix)
- [x] 10. Imagem: dimensões antes de decodificar
- [x] 11. Limite: 2FA por conta, código de e-mail, código de chegada (linha travada)
- [x] 12. Categoria conferida em pedido direto e proposta
- [x] 13. Aprovação de etapa de obra atômica
- [x] 14. Admin resolve resgate e transferência em VERIFICAR

## Média
- [x] Exclusão de conta: anonimiza pedidos do outro lado, apaga cartões/carteira, recusa com dinheiro em aberto
- [x] E-mail único no banco (V28, aplicada em produção)
- [x] CPF único no banco: não feito, as contas 2 e 3 são de teste (decisão do usuário)
- [x] Troca de senha no perfil removida (só /api/auth/senha)
- [x] Relatório do admin: período por pagaEm
- [x] publicar.ps1 com --profile servidor; .gitignore libera só .github/workflows; guia de implantação
- [x] Backup: dump vazio corrigido; cópia diária cifrada (7-Zip) no OneDrive, senha em Documentos\SenhabackupTaskgo; agendamento instalado
- [x] Jobs com uma transação por item (obra, recorrente, cartões, Pix do plano)
