@echo off
setlocal
title Unimed Tools - Publicar Producao

echo ================================================
echo   UNIMED TOOLS - PUBLICACAO MANUAL
echo ================================================
echo.
echo Este processo valida a aplicacao e publica a versao
echo atual da pasta local no endereco da rede.
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-unimed-tools.ps1" -PublishProduction
set "UNIMED_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%UNIMED_EXIT_CODE%"=="0" (
  echo A publicacao terminou com erro. A mensagem esta acima.
) else (
  echo Publicacao concluida com sucesso.
)
pause
exit /b %UNIMED_EXIT_CODE%
