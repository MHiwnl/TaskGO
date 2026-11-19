# 56 — Separar o local dos detalhes no Contratar

Pedido: no Agendar, o endereço e o mapa ficam numa tela; "Quando" e o que vem abaixo dele ficam em outra.

## Plano
- [x] Agendar: passos servico → local → detalhes → valor → confirmar
  - Local: endereço salvo ou novo, mapa com "Ajustar localização"
  - Detalhes: Quando (ainda hoje / dia e hora), "O serviço leva mais de um dia?", descrição com exemplo do serviço, fotos
- [x] Obra: mesma divisão (local → começo e descrição da obra)
- [x] Validação por passo: endereço e ponto no mapa ao sair do Local; data e hora ao sair dos Detalhes
- [x] Conferir: "Mudar" do endereço volta ao Local; o de Quando e Descrição, aos Detalhes
- [x] Reaproveitar pedido cancelado continua abrindo na tela de conferir
- [x] Testes no navegador (agendar, obra, recorrente) e PRODUCT.md

## Feito
- PASSOS agendar/obra: servico → local → detalhes → valor → confirmar; nova tela wLocalAgendar (endereço + mapa); wDetalhes sem endereço/mapa.
- Validação por passo em wGo (local: endereço e ponto no mapa; detalhes: dia e hora); botões com wGo(W.step+1).
- Conferir: "Mudar" do Endereço → Local (2); Quando/Descrição/Começo/O que fazer → Detalhes (3); valor/repetição → Valor (4).
- Reaproveitar continua no conferir (PASSOS[modo].length).
- Teste novo em tests/navegador.test.cjs (agendar, obra, recorrente, celular); teste antigo ajustado (valor = passo 4); ir() espera a carga a frio. npm test: 26/26.
- PRODUCT.md atualizado (fluxo do Agendar, Perfil/Configurações, CPF).
