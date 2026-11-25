# Organizar o código inteiro com /untangle-codebase

Escopo medido em 22/11/2026: ~22 mil linhas (Java 13,3 mil em 163 arquivos; JS 8,8 mil em 81 arquivos; CSS 2,3 mil).
Maiores pontos de entrada: ObraService (1077), PagamentoService (798), RecorrenteService, AuthController, DinheiroService; no front, contratar.js, entrar.js, painel/obra.js.

A skill só roda quando você digita `/untangle-codebase` (ela não dispara sozinha). Este plano adapta o procedimento dela às regras do TaskGo.

## Limites que o untangle NÃO pode quebrar (vêm do CLAUDE.md)
- Comportamento igual ao de hoje (exceto bugs que a reorganização corrigir, cada um com teste de regressão).
- Backend continua num pacote só (`com.taskgo.api`); nada de camadas ou módulos novos.
- Frontend continua JS puro, sem módulos nem build, carregado por `<script>` na ordem core → features → pages → painel → app. Arquivo novo = tag nova no index.html com `?v=`; arquivo alterado = `?v=` novo.
- Migrações aplicadas (V1–V30) nunca são editadas; mudança de esquema só em V31+. A meta é não precisar mudar o banco.
- Chamadas à API só por `apiJSON()`/fetch embrulhado; modo demo continua separado do modo real.
- Catálogo duplicado (catalogo.js ↔ CatalogoServicos.java) é proposital e testado: fica como está.
- Sessão, CSRF, identidade pela sessão e regras de dinheiro ficam idênticas.

## Fase 0 — Preparar (antes de rodar a skill)
- [ ] Você commita o que está pendente hoje (6 arquivos), para ter um ponto de volta limpo.
- [ ] Criar branch `untangle` (não trabalhar na main).
- [ ] Linha de base, guardada no log: `npm test` (27), testes do backend no Docker (46 classes), `tests/navegador.test.cjs` com o site no ar, sintaxe do JS.
- [ ] Rede de segurança extra onde falta teste e o código é grande: um teste de comportamento para os fluxos de obra, pagamento e recorrente antes de mexer neles.

## Fase 1 — Perguntas da skill (você escolhe)
- **Comentários:** remover os que só repetem o código. Recomendo **sim**.
- **Testes:** remover testes que não pegariam um bug real e corrigir os que falham sem bug. Recomendo **sim, mas por último** (como a skill manda), e sem tocar nos testes de dinheiro e segurança sem revisor.

## Fase 2 — Listar todos os problemas (nada muda no código)
- [ ] Gerar esqueleto (assinaturas sem corpo) e grafo de dependências: Java (quem chama quem entre Services/Controllers/Repositories) e JS (quem usa qual global e de qual arquivo, já que não há imports).
- [ ] Investigadores (Haiku) em paralelo, um por área: (a) dinheiro: Pagamento, Dinheiro, Carteira, Plano, Asaas; (b) pedidos e obra: Pedido, Obra, Recorrente, Agenda, ChamarAgora; (c) conta e segurança: Auth, sessão, Verificação, Moderação, Privacidade; (d) front core + features + app; (e) front pages + painel; (f) CSS.
- [ ] Cada um entrega a lista no formato da skill: ligações escondidas, ligações desnecessárias, sobras, casos especiais, com arquivo:linha.
- [ ] Tudo num log único: `docs/untangle/decisoes.md` (sobrevive à compactação do contexto).
- Observação: só há 5 commits, então o histórico do git ajuda pouco para saber o que muda junto. O grafo e a leitura substituem isso.

## Fase 3 — Redesenhar (arquiteto, Opus)
- [ ] Arquiteto lê o log e escreve as correções acima dos problemas que cada uma resolve, buscando a correção única por trás de vários problemas, dentro dos limites acima.
- [ ] **Você aprova o log antes da Fase 4.** Itens que mudem banco, dinheiro ou segurança vêm destacados.

## Fase 4 — Corrigir, em fatias
- [ ] Uma fatia por correção do log, na ordem do que menos muda para o que mais muda (core antes de painel; repositórios antes de serviços).
- [ ] Cada fatia: desenvolvedor (Haiku) implementa → testes front + back + rebuild da API → revisor. Fatias que tocam dinheiro, auth, permissão ou dados pessoais: revisor obrigatório e eu confiro as evidências.
- [ ] Nunca dois desenvolvedores nos mesmos arquivos ao mesmo tempo.
- [ ] Comentários (se escolhido) junto com cada fatia; testes (se escolhido) no fim.

## Fase 5 — Conferir e repetir
- [ ] Nova rodada de investigadores procurando os mesmos quatro tipos de problema; repetir até não sobrar nada que valha corrigir.
- [ ] Pronto quando: poucos casos especiais restantes; uma mudança típica cai num lugar só; nenhuma dependência aponta para algo mais instável; todos os testes da linha de base passam; conferência no navegador em modo real e demo (cliente e profissional).
- [ ] Relatório final no formato do CLAUDE.md, seção 14. Commit e PR só quando você pedir.

## Riscos
- É a maior mudança possível no projeto: muitos arquivos de uma vez. As fatias pequenas e o branch são o que permite voltar.
- Testes H2 não validam Postgres: depois de fatias que mexem em consultas, conferir no banco real (Docker).
- Custo alto de contexto: investigadores e desenvolvedor em Haiku; Opus só no redesenho e nas fatias de dinheiro e segurança.
