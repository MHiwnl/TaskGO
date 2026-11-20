# Copia o backup mais recente do banco do TaskGO para FORA deste PC (OneDrive, que sobe para a nuvem sozinho),
# CRIPTOGRAFADO (7-Zip, AES-256, nomes dos arquivos também cifrados). O dump tem CPF, telefone e hashes de senha:
# na nuvem só vai o .7z, nunca o .sql.gz aberto.
# Uso: .\scripts\backup-offsite.ps1            (copia o backup de hoje)
#      .\scripts\backup-offsite.ps1 -Destino "D:\outra-pasta"   (outro destino, ex.: pen drive)
# A senha fica FORA do projeto (padrão: Documentos\SenhabackupTaskgo\senha-backup.txt). Sem ela não há como abrir os backups:
# guarde uma cópia da senha em outro lugar seguro (gerenciador de senhas).
# Para restaurar: 7z x taskgo-AAAA.sql.gz.7z  (pede a senha)  ->  depois o .sql.gz volta ao procedimento normal.
# Roda sozinho todo dia se você instalar o agendamento (veja scripts\instalar-backup-automatico.ps1).
param(
    [string]$Destino = "$env:USERPROFILE\OneDrive\TaskGO-Backups",
    [string]$ArquivoSenha = "$env:USERPROFILE\Documents\SenhabackupTaskgo\senha-backup.txt"
)
$ErrorActionPreference = 'Stop'
# Os scripts ficam em .\scripts; a raiz do projeto é a pasta de cima.
$raiz = Split-Path $PSScriptRoot -Parent
Set-Location $raiz
$origem = Join-Path $raiz 'backups'
if (-not (Test-Path $origem)) { throw "Pasta 'backups' nao encontrada. Rode o site de producao primeiro (iniciar-producao.ps1)." }

$sete = @("$env:ProgramFiles\7-Zip\7z.exe", "${env:ProgramFiles(x86)}\7-Zip\7z.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $sete) { throw "7-Zip nao encontrado. Instale com: winget install --id 7zip.7zip -e" }
if (-not (Test-Path $ArquivoSenha)) { throw "Arquivo de senha nao encontrado: $ArquivoSenha" }
$senha = (Get-Content $ArquivoSenha -Raw).Trim()
if ($senha.Length -lt 20) { throw "A senha do backup precisa ter 20 caracteres ou mais." }

$novo = Get-ChildItem $origem -Filter 'taskgo-*.sql.gz' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $novo) { throw "Nenhum backup para copiar ainda." }
# Backup vazio (dump que falhou) nao vai para a nuvem: avisa em vez de esconder o problema.
if ($novo.Length -lt 1024) { throw "O backup mais recente ($($novo.Name)) esta vazio ($($novo.Length) bytes). Veja: docker logs taskgo-prod-backup-1" }

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$saida = Join-Path $Destino ($novo.Name + '.7z')
if (Test-Path $saida) { Remove-Item $saida -Force }
& $sete a -t7z -mx=0 -mhe=on "-p$senha" $saida $novo.FullName | Out-Null
if ($LASTEXITCODE -ne 0) { throw "7-Zip falhou ($LASTEXITCODE): backup NAO copiado." }
# Confere que abre com a senha antes de dar como feito.
& $sete t "-p$senha" $saida | Out-Null
if ($LASTEXITCODE -ne 0) { Remove-Item $saida -Force; throw "O arquivo cifrado nao passou no teste: backup NAO copiado." }

# Mantem os ultimos 30 backups fora do PC; apaga os mais antigos.
Get-ChildItem $Destino -Filter 'taskgo-*.sql.gz.7z' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 30 | Remove-Item -Force -ErrorAction SilentlyContinue

$log = Join-Path $Destino 'ultima-copia.txt'
"Ultima copia: $(Get-Date -Format 'yyyy-MM-dd HH:mm') -> $(Split-Path $saida -Leaf) ($([math]::Round((Get-Item $saida).Length/1KB,1)) KB, cifrado)" | Out-File $log -Encoding utf8
Write-Host "Backup cifrado copiado para fora do PC: $saida" -ForegroundColor Green
