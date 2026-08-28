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

function Start-XamppService(
  [string]$processName,
  [string]$startScript,
  [int]$port,
  [string]$serviceName
) {
  if (Get-Process -Name $processName -ErrorAction SilentlyContinue) {
    Write-Host "$serviceName ja esta em execucao." -ForegroundColor DarkGreen
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
  $processes = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object {
      $_.CommandLine -and
      $_.CommandLine -match 'unimed-tools-[^\s\"]+\.jar'
    }

  foreach ($process in $processes) {
    Write-Host "Encerrando backend anterior (PID $($process.ProcessId))..." -ForegroundColor Yellow
    Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
    Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
  }
}

try {
  Write-Step 'Validando requisitos e configuracoes'
  foreach ($command in @('npm', 'mvn', 'java')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
      throw "O comando '$command' nao foi encontrado no PATH."
    }
  }
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
    Invoke-Checked 'npm' @('test', '--', '--watch=false')
    Invoke-Checked 'npm' @('run', 'build:lan')
  } finally {
    Pop-Location
  }

  if (-not (Test-Path -LiteralPath $frontendBuild -PathType Container)) {
    throw "O build do frontend nao foi encontrado em '$frontendBuild'."
  }
  New-Item -ItemType Directory -Path $frontendDestination -Force | Out-Null
  Copy-Item -Path (Join-Path $frontendBuild '*') -Destination $frontendDestination -Recurse -Force
  Write-Host "Frontend publicado em $frontendDestination." -ForegroundColor Green

  Write-Step 'Compilando e reiniciando o backend'
  Stop-UnimedBackend
  Push-Location $backendDir
  try {
    Invoke-Checked 'mvn' @('clean', 'package')
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

  $javaPath = (Get-Command 'java').Source
  $backendProcess = Start-Process `
    -FilePath $javaPath `
    -ArgumentList @('-jar', "`"$($backendJar.FullName)`"", '--spring.profiles.active=local') `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $backendLog `
    -RedirectStandardError $backendErrorLog `
    -WindowStyle Hidden `
    -PassThru
  Set-Content -LiteralPath $backendPidFile -Value $backendProcess.Id -Encoding ascii
  Wait-LocalPort 8080 60 'Backend Unimed Tools'

  Write-Step 'Unimed Tools atualizada e iniciada'
  Write-Host 'Aplicacao: http://localhost/unimed-tools/' -ForegroundColor Green
  Write-Host "Backend: PID $($backendProcess.Id) - logs em $runtimeDir" -ForegroundColor Green
  exit 0
} catch {
  Write-Host "`nERRO: $($_.Exception.Message)" -ForegroundColor Red
  Write-Host "Logs do backend, quando disponiveis: $runtimeDir" -ForegroundColor DarkYellow
  exit 1
}
