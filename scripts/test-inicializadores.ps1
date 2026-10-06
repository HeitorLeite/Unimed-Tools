$ErrorActionPreference = 'Stop'
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
  (Join-Path $PSScriptRoot 'iniciar-unimed-tools.ps1'), [ref]$tokens, [ref]$errors)
if ($errors) { throw 'Sintaxe invalida no publicador.' }
# Carrega apenas funcoes: nunca executa a publicacao durante estes testes.
foreach ($function in $ast.FindAll({ param($node)
  $node -is [System.Management.Automation.Language.FunctionDefinitionAst]
}, $false)) { Invoke-Expression $function.Extent.Text }

function Assert-Equal($expected, $actual, [string]$message) {
  if ($expected -ne $actual) { throw "$message (esperado=$expected, recebido=$actual)" }
}
function Start-Sleep { param($Milliseconds) }
$script:attempts = 0
function Test-LocalPort { param($port) $script:attempts++; return $script:attempts -lt 3 }
Wait-PortReleased 8080 2
Assert-Equal 3 $script:attempts 'Deve aguardar a liberacao tardia da porta'
function Test-LocalPort { param($port) return $true }
$failed = $false
try { Wait-PortReleased 8080 0 } catch { $failed = $_.Exception.Message -match 'continua ocupada' }
Assert-Equal $true $failed 'Porta de outro aplicativo deve causar erro'

$xamppDir = 'C:\xampp'
$script:alive = $true
$script:killedHelper = $false
$script:started = 0
function Get-Process {
  param($Name, $ErrorAction)
  if ($script:alive) { [pscustomobject]@{ Id = 123; Path = 'C:\xampp\apache\bin\httpd.exe' } }
}
function Start-Process {
  param($FilePath, $ArgumentList, $WorkingDirectory, $WindowStyle, [switch]$PassThru)
  Assert-Equal 'C:\xampp\apache\bin\httpd.exe' $FilePath 'Deve usar o executavel nativo'
  Assert-Equal 'Hidden' $WindowStyle 'Nao deve abrir CMD do XAMPP'
  $script:started++
  $result = [pscustomobject]@{}
  $result | Add-Member ScriptMethod WaitForExit {
    param($timeout)
    Assert-Equal 5000 $timeout 'Parada deve ter prazo limitado'
    return $script:killedHelper
  }
  $result | Add-Member ScriptMethod Kill { $script:killedHelper = $true; $script:alive = $false }
  return $result
}
Stop-XamppService 'httpd' 'Apache'
Assert-Equal $true $script:killedHelper 'Comando de parada travado deve ser encerrado'
Assert-Equal 1 $script:started 'Somente uma tentativa de parada nativa'
function Get-Process {
  param($Name, $ErrorAction)
  [pscustomobject]@{ Id = 456; Path = 'C:\OutroApache\httpd.exe' }
}
Stop-XamppService 'httpd' 'Apache'
Assert-Equal 1 $script:started 'Nao deve encerrar Apache de outra instalacao'
Write-Host 'Testes dos inicializadores passaram: espera da porta, conflito, parada limitada e isolamento do Apache.'
