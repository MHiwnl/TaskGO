# Compila, instala e abre o app nativo da TaskGO no celular conectado pelo USB.
# Não precisa de Docker nem de internet: os dados do app são fictícios.
# Uso, dentro da pasta app-android:  .\rodar-no-celular.ps1
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw 'adb não encontrado. Instale o Android Studio (SDK do Android).' }

# O Gradle deste projeto precisa de Java 21 (o Java 25 do Android Studio não é lido pelo plugin do Kotlin).
$java21 = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '21' } | Select-Object -First 1
if (-not $java21) { throw 'Java 21 não encontrado em .jdks. No Android Studio: Settings > Build Tools > Gradle > Gradle JDK > Download JDK > versão 21.' }
$env:JAVA_HOME = $java21.FullName

$aparelhos = & $adb devices | Select-String "`tdevice$"
if (-not $aparelhos) { throw 'Nenhum aparelho pronto. Conecte o celular, ligue a depuração USB e aceite o pedido na tela.' }

Push-Location $PSScriptRoot
try { .\gradlew.bat installDebug --console=plain -q } finally { Pop-Location }

& $adb shell am force-stop br.com.taskgo.app
& $adb shell am start -n br.com.taskgo.app/.MainActivity | Out-Null
Write-Host 'App TaskGO aberto no aparelho.'
