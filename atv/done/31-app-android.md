# App Android local (Capacitor)

**Situação:** concluído: app rodando no celular do usuário (Xiaomi Redmi Note 13, Android 15).
**Pedido do usuário:** criar um app Android do projeto para testar localmente no Android Studio, sem mudar nada do site, da API ou do Docker. Pasta própria na raiz.

## Fazer
- [x] Pasta `mobile/` com Capacitor 8.6 (Android), separada do site; `mobile/.gitignore` ignora node_modules.
- [x] O app abre o site do Docker pelo emulador (`http://10.0.2.2:3000`), sem mudar código do site. Página de aviso em `mobile/www` se o site não responder. Login testado por simulação: o servidor aceita a origem 10.0.2.2.
- [x] Projeto `mobile/android` gerado (`npx cap add android`). A primeira compilação fica com o Android Studio.
- [x] Instruções para rodar no emulador passadas ao usuário.
- [x] Rodando no celular pelo USB. O emulador (Android 37) não inicia neste PC (i7-3770): trava ou fecha com erro.

## Depois (não agora)
- Celular físico na mesma rede, servidor na internet com HTTPS, ícone e tela de abertura, plugins (GPS, push, câmera).

## Como ficou
- O app abre `http://localhost:3000`; o `adb reverse tcp:3000 tcp:3000` liga essa porta do celular ao Docker do computador.
- `mobile/rodar-no-celular.ps1`: liga a porta, compila, instala e abre o app (refazer sempre que reconectar o cabo).
- `MainActivity.java`: margens para a barra de status e a de gestos (Android 15 desenha até a borda) e fundo preto atrás delas; o site não mudou.
- Rodando em localhost, o modo demonstração também funciona no app.
