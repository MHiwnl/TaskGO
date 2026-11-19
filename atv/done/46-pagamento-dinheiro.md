# 46 — Pagamento em dinheiro

Pedido do usuário:
- Pagamento direto: ao final do serviço, o cliente entrega o valor em espécie ao trabalhador.
- Troco e troco online: sem troco em espécie, o trabalhador usa o troco online; a diferença vai para a carteira TaskGO do cliente (só se o trabalhador tiver saldo para cobrir).
- Alteração de forma de pagamento: o cliente pode trocar a forma uma vez durante o atendimento (dinheiro → cartão ou carteira TaskGO, e o contrário).
- Ganhos do trabalhador: o dinheiro fica todo com ele; a taxa da TaskGO é descontada do saldo ou cobrada no balanço semanal.
- Calote: o trabalhador cancela e pede taxa de cancelamento; serviço não pago vira pendência do cliente, que fica bloqueado de pedir até pagar com cartão ou carteira TaskGO.

## Passos
- [x] Servidor: pendências (V18), carteira do cliente como forma de pagamento, pedido em dinheiro sai sem pagar antes
- [x] Servidor: registrar dinheiro recebido, troco online, taxa devida e desconto automático; balanço semanal
- [x] Servidor: trocar forma uma vez; cliente não pagou (pendência + taxa de cancelamento); bloqueio do cliente; pagar pendência
- [x] Site: escolher dinheiro ou carteira no Contratar; trocar forma no pedido; receber em dinheiro e troco no lado de quem atende
- [x] Site: pendências e taxas na Carteira; aviso de bloqueio no Contratar
- [x] Testes (backend, ponta a ponta, navegador), docs
