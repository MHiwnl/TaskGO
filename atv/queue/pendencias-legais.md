# Pendências legais (depois da revisão dos termos de 22/11/2026)

Ordem: 1 depende de você; 2, 3 e 4 são código e podem andar em paralelo (arquivos diferentes).
Cada correção leva teste de regressão (regra do CLAUDE.md, seção 7).

## 1. Identificação de quem responde pelo site (Decreto 7.962/2013, art. 2º, I e II)
Precisa de decisão sua, não de código.
- [ ] Decidir: CPF de pessoa física ou CNPJ. Recomendo abrir um MEI (gratuito): o CPF ficaria público no rodapé de todas as páginas, e o MEI resolve isso e já serve para emitir nota da taxa.
- [ ] Me passar: nome/razão social, CPF ou CNPJ, endereço físico (pode ser comercial ou de coworking).
- [ ] Colocar no rodapé (js/core/layout.js:121) e na seção 13 dos Termos (js/pages/legal.js). Atualizar os ?v=.

## 2. Trava de idade: só maiores de 18 (Código Civil, art. 5º) — FEITO 22/11/2026 (MaioridadeTest; também corrigiu o erro 500 no cadastro pelo Google)
Duas camadas: declaração no cadastro e conferência obrigatória na verificação.
- [x] Cadastro: caixa obrigatória "Tenho 18 anos ou mais" em js/pages/entrar.js e js/pages/entrar-google.js; o backend (AuthController, cadastro e GoogleRequest) recusa sem ela.
- [x] Verificação: ao aprovar (VerificacaoController:104), a equipe digita a data de nascimento lida no documento; o backend grava em Usuario.birth e recusa a aprovação se a idade for menor que 18, com motivo "Menor de idade" e encerramento da conta.
- [x] Tela da equipe (js/painel/verificacao.js): campo de data na aprovação.
- [x] Testes: cadastro sem a caixa → 400; aprovação com nascimento < 18 anos → recusada e conta encerrada; com 18 completos no dia → aprovada (caso de borda).
- Banco: Usuario.birth já existe (texto); sem migração.

## 3. Devolução do Plano Pro em 7 dias (CDC, art. 49) — FEITO 23/11/2026 (PlanoDesistenciaTest, V31+V32; revisado 2x)
Hoje só por e-mail, o que não basta: o Decreto 7.962/2013, art. 5º, §1º, exige que a desistência possa ser feita pela mesma ferramenta usada para contratar. Passa a ter botão.
- [x] Backend: PlanoService ganha desistir(usuarioId): só se a assinatura tiver menos de 7 dias desde criadaEm e não tiver sido estornada; estorna a cobrança paga pelo AsaasCliente.estornar (já existe) ou pelo caminho simulado; encerra o plano na hora.
- [x] Banco: nova migração V31 com assinatura.estornada_em (nunca editar V30 ou anteriores).
- [x] Front (js/painel/plano-pro.js): botão "Desistir e receber o valor de volta" visível só nos 7 primeiros dias, com o prazo final escrito.
- [x] Atualizar Termos 6 e Cancelamento 7: trocar "pelo e-mail" pelo botão.
- [x] Testes: desistir no 7º dia → estorna; no 8º → 400; segunda desistência → 400 (não estorna duas vezes); usuário sem plano → 404; outro usuário não consegue desistir do plano alheio.
- Toca dinheiro: revisor obrigatório antes de terminar.

## 4. Moderação e sigilo da conversa (Marco Civil, art. 8º, I; LGPD, art. 20) — FEITO 23/11/2026 (ConversaSigiloTest 14 testes, V33; revisado 3x)
O filtro automático pode continuar (está informado e serve para prevenir fraude). O risco está em pessoas lendo conversas sem motivo.
- [x] Levantar quem, na equipe, consegue abrir a conversa de um pedido e em quais telas (AdminController, ModeracaoService, js/painel/admin.js, moderacao.js).
- [x] Restringir: a equipe só abre a conversa de pedido com denúncia ou recurso aberto; fora disso, 403.
- [x] Registrar cada abertura no histórico (quem, quando, qual pedido), para responder a pedidos da ANPD.
- [x] Banimento automático do 3º aviso: conferir que o recurso desse caso cai para revisão humana e que a decisão final fica registrada como "revisada por pessoa".
- [x] Testes: admin abre conversa sem denúncia → 403; com denúncia → 200 e registro gravado.
- Toca dados pessoais e permissão: revisor obrigatório.

## Antes de implementar
- 3 e 4 mudam backend + banco + front: passar pelo arquiteto antes (regra do ~/.claude/CLAUDE.md).
- Depois de cada item: npm test, testes do backend no Docker, rebuild da API.

## ONDE PAROU (22/11/2026, PC desligado no meio)
- Item 3 (desistência Plano Pro): implementado + revisado. Correções do revisor JÁ ESCRITAS mas NÃO TESTADAS: V32__assinatura_contratacao_legada.sql (backfill pelo 1º pagamento), PlanoService (desistirAte = último instante do 7º dia; "já desistiu" → 409; RuntimeException no estorno vira "em andamento"; Pix pendente anterior à desistência não aparece; aviso de devolução tardia), 3 testes novos em PlanoDesistenciaTest. Próximo: rodar mvn test completo, rebuild da API (aplica V32), revisor rápido.
  - Pendente antes de dinheiro real: estorno sem transação/trava na cobrança (PagamentoService.devolverCobranca, Cobranca sem @Version) — afeta obras também.
  - Decisão do dono: pagamento feito na carência (status ativa, plano vencido) conta como contratação nova?
- Item 4 (sigilo da conversa): desenvolvedor INTERROMPIDO no meio. Ver git diff em ModeracaoService, AdminController, FotoPedidoService, ModeracaoController, repositórios, denuncias.js, moderacao.js e testes novos; conferir se compila, completar pelo plano do arquiteto e rodar testes. ?v= de denuncias.js/moderacao.js ainda não atualizados.
- Download das leis (pasta local leis-brasil, fora do repositório): interrompido; retomar com `git checkout main` na pasta (continua de onde parou).

## Atualização 23/11/2026
- Item 3 fechado. Decisões do dono: contas antigas contam do 1º pagamento (V32); pagamento na carência é renovação (não abre janela).
- Pendente antes de dinheiro real: estorno sem transação/trava (PagamentoService.devolverCobranca, Cobranca sem @Version) — afeta obras também.
- Item 4: registro de acesso não gravava (rota readOnly) — corrigido + teste. Falta: recurso guardar o pedido contestado (V33), em andamento.
- Teste de navegador (tests/navegador.test.cjs) falha às vezes (título vazio); investigar.

## Pendências técnicas que sobraram (antes de dinheiro real / produção)
- [x] Estorno seguro — FEITO 23/11/2026: reserva antes do Asaas (V35), conciliação por valor após 10 min, devolução fora da transação de quem chama (executor/job), pendências de obra/recorrente idempotentes, rede do job para cancelado+retido, lock_timeout 5s. PagamentoEstornoTest 20 testes + CobrancaEstornoRepositoryTest. Revisado 4x. Falta: teste em sandbox do Asaas.
- [x] Teste de integração (H2) provando que abrir a conversa grava os registros "acesso" — ConversaAcessoIntegrationTest (falha com readOnly, passa sem).
- [x] Corrida no recorrer — V34 uk_recursos_um_em_analise + 409 (segundoRecursoSimultaneoRecebe409).
- [x] tests/navegador.test.cjs instável — ir() espera o conteúdo até 20 s (a frio passava de 6,8 s).
