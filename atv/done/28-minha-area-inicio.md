# Minha área mais rica: aba Início e topo azul

**Situação:** concluído
**Pedido do usuário:** a "Minha área" está muito simples. Caminho escolhido: nova aba Início + topo azul.
**Usar:** a skill `impeccable`.

## Fazer
- [x] Aba "Início" como primeira aba (cliente e profissional), aberta depois do login.
- [x] Faixa azul com grão no topo: saudação, o que está acontecendo agora e o próximo passo.
- [x] Resumo em linhas: pedido em andamento, pendências (mensagens, notificações, avaliações) e atalhos.
- [x] Só dados reais da conta; estados vazios honestos quando não houver nada.
- [x] Tema claro, celular, movimento reduzido.

## Feito
- `js/painel/inicio.js` (novo): faixa azul (completa e curta) e a aba Início.
- `js/painel/area.js`: aba Início primeiro; faixa entre o topo e as abas; abas presas ao rolar.
- `js/pages/entrar.js`: depois do login vai para `/app/inicio`.
- `css/painel.css` e `css/motion.css`: estilos e entrada do título.
- PRODUCT.md e DESIGN.md atualizados.
