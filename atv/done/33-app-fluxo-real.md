# App nativo: visual de app, propostas, GPS, código, moderação do chat

**Situação:** concluído; visual refeito com base no site (pedido do usuário após rejeitar o azulejo).
**Pedido do usuário (09/11/2026):**
- Visual diferente do site: mais interativo, coisas destacadas, animado, com estrelas e pontuação. Sem logo e sem nome dentro do app.
- Atendimento do profissional em telas separadas, sem etapas e sem o tipo de serviço:
  1. Rota: GPS indo até o endereço do cliente, dados do cliente, opção "Abrir no Waze"; ao ir para o Waze, um botão para voltar quando chegar.
  2. Só confirma que chegou se o GPS estiver perto do endereço.
  3. Confirmação: campo do código (o cliente vê o código no app dele).
  4. Espera: "pode começar o trabalho".
  5. Depois de concluir, o profissional fica disponível de novo.
- Botão de emergência grande. Cancelar pequeno e difícil de tocar sem querer; o profissional pode cancelar a qualquer hora, com taxa conforme os termos.
- Valor: o profissional vê o valor com a taxa e quanto a TaskGO tira; o cliente vê o valor cheio.
- Propostas em tempo real nas oportunidades: os dois lados podem aceitar, recusar, cancelar ou mandar outra proposta.
- Profissional pode ficar offline. Pedido urgente chega em tempo real.
- Conversa só enquanto cliente e profissional estão conectados; a aba Conversas vira histórico.
- Chat: número, valor ou contato (telefone, CPF, por extenso) gera aviso. 1º aviso, 2º punição, 3º banimento, com recurso.
- Nota (estrelas) do cliente para o profissional antes de aceitar, e do profissional para o cliente.

**Decisões minhas (a confirmar com o usuário):**
- Taxa da TaskGO: 12% (a mesma da demonstração do site).
- Cancelamento (proposta dos termos): sem taxa antes do aceite; 10% depois do aceite; 20% depois da chegada.
- Punição do 2º aviso: 24 horas sem receber pedidos ou propostas.
- Botão "voltar ao TaskGO" durante o Waze: uma notificação com o botão "Cheguei ao local".
- Mapa desenhado no próprio app (sem internet) com o deslocamento simulado; o Waze abre o endereço de verdade.

## Fazer
- [x] Modelo de dados: propostas, valores, taxa, notas, avisos.
- [x] Componentes novos (cartões, notas, urgente, emergência, valores).
- [x] Mapa com rota e deslocamento; Waze; notificação de volta.
- [x] Profissional: online/offline, oportunidades em tempo real, propostas, rota, confirmação, espera, conclusão.
- [x] Cliente: pedido com oferta, propostas em tempo real, acompanhamento no mapa, código, espera, avaliação.
- [x] Chat com moderação (3 avisos) e recurso; aba Conversas como histórico.
- [x] Instalar e testar no celular.

## Visual (09/11/2026)
- Tentativa com o mundo "Azulejo do Recife Antigo" (sorteio do Impeccable) rejeitada pelo usuário: "faça baseado no site".
- Final: mundo do site (DESIGN.md) no app: preto puro, azul #2B59FF, Spline Sans Mono em duas vozes, linhas finas, cantos retos, áreas azuis com grão, seta em círculo, aba ativa em bloco azul.
- Tema no Perfil: Escuro (padrão), Claro, Do sistema; guardado no aparelho; barras do sistema e mapa acompanham.
- Contrato de direção em .impeccable/surfaces/app-android.md, atualizado para o mundo do site.
