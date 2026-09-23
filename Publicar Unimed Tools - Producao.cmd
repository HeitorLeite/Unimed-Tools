@echo off
setlocal
title Unimed Tools - Publicacao de Producao

echo ============================================================
echo  PUBLICACAO MANUAL DE PRODUCAO
echo  Destino: http://192.168.3.242/unimed-tools/
echo ============================================================
echo.
echo Este comando recompila, testa e publica a versao atual da pasta.
echo O watch mode de testes NAO executa esta publicacao automaticamente.
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-unimed-tools.ps1"
set "UNIMED_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%UNIMED_EXIT_CODE%"=="0" (
  echo A publicacao de producao terminou com erro. Consulte a mensagem acima.
) else (
  echo Publicacao de producao concluida.
)
pause
exit /b %UNIMED_EXIT_CODE%
