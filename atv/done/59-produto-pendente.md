# 59 — Fazer o que falta de produto (fases 2, 3, 4) + identidade grátis

Pedido: "Faça o que falta de produto" + pergunta sobre automatizar identidade (quer algo grátis).
Teste em celular real: já feito pelo usuário, OK.

## Descobertas (o resumo estava desatualizado — muita coisa já pronta)
- Web push (fase 4, itens 10/15): JÁ PRONTO front+back (AvisosController, AvisosExternosService, WebPushCripto, js/sw.js, avisos-conta.js). Nada a fazer.
- Moderação (fase 3): desbanir ✓, histórico p/ admin ✓, filas denúncia/recurso separadas ✓. Falta só item 17.

## Feito
- [x] Fase 3 item 17: /api/moderacao/eu devolve "historico" (aviso/suspensão/banimento/desbanimento/recurso);
      dashSituacaoConta ganhou a seção "O que aconteceu na sua conta" (historicoConta).
- [x] Fase 2 item 9: Perfil (#/painel = dashPerfil) e Configurações (#/painel/config = dashConfig) separados;
      abasPainel liga as duas; router mantém sub='config'; pageDash ramifica.
- [x] Fase 2 item 8: menuConta esconde a tela atual (Minha área em #/app, Perfil em #/painel) e inclui Configurações.
- [x] Identidade grátis (escolha do usuário: validar dígito + bloquear repetido):
      AuthController.cpfValido/cpfDigitos/cpfParaSalvar, flag taskgo.cadastro.validar-cpf (default true,
      FALSE nos testes), UsuarioRepository.findByCpf, validação no registrar e atualizarPerfil (cliente/pro).
      Front: checagem de CPF no cadastro (entrar.js) e CPF formatado no Perfil.

## Identidade automática (resposta ao usuário)
- Verificação de identidade 100% automática no Brasil (prova de vida + base oficial da Receita) = paga e exige CNPJ (Serpro Datavalid, Unico/Acesso, idwall, CAF). Fora do "grátis por enquanto".
- Grátis agora: validar dígito verificador do CPF no cadastro/verificação e bloquear CPF repetido; manter documento+selfie com aprovação humana. (Avaliar implementar se o usuário quiser.)
