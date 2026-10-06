[CmdletBinding()]
param([switch]$Remover)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $projectRoot 'Iniciar Unimed Tools com Windows.cmd'
$startupDir = [Environment]::GetFolderPath('Startup')
$shortcutPath = Join-Path $startupDir 'Unimed Tools - Inicio automatico.lnk'
if ($Remover) {
  if (Test-Path -LiteralPath $shortcutPath) { Remove-Item -LiteralPath $shortcutPath }
  Write-Host 'Inicio automatico do Unimed Tools removido.'
  exit 0
}
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) { throw 'Inicializador automatico nao encontrado.' }
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $env:ComSpec
$shortcut.Arguments = '/c ""' + $launcher + '""'
$shortcut.WorkingDirectory = $projectRoot
$shortcut.WindowStyle = 1
$shortcut.Description = 'Publica o Unimed Tools e inicia o ambiente de testes ao entrar no Windows.'
$shortcut.Save()
Write-Host "Inicio automatico configurado para o usuario atual: $shortcutPath"
