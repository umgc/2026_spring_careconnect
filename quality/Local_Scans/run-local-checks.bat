@echo off
REM File: quality/local/run-local-checks.bat
REM ==========================================================
REM CareConnect Local Gate Check — Windows Launcher
REM ----------------------------------------------------------
REM Launches run-local-checks.sh via Git Bash.
REM Git for Windows must be installed.
REM
REM Usage:
REM   Double-click this file, or run from CMD/PowerShell:
REM   quality\local\run-local-checks.bat
REM ==========================================================

setlocal

REM ----------------------------------------------------------
REM Locate Git Bash
REM ----------------------------------------------------------

REM Check common Git for Windows install locations
set "GIT_BASH="

if exist "C:\Program Files\Git\bin\bash.exe" (
    set "GIT_BASH=C:\Program Files\Git\bin\bash.exe"
    goto :found
)

if exist "C:\Program Files (x86)\Git\bin\bash.exe" (
    set "GIT_BASH=C:\Program Files (x86)\Git\bin\bash.exe"
    goto :found
)

REM Try to find git.exe on PATH and derive bash location from it
for /f "delims=" %%i in ('where git 2^>nul') do (
    set "GIT_PATH=%%i"
    goto :try_derive
)

:try_derive
if defined GIT_PATH (
    REM git.exe is typically at <root>\cmd\git.exe
    REM bash.exe is at        <root>\bin\bash.exe
    for %%i in ("%GIT_PATH%") do set "GIT_ROOT=%%~dpi.."
    if exist "%GIT_ROOT%\bin\bash.exe" (
        set "GIT_BASH=%GIT_ROOT%\bin\bash.exe"
        goto :found
    )
)

REM Not found
echo.
echo  ERROR: Git Bash not found.
echo.
echo  Please install Git for Windows from https://git-scm.com
echo  and re-run this script.
echo.
pause
exit /b 1

:found
echo.
echo  Using Git Bash: %GIT_BASH%
echo.

REM ----------------------------------------------------------
REM Resolve paths
REM ----------------------------------------------------------

REM Get the directory containing this .bat file
set "BAT_DIR=%~dp0"

REM Convert Windows path to Unix-style for bash
REM e.g. C:\Users\... -> /c/Users/...
set "SCRIPT_WIN=%BAT_DIR%run-local-checks.sh"

REM Use bash to convert the path itself (most reliable)
for /f "delims=" %%i in ('"%GIT_BASH%" -c "pwd" 2^>nul') do set "DUMMY=%%i"

REM ----------------------------------------------------------
REM Launch the script in Git Bash
REM ----------------------------------------------------------

"%GIT_BASH%" --login -i -c "cd '%BAT_DIR%' && sh run-local-checks.sh"

REM Capture exit code
set EXIT_CODE=%ERRORLEVEL%

echo.
if %EXIT_CODE% equ 0 (
    echo  Gate passed. Press any key to close.
) else (
    echo  Gate failed. Press any key to close.
)

pause
exit /b %EXIT_CODE%
