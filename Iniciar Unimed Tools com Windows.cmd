@echo off
setlocal
title Unimed Tools - Publicacao na inicializacao

rem Publicar primeiro evita dois builds Maven simultaneos na mesma pasta.
call "%~dp0Publicar Unimed Tools - Producao.cmd" /automatico
set "UNIMED_PUBLICACAO_EXIT=%ERRORLEVEL%"

start "Unimed Tools - Ambiente de Testes" /D "%~dp0" "%ComSpec%" /c ""%~dp0Iniciar Unimed Tools.cmd""
echo.
if not "%UNIMED_PUBLICACAO_EXIT%"=="0" echo A publicacao falhou. Revise a mensagem acima.
echo O ambiente de testes foi iniciado em outra janela.
echo Esta janela pode permanecer aberta para consultar o resultado da publicacao.
pause
