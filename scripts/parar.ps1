# Para banco, API e site do TaskGO. Os dados do banco ficam guardados.
# Uso, na raiz do projeto:  .\scripts\parar.ps1
Set-Location (Split-Path $PSScriptRoot -Parent)
docker compose stop
Write-Host 'TaskGO parado. Para subir de novo: .\scripts\iniciar.ps1' -ForegroundColor Green
