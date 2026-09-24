[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot 'unimed-tools-frontend'
$backendDir = Join-Path $projectRoot 'unimed-tools-backend'
$runtimeDir = Join-Path $env:LOCALAPPDATA 'UnimedTools\watch'
$backendPidFile = Join-Path $runtimeDir 'backend-test.pid'
$frontendPidFile = Join-Path $runtimeDir 'frontend-test.pid'
$backendRuntimeJar = Join-Path $runtimeDir 'unimed-tools-backend-test.jar'
$backendStagedJar = Join-Path $runtimeDir 'unimed-tools-backend-test-next.jar'
$xamppDir = 'C:\xampp'
$testFrontendUrl = 'http://localhost:4200/'
$testBackendUrl = 'http://127.0.0.1:8081/health'
$productionUrl = 'http://192.168.3.242/unimed-tools/'

function Write-Step([string]$message) {
  Write-Host ""
  Write-Host "==> $message" -ForegroundColor Cyan
}

function Get-ConfiguredValue([string]$name) {
  foreach ($scope in @('Process', 'User', 'Machine')) {
    $value = [Environment]::GetEnvironmentVariable($name, $scope)
    if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
  }
  return $null
}

function Test-Jdk21([string]$directory) {
  if ([string]::IsNullOrWhiteSpace($directory)) { return $false }
  foreach ($file in @('bin\java.exe', 'bin\javac.exe', 'release')) {
    if (-not (Test-Path -LiteralPath (Join-Path $directory $file) -PathType Leaf)) { return $false }
  }
  return [bool](Select-String -LiteralPath (Join-Path $directory 'release') -Pattern '^JAVA_VERSION="21(?:\.|"|\+|-)' -Quiet)
}

function Get-Jdk21 {
  $configured = Get-ConfiguredValue 'JAVA_HOME'
  if (Test-Jdk21 $configured) { return $configured }

  $compiler = Get-Command 'javac.exe' -ErrorAction SilentlyContinue
  if ($compiler) {
    $compilerHome = Split-Path -Parent (Split-Path -Parent $compiler.Source)
    if (Test-Jdk21 $compilerHome) { return $compilerHome }
  }

  foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'Zulu')) {
    $vendorDir = Join-Path $env:ProgramFiles $vendor
    if (-not (Test-Path -LiteralPath $vendorDir -PathType Container)) { continue }
    foreach ($candidate in (Get-ChildItem -LiteralPath $vendorDir -Directory | Sort-Object Name -Descending)) {
      if (Test-Jdk21 $candidate.FullName) { return $candidate.FullName }
    }
  }
  throw 'JDK 21 nao encontrado. Configure JAVA_HOME ou instale um JDK 21.'
}

function Invoke-Checked([string]$command, [string[]]$arguments, [string]$workingDirectory) {
  Push-Location $workingDirectory
  try {
    & $command @arguments
    if ($LASTEXITCODE -ne 0) {
      throw "O comando '$command $($arguments -join ' ')' terminou com codigo $LASTEXITCODE."
    }
  } finally {
    Pop-Location
  }
}

function Test-LocalPort([int]$port) {
  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $connection = $client.ConnectAsync('127.0.0.1', $port)
    return $connection.Wait(700) -and $client.Connected
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Start-MariaDB {
  if (Test-LocalPort 3306) {
    Write-Host 'MariaDB ja esta disponivel na porta 3306.' -ForegroundColor DarkGreen
    return
  }

  $scriptPath = Join-Path $xamppDir 'mysql_start.bat'
  if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw "MariaDB nao esta ativo e '$scriptPath' nao foi encontrado."
  }

  Write-Host 'Iniciando MariaDB do XAMPP...' -ForegroundColor Yellow
  Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', ('"' + $scriptPath + '"')) -WorkingDirectory $xamppDir -WindowStyle Hidden | Out-Null

  $deadline = [DateTime]::UtcNow.AddSeconds(30)
  do {
    if (Test-LocalPort 3306) {
      Write-Host 'MariaDB pronto para o ambiente de testes.' -ForegroundColor Green
      return
    }
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)

  throw 'MariaDB nao respondeu na porta 3306 em 30 segundos.'
}

function Stop-TestFrontend {
  if (-not (Test-Path -LiteralPath $frontendPidFile -PathType Leaf)) { return }
  $pidValue = Get-Content -LiteralPath $frontendPidFile -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($pidValue -match '^\d+$') {
    $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "Encerrando frontend de teste (PID $pidValue)..." -ForegroundColor Yellow
      & taskkill.exe /PID $pidValue /T /F | Out-Null
    }
  }
  Remove-Item -LiteralPath $frontendPidFile -Force -ErrorAction SilentlyContinue
}

function Stop-TestBackend {
  if (-not (Test-Path -LiteralPath $backendPidFile -PathType Leaf)) { return }
  $pidValue = Get-Content -LiteralPath $backendPidFile -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($pidValue -match '^\d+$') {
    $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "Encerrando backend de teste (PID $pidValue)..." -ForegroundColor Yellow
      Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
      Wait-Process -Id ([int]$pidValue) -Timeout 10 -ErrorAction SilentlyContinue
    }
  }
  Remove-Item -LiteralPath $backendPidFile -Force -ErrorAction SilentlyContinue
}

function Wait-Backend([System.Diagnostics.Process]$process) {
  $deadline = [DateTime]::UtcNow.AddSeconds(60)
  do {
    if ($process.HasExited) {
      throw "O backend de teste encerrou com codigo $($process.ExitCode)."
    }
    try {
      $response = Invoke-WebRequest -Uri $testBackendUrl -UseBasicParsing -TimeoutSec 2
      if ($response.StatusCode -eq 200 -and $response.Content.Trim() -eq 'ok') {
        Write-Host 'Backend de teste pronto em http://127.0.0.1:8081.' -ForegroundColor Green
        return
      }
    } catch {}
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)
  throw 'O backend de teste nao respondeu /health em 60 segundos.'
}

function Build-Backend([bool]$runTests) {
  if ($runTests) { Write-Step 'Validando e compilando backend de teste' }
  else { Write-Step 'Recompilando backend alterado' }

  $arguments = if ($runTests) { @('clean', 'package') } else { @('package', '-DskipTests') }
  Invoke-Checked 'mvn.cmd' $arguments $backendDir

  $jar = Get-ChildItem -LiteralPath (Join-Path $backendDir 'target') -Filter 'unimed-tools-*.jar' -File |
    Where-Object { $_.Name -notmatch '\.original$' } |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
  if ($null -eq $jar) { throw 'O JAR do backend nao foi encontrado depois da compilacao.' }
  Copy-Item -LiteralPath $jar.FullName -Destination $backendStagedJar -Force
}

function Start-TestBackend {
  Stop-TestBackend
  if (-not (Test-Path -LiteralPath $backendStagedJar -PathType Leaf)) {
    throw 'O JAR preparado do backend de teste nao foi encontrado.'
  }
  Copy-Item -LiteralPath $backendStagedJar -Destination $backendRuntimeJar -Force
  if (Test-LocalPort 8081) {
    throw 'A porta 8081 ja esta ocupada. Encerre o processo antes de iniciar o watch mode.'
  }

  $env:RELATORIO_PERSONALIZADO_API_NOME = '0090-relatorio-personalizado-dev'
  $env:RELATORIO_HOSPITAL_API_NOME = '0090-hospital-autorizacoes-dev'
  $env:SERVER_ADDRESS = '127.0.0.1'
  $env:SGU_API_KEY = Get-ConfiguredValue 'SGU_API_KEY'
  $env:SGU_API_KEY_HEADERS = Get-ConfiguredValue 'SGU_API_KEY_HEADERS'
  if ([string]::IsNullOrWhiteSpace($env:SGU_API_KEY_HEADERS)) { $env:SGU_API_KEY_HEADERS = 'apikey' }

  $env:DB_USERNAME = Get-ConfiguredValue 'DB_USERNAME'
  if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { $env:DB_USERNAME = 'root' }
  $dbPassword = Get-ConfiguredValue 'DB_PASSWORD'
  $env:DB_PASSWORD = if ($null -eq $dbPassword) { '' } else { $dbPassword }

  $javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
  $process = Start-Process -FilePath $javaPath -ArgumentList @('-jar', $backendRuntimeJar, '--spring.profiles.active=local', '--server.port=8081', '--server.address=127.0.0.1') -WorkingDirectory $backendDir -NoNewWindow -PassThru
  Set-Content -LiteralPath $backendPidFile -Value $process.Id -Encoding ascii
  Wait-Backend $process
  return $process
}

function Get-BackendStamp {
  $files = @(
    Get-ChildItem -LiteralPath (Join-Path $backendDir 'src') -Recurse -File -ErrorAction SilentlyContinue
    Get-Item -LiteralPath (Join-Path $backendDir 'pom.xml') -ErrorAction SilentlyContinue
  ) | Where-Object { $_ -ne $null }

  if (-not $files.Count) { return [DateTime]::MinValue }
  return ($files | Measure-Object -Property LastWriteTimeUtc -Maximum).Maximum
}

function Get-FrontendConfigStamp {
  $files = @(
    Get-Item -LiteralPath (Join-Path $frontendDir 'package.json') -ErrorAction SilentlyContinue
    Get-Item -LiteralPath (Join-Path $frontendDir 'package-lock.json') -ErrorAction SilentlyContinue
    Get-Item -LiteralPath (Join-Path $frontendDir 'angular.json') -ErrorAction SilentlyContinue
    Get-Item -LiteralPath (Join-Path $frontendDir 'proxy.conf.json') -ErrorAction SilentlyContinue
    Get-ChildItem -LiteralPath $frontendDir -Filter 'tsconfig*.json' -File -ErrorAction SilentlyContinue
  ) | Where-Object { $_ -ne $null }

  if (-not $files.Count) { return [DateTime]::MinValue }
  return ($files | Measure-Object -Property LastWriteTimeUtc -Maximum).Maximum
}

function Ensure-FrontendDependencies {
  if (Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules') -PathType Container) {
    return
  }
  Write-Step 'Instalando dependencias do frontend'
  Invoke-Checked 'npm.cmd' @('ci') $frontendDir
}

function Start-FrontendWatch {
  Stop-TestFrontend
  Ensure-FrontendDependencies
  if (Test-LocalPort 4200) {
    throw 'A porta 4200 ja esta ocupada. Encerre o processo antes de iniciar o frontend de teste.'
  }

  Write-Step 'Iniciando frontend de teste em watch mode'
  $process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', 'npm.cmd start -- --host 127.0.0.1 --port 4200') -WorkingDirectory $frontendDir -NoNewWindow -PassThru
  Set-Content -LiteralPath $frontendPidFile -Value $process.Id -Encoding ascii

  $deadline = [DateTime]::UtcNow.AddSeconds(90)
  do {
    if ($process.HasExited) {
      throw "O frontend de teste encerrou com codigo $($process.ExitCode)."
    }
    if (Test-LocalPort 4200) {
      Write-Host "Frontend de teste pronto em $testFrontendUrl" -ForegroundColor Green
      return $process
    }
    Start-Sleep -Milliseconds 500
  } while ([DateTime]::UtcNow -lt $deadline)
  throw 'O frontend de teste nao respondeu na porta 4200 em 90 segundos.'
}

$frontendProcess = $null
$backendProcess = $null

try {
  Write-Step 'Preparando ambiente local de testes'
  foreach ($command in @('npm.cmd', 'mvn.cmd')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
      throw "O comando '$command' nao foi encontrado no PATH."
    }
  }
  if (-not (Test-Path -LiteralPath $frontendDir -PathType Container) -or -not (Test-Path -LiteralPath $backendDir -PathType Container)) {
    throw 'Frontend ou backend nao foram encontrados na pasta da aplicacao.'
  }

  $env:JAVA_HOME = Get-Jdk21
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  if ([string]::IsNullOrWhiteSpace((Get-ConfiguredValue 'SGU_API_KEY'))) {
    throw 'Configure SGU_API_KEY antes de iniciar o ambiente de testes.'
  }

  New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
  Start-MariaDB

  Build-Backend $true
  $backendProcess = Start-TestBackend
  $frontendProcess = Start-FrontendWatch
  $backendStamp = Get-BackendStamp
  $frontendConfigStamp = Get-FrontendConfigStamp

  Write-Step 'Watch mode ativo'
  Write-Host "TESTE:    $testFrontendUrl" -ForegroundColor Green
  Write-Host "PRODUCAO: $productionUrl" -ForegroundColor DarkYellow
  Write-Host 'Frontend: Angular recompila automaticamente a cada arquivo salvo.' -ForegroundColor Gray
  Write-Host 'Backend: alteracoes recompilam e reiniciam somente a porta 8081.' -ForegroundColor Gray
  Write-Host 'GitHub e producao nunca sao atualizados automaticamente.' -ForegroundColor Gray
  Write-Host 'Pressione Ctrl+C para encerrar o ambiente de testes.' -ForegroundColor Cyan

  while ($true) {
    Start-Sleep -Seconds 2

    if ($frontendProcess.HasExited) {
      throw "O frontend de teste encerrou inesperadamente com codigo $($frontendProcess.ExitCode)."
    }
    if ($backendProcess.HasExited) {
      Write-Host 'Backend de teste encerrou. Tentando iniciar novamente...' -ForegroundColor Yellow
      $backendProcess = Start-TestBackend
    }

    $novoFrontendConfigStamp = Get-FrontendConfigStamp
    if ($novoFrontendConfigStamp -gt $frontendConfigStamp) {
      Start-Sleep -Milliseconds 800
      Write-Step 'Configuracao do frontend alterada'
      try {
        if ((Get-Item -LiteralPath (Join-Path $frontendDir 'package-lock.json') -ErrorAction SilentlyContinue).LastWriteTimeUtc -ge $frontendConfigStamp) {
          Write-Host 'package-lock.json alterado; sincronizando dependencias...' -ForegroundColor Yellow
          Invoke-Checked 'npm.cmd' @('ci') $frontendDir
        }
        $frontendProcess = Start-FrontendWatch
        $frontendConfigStamp = Get-FrontendConfigStamp
        Write-Host 'Frontend de teste reiniciado com a nova configuracao.' -ForegroundColor Green
      } catch {
        Write-Host "Falha ao reiniciar frontend de teste: $($_.Exception.Message)" -ForegroundColor Red
        $frontendConfigStamp = $novoFrontendConfigStamp
      }
    }

    $novoStamp = Get-BackendStamp
    if ($novoStamp -gt $backendStamp) {
      Start-Sleep -Milliseconds 1200
      $novoStamp = Get-BackendStamp
      Write-Step 'Mudanca detectada no backend'
      try {
        Build-Backend $false
        $backendProcess = Start-TestBackend
        $backendStamp = $novoStamp
        Write-Host 'Backend de teste atualizado sem tocar na producao.' -ForegroundColor Green
      } catch {
        Write-Host "Falha ao aplicar mudanca do backend: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host 'O watcher continuara ativo para a proxima alteracao.' -ForegroundColor Yellow
        $backendStamp = $novoStamp
      }
    }
  }
} catch {
  Write-Host ""
  Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
  exit 1
} finally {
  Stop-TestFrontend
  Stop-TestBackend
}
