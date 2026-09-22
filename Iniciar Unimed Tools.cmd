@echo off
setlocal
title Unimed Tools - Ambiente Local (Watch Mode)

echo ================================================
echo   UNIMED TOOLS - AMBIENTE LOCAL / TESTE
echo ================================================
echo.
echo Este modo acompanha alteracoes na pasta local.
echo A producao da rede NAO sera atualizada automaticamente.
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-unimed-tools.ps1"
set "UNIMED_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%UNIMED_EXIT_CODE%"=="0" (
  echo O watch mode terminou com erro. Consulte a mensagem acima.
) else (
  echo Ambiente local encerrado.
)
pause
exit /b %UNIMED_EXIT_CODE%
