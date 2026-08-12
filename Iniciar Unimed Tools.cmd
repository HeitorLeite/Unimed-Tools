@echo off
setlocal
title Atualizar e iniciar Unimed Tools

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-unimed-tools.ps1"
set "UNIMED_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%UNIMED_EXIT_CODE%"=="0" (
  echo A inicializacao terminou com erro. Consulte a mensagem acima.
) else (
  echo Processo concluido. Esta janela pode ser fechada.
)
pause
exit /b %UNIMED_EXIT_CODE%
