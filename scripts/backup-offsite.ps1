# Copia o backup mais recente do banco do TaskGO para FORA deste PC (OneDrive, que sobe para a nuvem sozinho).
# Uso: .\scripts\backup-offsite.ps1            (copia o backup de hoje)
#      .\scripts\backup-offsite.ps1 -Destino "D:\outra-pasta"   (outro destino, ex.: pen drive)
# Roda sozinho todo dia se você instalar o agendamento (veja scripts\instalar-backup-automatico.ps1).
param([string]$Destino = "$env:USERPROFILE\OneDrive\TaskGO-Backups")
$ErrorActionPreference = 'Stop'
# Os scripts ficam em .\scripts; a raiz do projeto é a pasta de cima.
$raiz = Split-Path $PSScriptRoot -Parent
Set-Location $raiz
$origem = Join-Path $raiz 'backups'
if (-not (Test-Path $origem)) { throw "Pasta 'backups' nao encontrada. Rode o site de producao primeiro (iniciar-producao.ps1)." }

$novo = Get-ChildItem $origem -Filter 'taskgo-*.sql.gz' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $novo) { throw "Nenhum backup para copiar ainda." }

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
Copy-Item $novo.FullName (Join-Path $Destino $novo.Name) -Force

# Mantem os ultimos 30 backups fora do PC; apaga os mais antigos.
Get-ChildItem $Destino -Filter 'taskgo-*.sql.gz' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 30 | Remove-Item -Force -ErrorAction SilentlyContinue

$log = Join-Path $Destino 'ultima-copia.txt'
"Ultima copia: $(Get-Date -Format 'yyyy-MM-dd HH:mm') -> $($novo.Name) ($([math]::Round($novo.Length/1KB,1)) KB)" | Out-File $log -Encoding utf8
Write-Host "Backup copiado para fora do PC: $Destino\$($novo.Name)" -ForegroundColor Green
