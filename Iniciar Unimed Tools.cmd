@echo off
setlocal
title Unimed Tools - Watch Mode

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-unimed-tools.ps1"
set "UNIMED_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%UNIMED_EXIT_CODE%"=="0" (
  echo O watch mode terminou com erro. Consulte a mensagem acima.
) else (
  echo O watch mode foi encerrado pelo usuario.
)
echo.
echo Pressione qualquer tecla para fechar esta janela.
pause >nul
exit /b %UNIMED_EXIT_CODE%
