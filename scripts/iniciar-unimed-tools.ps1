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
$localRuntimeDir = Join-Path $runtimeDir 'local'
$productionRuntimeDir = Join-Path $runtimeDir 'production'

$localBackendJar = Join-Path $localRuntimeDir 'unimed-tools-backend.jar'
$localBackendLog = Join-Path $localRuntimeDir 'backend.log'
$localBackendErrorLog = Join-Path $localRuntimeDir 'backend-error.log'
$localBackendPidFile = Join-Path $localRuntimeDir 'backend.pid'

$productionBackendJar = Join-Path $productionRuntimeDir 'unimed-tools-backend.jar'
$productionBackendLog = Join-Path $productionRuntimeDir 'backend.log'
$productionBackendErrorLog = Join-Path $productionRuntimeDir 'backend-error.log'
$productionBackendPidFile = Join-Path $productionRuntimeDir 'backend.pid'

$localFrontendUrl = 'http://localhost:4200/'
$localBackendHealthUrl = 'http://127.0.0.1:8081/health'
$lanFrontendUrl = 'http://192.168.3.242/unimed-tools/'
$productionBackendHealthUrl = 'http://127.0.0.1:8080/health'

$script:localBackendProcess = $null
$script:frontendProcess = $null
$script:javaPath = $null
$script:sguKey = $null

function Write-Step([string]$message) {
  Write-Host ''
  Write-Host "==> $message" -ForegroundColor Cyan
}

function Write-Watch([string]$message) {
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
    Start-Sleep -Milliseconds 400
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
    } catch {
      # O processo pode ainda estar iniciando.
    }
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)
  throw "$serviceName nao respondeu em '$url' dentro de $seconds segundos."
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

function Set-UnimedEnvironment {
  $env:DB_USERNAME = Get-ConfiguredValue 'DB_USERNAME'
  if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
    $env:DB_USERNAME = 'root'
  }

  $configuredDbPassword = Get-ConfiguredValue 'DB_PASSWORD'
  $env:DB_PASSWORD = if ($null -eq $configuredDbPassword) { '' } else { $configuredDbPassword }
  $env:SERVER_ADDRESS = '127.0.0.1'
  $env:SGU_API_KEY = $script:sguKey

  $configuredHeaders = Get-ConfiguredValue 'SGU_API_KEY_HEADERS'
  $env:SGU_API_KEY_HEADERS = if ([string]::IsNullOrWhiteSpace($configuredHeaders)) { 'apikey' } else { $configuredHeaders }
}

function Stop-ProcessTree([System.Diagnostics.Process]$process) {
  if ($null -eq $process) { return }

  try {
    if (-not $process.HasExited) {
      & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
  } catch {
    try {
      Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    } catch {
      # O processo ja pode ter encerrado.
    }
  }
}

function Stop-BackendFromPidFile([string]$pidFile, [string]$label) {
  if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) { return }

  $raw = (Get-Content -LiteralPath $pidFile -Raw -ErrorAction SilentlyContinue).Trim()
  $pidValue = 0

  if ([int]::TryParse($raw, [ref]$pidValue)) {
    $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "Encerrando $label (PID $pidValue)..." -ForegroundColor Yellow
      Stop-ProcessTree $process
    }
  }

  Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

function Wait-UnimedBackend([System.Diagnostics.Process]$backendProcess, [string]$healthUrl, [string]$label) {
  $deadline = [DateTime]::UtcNow.AddSeconds(75)

  do {
    if ($backendProcess.HasExited) {
      throw "$label encerrou com codigo $($backendProcess.ExitCode)."
    }

    try {
      $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2 -MaximumRedirection 0
      if ($response.StatusCode -eq 200 -and $response.Content.Trim() -eq 'ok' -and -not $backendProcess.HasExited) {
        Write-Host "$label respondeu /health com sucesso." -ForegroundColor Green
        return
      }
    } catch {
      # A porta pode abrir antes de o Spring terminar de iniciar.
    }

    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)

  throw "$label nao respondeu /health dentro de 75 segundos."
}

function Get-BackendJar {
  $jar = Get-ChildItem -LiteralPath (Join-Path $backendDir 'target') -Filter 'unimed-tools-*.jar' -File |
    Where-Object { $_.Name -notmatch '\.original$' } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1

  if ($null -eq $jar) {
    throw "O pacote do backend nao foi encontrado em '$(Join-Path $backendDir 'target')'."
  }

  return $jar
}

function Build-Backend([switch]$RunTests) {
  Remove-LegacyAuthFiles

  Push-Location $backendDir
  try {
    if ($RunTests) {
      Invoke-Checked 'mvn.cmd' @('clean', 'package')
    } else {
      Invoke-Checked 'mvn.cmd' @('-DskipTests', 'package')
    }
  } finally {
    Pop-Location
  }

  return Get-BackendJar
}

function Start-LocalBackend([System.IO.FileInfo]$jar) {
  Set-UnimedEnvironment
  New-Item -ItemType Directory -Path $localRuntimeDir -Force | Out-Null

  Stop-BackendFromPidFile $localBackendPidFile 'backend local'

  if (Test-LocalPort 8081) {
    throw 'A porta 8081 esta ocupada por outro processo. Libere a porta do ambiente de teste.'
  }

  Copy-Item -LiteralPath $jar.FullName -Destination $localBackendJar -Force

  $script:localBackendProcess = Start-Process -FilePath $script:javaPath -ArgumentList @('-jar', ('"' + $localBackendJar + '"'), '--spring.profiles.active=local', '--server.port=8081') -WorkingDirectory $backendDir -RedirectStandardOutput $localBackendLog -RedirectStandardError $localBackendErrorLog -WindowStyle Hidden -PassThru

  Set-Content -LiteralPath $localBackendPidFile -Value $script:localBackendProcess.Id -Encoding ascii
  Wait-UnimedBackend $script:localBackendProcess $localBackendHealthUrl 'Backend local'
}

function Rebuild-LocalBackend {
  Write-Step 'Alteracao no backend detectada - atualizando ambiente local'

  try {
    $jar = Build-Backend
    Start-LocalBackend $jar
    Write-Watch 'Backend local atualizado automaticamente.'
  } catch {
    Write-Host "Falha ao atualizar backend local: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'O watcher continua ativo; corrija o codigo e salve novamente.' -ForegroundColor DarkYellow
  }
}

function Start-LocalFrontend {
  if ($script:frontendProcess -and -not $script:frontendProcess.HasExited) { return }

  if (Test-LocalPort 4200) {
    throw 'A porta 4200 ja esta ocupada. Feche o processo atual antes de iniciar o watcher.'
  }

  Write-Step 'Iniciando frontend local em watch mode'

  $script:frontendProcess = Start-Process -FilePath 'npm.cmd' -ArgumentList @('run', 'start:local') -WorkingDirectory $frontendDir -NoNewWindow -PassThru
  Wait-HttpUrl $localFrontendUrl 90 'Frontend local'
}

function Publish-Production {
  Write-Step 'Publicando versao atual para a rede'
  Write-Host 'A publicacao de rede e manual e nao altera o GitHub.' -ForegroundColor Yellow

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

  $jar = Build-Backend -RunTests
  Set-UnimedEnvironment

  New-Item -ItemType Directory -Path $productionRuntimeDir -Force | Out-Null
  Stop-BackendFromPidFile $productionBackendPidFile 'backend da rede'

  if (Test-LocalPort 8080) {
    throw 'A porta 8080 esta ocupada por um processo que nao foi iniciado por este watcher. Encerre o backend antigo antes de publicar.'
  }

  Copy-Item -LiteralPath $jar.FullName -Destination $productionBackendJar -Force

  if (Test-Path -LiteralPath $frontendDestination -PathType Container) {
    Remove-Item -LiteralPath $frontendDestination -Recurse -Force
  }

  New-Item -ItemType Directory -Path $frontendDestination -Force | Out-Null
  Copy-Item -Path (Join-Path $frontendBuild '*') -Destination $frontendDestination -Recurse -Force

  Start-XamppService 'httpd' 'apache_start.bat' 80 'Apache'

  $productionBackend = Start-Process -FilePath $script:javaPath -ArgumentList @('-jar', ('"' + $productionBackendJar + '"'), '--spring.profiles.active=local', '--server.port=8080') -WorkingDirectory $backendDir -RedirectStandardOutput $productionBackendLog -RedirectStandardError $productionBackendErrorLog -WindowStyle Hidden -PassThru

  Set-Content -LiteralPath $productionBackendPidFile -Value $productionBackend.Id -Encoding ascii
  Wait-UnimedBackend $productionBackend $productionBackendHealthUrl 'Backend da rede'
  Wait-HttpUrl $lanFrontendUrl 45 'Frontend da rede'

  Write-Host ''
  Write-Host 'Publicacao concluida.' -ForegroundColor Green
  Write-Host "Rede: $lanFrontendUrl" -ForegroundColor Green
}

function Run-FullTests {
  Write-Step 'Executando validacao completa sem publicar'

  Push-Location $frontendDir
  try {
    Invoke-Checked 'npm.cmd' @('test', '--', '--watch=false')
    Invoke-Checked 'npm.cmd' @('run', 'build')
  } finally {
    Pop-Location
  }

  [void](Build-Backend -RunTests)
  Write-Host 'Frontend e backend validados com sucesso.' -ForegroundColor Green
}

function New-Watcher([string]$path, [string]$filter, [bool]$includeSubdirectories) {
  $watcher = [System.IO.FileSystemWatcher]::new()
  $watcher.Path = $path
  $watcher.Filter = $filter
  $watcher.IncludeSubdirectories = $includeSubdirectories
  $watcher.NotifyFilter = [IO.NotifyFilters]'FileName, LastWrite, DirectoryName'
  $watcher.EnableRaisingEvents = $true
  return $watcher
}

function Register-WatcherEvents([System.IO.FileSystemWatcher]$watcher, [string]$prefix) {
  $registrations = @()

  foreach ($eventName in @('Changed', 'Created', 'Deleted', 'Renamed')) {
    $source = "$prefix-$eventName"
    $registrations += Register-ObjectEvent -InputObject $watcher -EventName $eventName -SourceIdentifier $source
  }

  return $registrations
}

function Test-BackendWatchPath([string]$fullPath) {
  if ([string]::IsNullOrWhiteSpace($fullPath)) { return $false }

  if ($fullPath.StartsWith((Join-Path $backendDir 'target'), [StringComparison]::OrdinalIgnoreCase)) {
    return $false
  }

  $name = [IO.Path]::GetFileName($fullPath)
  $extension = [IO.Path]::GetExtension($fullPath).ToLowerInvariant()

  return $name -eq 'pom.xml' -or $extension -in @('.java', '.xml', '.properties', '.yml', '.yaml')
}

function Show-WatcherHelp {
  Write-Host ''
  Write-Host 'Atalhos do watcher:' -ForegroundColor Cyan
  Write-Host '  P  Publicar a versao atual em 192.168.3.242' -ForegroundColor Gray
  Write-Host '  R  Recompilar e reiniciar o backend LOCAL agora' -ForegroundColor Gray
  Write-Host '  T  Executar todos os testes sem publicar' -ForegroundColor Gray
  Write-Host '  H  Mostrar estes atalhos novamente' -ForegroundColor Gray
  Write-Host '  Q  Encerrar somente o ambiente local e o watcher' -ForegroundColor Gray
  Write-Host ''
}

$watchers = @()
$registrations = @()

try {
  Write-Step 'Validando requisitos e configuracoes'

  foreach ($command in @('npm.cmd', 'mvn.cmd')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
      throw "O comando '$command' nao foi encontrado no PATH."
    }
  }

  if (-not (Test-Path -LiteralPath $frontendDir -PathType Container) -or -not (Test-Path -LiteralPath $backendDir -PathType Container)) {
    throw 'As pastas do frontend e do backend nao foram encontradas ao lado do iniciador.'
  }

  $env:JAVA_HOME = Get-Jdk21
  $script:javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"

  $script:sguKey = Get-ConfiguredValue 'SGU_API_KEY'
  if ([string]::IsNullOrWhiteSpace($script:sguKey)) {
    throw 'Configure SGU_API_KEY nas variaveis de ambiente do usuario antes de iniciar.'
  }

  New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
  New-Item -ItemType Directory -Path $localRuntimeDir -Force | Out-Null
  New-Item -ItemType Directory -Path $productionRuntimeDir -Force | Out-Null

  Write-Host "JDK 21 selecionado: $env:JAVA_HOME" -ForegroundColor DarkGreen

  Write-Step 'Preparando servicos compartilhados'
  Start-XamppService 'mysqld' 'mysql_start.bat' 3306 'MariaDB'

  Write-Step 'Preparando ambiente LOCAL de testes'
  $initialJar = Build-Backend
  Start-LocalBackend $initialJar
  Start-LocalFrontend

  Write-Host ''
  Write-Host 'Ambientes separados:' -ForegroundColor Green
  Write-Host "  Teste local: $localFrontendUrl" -ForegroundColor Green
  Write-Host "  Rede:        $lanFrontendUrl" -ForegroundColor DarkGreen
  Write-Host ''
  Write-Host 'Alteracoes no frontend sao recompiladas automaticamente pelo Angular. Alteracoes no backend reiniciam somente o backend local.' -ForegroundColor Gray
  Write-Host 'Nada e enviado automaticamente para a rede. Pressione P quando quiser publicar.' -ForegroundColor Yellow

  $backendWatcher = New-Watcher $backendDir '*.*' $true
  $frontendWatcher = New-Watcher (Join-Path $frontendDir 'src') '*.*' $true
  $watchers = @($backendWatcher, $frontendWatcher)

  $registrations += Register-WatcherEvents $backendWatcher 'UnimedBackend'
  $registrations += Register-WatcherEvents $frontendWatcher 'UnimedFrontend'

  Show-WatcherHelp

  $backendPendingAt = $null
  $frontendPendingAt = $null
  $running = $true

  while ($running) {
    foreach ($event in @(Get-Event)) {
      try {
        $sourceIdentifier = [string]$event.SourceIdentifier
        $fullPath = [string]$event.SourceEventArgs.FullPath

        if ($sourceIdentifier.StartsWith('UnimedBackend')) {
          if (Test-BackendWatchPath $fullPath) {
            $backendPendingAt = [DateTime]::UtcNow
          }
        } elseif ($sourceIdentifier.StartsWith('UnimedFrontend')) {
          $frontendPendingAt = [DateTime]::UtcNow
        }
      } finally {
        Remove-Event -EventIdentifier $event.EventIdentifier -ErrorAction SilentlyContinue
      }
    }

    if ($null -ne $backendPendingAt -and ([DateTime]::UtcNow - $backendPendingAt).TotalMilliseconds -ge 1200) {
      $backendPendingAt = $null
      Rebuild-LocalBackend
    }

    if ($null -ne $frontendPendingAt -and ([DateTime]::UtcNow - $frontendPendingAt).TotalMilliseconds -ge 900) {
      $frontendPendingAt = $null
      Write-Watch 'Alteracao no frontend detectada; o Angular esta recompilando o localhost.'
    }

    if ($script:frontendProcess -and $script:frontendProcess.HasExited) {
      Write-Host 'O servidor Angular local encerrou inesperadamente.' -ForegroundColor Red
      Write-Host 'Pressione Q para sair ou corrija o problema e reinicie o atalho.' -ForegroundColor DarkYellow
      $script:frontendProcess = $null
    }

    if ([Console]::KeyAvailable) {
      $key = [Console]::ReadKey($true).Key

      switch ($key) {
        'P' {
          try {
            Publish-Production
          } catch {
            Write-Host "Falha na publicacao: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host 'O ambiente local e o watcher continuam ativos.' -ForegroundColor DarkYellow
          }
        }
        'R' {
          Rebuild-LocalBackend
        }
        'T' {
          try {
            Run-FullTests
          } catch {
            Write-Host "Validacao falhou: $($_.Exception.Message)" -ForegroundColor Red
          }
        }
        'H' {
          Show-WatcherHelp
        }
        'Q' {
          $running = $false
        }
      }
    }

    Start-Sleep -Milliseconds 200
  }

  Write-Step 'Encerrando ambiente local'
} catch {
  Write-Host ''
  Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
  Write-Host "Logs locais: $localRuntimeDir" -ForegroundColor DarkYellow
  exit 1
} finally {
  foreach ($registration in $registrations) {
    try {
      Unregister-Event -SubscriptionId $registration.Id -ErrorAction SilentlyContinue
    } catch {
      # Ignora assinaturas que ja tenham sido encerradas.
    }
  }

  foreach ($watcher in $watchers) {
    try {
      $watcher.EnableRaisingEvents = $false
      $watcher.Dispose()
    } catch {
      # Ignora watcher ja descartado.
    }
  }

  Stop-ProcessTree $script:frontendProcess
  Stop-BackendFromPidFile $localBackendPidFile 'backend local'
}

Write-Host ''
Write-Host 'Watcher encerrado. A versao publicada na rede, se houver, continua ativa.' -ForegroundColor Green
exit 0
