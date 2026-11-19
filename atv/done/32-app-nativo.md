# App nativo da TaskGO (Android, Kotlin + Jetpack Compose)

**Situação:** concluído (testado no celular do usuário).
**Pedido do usuário:** um aplicativo da TaskGO separado do site (como o 99: site apresenta, app é o produto), só para visualizar, com o que já temos.
**Decisões do usuário:** nativo (Kotlin + Jetpack Compose); dados fictícios, sem servidor; um app com os dois perfis (cliente e profissional).
**Usar:** a skill `impeccable` (identidade do DESIGN.md: preto, azul #2B59FF, Spline Sans Mono, cantos retos, pedido em linhas).

## Fazer
- [x] Projeto Android novo em pasta própria na raiz (`app-android/`), sem mexer no site.
- [x] Entrada: escolher perfil (preciso de um serviço / sou profissional), selo de protótipo.
- [x] Cliente: início com categorias, pedir serviço, acompanhar pedido (etapas, código de chegada), conversa, perfil.
- [x] Profissional: oportunidades (aceitar/recusar, endereço só depois do aceite), atendimento (status, código, conclusão, avaliação), conversa, carteira "ainda não disponível", perfil.
- [x] Fontes e cores da marca; tema escuro.
- [x] Compilar e instalar no celular (entrada conferida por captura de tela).
- [x] Fluxos testados no celular, tocando pelo adb: cliente (pedido, código, conversa, avaliação) e profissional (oportunidade, aceite, código errado e certo, conclusão, avaliação recebida).
- [x] Ajustes do teste: voltar da conversa leva ao pedido; Início chama para avaliar; abas somem com o teclado; botão desativado mais apagado.

## Pendente de decisão
- A pasta `mobile/` (Capacitor abrindo o site) perde o sentido; apagar só se o usuário pedir.

## Notas técnicas
- Kotlin 2.2.21, Compose BOM 2025.08.00, AGP 8.13, minSdk 26.
- O Gradle precisa de Java 21: o Java 25 do Android Studio quebra o plugin do Kotlin. `gradle/gradle-daemon-jvm.properties` fixa 21; o JDK usado está em `~/.jdks/jbr-21.0.11`.
- `app-android/rodar-no-celular.ps1` compila, instala e abre.
