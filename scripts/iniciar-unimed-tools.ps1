[CmdletBinding()]
param([switch]$PublishProduction)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot 'unimed-tools-frontend'
$backendDir = Join-Path $projectRoot 'unimed-tools-backend'
$frontendBuild = Join-Path $frontendDir 'dist\unimed-tools-frontend\browser'
$xamppDir = 'C:\xampp'
$productionFrontendDestination = Join-Path $xamppDir 'htdocs\unimed-tools'

$runtimeDir = Join-Path $env:LOCALAPPDATA 'UnimedTools'
$localRuntimeDir = Join-Path $runtimeDir 'local'
$productionRuntimeDir = Join-Path $runtimeDir 'production'
$localBackendJar = Join-Path $localRuntimeDir 'unimed-tools-backend-local.jar'
$productionBackendJar = Join-Path $productionRuntimeDir 'unimed-tools-backend-production.jar'
$legacyProductionBackendJar = Join-Path $runtimeDir 'unimed-tools-backend.jar'

$localBackendLog = Join-Path $localRuntimeDir 'backend.log'
$localBackendErrorLog = Join-Path $localRuntimeDir 'backend-error.log'
$localFrontendLog = Join-Path $localRuntimeDir 'frontend.log'
$localFrontendErrorLog = Join-Path $localRuntimeDir 'frontend-error.log'
$localFrontendPidFile = Join-Path $localRuntimeDir 'frontend.pid'
$productionBackendLog = Join-Path $productionRuntimeDir 'backend.log'
$productionBackendErrorLog = Join-Path $productionRuntimeDir 'backend-error.log'

$localBackendPort = 8081
$productionBackendPort = 8080
$localFrontendPort = 4200
$localFrontendUrl = "http://localhost:$localFrontendPort/"
$productionFrontendUrl = 'http://192.168.3.242/unimed-tools/'

$script:javaPath = $null
$script:localBackendProcess = $null
$script:localFrontendProcess = $null

function Write-Step([string]$message) {
  Write-Host ''
  Write-Host "==> $message" -ForegroundColor Cyan
}

function Write-Info([string]$message) {
  Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $message" -ForegroundColor DarkCyan
}

function Invoke-Checked([string]$command, [string[]]$arguments) {
  & $command @arguments
  if ($LASTEXITCODE -ne 0) {
    throw "O comando '$command $($arguments -join ' ')' terminou com codigo $LASTEXITCODE."
  }
}

function Get-ConfiguredValue([string]$name) {
  $value = [Environment]::GetEnvironmentVariable($name, 'Process')
  if ([string]::IsNullOrWhiteSpace($value)) { $value = [Environment]::GetEnvironmentVariable($name, 'User') }
  if ([string]::IsNullOrWhiteSpace($value)) { $value = [Environment]::GetEnvironmentVariable($name, 'Machine') }
  return $value
}

function Test-Jdk21([string]$directory) {
  if ([string]::IsNullOrWhiteSpace($directory)) { return $false }
  foreach ($file in @('bin\java.exe', 'bin\javac.exe', 'release')) {
    if (-not (Test-Path -LiteralPath (Join-Path $directory $file) -PathType Leaf)) { return $false }
  }
  return [bool](Select-String -LiteralPath (Join-Path $directory 'release') -Pattern '^JAVA_VERSION="21(?:\.|"|\+|-)' -Quiet)
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

function Wait-HttpUrl([string]$url, [int]$seconds, [string]$serviceName) {
  $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
  do {
    try {
      $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
        Write-Host "$serviceName respondeu em $url" -ForegroundColor Green
        return
      }
    } catch {}
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)
  throw "$serviceName nao respondeu em '$url' dentro de $seconds segundos."
}

function Stop-XamppService([string]$processName, [string]$stopScript, [string]$serviceName) {
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

  Get-Process -Name $processName -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction Stop
}

function Start-XamppService([string]$processName, [string]$startScript, [int]$port, [string]$serviceName) {
  if (Get-Process -Name $processName -ErrorAction SilentlyContinue) {
    Wait-LocalPort $port 30 $serviceName
    return
  }

  $scriptPath = Join-Path $xamppDir $startScript
  if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw "Nao foi encontrado o iniciador do $serviceName em '$scriptPath'."
  }

  Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', ('"' + $scriptPath + '"')) -WorkingDirectory $xamppDir -WindowStyle Hidden | Out-Null
  Wait-LocalPort $port 30 $serviceName
}

function Remove-LegacyAuthFiles {
  $legacyFiles = @(
    'src\main\java\com\unimedlorena\tools\auth\TotpService.java',
    'src\main\java\com\unimedlorena\tools\auth\CriptografiaMfaService.java',
    'src\test\java\com\unimedlorena\tools\auth\TotpServiceTest.java',
    'src\test\java\com\unimedlorena\tools\auth\CriptografiaMfaServiceTest.java'
  )

  foreach ($relativePath in $legacyFiles) {
    $fullPath = Join-Path $backendDir $relativePath
    if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
      Remove-Item -LiteralPath $fullPath -Force
      Write-Host "Arquivo legado removido: $relativePath" -ForegroundColor DarkYellow
    }
  }
}

function Initialize-Environment {
  Write-Step 'Validando requisitos e configuracoes'
  foreach ($command in @('npm.cmd', 'mvn.cmd')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
      throw "O comando '$command' nao foi encontrado no PATH."
    }
  }

  $env:JAVA_HOME = Get-Jdk21
  $script:javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"

  if (-not (Test-Path -LiteralPath $frontendDir -PathType Container) -or -not (Test-Path -LiteralPath $backendDir -PathType Container)) {
    throw 'As pastas do frontend e do backend nao foram encontradas ao lado do iniciador.'
  }

  $sguKey = Get-ConfiguredValue 'SGU_API_KEY'
  if ([string]::IsNullOrWhiteSpace($sguKey)) {
    throw 'Configure SGU_API_KEY nas variaveis de ambiente do usuario antes de iniciar.'
  }

  $env:DB_USERNAME = Get-ConfiguredValue 'DB_USERNAME'
  if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { $env:DB_USERNAME = 'root' }
  $configuredDbPassword = Get-ConfiguredValue 'DB_PASSWORD'
  $env:DB_PASSWORD = if ($null -eq $configuredDbPassword) { '' } else { $configuredDbPassword }
  $env:SERVER_ADDRESS = '127.0.0.1'
  $env:SGU_API_KEY = $sguKey

  $configuredHeaders = Get-ConfiguredValue 'SGU_API_KEY_HEADERS'
  $env:SGU_API_KEY_HEADERS = if ([string]::IsNullOrWhiteSpace($configuredHeaders)) { 'apikey' } else { $configuredHeaders }

  foreach ($directory in @($runtimeDir, $localRuntimeDir, $productionRuntimeDir)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
  }

  Start-XamppService 'mysqld' 'mysql_start.bat' 3306 'MariaDB'
  Remove-LegacyAuthFiles
}

function Get-BackendJar {
  $jar = Get-ChildItem -LiteralPath (Join-Path $backendDir 'target') -Filter 'unimed-tools-*.jar' -File |
    Where-Object { $_.Name -notmatch '\.original$' } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

  if ($null -eq $jar) { throw "O pacote do backend nao foi encontrado em '$(Join-Path $backendDir 'target')'." }
  return $jar
}

function Build-Backend([bool]$runTests, [bool]$clean) {
  Write-Step $(if ($runTests) { 'Testando e compilando o backend' } else { 'Compilando backend local' })
  $arguments = @()
  if ($clean) { $arguments += 'clean' }
  $arguments += 'package'
  if (-not $runTests) { $arguments += '-DskipTests' }

  Push-Location $backendDir
  try { Invoke-Checked 'mvn.cmd' $arguments } finally { Pop-Location }
  return Get-BackendJar
}

function Get-JavaProcessForJar([string]$jarPath) {
  return @(
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
      Where-Object {
        if ($_.CommandLine -match '(?:^|\s)-jar\s+(?:"([^"]+)"|(\S+))') {
          $runningJar = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
          return $runningJar.Equals($jarPath, [StringComparison]::OrdinalIgnoreCase)
        }
        return $false
      }
  )
}

function Stop-Backend([string[]]$jarPaths, [string]$label) {
  foreach ($jarPath in $jarPaths) {
    foreach ($process in (Get-JavaProcessForJar $jarPath)) {
      Write-Host "Encerrando $label (PID $($process.ProcessId))..." -ForegroundColor Yellow
      Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
      Wait-Process -Id $process.ProcessId -Timeout 15 -ErrorAction SilentlyContinue
    }
  }
}

function Stop-LegacyTargetBackends {
  $targetDirectory = (Join-Path $backendDir 'target') + '\'
  $processes = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    if ($_.CommandLine -match '(?:^|\s)-jar\s+(?:"([^"]+)"|(\S+))') {
      $runningJar = if ($Matches[1]) { $Matches[1] } else { $Matches[2] }
      return $runningJar.StartsWith($targetDirectory, [StringComparison]::OrdinalIgnoreCase)
    }
    return $false
  })
  foreach ($process in $processes) {
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
  }
}

function Wait-Backend([System.Diagnostics.Process]$process, [int]$port, [string]$label) {
  $deadline = [DateTime]::UtcNow.AddSeconds(60)
  do {
    if ($process.HasExited) { throw "$label encerrou com codigo $($process.ExitCode)." }
    try {
      $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/health" -UseBasicParsing -TimeoutSec 2 -MaximumRedirection 0
      if ($response.StatusCode -eq 200 -and $response.Content.Trim() -eq 'ok') {
        Write-Host "$label respondeu /health na porta $port." -ForegroundColor Green
        return
      }
    } catch {}
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)
  throw "$label nao respondeu /health na porta $port dentro de 60 segundos."
}

function Start-Backend([System.IO.FileInfo]$sourceJar, [string]$runtimeJar, [int]$port, [string]$stdoutLog, [string]$stderrLog, [string]$label) {
  if (Test-LocalPort $port) { throw "A porta $port, reservada para $label, esta ocupada por outro processo." }
  Copy-Item -LiteralPath $sourceJar.FullName -Destination $runtimeJar -Force

  $arguments = @('-jar', ('"' + $runtimeJar + '"'), '--spring.profiles.active=local', "--server.port=$port", '--server.address=127.0.0.1')
  $process = Start-Process -FilePath $script:javaPath -ArgumentList $arguments -WorkingDirectory $backendDir -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -WindowStyle Hidden -PassThru
  Wait-Backend $process $port $label
  return $process
}

function Stop-ProcessTreeFromPidFile([string]$pidFile, [string]$label) {
  if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) { return }
  $rawPid = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
  $pidValue = 0
  if ([int]::TryParse([string]$rawPid, [ref]$pidValue) -and $pidValue -gt 0) {
    $running = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    if ($running) {
      Write-Host "Encerrando $label (PID $pidValue)..." -ForegroundColor Yellow
      & taskkill.exe /PID $pidValue /T /F 2>$null | Out-Null
    }
  }
  Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

function Start-LocalFrontend {
  Stop-ProcessTreeFromPidFile $localFrontendPidFile 'frontend local'
  if (Test-LocalPort $localFrontendPort) {
    throw "A porta $localFrontendPort esta ocupada. Encerre o processo antes de iniciar o watch mode."
  }

  $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', 'npm.cmd start -- --host 127.0.0.1 --port 4200') -WorkingDirectory $frontendDir -RedirectStandardOutput $localFrontendLog -RedirectStandardError $localFrontendErrorLog -WindowStyle Hidden -PassThru
  Set-Content -LiteralPath $localFrontendPidFile -Value $process.Id -Encoding ascii
  Wait-HttpUrl $localFrontendUrl 90 'Frontend local'
  return $process
}

function Get-PathSignature([string[]]$paths) {
  $files = @()
  foreach ($path in $paths) {
    if (Test-Path -LiteralPath $path -PathType Leaf) {
      $files += Get-Item -LiteralPath $path
    } elseif (Test-Path -LiteralPath $path -PathType Container) {
      $files += Get-ChildItem -LiteralPath $path -Recurse -File -ErrorAction SilentlyContinue
    }
  }
  if (-not $files.Count) { return '0:0:0' }
  $latest = ($files | Measure-Object -Property LastWriteTimeUtc -Maximum).Maximum
  $bytes = ($files | Measure-Object -Property Length -Sum).Sum
  return "$($files.Count):$($latest.Ticks):$bytes"
}

function Wait-SignatureStable([string[]]$paths, [string]$initial) {
  $previous = $initial
  for ($attempt = 0; $attempt -lt 8; $attempt++) {
    Start-Sleep -Milliseconds 750
    $current = Get-PathSignature $paths
    if ($current -eq $previous) { return $current }
    $previous = $current
  }
  return $previous
}

function Ensure-FrontendDependencies {
  $ngExecutable = Join-Path $frontendDir 'node_modules\.bin\ng.cmd'
  if (Test-Path -LiteralPath $ngExecutable -PathType Leaf) { return }

  Write-Step 'Instalando dependencias do frontend local'
  Push-Location $frontendDir
  try { Invoke-Checked 'npm.cmd' @('ci') } finally { Pop-Location }
}

function Start-LocalWatchMode {
  Write-Step 'Preparando ambiente LOCAL de teste'
  Write-Host 'A producao NAO sera alterada por este modo.' -ForegroundColor Yellow

  Ensure-FrontendDependencies
  $backendJar = Build-Backend $false $true
  Stop-Backend @($localBackendJar) 'backend local anterior'
  $script:localBackendProcess = Start-Backend $backendJar $localBackendJar $localBackendPort $localBackendLog $localBackendErrorLog 'Backend local'
  $script:localFrontendProcess = Start-LocalFrontend

  $backendWatchPaths = @((Join-Path $backendDir 'src'), (Join-Path $backendDir 'pom.xml'))
  $frontendSourcePaths = @((Join-Path $frontendDir 'src'), (Join-Path $frontendDir 'public'))
  $frontendConfigPaths = @(
    (Join-Path $frontendDir 'package.json'),
    (Join-Path $frontendDir 'package-lock.json'),
    (Join-Path $frontendDir 'angular.json'),
    (Join-Path $frontendDir 'proxy.conf.json'),
    (Join-Path $frontendDir 'tsconfig.json'),
    (Join-Path $frontendDir 'tsconfig.app.json')
  )

  $backendSignature = Get-PathSignature $backendWatchPaths
  $frontendSignature = Get-PathSignature $frontendSourcePaths
  $frontendConfigSignature = Get-PathSignature $frontendConfigPaths

  Write-Step 'WATCH MODE ativo'
  Write-Host "Teste local: $localFrontendUrl" -ForegroundColor Green
  Write-Host "Backend local: http://127.0.0.1:$localBackendPort" -ForegroundColor Green
  Write-Host "Producao: $productionFrontendUrl (inalterada)" -ForegroundColor Yellow
  Write-Host ''
  Write-Host 'Frontend: Angular recompila automaticamente ao salvar arquivos.' -ForegroundColor DarkGreen
  Write-Host 'Backend: o watcher compila e reinicia somente a porta local.' -ForegroundColor DarkGreen
  Write-Host 'Para publicar na rede, use "Publicar Unimed Tools - Producao.cmd".' -ForegroundColor Yellow
  Write-Host 'Use Ctrl+C para encerrar somente o ambiente local.' -ForegroundColor DarkGray
  Write-Host "Logs frontend: $localFrontendLog" -ForegroundColor DarkGray
  Write-Host "Logs backend:  $localBackendLog" -ForegroundColor DarkGray

  try {
    while ($true) {
      Start-Sleep -Seconds 2

      if ($script:localFrontendProcess -and $script:localFrontendProcess.HasExited) {
        Write-Host 'Frontend local encerrou; reiniciando...' -ForegroundColor Yellow
        $script:localFrontendProcess = Start-LocalFrontend
      }

      if ($script:localBackendProcess -and $script:localBackendProcess.HasExited) {
        Write-Host 'Backend local encerrou; reiniciando ultimo build valido...' -ForegroundColor Yellow
        $backendJar = Get-BackendJar
        $script:localBackendProcess = Start-Backend $backendJar $localBackendJar $localBackendPort $localBackendLog $localBackendErrorLog 'Backend local'
      }

      $newFrontendSignature = Get-PathSignature $frontendSourcePaths
      if ($newFrontendSignature -ne $frontendSignature) {
        $frontendSignature = Wait-SignatureStable $frontendSourcePaths $newFrontendSignature
        Write-Info 'Alteracao no frontend detectada. Angular watch esta recompilando o teste local.'
      }

      $newFrontendConfigSignature = Get-PathSignature $frontendConfigPaths
      if ($newFrontendConfigSignature -ne $frontendConfigSignature) {
        $frontendConfigSignature = Wait-SignatureStable $frontendConfigPaths $newFrontendConfigSignature
        Write-Info 'Configuracao do frontend alterada. Reinstalando dependencias e reiniciando.'
        try {
          Push-Location $frontendDir
          try { Invoke-Checked 'npm.cmd' @('ci') } finally { Pop-Location }
          $script:localFrontendProcess = Start-LocalFrontend
        } catch {
          Write-Host "Falha ao reiniciar frontend local: $($_.Exception.Message)" -ForegroundColor Red
        }
      }

      $newBackendSignature = Get-PathSignature $backendWatchPaths
      if ($newBackendSignature -ne $backendSignature) {
        $backendSignature = Wait-SignatureStable $backendWatchPaths $newBackendSignature
        Write-Info 'Alteracao no backend detectada. Preparando atualizacao local...'
        try {
          $backendJar = Build-Backend $false $false
          Stop-Backend @($localBackendJar) 'backend local'
          if (Test-LocalPort $localBackendPort) {
            throw "A porta $localBackendPort continuou ocupada depois da parada."
          }
          $script:localBackendProcess = Start-Backend $backendJar $localBackendJar $localBackendPort $localBackendLog $localBackendErrorLog 'Backend local'
          Write-Info 'Backend local atualizado com sucesso.'
        } catch {
          Write-Host "Atualizacao local rejeitada: $($_.Exception.Message)" -ForegroundColor Red
          Write-Host 'Corrija o codigo; o watcher tentara novamente na proxima alteracao.' -ForegroundColor DarkYellow
        }
      }
    }
  } finally {
    Write-Step 'Encerrando ambiente LOCAL'
    Stop-ProcessTreeFromPidFile $localFrontendPidFile 'frontend local'
    Stop-Backend @($localBackendJar) 'backend local'
    Write-Host 'Producao permaneceu intacta.' -ForegroundColor Green
  }
}

function Publish-Production {
  Write-Step 'PUBLICACAO MANUAL PARA PRODUCAO'
  Write-Host "Destino: $productionFrontendUrl" -ForegroundColor Yellow
  Write-Host 'O watch local nao promove alteracoes automaticamente.' -ForegroundColor DarkYellow

  Write-Step 'Testando e gerando frontend de producao'
  Push-Location $frontendDir
  try {
    Invoke-Checked 'npm.cmd' @('ci')
    Invoke-Checked 'npm.cmd' @('test', '--', '--watch=false')
    Invoke-Checked 'npm.cmd' @('run', 'build:lan')
  } finally {
    Pop-Location
  }

  if (-not (Test-Path -LiteralPath $frontendBuild -PathType Container)) {
    throw "O build do frontend nao foi encontrado em '$frontendBuild'."
  }

  $backendJar = Build-Backend $true $true

  Write-Step 'Promovendo versao validada para a rede'
  Stop-XamppService 'httpd' 'apache_stop.bat' 'Apache'
  Stop-Backend @($productionBackendJar, $legacyProductionBackendJar) 'backend de producao anterior'
  Stop-LegacyTargetBackends

  if (Test-LocalPort $productionBackendPort) {
    throw "A porta $productionBackendPort esta ocupada por outro processo. A publicacao foi interrompida."
  }

  if (Test-Path -LiteralPath $productionFrontendDestination -PathType Container) {
    Remove-Item -LiteralPath $productionFrontendDestination -Recurse -Force
  }
  New-Item -ItemType Directory -Path $productionFrontendDestination -Force | Out-Null
  Copy-Item -Path (Join-Path $frontendBuild '*') -Destination $productionFrontendDestination -Recurse -Force

  Start-XamppService 'httpd' 'apache_start.bat' 80 'Apache'
  $productionProcess = Start-Backend $backendJar $productionBackendJar $productionBackendPort $productionBackendLog $productionBackendErrorLog 'Backend de producao'
  Wait-HttpUrl $productionFrontendUrl 45 'Frontend de producao'

  Write-Step 'PUBLICACAO CONCLUIDA'
  Write-Host "Producao: $productionFrontendUrl" -ForegroundColor Green
  Write-Host "Backend producao: PID $($productionProcess.Id), porta $productionBackendPort" -ForegroundColor Green
  Write-Host "Logs: $productionRuntimeDir" -ForegroundColor DarkGray
}

try {
  Initialize-Environment
  if ($PublishProduction) {
    Publish-Production
    exit 0
  }

  Start-LocalWatchMode
  exit 0
} catch {
  Write-Host ''
  Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
  Write-Host "Logs locais: $localRuntimeDir" -ForegroundColor DarkYellow
  Write-Host "Logs de producao: $productionRuntimeDir" -ForegroundColor DarkYellow
  exit 1
}
