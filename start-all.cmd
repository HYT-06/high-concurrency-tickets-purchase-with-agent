@echo off
REM ============================================================
REM  12306 service launcher (Windows CMD wrapper)
REM  Delegates to start-all.sh via Git Bash.
REM
REM  Usage:
REM    start-all.cmd              start all services in order
REM    start-all.cmd stop         stop all services
REM    start-all.cmd restart      restart all services
REM    start-all.cmd status       show service status
REM    start-all.cmd build        build only
REM    start-all.cmd logs demo    tail one service log
REM ============================================================
setlocal

chcp 65001 >nul 2>&1

set "BASH_EXE="
if exist "%ProgramFiles%\Git\bin\bash.exe"                  set "BASH_EXE=%ProgramFiles%\Git\bin\bash.exe"
if not defined BASH_EXE if exist "%ProgramFiles(x86)%\Git\bin\bash.exe" set "BASH_EXE=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not defined BASH_EXE if exist "%LOCALAPPDATA%\Programs\Git\bin\bash.exe" set "BASH_EXE=%LOCALAPPDATA%\Programs\Git\bin\bash.exe"
if not defined BASH_EXE for /f "delims=" %%i in ('where bash 2^>nul') do if not defined BASH_EXE set "BASH_EXE=%%i"

if not defined BASH_EXE (
    echo [ERROR] Git Bash not found. Install Git for Windows, or run start-all.sh from Git Bash directly.
    exit /b 1
)

"%BASH_EXE%" "%~dp0start-all.sh" %*
set "RC=%ERRORLEVEL%"

if "%~1"=="" if "%RC%"=="0" pause
exit /b %RC%
