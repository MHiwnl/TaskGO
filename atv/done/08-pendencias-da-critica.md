# Pendências da crítica depois do redesign

**Situação:** concluída. Único ponto em aberto: estrutura própria para cada página interna (registrado na atividade 30).
**Nota:** 22/36 (antes: 18/36). Relatório em `.impeccable/critique/`.
**Usar:** a skill `impeccable` em toda mudança visual.

## P1
- [x] **O visual ainda pode parecer gerado:** duas vozes só no topo e em uma segunda seção (função `titulo()` para as outras); catálogo de serviços em três grupos, sem filtros. Fica em aberto dar uma estrutura própria a cada página interna.
- [x] **Pedido principal:** o contato diz quem lê (a equipe fundadora) e o prazo: até 2 dias úteis, pelo e-mail informado.

## P2
- [x] **Pedido de exemplo:** o celular simulado vai do aceite à avaliação: valor "a combinar", código de chegada (2217), conclusão e avaliação do cliente.
- [x] **Rota do cliente:** "Preciso deste serviço" abre o contato com assunto "Preciso de um serviço" e a categoria na mensagem; a frase da faixa de preço saiu de Como funciona e do Início; o catálogo não tem mais filtros.

## Outros pontos encontrados
- [x] Textos legais: reescritos no condicional na atividade 30 ("Ainda não existem", "Proposta: ..."), sem data falsa de atualização.
- [x] **CPF obrigatório:** continua obrigatório (decisão do usuário); a dica do campo explica que a verificação de identidade vai usar o CPF e que hoje ele só fica guardado.
- [x] Erro 500 em `/api/profissionais`: o Spring Data devolvia o resumo de avaliações como [[média, total]] e o controller lia [média, total]. Corrigido em `ProfissionalController.resumo()`, com teste novo; o 401 que aparecia nas páginas públicas era esse 500 caindo em /error.
- [x] Vídeo sem uso (gerado por IA) apagado a pedido do usuário; o vídeo do topo continua.
