[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot 'unimed-tools-frontend'
$backendDir = Join-Path $projectRoot 'unimed-tools-backend'
$frontendBuild = Join-Path $frontendDir 'dist\unimed-tools-frontend\browser'
$xamppDir = 'C:\xampp'
$frontendDestination = Join-Path $xamppDir 'htdocs\unimed-tools'
$runtimeDir = Join-Path $env:LOCALAPPDATA 'UnimedTools'
$backendLog = Join-Path $runtimeDir 'backend.log'
$backendErrorLog = Join-Path $runtimeDir 'backend-error.log'
$backendPidFile = Join-Path $runtimeDir 'backend.pid'
$backendRuntimeJar = Join-Path $runtimeDir 'unimed-tools-backend.jar'
$localFrontendUrl = 'http://localhost/unimed-tools/'
$lanFrontendUrl = 'http://192.168.3.242/unimed-tools/'

function Write-Step([string]$message) {
  Write-Host "`n==> $message" -ForegroundColor Cyan
}

function Invoke-Checked([string]$command, [string[]]$arguments) {
  & $command @arguments
  if ($LASTEXITCODE -ne 0) {
    throw "O comando '$command $($arguments -join ' ')' terminou com codigo $LASTEXITCODE."
  }
}

function Get-ConfiguredValue([string]$name) {
  $value = [Environment]::GetEnvironmentVariable($name, 'Process')
  if ([string]::IsNullOrWhiteSpace($value)) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
  }
  if ([string]::IsNullOrWhiteSpace($value)) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Machine')
  }
  return $value
}

function Test-Jdk21([string]$directory) {
  if ([string]::IsNullOrWhiteSpace($directory)) { return $false }
  foreach ($file in @('bin\java.exe', 'bin\javac.exe', 'release')) {
    if (-not (Test-Path -LiteralPath (Join-Path $directory $file) -PathType Leaf)) {
      return $false
    }
  }
  return [bool](Select-String -LiteralPath (Join-Path $directory 'release') `
    -Pattern '^JAVA_VERSION="21(?:\.|"|\+|-)' -Quiet)
}

function Get-Jdk21 {
  $configuredJavaHome = Get-ConfiguredValue 'JAVA_HOME'
  if (-not [string]::IsNullOrWhiteSpace($configuredJavaHome)) {
    if (-not (Test-Jdk21 $configuredJavaHome)) {
      throw 'JAVA_HOME deve apontar para a pasta de um JDK 21, sem incluir bin ou java.exe.'
    }
    return $configuredJavaHome
  }

  $compiler = Get-Command 'javac.exe' -ErrorAction SilentlyContinue
  if ($compiler) {
    $compilerHome = Split-Path -Parent (Split-Path -Parent $compiler.Source)
    if (Test-Jdk21 $compilerHome) { return $compilerHome }
  }

  # O javapath da Oracle pode priorizar um JRE 8 mesmo com o JDK 21 instalado.
  foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'Zulu')) {
    $vendorDir = Join-Path $env:ProgramFiles $vendor
    if (Test-Path -LiteralPath $vendorDir -PathType Container) {
      foreach ($candidate in (Get-ChildItem -LiteralPath $vendorDir -Directory | Sort-Object Name -Descending)) {
        if (Test-Jdk21 $candidate.FullName) { return $candidate.FullName }
      }
    }
  }
  throw 'JDK 21 nao encontrado. Instale-o ou configure JAVA_HOME nas variaveis do usuario.'
}

function Test-LocalPort([int]$port) {
  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $connection = $client.ConnectAsync('127.0.0.1', $port)
    return $connection.Wait(800) -and $client.Connected
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Wait-LocalPort([int]$port, [int]$seconds, [string]$serviceName) {
  $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
  do {
    if (Test-LocalPort $port) {
      Write-Host "$serviceName disponivel na porta $port." -ForegroundColor Green
      return
    }
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)

  throw "$serviceName nao respondeu na porta $port dentro de $seconds segundos."
}

function Stop-XamppService(
  [string]$processName,
  [string]$stopScript,
  [string]$serviceName
) {
  $running = @(Get-Process -Name $processName -ErrorAction SilentlyContinue)
  if (-not $running.Count) { return }

  Write-Host "Encerrando $serviceName anterior..." -ForegroundColor Yellow
  $scriptPath = Join-Path $xamppDir $stopScript
  if (Test-Path -LiteralPath $scriptPath -PathType Leaf) {
    Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', ('"' + $scriptPath + '"')) -WorkingDirectory $xamppDir -WindowStyle Hidden -Wait | Out-Null
  }

  $deadline = [DateTime]::UtcNow.AddSeconds(20)
  do {
    $stillRunning = @(Get-Process -Name $processName -ErrorAction SilentlyContinue)
    if (-not $stillRunning.Count) { return }
    Start-Sleep -Milliseconds 400
  } while ([DateTime]::UtcNow -lt $deadline)

  Write-Host "$serviceName nao encerrou pelo script; finalizando os processos restantes..." -ForegroundColor DarkYellow
  Get-Process -Name $processName -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction Stop
}
function Start-XamppService(
  [string]$processName,
  [string]$startScript,
  [int]$port,
  [string]$serviceName
) {
  if (Get-Process -Name $processName -ErrorAction SilentlyContinue) {
    Write-Host "$serviceName ja esta em execucao." -ForegroundColor DarkGreen
    Wait-LocalPort $port 30 $serviceName
    return
  }

  $scriptPath = Join-Path $xamppDir $startScript
  if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw "Nao foi encontrado o iniciador do $serviceName em '$scriptPath'."
  }

  Start-Process `
    -FilePath $env:ComSpec `
    -ArgumentList @('/c', "`"$scriptPath`"") `
    -WorkingDirectory $xamppDir `
    -WindowStyle Hidden | Out-Null
  Wait-LocalPort $port 30 $serviceName
}

function Stop-UnimedBackend {
  $targetDirectory = (Join-Path $backendDir 'target') + '\'
  $processes = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object {
      if ($_.CommandLine -match '(?:^|\s)-jar\s+(?:"([^"]+)"|(\S+))') {
        $jarPath = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
        $jarPath -eq $backendRuntimeJar -or (
          $jarPath.StartsWith($targetDirectory, [StringComparison]::OrdinalIgnoreCase) -and
          (Split-Path -Leaf $jarPath) -like 'unimed-tools-*.jar'
        )
      }
    }

  foreach ($process in $processes) {
    Write-Host "Encerrando backend anterior (PID $($process.ProcessId))..." -ForegroundColor Yellow
    Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
    Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
  }
}

function Wait-UnimedBackend([System.Diagnostics.Process]$backendProcess) {
  $deadline = [DateTime]::UtcNow.AddSeconds(60)
  do {
    if ($backendProcess.HasExited) {
      throw "O backend encerrou com codigo $($backendProcess.ExitCode). Consulte os logs em '$runtimeDir'."
    }
    $healthy = $false
    try {
      $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/health' `
        -UseBasicParsing -TimeoutSec 2 -MaximumRedirection 0
      $healthy = $response.StatusCode -eq 200 -and $response.Content.Trim() -eq 'ok'
    } catch {
      # A porta pode abrir antes de o Spring concluir a inicializacao.
    }
    if ($healthy -and -not $backendProcess.HasExited) {
      Write-Host 'Backend Unimed Tools respondeu /health com sucesso.' -ForegroundColor Green
      return
    }
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)
  throw "O backend nao respondeu /health dentro de 60 segundos. Consulte os logs em '$runtimeDir'."
}

try {
  Write-Step 'Validando requisitos e configuracoes'
  foreach ($command in @('npm.cmd', 'mvn.cmd')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
      throw "O comando '$command' nao foi encontrado no PATH."
    }
  }
  $env:JAVA_HOME = Get-Jdk21
  $javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
  # Maven e o JAR devem usar o mesmo JDK, apenas no ambiente desta execucao.
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  Write-Host "JDK 21 selecionado: $env:JAVA_HOME" -ForegroundColor DarkGreen
  Invoke-Checked $javaPath @('-version')
  Invoke-Checked 'mvn.cmd' @('--version')
  if (-not (Test-Path -LiteralPath $frontendDir -PathType Container) -or
      -not (Test-Path -LiteralPath $backendDir -PathType Container)) {
    throw 'As pastas do frontend e do backend nao foram encontradas ao lado do iniciador.'
  }

  $mfaKey = Get-ConfiguredValue 'AUTH_MFA_ENCRYPTION_KEY'
  $sguKey = Get-ConfiguredValue 'SGU_API_KEY'
  if ([string]::IsNullOrWhiteSpace($mfaKey)) {
    throw "Configure AUTH_MFA_ENCRYPTION_KEY nas variaveis de ambiente do usuario antes de iniciar."
  }
  if ([string]::IsNullOrWhiteSpace($sguKey)) {
    throw "Configure SGU_API_KEY nas variaveis de ambiente do usuario antes de iniciar."
  }

  New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null

  Write-Step 'Iniciando Apache e MariaDB do XAMPP'
  Start-XamppService 'httpd' 'apache_start.bat' 80 'Apache'
  Start-XamppService 'mysqld' 'mysql_start.bat' 3306 'MariaDB'

  Write-Step 'Testando e gerando o frontend para a rede local'
  Push-Location $frontendDir
  try {
    Invoke-Checked 'npm.cmd' @('test', '--', '--watch=false')
    Invoke-Checked 'npm.cmd' @('run', 'build:lan')
  } finally {
    Pop-Location
  }

  if (-not (Test-Path -LiteralPath $frontendBuild -PathType Container)) {
    throw "O build do frontend nao foi encontrado em '$frontendBuild'."
  }
  Write-Step 'Testando e compilando o backend'
  Push-Location $backendDir
  try {
    Invoke-Checked 'mvn.cmd' @('clean', 'package')
  } finally {
    Pop-Location
  }

  $backendJar = Get-ChildItem -LiteralPath (Join-Path $backendDir 'target') -Filter 'unimed-tools-*.jar' -File |
    Where-Object { $_.Name -notmatch '\.original$' } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
  if ($null -eq $backendJar) {
    throw "O pacote do backend nao foi encontrado em '$(Join-Path $backendDir 'target')'."
  }

  $env:DB_USERNAME = Get-ConfiguredValue 'DB_USERNAME'
  if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { $env:DB_USERNAME = 'root' }
  $configuredDbPassword = Get-ConfiguredValue 'DB_PASSWORD'
  $env:DB_PASSWORD = if ($null -eq $configuredDbPassword) { '' } else { $configuredDbPassword }
  $env:AUTH_MFA_ENCRYPTION_KEY = $mfaKey
  $env:SERVER_ADDRESS = '127.0.0.1'
  $env:SGU_API_KEY = $sguKey
  $configuredHeaders = Get-ConfiguredValue 'SGU_API_KEY_HEADERS'
  $env:SGU_API_KEY_HEADERS = if ([string]::IsNullOrWhiteSpace($configuredHeaders)) {
    'apikey'
  } else {
    $configuredHeaders
  }

  Write-Step 'Publicando os arquivos e reiniciando o backend'
  Stop-UnimedBackend
  if (Test-LocalPort 8080) {
    throw 'A porta 8080 esta ocupada por outro processo. Libere a porta antes de iniciar.'
  }
  # O Windows bloqueia o JAR em execucao. A copia fora de target permite
  # compilar a proxima versao antes de encerrar o backend atual.
  Copy-Item -LiteralPath $backendJar.FullName -Destination $backendRuntimeJar -Force
  New-Item -ItemType Directory -Path $frontendDestination -Force | Out-Null
  Copy-Item -Path (Join-Path $frontendBuild '*') -Destination $frontendDestination -Recurse -Force
  Write-Host "Frontend publicado em $frontendDestination." -ForegroundColor Green

  $backendProcess = Start-Process `
    -FilePath $javaPath `
    -ArgumentList @('-jar', "`"$backendRuntimeJar`"", '--spring.profiles.active=local', '--server.port=8080') `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $backendLog `
    -RedirectStandardError $backendErrorLog `
    -WindowStyle Hidden `
    -PassThru
  Set-Content -LiteralPath $backendPidFile -Value $backendProcess.Id -Encoding ascii
  Wait-UnimedBackend $backendProcess

  Write-Step 'Unimed Tools atualizada e iniciada'
  Write-Host 'Aplicacao: http://localhost/unimed-tools/' -ForegroundColor Green
  Write-Host "Backend: PID $($backendProcess.Id) - logs em $runtimeDir" -ForegroundColor Green
  exit 0
} catch {
  Write-Host "`nERRO: $($_.Exception.Message)" -ForegroundColor Red
  Write-Host "Logs do backend, quando disponiveis: $runtimeDir" -ForegroundColor DarkYellow
  exit 1
}
