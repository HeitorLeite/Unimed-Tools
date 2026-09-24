@echo off
setlocal
title Unimed Tools - Ambiente de Testes (Watch Mode)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\watch-unimed-tools.ps1"
set "UNIMED_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%UNIMED_EXIT_CODE%"=="0" (
  echo O ambiente de testes terminou com erro. Consulte a mensagem acima.
) else (
  echo Watch mode encerrado.
)
pause
exit /b %UNIMED_EXIT_CODE%
