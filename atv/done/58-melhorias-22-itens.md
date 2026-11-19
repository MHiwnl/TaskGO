# 58 — Melhorias, correções e novas funções (lista de 22 itens)

Pedido do usuário: lista colada com 22 itens. Decisão: "adicionar saldo" na carteira fica de fora (sem CNPJ, dinheiro de cliente não pode ficar na conta do CPF). Cobrança automática da taxa entra, com cartão autorizado pelo profissional.

## Fases
- [x] 1. Correções de uso: aviso leva ao lugar certo (1), tela sem piscar (3), botão Cheguei (4), pedido concluído só deixa denunciar (11), fotos protegidas (12), denúncia sem duplicar (13), clicar na notificação abre algo (14)
- [x] 2. Telas: profissional a caminho só com tempo, Waze e Cheguei (2), avisos com visual novo (6 — confirmar.js, sem confirm nativo), pedido concluído simplificado (7), menu sem "Minha área" dentro dela (8), Perfil e Configurações separados (9)
- [x] 3. Moderação: desbanir (16), situação da conta com o motivo real (17 — historicoConta), filas de denúncias e recursos separadas (18)
- [x] 4. Tempo real (15 — EventosService/SSE) e notificações no celular e no computador (10 — web push pronto)
- [x] 5. Recibos (5), cartões salvos (19, sem adicionar saldo), cobrança automática da taxa (20)
- [~] 6. Segurança: CSP [x]. Backup fora do PC: scripts prontos (backup-offsite.ps1 + instalar-backup-automatico.ps1, destino OneDrive), AGUARDANDO autorização do usuário para executar (copia dado pessoal para a nuvem).
- [x] 7. Revisão geral e testes: 136 testes backend 0 falhas; site publicado e verificado no ar (API/health 200, headers, sw, JS novos). Relatório final entregue no chat.
