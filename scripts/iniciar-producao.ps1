# Coloca o TaskGO no ar pela internet, a partir deste computador (Cloudflare Tunnel).
# Uso, na raiz do projeto:  .\scripts\iniciar-producao.ps1          (sobe ou atualiza)
#                           .\scripts\iniciar-producao.ps1 -Parar   (tira do ar)
# Se o PowerShell bloquear scripts:  powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-producao.ps1
# O site de desenvolvimento (localhost:3000, scripts\iniciar.ps1) é outro: os dois podem rodar juntos.
param([switch]$Parar)
$ErrorActionPreference = 'Stop'
# Os scripts ficam em .\scripts; a raiz do projeto é a pasta de cima.
$raiz = Split-Path $PSScriptRoot -Parent
Set-Location $raiz
function Escrever($texto) { Write-Host "-> $texto" -ForegroundColor Cyan }
function DockerQuieto($argumentos) { cmd /c "docker $argumentos >nul 2>&1"; return ($LASTEXITCODE -eq 0) }
$compose = @('compose', '-p', 'taskgo-prod', '-f', 'docker-compose.prod.yml', '--env-file', '.env.producao', '--profile', 'tunel')

if (-not (Test-Path .env.producao)) { throw 'Falta o arquivo .env.producao (veja docs/PUBLICAR.md).' }
if (-not (DockerQuieto 'info')) { throw 'O Docker Desktop não está aberto. Abra-o e rode de novo.' }

if ($Parar) {
    Escrever 'Tirando o site do ar (os dados ficam guardados)...'
    & docker @($compose + @('down'))
    return
}

$token = (Get-Content .env.producao | Where-Object { $_ -match '^CLOUDFLARE_TUNNEL_TOKEN=.+' })
if (-not $token) { throw 'Cole o token do túnel do Cloudflare em CLOUDFLARE_TUNNEL_TOKEN= no .env.producao (veja docs/PUBLICAR.md).' }

# Versão nova nos endereços do site (?v=): o navegador baixa os arquivos novos em vez de usar a cópia guardada.
$versao = Get-Date -Format 'yyyyMMddHHmm'
$html = [IO.File]::ReadAllText((Join-Path $raiz 'index.html'))
$html = [regex]::Replace($html, '((?:src|href)="(?:js|css)/[^"?]+\.(?:js|css))(?:\?v=\d+)?"', ('$1?v=' + $versao + '"'))
[IO.File]::WriteAllText((Join-Path $raiz 'index.html'), $html, (New-Object System.Text.UTF8Encoding($false)))
Escrever "Versão do site: $versao"

Escrever 'Subindo banco, API, site, backup e o túnel (a primeira vez demora alguns minutos)...'
& docker build -t taskgo-api-prod ./backend
if ($LASTEXITCODE -ne 0) { throw 'Falhou ao construir a API.' }
& docker @($compose + @('up', '-d', '--no-build'))
if ($LASTEXITCODE -ne 0) { throw 'O Docker não conseguiu subir o site.' }
& docker @($compose + @('ps'))
$dominio = ((Get-Content .env.producao | Where-Object { $_ -match '^TASKGO_DOMINIO=' }) -replace '^TASKGO_DOMINIO=', '').Trim()
Escrever "No ar em https://$dominio (a API leva cerca de 1 minuto para ficar pronta)."
Escrever 'Deixe este computador ligado e sem suspender: o site fica no ar enquanto ele estiver ligado.'
