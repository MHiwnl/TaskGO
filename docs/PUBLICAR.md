# Publicar o TaskGO (https://taskgohq.com.br)

O site roda neste computador e o Cloudflare Tunnel publica na internet, com HTTPS e sem abrir portas no roteador.
Fica no ar enquanto o computador estiver ligado, com o Docker Desktop aberto.

## Uma vez só

1. **Cloudflare: adicionar o domínio**
   - cloudflare.com > *Add a domain* > `taskgohq.com.br` > plano **Free**.
   - O Cloudflare mostra **dois nameservers** (ex.: `ana.ns.cloudflare.com`).
2. **Registro.br: apontar para o Cloudflare**
   - registro.br > o domínio > **DNS** > *Alterar servidores DNS*.
   - Troque pelos dois nameservers do Cloudflare e salve.
   - Pode levar de minutos a algumas horas; o Cloudflare avisa por e-mail quando o domínio ficar ativo.
3. **Cloudflare: criar o túnel**
   - Zero Trust (menu da esquerda) > **Networks > Tunnels** > *Create a tunnel* > **Cloudflared** > nome `taskgo`.
   - Na tela de instalação, copie o **token**: o texto longo depois de `--token` no comando mostrado.
   - Cole no arquivo `.env.producao`, na linha `CLOUDFLARE_TUNNEL_TOKEN=` (sem aspas). Não mande o token por chat.
   - Em **Public Hostname**: domínio `taskgohq.com.br`, tipo **HTTP**, URL `web:80`. Repita com subdomínio `www`.
4. **Asaas: webhook** (depois que o site abrir)
   - Painel do Asaas > Integrações > Webhooks > novo.
   - URL: `https://taskgohq.com.br/api/pagamentos/webhook/asaas`
   - Token de autenticação: o valor de `ASAAS_WEBHOOK_TOKEN` do `.env.producao`.
   - Eventos de cobrança (pagamento confirmado, recebido, estornado, vencido).

## Ligar e desligar

```powershell
.\scripts\iniciar-producao.ps1          # sobe ou atualiza (use depois de cada mudança no site)
.\scripts\iniciar-producao.ps1 -Parar   # tira do ar; os dados ficam guardados
```

## Backup

O banco é copiado todo dia para a pasta `backups/` (os últimos 14 dias). Copie de vez em quando para fora do computador (Google Drive, pen drive).

## Para não sair do ar

- Windows: Configurações > Sistema > Energia > **Suspender: Nunca** (na tomada).
- Docker Desktop: Configurações > General > **Start Docker Desktop when you sign in**.
- Os contêineres voltam sozinhos quando o Docker abre.
