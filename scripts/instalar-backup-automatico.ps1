# Instala o agendamento que copia o backup do TaskGO para o OneDrive todo dia, sozinho.
# Uso: .\scripts\instalar-backup-automatico.ps1            (instala/atualiza)
#      .\scripts\instalar-backup-automatico.ps1 -Remover   (desinstala)
param([switch]$Remover)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$nome = 'TaskGO Backup OneDrive'

if ($Remover) {
    schtasks /Delete /TN "$nome" /F
    Write-Host "Agendamento removido." -ForegroundColor Yellow
    return
}

$script = Join-Path $PSScriptRoot 'backup-offsite.ps1'
if (-not (Test-Path $script)) { throw "Falta backup-offsite.ps1 nesta pasta." }
$acao = "powershell -ExecutionPolicy Bypass -NoProfile -File `"$script`""
# Todo dia as 13:17 (horario de pouco movimento; o PC precisa estar ligado).
schtasks /Create /TN "$nome" /TR "$acao" /SC DAILY /ST 13:17 /F
Write-Host "Pronto: o backup vai para o OneDrive todo dia as 13:17 (com o PC ligado)." -ForegroundColor Green
Write-Host "Para testar agora:  .\scripts\backup-offsite.ps1"
