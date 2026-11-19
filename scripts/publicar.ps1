# Manda o TaskGO para o servidor da Oracle e sobe (ou atualiza) o site.
# (Hoje o site está no ar pelo Cloudflare Tunnel via .\scripts\iniciar-producao.ps1; este é o caminho antigo da Oracle.)
# Uso, na raiz do projeto:
#   .\scripts\publicar.ps1 -Servidor 123.45.67.89 -Chave C:\Users\Mendes\.ssh\oracle.key            (atualiza o site)
#   .\scripts\publicar.ps1 -Servidor 123.45.67.89 -Chave C:\Users\Mendes\.ssh\oracle.key -Preparar  (1ª vez: instala o Docker)
#   .\scripts\publicar.ps1 -Servidor 123.45.67.89 -Chave C:\Users\Mendes\.ssh\oracle.key -EnviarEnv (manda o .env.producao)
# Se o PowerShell bloquear scripts:  powershell -ExecutionPolicy Bypass -File .\scripts\publicar.ps1 ...
param(
    [Parameter(Mandatory = $true)][string]$Servidor,
    [Parameter(Mandatory = $true)][string]$Chave,
    [string]$Usuario = 'ubuntu',
    [switch]$Preparar,
    [switch]$EnviarEnv
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$destino = "$Usuario@$Servidor"
$ssh = @('-i', $Chave, '-o', 'StrictHostKeyChecking=accept-new')
function Escrever($texto) { Write-Host "-> $texto" -ForegroundColor Cyan }
function Rodar($exe, $argumentos) { & $exe @argumentos; if ($LASTEXITCODE -ne 0) { throw "$exe falhou (código $LASTEXITCODE)." } }

if (-not (Test-Path $Chave)) { throw "Chave SSH não encontrada: $Chave" }

if ($Preparar) {
    Escrever 'Preparando o servidor (Docker e portas 80/443)...'
    Rodar scp ($ssh + @('docker/preparar-servidor.sh', "${destino}:~/preparar-servidor.sh"))
    Rodar ssh ($ssh + @($destino, 'sh ~/preparar-servidor.sh'))
    Escrever 'Servidor preparado. Agora rode com -EnviarEnv e depois sem opções.'
    return
}

if ($EnviarEnv) {
    if (-not (Test-Path .env.producao)) { throw 'Crie o arquivo .env.producao antes (veja docs/PUBLICAR-ORACLE.md).' }
    Escrever 'Enviando o .env.producao (vira ~/taskgo/.env no servidor)...'
    Rodar ssh ($ssh + @($destino, 'mkdir -p ~/taskgo'))
    Rodar scp ($ssh + @('.env.producao', "${destino}:~/taskgo/.env"))
    Rodar ssh ($ssh + @($destino, 'chmod 600 ~/taskgo/.env'))
    Escrever 'Pronto.'
    return
}

# Pacote só com o que o servidor precisa: site, backend (sem target), configuração do Docker.
$pacote = Join-Path $env:TEMP 'taskgo-publicar.tgz'
if (Test-Path $pacote) { Remove-Item $pacote -Force }
Escrever 'Empacotando o projeto...'
Rodar tar @('-czf', $pacote, '--exclude=backend/target', 'index.html', 'css', 'js', 'assets', 'docker', 'backend', 'docker-compose.prod.yml')
Escrever 'Enviando para o servidor...'
Rodar scp ($ssh + @($pacote, "${destino}:/tmp/taskgo.tgz"))
Escrever 'Atualizando e subindo o site (a primeira vez demora uns minutos)...'
Rodar ssh ($ssh + @($destino, 'set -e; mkdir -p ~/taskgo/backups; cd ~/taskgo; test -f .env || { echo "Falta o .env: rode com -EnviarEnv"; exit 1; }; tar -xzf /tmp/taskgo.tgz -C ~/taskgo; rm /tmp/taskgo.tgz; docker compose -f docker-compose.prod.yml up -d --build; docker compose -f docker-compose.prod.yml ps'))
Remove-Item $pacote -Force
Escrever 'Publicado. Confira o site no seu domínio em 1 ou 2 minutos.'
